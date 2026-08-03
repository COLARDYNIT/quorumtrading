package com.quorumtrading.application.orchestration;

import com.quorumtrading.application.event.ConsensusStartedEvent;
import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.service.BrokerRouter;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationExecutorTest {

    @Mock private ReachConsensusUseCase reachConsensusUseCase;
    @Mock private UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase;
    @Mock private StrategyRepository strategyRepository;
    @Mock private MarketEventRepository marketEventRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private SimulationPortfolioRepository simulationPortfolioRepository;
    @Mock private BrokerRouter brokerRouter;
    @Mock private SimulationRepository simulationRepository;
    @Mock private com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private SimulationLockPort simulationLockPort;
    @Mock private com.quorumtrading.application.service.consensus.DataGapCircuitBreaker dataGapCircuitBreaker;

    private java.util.concurrent.ExecutorService consensusExecutor;

    private StrategyParser strategyParser;
    private SimulationExecutor executor;

    /** Generous by design: these tests are about the loop, not about the ceiling. */
    private static final java.time.Duration EVALUATION_CEILING = java.time.Duration.ofSeconds(30);

    private static final String STOCK_STRATEGY_MARKDOWN = """
        ---
        consensus_method: MAJORITY
        stock_tickers:
          - AAPL
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"
        backtest_schedule:
          timezone: "America/New_York"
          trading_days: [WEDNESDAY]
          evaluation_times: ["10:00"]
          market_open: "09:30"
          market_close: "16:00"
        ---
        # Test Strategy
        Buy and hold.
        """;

    private static final String CRYPTO_STRATEGY_MARKDOWN = """
        ---
        consensus_method: MAJORITY
        crypto_tickers:
          - BTC
        backtest_schedule:
          interval: 1w
        ---
        # Crypto Test Strategy
        Buy and hold crypto.
        """;

    @BeforeEach
    void setUp() {
        // The heartbeat renews the lease each evaluation; grant it by default (lenient: a few
        // tests never enter the evaluation loop).
        lenient().when(simulationLockPort.refresh(any(SimulationId.class), any(String.class)))
            .thenReturn(true);
        strategyParser = new StrategyParser();
        consensusExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor = new SimulationExecutor(
            new BoundedConsensusEvaluator(reachConsensusUseCase, dataGapCircuitBreaker,
                consensusExecutor, EVALUATION_CEILING),
            updatePortfolioWithMarketValueUseCase,
            strategyParser,
            strategyRepository,
            marketEventRepository,
            portfolioRepository,
            consensusStartedEventPublisher,
            brokerAccountRepository,
            simulationPortfolioRepository,
            brokerRouter,
            simulationRepository,
            simulationLockPort,
            null,  // dispatchNotificationUseCase — pause-budget notifications skipped in this test
            consensusDecisionRepository
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        consensusExecutor.shutdownNow();
    }

    /** Rebuild the executor with a different evaluation ceiling. */
    private SimulationExecutor executorWithCeiling(java.time.Duration ceiling) {
        return new SimulationExecutor(
            new BoundedConsensusEvaluator(reachConsensusUseCase, dataGapCircuitBreaker,
                consensusExecutor, ceiling),
            updatePortfolioWithMarketValueUseCase, strategyParser,
            strategyRepository, marketEventRepository, portfolioRepository,
            consensusStartedEventPublisher, brokerAccountRepository, simulationPortfolioRepository,
            brokerRouter, simulationRepository, simulationLockPort, null,
            consensusDecisionRepository);
    }

    @Test
    @org.junit.jupiter.api.Timeout(30)
    @DisplayName("an evaluation that never returns is abandoned at the ceiling and recorded as a skip")
    void shouldAbandonAnEvaluationThatExceedsTheCeilingAndRecordItAsASkip() {
        // The failure this closes: an evaluation that hangs writes no consensus row, so the
        // circuit breaker is blind to it; stops renewing the lease, so the row looks abandoned;
        // and never returns, so the in-JVM slot is held for the life of the process. One
        // documented run sat here for eight minutes with three agents that never answered, and
        // nothing in the system could tell that from a run that had finished. Bounding the call
        // makes a hung engine produce the same honest outcome as a failing one.
        Simulation simulation = createBacktestSimulation();
        simulation.start();
        Strategy strategy = new Strategy(simulation.getStrategyId(), "Test", STOCK_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(strategyRepository.findById(simulation.getStrategyId())).thenReturn(Optional.of(strategy));
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        // An evaluation that simply does not come back.
        doAnswer(invocation -> {
            Thread.sleep(30_000);
            return null;
        }).when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));

        SimulationExecutor bounded = executorWithCeiling(java.time.Duration.ofMillis(200));
        bounded.startSimulation(simulation, STOCK_STRATEGY_MARKDOWN);

        // The round became visible instead of vanishing: a skip row through the same path a
        // failing engine takes, which feeds the counters, the health model, and eventually the
        // LLM_FAILURE_DETECTED pause.
        verify(dataGapCircuitBreaker, atLeast(2)).recordLlmFailureSkip(
            eq(simulation), any(Instant.class), any(String.class), contains("did not answer"));
        // At least two rounds means the loop did not hang and did not bail out on the first
        // abandonment: it kept its lease alive and carried on, which is what turns "the process
        // is stuck forever" into "these rounds were skipped, and here is why".
        verify(simulationLockPort, atLeast(2)).refresh(any(SimulationId.class), any(String.class));
    }

    @Test
    void shouldPauseSimulationOnBudgetDepleted_stockMode() {
        // Given: a backtest simulation in SIMULATING state
        Simulation simulation = createBacktestSimulation();
        simulation.start(); // transitions to SIMULATING (start date is in the past)

        Strategy strategy = new Strategy(simulation.getStrategyId(), "Test", STOCK_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        // First call returns the running simulation (for fresh simulation check in loop)
        // Subsequent calls don't matter since we break after BudgetDepletedException
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        // When consensus is reached, throw BudgetDepletedException
        UserId userId = UserId.generate();
        doThrow(new BudgetDepletedException(userId))
            .when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));

        // When
        executor.startSimulation(simulation, STOCK_STRATEGY_MARKDOWN);

        // Then: simulation should be paused for external reason, NOT completed
        assertTrue(simulation.isPausedForExternalReason(),
            "Simulation should be paused for external reason");
        assertEquals("BUDGET_DEPLETED", simulation.getExternalPauseReason(),
            "External pause reason should be BUDGET_DEPLETED");
        assertNotEquals(SimulationStatus.COMPLETED, simulation.getStatus(),
            "Simulation should NOT be marked as COMPLETED");
        assertNotEquals(SimulationStatus.FAILED, simulation.getStatus(),
            "Simulation should NOT be marked as FAILED");

        // Verify pause was persisted via direct SQL update (not via save())
        verify(simulationRepository).updatePauseState(
            eq(simulation.getSimulationId()), eq(false), eq(true), eq("BUDGET_DEPLETED"));
        // Verify lock was released
        verify(simulationLockPort).unlock(eq(simulation.getSimulationId()), any());
    }

    @Test
    void shouldPauseSimulationOnDailySpendLimitExceeded_cryptoMode() {
        // Given: a crypto backtest simulation in SIMULATING state
        Simulation simulation = createCryptoBacktestSimulation();
        simulation.start();

        Strategy strategy = new Strategy(simulation.getStrategyId(), "Crypto Test", CRYPTO_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        // When consensus is reached, throw DailySpendLimitExceededException
        UserId userId = UserId.generate();
        doThrow(new DailySpendLimitExceededException(userId, new BigDecimal("5.00"), new BigDecimal("5.50")))
            .when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));

        // When
        executor.startSimulation(simulation, CRYPTO_STRATEGY_MARKDOWN);

        // Then
        assertTrue(simulation.isPausedForExternalReason());
        assertEquals("BUDGET_DEPLETED", simulation.getExternalPauseReason());
        assertNotEquals(SimulationStatus.COMPLETED, simulation.getStatus());
        assertNotEquals(SimulationStatus.FAILED, simulation.getStatus());
        verify(simulationRepository).updatePauseState(
            eq(simulation.getSimulationId()), eq(false), eq(true), eq("BUDGET_DEPLETED"));
        verify(simulationLockPort).unlock(eq(simulation.getSimulationId()), any());
    }

    @Test
    void shouldNotCompleteSimulationWhenPausedForBudget() {
        // Given: simulation that would normally complete (end date in the past)
        Simulation simulation = createBacktestSimulation();
        simulation.start();

        Strategy strategy = new Strategy(simulation.getStrategyId(), "Test", STOCK_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        doThrow(new BudgetDepletedException(UserId.generate()))
            .when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));

        // When
        executor.startSimulation(simulation, STOCK_STRATEGY_MARKDOWN);

        // Then: verify the simulation was NOT completed or transitioned to LIVE
        assertEquals(SimulationStatus.SIMULATING, simulation.getStatus(),
            "Status should remain SIMULATING (not COMPLETED or LIVE)");
        assertTrue(simulation.isPausedForExternalReason(),
            "Should be paused for external reason");
        verify(simulationRepository).updatePauseState(
            eq(simulation.getSimulationId()), eq(false), eq(true), eq("BUDGET_DEPLETED"));
    }

    @Test
    void shouldCompleteStockBacktestWhoseEndDateHasPassed() {
        // Given: a stock backtest fully in the past. The Wednesday-only backtest schedule means
        // the last executed tick always lands strictly BEFORE the end date, so completion must
        // be decided by the elapsed window, not by a tick landing exactly on the end date.
        Simulation simulation = createBacktestSimulation();
        simulation.start();

        Strategy strategy = new Strategy(simulation.getStrategyId(), "Test", STOCK_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        // When: all evaluations run without error
        executor.startSimulation(simulation, STOCK_STRATEGY_MARKDOWN);

        // Then: the backtest window has fully elapsed — it must be COMPLETED, not parked in LIVE
        assertEquals(SimulationStatus.COMPLETED, simulation.getStatus(),
            "Backtest with end date in the past must complete, not transition to LIVE");
        assertNotNull(simulation.getCompletedAt(), "completedAt must be set");
    }

    @Test
    void shouldCompleteCryptoBacktestWhoseEndDateHasPassed() {
        Simulation simulation = createCryptoBacktestSimulation();
        simulation.start();

        Strategy strategy = new Strategy(simulation.getStrategyId(), "Crypto Test", CRYPTO_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        executor.startSimulation(simulation, CRYPTO_STRATEGY_MARKDOWN);

        assertEquals(SimulationStatus.COMPLETED, simulation.getStatus(),
            "Crypto backtest with end date in the past must complete, not transition to LIVE");
    }

    @Test
    void shouldStopWithoutStateTransitionWhenLockLeaseIsLost() {
        // Given: a backtest whose lock lease is stolen by another instance mid-run
        Simulation simulation = createBacktestSimulation();
        simulation.start();

        Strategy strategy = new Strategy(simulation.getStrategyId(), "Test", STOCK_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(simulationLockPort.refresh(any(SimulationId.class), any(String.class)))
            .thenReturn(false); // lease lost immediately
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        // When
        executor.startSimulation(simulation, STOCK_STRATEGY_MARKDOWN);

        // Then: the takeover instance owns the simulation — this loop must not touch its state
        assertEquals(SimulationStatus.SIMULATING, simulation.getStatus(),
            "Losing the lease must not complete, fail, or transition the simulation");
        verify(reachConsensusUseCase, never()).execute(any(ReachConsensusCommand.class));
        verify(simulationLockPort).unlock(eq(simulation.getSimulationId()), any());
    }

    /**
     * Creates a backtest simulation with dates in the past (STOCK mode).
     * Using a short range (2 weeks) to keep tests fast.
     */
    private Simulation createBacktestSimulation() {
        Instant now = Instant.now();
        return new Simulation(
            SimulationId.generate(),
            "Budget Test Backtest",
            StrategyId.generate(),
            now.minus(14, ChronoUnit.DAYS),
            now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD)
        );
    }

    /**
     * Creates a crypto backtest simulation with dates in the past (CRYPTO mode).
     * Using a 2-week range with 1-week interval = ~2 evaluations.
     */
    /**
     * A fresh backtest must evaluate AT its own start date.
     *
     * <p>Every simulation is seeded with an opening portfolio snapshot at its start date before
     * anything runs, so a resume point derived from snapshots always found a "checkpoint" and
     * advanced past the first scheduled evaluation. Across 40 simulations in the database, not
     * one had a decision at its own start date.</p>
     */
    @Test
    void shouldEvaluateAtStartDateWhenNoDecisionsExistYet() {
        Simulation simulation = createCryptoBacktestSimulation();
        simulation.start();

        Strategy strategy = new Strategy(simulation.getStrategyId(), "Crypto Test",
            CRYPTO_STRATEGY_MARKDOWN, UserId.generate());

        when(simulationLockPort.tryLock(any(SimulationId.class), any(String.class), any(Duration.class)))
            .thenReturn(true);
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        when(simulationRepository.findById(simulation.getSimulationId()))
            .thenReturn(Optional.of(simulation));

        // The opening snapshot exists, exactly as every real simulation has one...
        PortfolioSnapshot openingSnapshot = new PortfolioSnapshot(
            simulation.getSimulationId(),
            simulation.getStartDate(),
            Map.of(),
            simulation.getInitialCash(),
            simulation.getInitialCash());
        when(portfolioRepository.findBySimulationId(any(), any(), any(), anyInt()))
            .thenReturn(List.of(openingSnapshot));
        // ...but nothing has been evaluated yet.
        when(consensusDecisionRepository.findLatestBySimulationId(simulation.getSimulationId()))
            .thenReturn(Optional.empty());

        ArgumentCaptor<ReachConsensusCommand> commands =
            ArgumentCaptor.forClass(ReachConsensusCommand.class);

        executor.startSimulation(simulation, CRYPTO_STRATEGY_MARKDOWN);

        verify(reachConsensusUseCase, atLeastOnce()).execute(commands.capture());
        Instant firstEvaluation = commands.getAllValues().get(0).analysisTimestamp();

        assertEquals(simulation.getStartDate(), firstEvaluation,
            "First evaluation must land on the simulation's start date, not one interval after it");
    }

    private Simulation createCryptoBacktestSimulation() {
        Instant now = Instant.now();
        return new Simulation(
            SimulationId.generate(),
            "Crypto Budget Test",
            StrategyId.generate(),
            now.minus(14, ChronoUnit.DAYS),
            now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("50000"), Money.Currency.USD)
        );
    }
}
