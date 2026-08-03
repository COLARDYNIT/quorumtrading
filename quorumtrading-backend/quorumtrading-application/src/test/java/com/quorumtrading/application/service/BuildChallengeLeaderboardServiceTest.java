package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.dto.ChallengeLeaderboard;
import com.quorumtrading.application.usecase.query.BuildChallengeLeaderboardQuery;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The challenge leaderboard used to be assembled independently by ChallengeResource and by the
 * MCP ChallengeTools, and the two disagreed: MCP passed no contributions, so a DCA top-up read
 * as profit and inflated that entry's return. These tests pin the shared behaviour both
 * adapters now render.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildChallengeLeaderboardServiceTest {

    private static final ChallengeId CHALLENGE_ID = new ChallengeId(UUID.randomUUID());
    private static final UserId ALICE = new UserId(UUID.randomUUID());
    private static final UserId BOB = new UserId(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private ChallengeRepository challengeRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private SimulationContributionRepository contributionRepository;

    private BuildChallengeLeaderboardService service;

    @BeforeEach
    void setUp() {
        service = new BuildChallengeLeaderboardService(challengeRepository, simulationRepository,
            strategyRepository, userRepository, portfolioRepository, contributionRepository);

        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge()));
        when(portfolioRepository.findLatestBySimulationId(any())).thenReturn(Optional.empty());
        when(contributionRepository.findBySimulationId(any())).thenReturn(List.of());
        when(strategyRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findById(ALICE)).thenReturn(Optional.of(user(ALICE, "Alice")));
        when(userRepository.findById(BOB)).thenReturn(Optional.of(user(BOB, "Bob")));
    }

    @Test
    void unknownChallengeIsReported() {
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(BuildChallengeLeaderboardQuery.anonymous(CHALLENGE_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(CHALLENGE_ID.value().toString());
    }

    // ----- the divergence this class was extracted to fix -----

    @Test
    void contributionsCountAsInvestedCapitalNotProfit() {
        // Seeded with 1000, topped up with 1000, now worth 2000: the investor is flat, not +100%.
        Simulation entry = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(1000));
        givenEntries(entry);
        givenLatestValue(entry, Money.usd(2000));
        when(contributionRepository.findBySimulationId(entry.getSimulationId()))
            .thenReturn(List.of(contribution(entry, Money.usd(1000))));

        ChallengeLeaderboard.Entry result = onlyEntry();

        assertThat(result.returnPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void withoutContributionsReturnIsMeasuredAgainstSeedCash() {
        Simulation entry = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(1000));
        givenEntries(entry);
        givenLatestValue(entry, Money.usd(1250));

        assertThat(onlyEntry().returnPercent()).isEqualByComparingTo(new BigDecimal("25"));
    }

    @Test
    void returnIsUndefinedWhenThereIsNoBasisToMeasureAgainst() {
        Simulation entry = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(0));
        givenEntries(entry);
        givenLatestValue(entry, Money.usd(500));

        assertThat(onlyEntry().returnPercent()).isNull();
    }

    @Test
    void currentValueFallsBackToSeedCashBeforeAnySnapshotExists() {
        Simulation entry = simulation(ALICE, SimulationStatus.LIVE, Money.usd(7500));
        givenEntries(entry);

        ChallengeLeaderboard.Entry result = onlyEntry();

        assertThat(result.currentValue()).isEqualTo(Money.usd(7500));
        assertThat(result.returnPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ----- visibility -----

    @Test
    void liveAndCompletedEntriesAreVisibleToEveryone() {
        Simulation live = simulation(ALICE, SimulationStatus.LIVE, Money.usd(1000));
        Simulation completed = simulation(BOB, SimulationStatus.COMPLETED, Money.usd(1000));
        givenEntries(live, completed);

        assertThat(service.execute(BuildChallengeLeaderboardQuery.anonymous(CHALLENGE_ID)).entries())
            .hasSize(2);
    }

    @Test
    void pendingAndFailedEntriesAreNeverShownEvenToTheirOwner() {
        Simulation pending = simulation(ALICE, SimulationStatus.PENDING, Money.usd(1000));
        Simulation failed = simulation(ALICE, SimulationStatus.FAILED, Money.usd(1000));
        givenEntries(pending, failed);

        ChallengeLeaderboard board =
            service.execute(new BuildChallengeLeaderboardQuery(CHALLENGE_ID, ALICE));

        assertThat(board.entries()).isEmpty();
        assertThat(board.totalParticipants()).isEqualTo(2);
    }

    @Test
    void anInProgressEntryIsVisibleToItsOwnerOnly() {
        Simulation aliceRunning = simulation(ALICE, SimulationStatus.SIMULATING, Money.usd(1000));
        givenEntries(aliceRunning);

        assertThat(service.execute(new BuildChallengeLeaderboardQuery(CHALLENGE_ID, ALICE)).entries())
            .hasSize(1);
        assertThat(service.execute(new BuildChallengeLeaderboardQuery(CHALLENGE_ID, BOB)).entries())
            .isEmpty();
        assertThat(service.execute(BuildChallengeLeaderboardQuery.anonymous(CHALLENGE_ID)).entries())
            .isEmpty();
    }

    @Test
    void totalParticipantsCountsEntriesThatAreNotShown() {
        Simulation shown = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(1000));
        Simulation hidden = simulation(BOB, SimulationStatus.SIMULATING, Money.usd(1000));
        givenEntries(shown, hidden);

        ChallengeLeaderboard board = service.execute(BuildChallengeLeaderboardQuery.anonymous(CHALLENGE_ID));

        assertThat(board.entries()).hasSize(1);
        assertThat(board.totalParticipants()).isEqualTo(2);
    }

    // ----- ranking and attribution -----

    @Test
    void entriesAreRankedByCurrentValueDescending() {
        Simulation small = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(1000));
        Simulation large = simulation(BOB, SimulationStatus.COMPLETED, Money.usd(1000));
        givenEntries(small, large);
        givenLatestValue(small, Money.usd(1100));
        givenLatestValue(large, Money.usd(9000));

        List<ChallengeLeaderboard.Entry> entries =
            service.execute(BuildChallengeLeaderboardQuery.anonymous(CHALLENGE_ID)).entries();

        assertThat(entries).extracting(ChallengeLeaderboard.Entry::simulationId)
            .containsExactly(large.getSimulationId(), small.getSimulationId());
        assertThat(entries).extracting(ChallengeLeaderboard.Entry::rank).containsExactly(1, 2);
    }

    @Test
    void ownershipIsResolvedThroughTheStrategyOwnerNotTheDisplayName() {
        // Two participants sharing a display name used to collide, because the REST leaderboard
        // located "your" entry by matching the owner's name.
        Simulation aliceEntry = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(1000));
        Simulation bobEntry = simulation(BOB, SimulationStatus.COMPLETED, Money.usd(1000));
        when(userRepository.findById(BOB)).thenReturn(Optional.of(user(BOB, "Alice")));
        givenEntries(aliceEntry, bobEntry);
        givenLatestValue(aliceEntry, Money.usd(1100));
        givenLatestValue(bobEntry, Money.usd(2200));

        List<ChallengeLeaderboard.Entry> entries =
            service.execute(new BuildChallengeLeaderboardQuery(CHALLENGE_ID, ALICE)).entries();

        assertThat(entries).allSatisfy(e -> assertThat(e.ownerDisplayName()).isEqualTo("Alice"));
        assertThat(entries).filteredOn(ChallengeLeaderboard.Entry::ownedByViewer)
            .extracting(ChallengeLeaderboard.Entry::simulationId)
            .containsExactly(aliceEntry.getSimulationId());
    }

    @Test
    void anonymousViewersOwnNothing() {
        Simulation entry = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(1000));
        givenEntries(entry);

        assertThat(onlyEntry().ownedByViewer()).isFalse();
    }

    @Test
    void entriesWhoseStrategyIsGoneStillRankRatherThanBreakTheBoard() {
        Simulation orphan = simulation(null, SimulationStatus.COMPLETED, Money.usd(1000));
        givenEntries(orphan);
        givenLatestValue(orphan, Money.usd(1200));

        ChallengeLeaderboard.Entry result = onlyEntry();

        assertThat(result.strategyName()).isEqualTo("Unknown");
        assertThat(result.ownerDisplayName()).isEqualTo("Anonymous");
        assertThat(result.returnPercent()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void executionTimeSpansStartToCompletion() {
        Simulation entry = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("timed")
            .strategyId(new StrategyId(UUID.randomUUID()))
            .startDate(T0)
            .initialCash(Money.usd(1000))
            .challengeId(CHALLENGE_ID)
            .status(SimulationStatus.COMPLETED)
            .startedAt(T0)
            .completedAt(T0.plus(90, ChronoUnit.MINUTES))
            .build();
        givenEntries(entry);

        assertThat(onlyEntry().executionTimeSeconds()).isEqualTo(90 * 60L);
    }

    @Test
    void executionTimeIsAbsentForAnEntryThatNeverStarted() {
        Simulation entry = simulation(ALICE, SimulationStatus.COMPLETED, Money.usd(1000));
        givenEntries(entry);

        assertThat(onlyEntry().executionTimeSeconds()).isNull();
    }

    // ----- fixtures -----

    private ChallengeLeaderboard.Entry onlyEntry() {
        List<ChallengeLeaderboard.Entry> entries =
            service.execute(BuildChallengeLeaderboardQuery.anonymous(CHALLENGE_ID)).entries();
        assertThat(entries).hasSize(1);
        return entries.get(0);
    }

    private void givenEntries(Simulation... simulations) {
        when(simulationRepository.findByChallengeId(CHALLENGE_ID)).thenReturn(List.of(simulations));
    }

    private void givenLatestValue(Simulation simulation, Money value) {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
            simulation.getSimulationId(), T0, Map.of(), value, value);
        when(portfolioRepository.findLatestBySimulationId(simulation.getSimulationId()))
            .thenReturn(Optional.of(snapshot));
    }

    /** A challenge entry owned by {@code owner}; a null owner leaves the strategy unresolvable. */
    private Simulation simulation(UserId owner, SimulationStatus status, Money seed) {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        if (owner != null) {
            Strategy strategy = new Strategy(strategyId, "strategy-" + owner.value(), "content", owner);
            when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        }
        return Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("entry")
            .strategyId(strategyId)
            .startDate(T0)
            .initialCash(seed)
            .challengeId(CHALLENGE_ID)
            .status(status)
            .build();
    }

    private Contribution contribution(Simulation simulation, Money amount) {
        return new Contribution(ContributionId.generate(), simulation.getSimulationId(), amount,
            T0.plus(30, ChronoUnit.DAYS), null, null, T0);
    }

    private static Challenge challenge() {
        return new Challenge(CHALLENGE_ID, "Test Challenge", "desc",
            new Symbol("BTC", InvestmentType.CRYPTO), Money.usd(1000),
            T0, T0.plus(365, ChronoUnit.DAYS),
            T0.minus(7, ChronoUnit.DAYS), T0,
            T0.minus(7, ChronoUnit.DAYS), ALICE, true);
    }

    private static User user(UserId id, String displayName) {
        return User.create(id, displayName.toLowerCase() + "@example.com", displayName,
            null, OAuthProvider.GOOGLE);
    }
}
