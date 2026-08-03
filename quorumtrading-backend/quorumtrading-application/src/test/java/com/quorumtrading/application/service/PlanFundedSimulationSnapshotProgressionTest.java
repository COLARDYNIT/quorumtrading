package com.quorumtrading.application.service;

import com.quorumtrading.application.config.TradingLimitsConfig;
import com.quorumtrading.application.event.PortfolioChangeEvent;
import com.quorumtrading.application.event.TradeExecutionEvent;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The exact three-step progression that made every plan-funded LIVE simulation report a wrong
 * value: create → contribute → the 5-minute LIVE valuation.
 *
 * <p>Observed on {@code a81d46f1-04b4-493c-8813-c2145229d8dd}, whose mock broker account states
 * $12,000 of starting capital while the recurring plan funded it with $500 and topped it up by
 * $500:</p>
 *
 * <pre>
 * 10:48:50    500.00   created by the plan with $500        correct
 * 10:49:08   1000.00   $500 contribution recorded           correct
 * 10:50:26  12000.00   scheduled 5-minute valuation ran     the ACCOUNT's capital, and every
 *                                                           5 minutes after that
 * </pre>
 *
 * <p>The third row is what the UI and API read, so the simulation reported +1100% on a portfolio
 * that had not traded. The return arithmetic was right — (12000-1000)/1000 — the input was not.
 * This test drives the three steps through the real services and asserts the valuation leaves the
 * figure at $1,000.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("plan-funded LIVE simulation — create, contribute, then value")
class PlanFundedSimulationSnapshotProgressionTest {

    /** The plan funded this much; the mock account it borrows from states $12,000. */
    private static final Money PLAN_FUNDING = Money.usd(new BigDecimal("500.00"));
    private static final Money TOP_UP = Money.usd(new BigDecimal("500.00"));
    private static final Money ACCOUNT_CAPITAL = Money.usd(new BigDecimal("12000"));

    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final BrokerAccountId ACCOUNT_ID = BrokerAccountId.generate();
    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UUID PLAN_ID = UUID.randomUUID();

    private static final Instant CREATED_AT = Instant.parse("2026-07-30T10:48:50Z");
    private static final Instant CONTRIBUTED_AT = Instant.parse("2026-07-30T10:49:08Z");
    private static final Instant VALUED_AT = Instant.parse("2026-07-30T10:50:26Z");

    /** Snapshot rows written so far, oldest first — stands in for {@code portfolio_snapshots}. */
    private final List<PortfolioSnapshot> snapshots = new ArrayList<>();

    private SimulationPortfolio ledger;
    private SimulationContributionService contributionService;
    private ExecuteConsensusDecisionService valuationService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // ---- the simulation's own capital ledger, seeded with what the plan actually funded
        ledger = SimulationPortfolio.initialize(SIM_ID, ACCOUNT_ID, PLAN_FUNDING);
        SimulationPortfolioRepository ledgerRepository = mock(SimulationPortfolioRepository.class);
        when(ledgerRepository.findById(SIM_ID)).thenReturn(Optional.of(ledger));
        when(ledgerRepository.existsById(SIM_ID)).thenReturn(true);

        // ---- the mock broker, whose per-simulation state is seeded from the ACCOUNT's capital
        BrokerPort accountLevelBroker = mock(BrokerPort.class);
        when(accountLevelBroker.getCurrentPortfolio(SIM_ID))
                .thenReturn(new Portfolio(SIM_ID, Map.of(), ACCOUNT_CAPITAL));
        when(accountLevelBroker.getAvailableBalance(SIM_ID)).thenReturn(ACCOUNT_CAPITAL);
        when(accountLevelBroker.getPortfolioValue(any(), any())).thenReturn(ACCOUNT_CAPITAL);

        SimulationRepository simulationRepository = mock(SimulationRepository.class);
        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getBrokerAccountId()).thenReturn(ACCOUNT_ID);
        when(simulation.getEndDate()).thenReturn(null);
        when(simulation.getStrategyId()).thenReturn(null);
        when(simulation.buildContribution(any(), any(), any(), any())).thenAnswer(inv ->
                new Contribution(new ContributionId(UUID.randomUUID()), SIM_ID,
                        inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), inv.getArgument(3), CONTRIBUTED_AT));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        BrokerRouter router = new BrokerRouter();
        router.mockBroker = satisfied(accountLevelBroker, BrokerPort.class);
        router.alpacaBroker = (Instance<BrokerPort>) mock(Instance.class);
        router.saxoBroker = (Instance<BrokerPort>) mock(Instance.class);
        when(router.alpacaBroker.isUnsatisfied()).thenReturn(true);
        when(router.saxoBroker.isUnsatisfied()).thenReturn(true);
        router.simulationRepository = simulationRepository;
        // The account is reachable, so routing to the broker works. Without this the test would
        // fail on a missing repository rather than on the figure it is about.
        com.quorumtrading.model.entity.BrokerAccount account =
                mock(com.quorumtrading.model.entity.BrokerAccount.class);
        when(account.getId()).thenReturn(ACCOUNT_ID);
        when(account.getBrokerType()).thenReturn(com.quorumtrading.model.enums.BrokerType.MOCK);
        BrokerAccountRepository accounts = mock(BrokerAccountRepository.class);
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        router.brokerAccountRepository = satisfied(accounts, BrokerAccountRepository.class);
        router.simulationPortfolioRepository = satisfied(ledgerRepository, SimulationPortfolioRepository.class);
        MarketEventRepository marketEvents = mock(MarketEventRepository.class);
        when(marketEvents.findByTickersAndTimeRange(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        router.marketEventRepository = satisfied(marketEvents, MarketEventRepository.class);

        PortfolioRepository portfolioRepository = inMemorySnapshots();

        StrategyRepository strategyRepository = mock(StrategyRepository.class);
        when(strategyRepository.findById(any())).thenReturn(Optional.of(mock(Strategy.class)));
        AuthorizationService authorizationService = mock(AuthorizationService.class);

        SimulationContributionRepository contributionRepository = mock(SimulationContributionRepository.class);
        when(contributionRepository.findByPlanAndDate(any(), any())).thenReturn(Optional.empty());
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contributionService = new SimulationContributionService(
                simulationRepository, ledgerRepository, mock(BrokerAccountRepository.class),
                contributionRepository, strategyRepository, authorizationService, portfolioRepository);

        valuationService = new ExecuteConsensusDecisionService(
                router, portfolioRepository, mock(ConsensusDecisionRepository.class), marketEvents,
                (EventPublisher<TradeExecutionEvent>) mock(EventPublisher.class),
                (EventPublisher<PortfolioChangeEvent>) mock(EventPublisher.class),
                mock(TradingLimitsConfig.class), simulationRepository, strategyRepository,
                new StrategyConfigParser());
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> satisfied(T bean, Class<T> type) {
        Instance<T> instance = (Instance<T>) mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(bean);
        return instance;
    }

    private PortfolioRepository inMemorySnapshots() {
        PortfolioRepository repository = mock(PortfolioRepository.class);
        when(repository.save(any(PortfolioSnapshot.class))).thenAnswer(inv -> {
            PortfolioSnapshot saved = inv.getArgument(0);
            snapshots.add(saved);
            return saved;
        });
        when(repository.findLatestBySimulationId(SIM_ID)).thenAnswer(inv ->
                snapshots.stream().max(Comparator.comparing(PortfolioSnapshot::getTimestamp)));
        when(repository.findBySimulationIdAndTimeRange(any(), any(), any())).thenAnswer(inv -> {
            Instant from = inv.getArgument(1);
            Instant to = inv.getArgument(2);
            return snapshots.stream()
                    .filter(s -> !s.getTimestamp().isBefore(from) && !s.getTimestamp().isAfter(to))
                    .toList();
        });
        return repository;
    }

    /** Step 1: the plan creates the simulation and its opening snapshot with what it funded. */
    private void planCreatesTheSimulation() {
        snapshots.add(new PortfolioSnapshot(
                SIM_ID, CREATED_AT, Map.<Symbol, com.quorumtrading.model.entity.Position>of(),
                PLAN_FUNDING, PLAN_FUNDING));
    }

    /** Step 2: the plan tops it up. */
    private void planContributes() {
        contributionService.execute(new RecordSimulationContributionCommand(
                SIM_ID, TOP_UP, CONTRIBUTED_AT, PLAN_ID, UUID.randomUUID(), OWNER));
    }

    /** Step 3: the scheduled LIVE valuation runs. */
    private PortfolioSnapshot scheduledValuationRuns() {
        return valuationService.updatePortfolioWithMarketValue(SIM_ID, VALUED_AT);
    }

    @Test
    @DisplayName("the scheduled valuation leaves the value at what the plan funded, not the account's capital")
    void theScheduledValuationDoesNotClobberTheFundedAmount() {
        planCreatesTheSimulation();
        assertThat(snapshots.get(0).getTotalValue().amount()).isEqualByComparingTo("500.00");

        planContributes();
        assertThat(snapshots.get(1).getTotalValue().amount()).isEqualByComparingTo("1000.00");
        assertThat(ledger.getCashAvailable().amount()).isEqualByComparingTo("1000.00");

        PortfolioSnapshot valued = scheduledValuationRuns();

        assertThat(valued).isNotNull();
        assertThat(valued.getTotalValue().amount())
                .as("the 5-minute valuation must not report the mock account's $12,000 capital")
                .isEqualByComparingTo("1000.00");
        assertThat(valued.getCashAvailable().amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("repeated valuations stay at the funded amount — the wrong figure was written every 5 minutes")
    void repeatedValuationsStayCorrect() {
        planCreatesTheSimulation();
        planContributes();

        scheduledValuationRuns();
        valuationService.updatePortfolioWithMarketValue(SIM_ID, VALUED_AT.plusSeconds(300));
        valuationService.updatePortfolioWithMarketValue(SIM_ID, VALUED_AT.plusSeconds(600));

        assertThat(snapshots)
                .allSatisfy(s -> assertThat(s.getTotalValue().amount())
                        .isLessThanOrEqualTo(new BigDecimal("1000.00")));
        assertThat(snapshots.get(snapshots.size() - 1).getTotalValue().amount())
                .isEqualByComparingTo("1000.00");
    }
}
