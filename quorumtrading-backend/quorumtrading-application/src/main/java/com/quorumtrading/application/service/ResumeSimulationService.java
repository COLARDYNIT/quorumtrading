package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.SimulationExecutor;
import com.quorumtrading.application.port.out.AsyncExecutionPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ResumeSimulationUseCase;
import com.quorumtrading.application.usecase.command.ResumeSimulationCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for resuming simulation execution.
 * Nearly identical to StartSimulationService but with different semantics:
 * - StartSimulationService is for brand new simulations (PENDING status)
 * - ResumeSimulationService is for restarting existing simulations (any status)
 *
 * The actual resume logic is handled by SimulationExecutor.startSimulation()
 * which now supports idempotent resume via ResumeContext.
 */
public class ResumeSimulationService implements ResumeSimulationUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ResumeSimulationService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final SimulationExecutor simulationExecutor;
    private final AsyncExecutionPort asyncExecutionPort;
    private final AuthorizationService authorizationService;

    public ResumeSimulationService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            SimulationExecutor simulationExecutor,
            AsyncExecutionPort asyncExecutionPort,
            AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.simulationExecutor = simulationExecutor;
        this.asyncExecutionPort = asyncExecutionPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(ResumeSimulationCommand command) {
        // Find simulation
        Simulation simulation = simulationRepository.findById(command.simulationId())
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Simulation not found with ID: " + command.simulationId()));

        // Find strategy
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Strategy not found with ID: " + simulation.getStrategyId()));

        // Ownership check. This use case previously had none while being reachable from
        // POST /simulations/{id}/resume, which resolved the caller and then discarded it — any
        // authenticated user could resume any other user's simulation, restarting a run that
        // sends orders to that user's broker. System-triggered resumes (startup recovery) pass
        // a null user deliberately.
        authorizationService.requireEdit(command.requestingUserId(), simulation, strategy);

        // Clear pause state via direct SQL before resuming to prevent stale overwrites
        simulationRepository.updatePauseState(command.simulationId(), false, false, null);

        // Submit resume execution asynchronously to avoid blocking the request thread.
        // SimulationExecutor handles its own state management (locking, completion, error recovery).
        String strategyContent = strategy.getContent();
        LOG.info("Submitting simulation {} for async resume",
            simulation.getSimulationId().value().toString().substring(0, 8));
        asyncExecutionPort.submitAsync(() -> {
            try {
                // Save only when this call actually ran the simulation — on a refused or lost
                // lease the entity here is a stale copy of state another executor owns.
                if (simulationExecutor.startSimulation(simulation, strategyContent)) {
                    simulationRepository.save(simulation);
                }
            } catch (Exception e) {
                LOG.error("Async simulation resume failed for {}: {}",
                    simulation.getSimulationId().value().toString().substring(0, 8),
                    e.getMessage(), e);
            }
        });
    }
}
