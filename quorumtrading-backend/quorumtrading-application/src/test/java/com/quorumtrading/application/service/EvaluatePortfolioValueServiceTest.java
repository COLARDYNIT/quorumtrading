package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.SimulationEvaluationResult;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluatePortfolioValueServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MarketEventRepository marketEventRepository;

    @Mock
    private BrokerPort brokerPort;

    private EvaluatePortfolioValueService service;
    private SimulationId simulationId;

    @BeforeEach
    void setUp() {
        service = new EvaluatePortfolioValueService(
            simulationRepository,
            portfolioRepository,
            marketEventRepository,
            brokerPort
        );
        simulationId = new SimulationId(UUID.randomUUID());
    }

    @Test
    @DisplayName("should skip evaluation when simulation end date has passed")
    void shouldSkipEvaluationWhenEndDatePassed() {
        // Backtest with end date 30 days ago — still in SIMULATING (e.g. stuck or just completed execution)
        Instant pastEndDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Simulation simulation = createBacktest(pastEndDate);
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));

        SimulationEvaluationResult result = service.evaluateSimulation(simulationId);

        assertThat(result.skipped()).isTrue();
        assertThat(result.errorMessage()).contains("has ended");
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("should skip evaluation when simulation end date just passed (edge case)")
    void shouldSkipEvaluationWhenEndDateJustPassed() {
        Instant justPastEndDate = Instant.now().minus(1, ChronoUnit.SECONDS);
        Simulation simulation = createBacktest(justPastEndDate);
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));

        SimulationEvaluationResult result = service.evaluateSimulation(simulationId);

        assertThat(result.skipped()).isTrue();
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("should proceed with evaluation when simulation has no end date")
    void shouldProceedWhenNoEndDate() {
        // SIMULATING simulation with no end date — guard should not fire
        Simulation simulation = createBacktest(null);
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));

        // No snapshot found — hits the "no snapshot" path, proving the end-date guard was bypassed
        when(portfolioRepository.findLatestBySimulationId(simulationId)).thenReturn(Optional.empty());

        SimulationEvaluationResult result = service.evaluateSimulation(simulationId);

        assertThat(result.skipped()).isTrue();
        assertThat(result.errorMessage()).contains("No portfolio snapshot found");
    }

    @Test
    @DisplayName("should create snapshot for cash-only portfolio when no end date")
    void shouldCreateSnapshotForCashOnlyWhenNoEndDate() {
        // SIMULATING simulation with no end date — passes both guards
        Simulation simulation = createBacktest(null);
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));

        // Cash-only portfolio snapshot exists
        PortfolioSnapshot latestSnapshot = new PortfolioSnapshot(
            simulationId,
            Instant.now().minus(1, ChronoUnit.HOURS),
            Map.of(),
            Money.usd(new BigDecimal("10000")),
            Money.usd(new BigDecimal("10000"))
        );
        when(portfolioRepository.findLatestBySimulationId(simulationId)).thenReturn(Optional.of(latestSnapshot));

        SimulationEvaluationResult result = service.evaluateSimulation(simulationId);

        assertThat(result.success()).isTrue();
        verify(portfolioRepository).save(any(PortfolioSnapshot.class));
    }

    @Test
    @DisplayName("should skip evaluation when simulation is not SIMULATING")
    void shouldSkipWhenNotSimulating() {
        Simulation simulation = createBacktest(null);
        simulation.complete(); // terminal state
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));

        SimulationEvaluationResult result = service.evaluateSimulation(simulationId);

        assertThat(result.skipped()).isTrue();
        assertThat(result.errorMessage()).contains("not in SIMULATING status");
    }

    @Test
    @DisplayName("a system-paused simulation (pausedForExternalReason) is never evaluated")
    void shouldNotEvaluateSystemPausedSimulations() {
        // Third of the three schedulers that must agree: the circuit breaker and the stall
        // detector set pausedForExternalReason while leaving paused false, so a filter that only
        // looked at `paused` kept evaluating rows the system had already given up on — once a
        // minute, forever. All three must skip them or a pause relieves nothing.
        Simulation systemPaused = createLiveSimulation();
        systemPaused.pauseForExternalReason("EXECUTION_STALLED");
        when(simulationRepository.findRunnable(SimulationStatus.SIMULATING))
            .thenReturn(java.util.List.of(systemPaused));

        var result = service.evaluateAllActive();

        assertThat(result.processed()).isZero();
        verify(simulationRepository, never()).findById(any());
        verify(simulationRepository, never()).findAll();
    }

    @Test
    @DisplayName("a user-paused simulation is never evaluated either")
    void shouldNotEvaluateUserPausedSimulations() {
        Simulation userPaused = createLiveSimulation();
        userPaused.pause();
        when(simulationRepository.findRunnable(SimulationStatus.SIMULATING))
            .thenReturn(java.util.List.of(userPaused));

        var result = service.evaluateAllActive();

        assertThat(result.processed()).isZero();
        verify(simulationRepository, never()).findById(any());
    }

    /** A non-backtest SIMULATING run — the population this scheduler is actually for. */
    private Simulation createLiveSimulation() {
        Simulation simulation = new Simulation(
            simulationId,
            "Live-ish Simulation",
            new StrategyId(UUID.randomUUID()),
            Instant.now().plus(1, ChronoUnit.HOURS),
            null,
            Money.usd(new BigDecimal("100000"))
        );
        simulation.setStatus(SimulationStatus.SIMULATING);
        return simulation;
    }

    /**
     * Create a backtest simulation in SIMULATING status.
     * Start date is always in the past; end date must be null or in the past.
     */
    private Simulation createBacktest(Instant endDate) {
        Simulation simulation = new Simulation(
            simulationId,
            "Test Backtest",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2020-01-01T00:00:00Z"),
            endDate,
            Money.usd(new BigDecimal("100000"))
        );
        simulation.start(); // → SIMULATING for backtests
        return simulation;
    }
}
