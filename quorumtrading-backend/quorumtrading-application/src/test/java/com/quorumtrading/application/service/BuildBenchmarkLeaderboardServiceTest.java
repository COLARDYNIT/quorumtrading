package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.dto.BenchmarkLeaderboard;
import com.quorumtrading.application.usecase.query.BuildBenchmarkLeaderboardQuery;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.BrokerAccountId;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BenchmarkResource and the MCP BenchmarkTools each computed benchmark entries themselves and
 * disagreed on both the return baseline and the sort order. These tests pin the shared
 * behaviour both adapters now render.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildBenchmarkLeaderboardServiceTest {

    private static final BenchmarkId BENCHMARK_ID = new BenchmarkId(UUID.randomUUID());
    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private BenchmarkRepository benchmarkRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private SimulationContributionRepository contributionRepository;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;

    private BuildBenchmarkLeaderboardService service;

    @BeforeEach
    void setUp() {
        service = new BuildBenchmarkLeaderboardService(benchmarkRepository, simulationRepository,
            strategyRepository, portfolioRepository, contributionRepository,
            consensusDecisionRepository, brokerAccountRepository);

        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.of(benchmark()));
        when(portfolioRepository.findLatestBySimulationId(any())).thenReturn(Optional.empty());
        when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());
        when(contributionRepository.findBySimulationId(any())).thenReturn(List.of());
        when(strategyRepository.findById(any())).thenReturn(Optional.empty());
        when(consensusDecisionRepository.countExecutedTradesBySimulationId(any())).thenReturn(0L);
    }

    @Test
    void unknownBenchmarkIsReported() {
        when(benchmarkRepository.findById(BENCHMARK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new BuildBenchmarkLeaderboardQuery(BENCHMARK_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(BENCHMARK_ID.value().toString());
    }

    // ----- the ranking rule -----

    @Test
    void aStrategyThatNeverTradedRanksBelowOneThatDidEvenWhenItsReturnIsHigher() {
        // The case this rule exists for: doing nothing scores a flat 0% and would otherwise
        // beat a strategy that took real positions into a falling market.
        Simulation idle = backtest("never traded", Money.usd(1000));
        Simulation active = backtest("traded and lost", Money.usd(1000));
        givenMembers(idle, active);
        givenLatestValue(idle, Money.usd(1000));
        givenLatestValue(active, Money.usd(895));
        givenTradeCount(active, 12);

        List<BenchmarkLeaderboard.Entry> entries = rank();

        assertThat(entries).extracting(BenchmarkLeaderboard.Entry::simulationName)
            .containsExactly("traded and lost", "never traded");
        assertThat(entries).extracting(BenchmarkLeaderboard.Entry::rank).containsExactly(1, 2);
    }

    @Test
    void amongStrategiesThatTradedTheBetterReturnWins() {
        Simulation worse = backtest("worse", Money.usd(1000));
        Simulation better = backtest("better", Money.usd(1000));
        givenMembers(worse, better);
        givenLatestValue(worse, Money.usd(1100));
        givenLatestValue(better, Money.usd(1400));
        givenTradeCount(worse, 3);
        givenTradeCount(better, 5);

        assertThat(rank()).extracting(BenchmarkLeaderboard.Entry::simulationName)
            .containsExactly("better", "worse");
    }

    @Test
    void chartOrdinalTracksBenchmarkMembershipNotRank() {
        // Adapters colour a series by ordinal; if it followed rank, a simulation would change
        // colour every time the ranking moved.
        Simulation first = backtest("first", Money.usd(1000));
        Simulation second = backtest("second", Money.usd(1000));
        givenMembers(first, second);
        givenLatestValue(first, Money.usd(1000));
        givenLatestValue(second, Money.usd(5000));
        givenTradeCount(second, 4);

        List<BenchmarkLeaderboard.Entry> entries = rank();

        assertThat(entries).extracting(BenchmarkLeaderboard.Entry::simulationName)
            .containsExactly("second", "first");
        assertThat(entries).extracting(BenchmarkLeaderboard.Entry::benchmarkOrdinal)
            .containsExactly(1, 0);
    }

    // ----- the return baseline -----

    @Test
    void contributionsCountAsInvestedCapitalNotProfit() {
        Simulation entry = backtest("dca", Money.usd(1000));
        givenMembers(entry);
        givenLatestValue(entry, Money.usd(2000));
        when(contributionRepository.findBySimulationId(entry.getSimulationId()))
            .thenReturn(List.of(contribution(entry, Money.usd(1000))));

        assertThat(rank().get(0).returnPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aBacktestMeasuresAgainstItsSeedCashEvenWhenSnapshotsExist() {
        Simulation entry = backtest("backtest", Money.usd(1000));
        givenMembers(entry);
        // A snapshot history that would move the baseline if the broker-backed rule applied.
        when(portfolioRepository.findAllBySimulationId(entry.getSimulationId()))
            .thenReturn(List.of(snapshot(entry, Money.usd(1000), false),
                                snapshot(entry, Money.usd(4000), true)));
        givenLatestValue(entry, Money.usd(1500));

        assertThat(rank().get(0).returnPercent()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void aBrokerBackedRunSkipsItsBootstrapSnapshotToFindTheRealOpeningBalance() {
        // The first snapshot carries the user-typed placeholder cash, not the account balance.
        Simulation entry = brokerBacked("paper", Money.usd(10_000), BrokerType.ALPACA_PAPER);
        givenMembers(entry);
        when(portfolioRepository.findAllBySimulationId(entry.getSimulationId()))
            .thenReturn(List.of(snapshot(entry, Money.usd(10_000), false),
                                snapshot(entry, Money.usd(100_000), true)));
        givenLatestValue(entry, Money.usd(110_000));

        BenchmarkLeaderboard.Entry result = rank().get(0);

        assertThat(result.returnPercent()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.tradingMode()).isEqualTo("PAPER");
    }

    @Test
    void aBrokerBackedRunWhoseFirstSnapshotAlreadyHoldsPositionsUsesIt() {
        Simulation entry = brokerBacked("live", Money.usd(10_000), BrokerType.ALPACA_LIVE);
        givenMembers(entry);
        when(portfolioRepository.findAllBySimulationId(entry.getSimulationId()))
            .thenReturn(List.of(snapshot(entry, Money.usd(50_000), true)));
        givenLatestValue(entry, Money.usd(75_000));

        BenchmarkLeaderboard.Entry result = rank().get(0);

        assertThat(result.returnPercent()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(result.tradingMode()).isEqualTo("LIVE");
    }

    @Test
    void aMockBrokerIsStillABacktest() {
        Simulation entry = brokerBacked("mock", Money.usd(1000), BrokerType.MOCK);
        givenMembers(entry);

        assertThat(rank().get(0).tradingMode()).isEqualTo("BACKTEST");
    }

    // ----- entries that carry no measurement -----

    @Test
    void anEntryWithNoSnapshotIsFlaggedUnmeasuredAndFallsBackToSeedCash() {
        Simulation entry = backtest("not started", Money.usd(1000));
        givenMembers(entry);

        BenchmarkLeaderboard.Entry result = rank().get(0);

        assertThat(result.hasPortfolioData()).isFalse();
        assertThat(result.measurable()).isFalse();
        assertThat(result.currentValue()).isEqualTo(Money.usd(1000));
    }

    @Test
    void anEntryWithASnapshotIsMeasurable() {
        Simulation entry = backtest("running", Money.usd(1000));
        givenMembers(entry);
        givenLatestValue(entry, Money.usd(1200));

        BenchmarkLeaderboard.Entry result = rank().get(0);

        assertThat(result.hasPortfolioData()).isTrue();
        assertThat(result.measurable()).isTrue();
    }

    @Test
    void strategyNamesAreResolvedAndFallBackWhenTheStrategyIsGone() {
        StrategyId known = new StrategyId(UUID.randomUUID());
        when(strategyRepository.findById(known))
            .thenReturn(Optional.of(new Strategy(known, "Momentum", "content", OWNER)));

        Simulation named = member("named", Money.usd(1000), known, null);
        Simulation orphan = backtest("orphan", Money.usd(1000));
        givenMembers(named, orphan);

        assertThat(rank()).extracting(BenchmarkLeaderboard.Entry::strategyName)
            .containsExactlyInAnyOrder("Momentum", "Unknown Strategy");
    }

    // ----- fixtures -----

    private List<BenchmarkLeaderboard.Entry> rank() {
        return service.execute(new BuildBenchmarkLeaderboardQuery(BENCHMARK_ID)).entries();
    }

    private void givenMembers(Simulation... simulations) {
        when(simulationRepository.findByBenchmarkId(BENCHMARK_ID)).thenReturn(List.of(simulations));
    }

    private void givenLatestValue(Simulation simulation, Money value) {
        when(portfolioRepository.findLatestBySimulationId(simulation.getSimulationId()))
            .thenReturn(Optional.of(snapshot(simulation, value, false)));
    }

    private void givenTradeCount(Simulation simulation, long trades) {
        when(consensusDecisionRepository.countExecutedTradesBySimulationId(
            simulation.getSimulationId().value())).thenReturn(trades);
    }

    private PortfolioSnapshot snapshot(Simulation simulation, Money value, boolean withPositions) {
        Map<Symbol, Position> positions = withPositions
            ? Map.of(new Symbol("BTC", InvestmentType.CRYPTO),
                     new Position(new Symbol("BTC", InvestmentType.CRYPTO), BigDecimal.ONE, value))
            : Map.of();
        return new PortfolioSnapshot(simulation.getSimulationId(), T0, positions, value, value);
    }

    private Simulation backtest(String name, Money seed) {
        return member(name, seed, new StrategyId(UUID.randomUUID()), null);
    }

    /** Only the broker type matters here, so the account itself is a stub. */
    private Simulation brokerBacked(String name, Money seed, BrokerType brokerType) {
        BrokerAccountId accountId = new BrokerAccountId(UUID.randomUUID());
        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getBrokerType()).thenReturn(brokerType);
        when(brokerAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
        return member(name, seed, new StrategyId(UUID.randomUUID()), accountId);
    }

    private Simulation member(String name, Money seed, StrategyId strategyId, BrokerAccountId accountId) {
        return Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name(name)
            .strategyId(strategyId)
            .startDate(T0)
            .initialCash(seed)
            .benchmarkId(BENCHMARK_ID)
            .brokerAccountId(accountId)
            .status(SimulationStatus.COMPLETED)
            .build();
    }

    private Contribution contribution(Simulation simulation, Money amount) {
        return new Contribution(ContributionId.generate(), simulation.getSimulationId(), amount,
            T0.plus(30, ChronoUnit.DAYS), null, null, T0);
    }

    private static Benchmark benchmark() {
        return new Benchmark(BENCHMARK_ID, "Test Benchmark", T0, T0.plus(90, ChronoUnit.DAYS), T0, OWNER);
    }
}
