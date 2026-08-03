package com.quorumtrading.adapter.rest.mapper;

import com.quorumtrading.adapter.rest.dto.response.SimulationResponse;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.service.PerformanceCalculator;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link SimulationMapper}, covering two things.
 *
 * <p>The list path: it must batch-load strategies and broker accounts with ONE {@code findByIds}
 * call each — never one {@code findById} per simulation (the dashboard polls GET /simulations
 * every few seconds, so per-item lookups were an N+1).</p>
 *
 * <p>The detail path's performance enrichment: TWR/MWR must describe the simulation, not the
 * moment it was read. This overload had no test at all, which is how an as-of instant of
 * {@code Instant.now()} reached production and made a finished backtest's reported return decay
 * on every refresh.</p>
 */
class SimulationMapperTest {

    private StrategyRepository strategyRepository;
    private BrokerAccountRepository brokerAccountRepository;
    private SimulationMapper mapper;

    private final UserId ownerId = UserId.generate();

    @BeforeEach
    void setUp() {
        strategyRepository = mock(StrategyRepository.class);
        brokerAccountRepository = mock(BrokerAccountRepository.class);
        mapper = new SimulationMapper();
        mapper.strategyRepository = strategyRepository;
        mapper.brokerAccountRepository = brokerAccountRepository;
        mapper.stockBrokerType = "mock";
    }

    private Strategy strategy(String name) {
        return new Strategy(StrategyId.generate(), name, "## Rules\n- test", ownerId);
    }

    private BrokerAccount alpacaAccount() {
        Money balance = Money.usd(new BigDecimal("10000"));
        return BrokerAccount.createReal(
            ownerId, "Paper", BrokerType.ALPACA_PAPER,
            new ApiKeyCredentials("PKTEST", "secret"), balance, balance);
    }

    private Simulation simulation(StrategyId strategyId, BrokerAccountId brokerAccountId) {
        Instant now = Instant.now();
        return new Simulation(
            new SimulationId(UUID.randomUUID()),
            "sim",
            strategyId,
            now.minus(90, ChronoUnit.DAYS),
            now.minus(30, ChronoUnit.DAYS),
            Money.usd(new BigDecimal("100000")),
            null,
            brokerAccountId,
            null);
    }

    @Test
    void toResponseListBatchLoadsInsteadOfPerItemLookups() {
        Strategy strategyA = strategy("A");
        Strategy strategyB = strategy("B");
        BrokerAccount account = alpacaAccount();

        // Two sims share strategy A (ids must be deduplicated), one uses B + a broker account.
        Simulation sim1 = simulation(strategyA.getId(), null);
        Simulation sim2 = simulation(strategyA.getId(), null);
        Simulation sim3 = simulation(strategyB.getId(), account.getId());

        when(strategyRepository.findByIds(any())).thenReturn(List.of(strategyA, strategyB));
        when(brokerAccountRepository.findByIds(any())).thenReturn(List.of(account));

        List<SimulationResponse> responses = mapper.toResponseList(List.of(sim1, sim2, sim3));

        assertThat(responses).hasSize(3);

        // Exactly ONE batch call per repository, with the distinct ids...
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<StrategyId>> strategyIdsCaptor =
            ArgumentCaptor.forClass((Class) Collection.class);
        verify(strategyRepository, times(1)).findByIds(strategyIdsCaptor.capture());
        assertThat(strategyIdsCaptor.getValue())
            .containsExactlyInAnyOrderElementsOf(Set.of(strategyA.getId(), strategyB.getId()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<BrokerAccountId>> accountIdsCaptor =
            ArgumentCaptor.forClass((Class) Collection.class);
        verify(brokerAccountRepository, times(1)).findByIds(accountIdsCaptor.capture());
        assertThat(accountIdsCaptor.getValue()).containsExactly(account.getId());

        // ...and never a per-item lookup.
        verify(strategyRepository, never()).findById(any());
        verify(brokerAccountRepository, never()).findById(any());

        // Mapping output uses the batch-loaded rows.
        assertThat(responses.get(0).brokerType()).isEqualTo(BrokerType.MOCK.name());
        assertThat(responses.get(1).brokerType()).isEqualTo(BrokerType.MOCK.name());
        assertThat(responses.get(2).brokerType()).isEqualTo(BrokerType.ALPACA_PAPER.name());
        assertThat(responses.get(2).strategyId()).isEqualTo(strategyB.getId().value().toString());
        assertThat(responses.get(0).strategyLineage()).isNotNull();
    }

    @Test
    void toResponseListWithEmptyInputSkipsRepositories() {
        assertThat(mapper.toResponseList(List.of())).isEmpty();
        assertThat(mapper.toResponseList(null)).isEmpty();
        verifyNoInteractions(strategyRepository, brokerAccountRepository);
    }

    @Test
    void toResponseListToleratesMissingRows() {
        // A simulation whose strategy and broker account rows are gone must
        // still map (null lineage, null brokerType) — same as the single-item path.
        Simulation orphan = simulation(StrategyId.generate(), BrokerAccountId.generate());
        when(strategyRepository.findByIds(any())).thenReturn(List.of());
        when(brokerAccountRepository.findByIds(any())).thenReturn(List.of());

        List<SimulationResponse> responses = mapper.toResponseList(List.of(orphan));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).brokerType()).isNull();
        assertThat(responses.get(0).strategyLineage()).isNull();
    }

    // ---------- performance enrichment (TWR / MWR) ----------

    private static final Instant SIM_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant SIM_END = Instant.parse("2026-04-01T00:00:00Z");

    /** A finished run: seeded at 100k, last valued at 120k on the simulation's own end date. */
    private Simulation completedSimulation() {
        return new Simulation(
            new SimulationId(UUID.randomUUID()), "completed", StrategyId.generate(),
            SIM_START, SIM_END, Money.usd(new BigDecimal("100000")));
    }

    private List<PortfolioSnapshot> twoSnapshots(SimulationId simId) {
        return List.of(
            new PortfolioSnapshot(simId, SIM_START, Map.of(),
                Money.usd(new BigDecimal("100000")), Money.usd(new BigDecimal("100000"))),
            new PortfolioSnapshot(simId, SIM_END, Map.of(),
                Money.usd(new BigDecimal("120000")), Money.usd(new BigDecimal("120000"))));
    }

    /**
     * The reported money-weighted return of a COMPLETED simulation must be a property of the run,
     * not of when the page was opened. Passing a wall-clock as-of instant made it decay on every
     * refresh — the same finished backtest quoted 59.7375, then 59.7371, then 59.7368 — while the
     * MCP surface, which anchors to the last snapshot, reported 66.3888 for the same row.
     */
    @Test
    void mwrIsAnchoredToTheSimulationNotToWallClock() {
        Simulation sim = completedSimulation();
        List<PortfolioSnapshot> snapshots = twoSnapshots(sim.getSimulationId());
        Money currentValue = Money.usd(new BigDecimal("120000"));

        SimulationResponse response =
            mapper.toResponseWithContributions(sim, List.of(), snapshots, currentValue);

        BigDecimal anchored = PerformanceCalculator.asPercent(PerformanceCalculator.computeMwr(
            sim.getInitialCash(), SIM_START, List.of(), currentValue, SIM_END));
        assertThat(response.mwrPercent())
            .as("MWR must annualise over the simulated period, ending at the last snapshot")
            .isEqualByComparingTo(anchored);

        BigDecimal wallClock = PerformanceCalculator.asPercent(PerformanceCalculator.computeMwr(
            sim.getInitialCash(), SIM_START, List.of(), currentValue, Instant.now()));
        assertThat(response.mwrPercent())
            .as("a wall-clock horizon is what made a finished run's return drift downward")
            .isNotEqualByComparingTo(wallClock);
    }

    @Test
    void mwrIsStableAcrossRepeatedReads() {
        Simulation sim = completedSimulation();
        List<PortfolioSnapshot> snapshots = twoSnapshots(sim.getSimulationId());
        Money currentValue = Money.usd(new BigDecimal("120000"));

        BigDecimal first = mapper
            .toResponseWithContributions(sim, List.of(), snapshots, currentValue).mwrPercent();
        BigDecimal second = mapper
            .toResponseWithContributions(sim, List.of(), snapshots, currentValue).mwrPercent();

        assertThat(second)
            .as("two reads of one unchanged simulation must return the same number")
            .isEqualByComparingTo(first);
    }

    /**
     * With no snapshots there is nothing to anchor to, so the simulation's own end date is the
     * next-best horizon — still the simulated axis, still not the wall clock.
     */
    @Test
    void mwrFallsBackToTheEndDateWhenNoSnapshotsExist() {
        Simulation sim = completedSimulation();
        Money currentValue = Money.usd(new BigDecimal("120000"));

        SimulationResponse response =
            mapper.toResponseWithContributions(sim, List.of(), List.of(), currentValue);

        BigDecimal atEndDate = PerformanceCalculator.asPercent(PerformanceCalculator.computeMwr(
            sim.getInitialCash(), SIM_START, List.of(), currentValue, SIM_END));
        assertThat(response.mwrPercent()).isEqualByComparingTo(atEndDate);
    }

    @Test
    void singleItemToResponseStillUsesPerItemLookups() {
        Strategy strategyA = strategy("A");
        Simulation sim = simulation(strategyA.getId(), null);
        when(strategyRepository.findById(strategyA.getId())).thenReturn(Optional.of(strategyA));

        SimulationResponse response = mapper.toResponse(sim);

        assertThat(response.brokerType()).isEqualTo(BrokerType.MOCK.name());
        assertThat(response.strategyLineage()).isNotNull();
        verify(strategyRepository, times(1)).findById(strategyA.getId());
        // No broker account linked — no lookup needed at all.
        verifyNoInteractions(brokerAccountRepository);
    }
}
