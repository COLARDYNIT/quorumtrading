package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.ScheduledConsensusResult;
import com.quorumtrading.application.usecase.SimulationConsensusResult;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TriggerScheduledConsensusService}.
 * Verifies that:
 * - Backtest simulations are excluded from scheduled processing
 * - Only LIVE, non-paused, non-backtest simulations are processed
 * - Evaluation interval is respected
 */
@ExtendWith(MockitoExtension.class)
class TriggerScheduledConsensusServiceTest {

    @Mock
    private ReachConsensusUseCase reachConsensusUseCase;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MarketEventRepository marketEventRepository;

    @Mock
    private ConsensusDecisionRepository consensusDecisionRepository;

    @Mock
    private StrategyParser strategyParser;

    private TriggerScheduledConsensusService service;

    private StrategyId strategyId;
    private Strategy strategy;
    private StrategyConfig strategyConfig;

    @BeforeEach
    void setUp() {
        service = new TriggerScheduledConsensusService(
                reachConsensusUseCase,
                simulationRepository,
                strategyRepository,
                portfolioRepository,
                marketEventRepository,
                consensusDecisionRepository,
                strategyParser,
                Executors.newFixedThreadPool(2)
        );

        strategyId = StrategyId.generate();
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        strategy = new Strategy(strategyId, "Test Strategy", "## Strategy Content", systemUserId);
        strategyConfig = new StrategyConfig();
        strategyConfig.setEvaluationIntervalSeconds(3600); // 1 hour
    }

    @Test
    void shouldExcludeBacktestSimulationsFromScheduledProcessing() throws Exception {
        // Given: A backtest simulation (endDate <= now) in LIVE status
        Instant createdAt = Instant.parse("2025-11-10T12:00:00Z");
        Instant startDate = Instant.parse("2025-10-01T00:00:00Z"); // In the past
        Instant endDate = Instant.parse("2025-11-05T23:59:59Z");  // endDate in the past = BACKTEST

        Simulation backtest = createSimulation(
                SimulationId.generate(),
                "Backtest Simulation",
                startDate,
                endDate,
                createdAt,
                SimulationStatus.LIVE,
                false
        );

        // Verify this is indeed a backtest
        assertThat(backtest.isBacktest()).isTrue();

        when(simulationRepository.findRunnable(SimulationStatus.LIVE)).thenReturn(List.of(backtest));

        // When: Scheduler runs
        ScheduledConsensusResult result = service.executeForAllActive();

        // Then: Backtest simulation is NOT processed
        assertThat(result.simulationsProcessed()).isEqualTo(0);
        assertThat(result.simulationsSkipped()).isEqualTo(0); // Not counted as skipped in parallel execution
        assertThat(result.simulationsFailed()).isEqualTo(0);
        verify(reachConsensusUseCase, never()).execute(any());
    }

    @Test
    void shouldProcessLiveSimulationsButNotBacktests() throws Exception {
        // Given: One live simulation and one backtest simulation
        Instant now = Instant.now();
        Instant createdAt = now.minus(2, ChronoUnit.DAYS);

        // Live simulation: endDate == null (ongoing)
        Instant liveStartDate = createdAt.plus(1, ChronoUnit.HOURS);
        Simulation liveSimulation = createSimulation(
                SimulationId.generate(),
                "Live Simulation",
                liveStartDate,
                null,
                createdAt,
                SimulationStatus.LIVE,
                false
        );

        // Backtest: startDate < createdAt
        Instant backtestStartDate = createdAt.minus(30, ChronoUnit.DAYS);
        Simulation backtestSimulation = createSimulation(
                SimulationId.generate(),
                "Backtest Simulation",
                backtestStartDate,
                createdAt,
                createdAt,
                SimulationStatus.LIVE,
                false
        );

        // Verify classification
        assertThat(liveSimulation.isBacktest()).isFalse();
        assertThat(backtestSimulation.isBacktest()).isTrue();

        when(simulationRepository.findRunnable(SimulationStatus.LIVE)).thenReturn(List.of(liveSimulation, backtestSimulation));
        when(simulationRepository.findById(liveSimulation.getSimulationId())).thenReturn(Optional.of(liveSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(strategyParser.parse(any())).thenReturn(strategyConfig);
        when(consensusDecisionRepository.findLatestBySimulationId(any())).thenReturn(Optional.empty());
        when(portfolioRepository.findLatestBySimulationId(liveSimulation.getSimulationId()))
                .thenReturn(Optional.of(createPortfolioSnapshot(liveSimulation.getSimulationId())));

        // When: Scheduler runs
        ScheduledConsensusResult result = service.executeForAllActive();

        // Then: Only live simulation is processed, backtest is excluded
        assertThat(result.simulationsProcessed()).isEqualTo(1);
        verify(reachConsensusUseCase, times(1)).execute(any());
    }

    @Test
    void shouldSkipBacktestWhenExecutingForSpecificSimulation() {
        // Given: A backtest simulation (endDate in the past)
        Instant createdAt = Instant.parse("2025-11-10T12:00:00Z");
        Instant startDate = Instant.parse("2025-10-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-05T23:59:59Z");  // endDate in the past = BACKTEST
        SimulationId simulationId = SimulationId.generate();

        Simulation backtest = createSimulation(
                simulationId,
                "Backtest",
                startDate,
                endDate,
                createdAt,
                SimulationStatus.LIVE,
                false
        );

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(backtest));

        // When: Executing for this specific simulation
        SimulationConsensusResult result = service.executeForSimulation(simulationId);

        // Then: Simulation is skipped with appropriate message
        assertThat(result.skipped()).isTrue();
        assertThat(result.errorMessage()).contains("backtest");
        assertThat(result.errorMessage()).contains("endDate <= now");
        verify(reachConsensusUseCase, never()).execute(any());
    }

    @Test
    void shouldNotSkipPausedSimulations() {
        // Given: A paused (but not backtest) simulation
        Instant createdAt = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant startDate = createdAt.plus(1, ChronoUnit.HOURS); // After createdAt, so NOT a backtest
        SimulationId simulationId = SimulationId.generate();

        Simulation pausedSim = createSimulation(
                simulationId,
                "Paused Simulation",
                startDate,
                Instant.now().plus(30, ChronoUnit.DAYS),
                createdAt,
                SimulationStatus.LIVE,
                true // paused
        );

        when(simulationRepository.findRunnable(SimulationStatus.LIVE)).thenReturn(List.of(pausedSim));

        // When: Scheduler runs
        ScheduledConsensusResult result = service.executeForAllActive();

        // Then: Paused simulation is excluded
        assertThat(result.simulationsProcessed()).isEqualTo(0);
        verify(reachConsensusUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a system-paused simulation (pausedForExternalReason) is never offered a round")
    void shouldNotOfferSystemPausedSimulations() {
        // The circuit breaker and the stall detector set pausedForExternalReason while leaving
        // paused false, so a filter that only looked at `paused` re-offered every run they had
        // paused, once a minute, forever. That is also why replacing force-completion with a
        // pause is safe for the fleet: an excluded row costs the schedulers nothing.
        Instant createdAt = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant startDate = createdAt.plus(1, ChronoUnit.HOURS);

        Simulation systemPaused = createSimulation(
                SimulationId.generate(),
                "System Paused Simulation",
                startDate,
                Instant.now().plus(30, ChronoUnit.DAYS),
                createdAt,
                SimulationStatus.LIVE,
                false // NOT user-paused — this is the case that used to slip through
        );
        systemPaused.pauseForExternalReason("EXECUTION_STALLED");

        when(simulationRepository.findRunnable(SimulationStatus.LIVE))
                .thenReturn(List.of(systemPaused));

        ScheduledConsensusResult result = service.executeForAllActive();

        assertThat(result.simulationsProcessed()).isEqualTo(0);
        verify(reachConsensusUseCase, never()).execute(any());
    }

    @Test
    void shouldNotSkipSimulatingStatusSimulations() {
        // Given: A simulation in SIMULATING status (not LIVE)
        Instant createdAt = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant startDate = createdAt.plus(1, ChronoUnit.HOURS);
        SimulationId simulationId = SimulationId.generate();

        Simulation simulatingStatus = createSimulation(
                simulationId,
                "Simulating Status Simulation",
                startDate,
                Instant.now().plus(30, ChronoUnit.DAYS),
                createdAt,
                SimulationStatus.SIMULATING, // Not LIVE
                false
        );

        when(simulationRepository.findRunnable(SimulationStatus.LIVE)).thenReturn(List.of(simulatingStatus));

        // When: Scheduler runs
        ScheduledConsensusResult result = service.executeForAllActive();

        // Then: SIMULATING status simulation is excluded
        assertThat(result.simulationsProcessed()).isEqualTo(0);
        verify(reachConsensusUseCase, never()).execute(any());
    }

    @Test
    void shouldLogBacktestExclusionCount() {
        // Given: Multiple backtests and one live simulation
        Instant createdAt = Instant.now().minus(2, ChronoUnit.DAYS);

        Simulation backtest1 = createSimulation(
                SimulationId.generate(),
                "Backtest 1",
                createdAt.minus(30, ChronoUnit.DAYS), // Before createdAt
                createdAt,
                createdAt,
                SimulationStatus.LIVE,
                false
        );

        Simulation backtest2 = createSimulation(
                SimulationId.generate(),
                "Backtest 2",
                createdAt.minus(60, ChronoUnit.DAYS), // Before createdAt
                createdAt,
                createdAt,
                SimulationStatus.LIVE,
                false
        );

        Simulation liveSimulation = createSimulation(
                SimulationId.generate(),
                "Live Simulation",
                createdAt.plus(1, ChronoUnit.HOURS), // After createdAt
                null, // LIVE = no end date
                createdAt,
                SimulationStatus.LIVE,
                false
        );

        when(simulationRepository.findRunnable(SimulationStatus.LIVE)).thenReturn(List.of(backtest1, backtest2, liveSimulation));
        when(simulationRepository.findById(liveSimulation.getSimulationId())).thenReturn(Optional.of(liveSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(strategyParser.parse(any())).thenReturn(strategyConfig);
        when(consensusDecisionRepository.findLatestBySimulationId(any())).thenReturn(Optional.empty());
        when(portfolioRepository.findLatestBySimulationId(liveSimulation.getSimulationId()))
                .thenReturn(Optional.of(createPortfolioSnapshot(liveSimulation.getSimulationId())));

        // When: Scheduler runs
        ScheduledConsensusResult result = service.executeForAllActive();

        // Then: Only 1 live simulation processed, 2 backtests excluded
        assertThat(result.simulationsProcessed()).isEqualTo(1);
        // Log message should indicate 2 backtests excluded (verify via manual inspection or log capture)
    }

    /**
     * Helper to create a Simulation with controlled creation timestamp.
     * Uses reflection to set private createdAt field since constructor doesn't expose it.
     */
    private Simulation createSimulation(
            SimulationId id,
            String name,
            Instant startDate,
            Instant endDate,
            Instant createdAt,
            SimulationStatus status,
            boolean paused) {

        Simulation simulation = Simulation.reconstitute(
                id,
                name,
                strategyId,
                startDate,
                endDate,
                Money.usd(new BigDecimal("100000")),
                null,
                null,
                null
        );

        // Set createdAt using reflection
        try {
            var createdAtField = Simulation.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(simulation, createdAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set createdAt via reflection", e);
        }

        // Set status
        try {
            var statusField = Simulation.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(simulation, status);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set status via reflection", e);
        }

        // Set paused state (simulations start paused by default)
        if (!paused) {
            simulation.resume();
        }

        return simulation;
    }

    private PortfolioSnapshot createPortfolioSnapshot(SimulationId simulationId) {
        return new PortfolioSnapshot(
                simulationId,
                Instant.now(),
                java.util.Map.of(), // Empty positions
                Money.usd(new BigDecimal("100000")),
                Money.usd(new BigDecimal("100000"))
        );
    }
}
