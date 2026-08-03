package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.orchestration.SimulationExecutor;
import com.quorumtrading.application.port.out.AsyncExecutionPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pickup scheduler used to {@code save(simulation)} unconditionally after every
 * {@code startSimulation} attempt. When the execution lease was refused (the normal case for
 * a simulation already running — locally or on another instance), that wrote the scheduler's
 * stale copy of the entity back over the owning executor's state. The save is now gated on
 * the executor's return value: only the call that actually ran the simulation persists it.
 */
class SimulationPickupSchedulerTest {

    private SimulationRepository simulationRepository;
    private SimulationExecutor simulationExecutor;

    private SimulationPickupScheduler scheduler;
    private Simulation simulation;

    @BeforeEach
    void setUp() {
        simulationRepository = mock(SimulationRepository.class);
        StrategyRepository strategyRepository = mock(StrategyRepository.class);
        simulationExecutor = mock(SimulationExecutor.class);
        AsyncExecutionPort asyncExecutionPort = mock(AsyncExecutionPort.class);

        scheduler = new SimulationPickupScheduler();
        scheduler.simulationRepository = simulationRepository;
        scheduler.strategyRepository = strategyRepository;
        scheduler.simulationExecutor = simulationExecutor;
        scheduler.asyncExecutionPort = asyncExecutionPort;
        scheduler.enabled = true;

        simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Pickup Sim",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-02-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));
        simulation.start();

        Strategy strategy = new Strategy(
            simulation.getStrategyId(), "Pickup Strategy", "content", new UserId(UUID.randomUUID()));

        when(simulationRepository.findRunnable(any()))
            .thenReturn(List.of(simulation));
        when(strategyRepository.findById(simulation.getStrategyId()))
            .thenReturn(Optional.of(strategy));
        // Run submitted work inline so the scheduler's async body is observable synchronously
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(asyncExecutionPort).submitAsync(any());
    }

    @Test
    @DisplayName("lease refused (already running) → the stale entity is NOT saved back")
    void refusedLeaseDoesNotSaveTheStaleEntity() {
        when(simulationExecutor.startSimulation(any(), anyString())).thenReturn(false);

        scheduler.pickupSimulations();

        verify(simulationExecutor).startSimulation(any(), anyString());
        verify(simulationRepository, never()).save(any());
    }

    @Test
    @DisplayName("simulation actually executed → the entity is saved")
    void executedRunIsSaved() {
        when(simulationExecutor.startSimulation(any(), anyString())).thenReturn(true);

        scheduler.pickupSimulations();

        verify(simulationRepository).save(simulation);
    }

    @Test
    @DisplayName("a system-paused run (pausedForExternalReason) is never offered work")
    void systemPausedSimulationIsNotOffered() {
        // The circuit breaker and the stall detector both set pausedForExternalReason while
        // leaving paused false. Filtering only on paused meant every run they paused was
        // re-offered here every 30 seconds forever — a strategy parse, a resume-point query and
        // three writes per run per tick — which is why "pause instead of force-complete" would
        // otherwise have given the fleet no relief at all.
        simulation.pauseForExternalReason("EXECUTION_STALLED");

        scheduler.pickupSimulations();

        verify(simulationExecutor, never()).startSimulation(any(), anyString());
        verify(simulationRepository, never()).save(any());
    }

    @Test
    @DisplayName("a user-paused run is never offered work either")
    void userPausedSimulationIsNotOffered() {
        simulation.pause();

        scheduler.pickupSimulations();

        verify(simulationExecutor, never()).startSimulation(any(), anyString());
    }

    @Test
    @DisplayName("eligibility is asked of the database, not filtered out of every row ever created")
    void asksTheDatabaseForRunnableRowsOnly() {
        scheduler.pickupSimulations();

        verify(simulationRepository).findRunnable(SimulationStatus.SIMULATING);
        verify(simulationRepository, never()).findAll();
    }
}
