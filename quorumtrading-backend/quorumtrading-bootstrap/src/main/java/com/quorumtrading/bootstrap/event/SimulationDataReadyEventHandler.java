package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.SimulationDataReadyEvent;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI event handler that observes SimulationDataReadyEvent and starts simulations.
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * Bootstrap layer adapter that bridges CDI events to the application layer
 * StartSimulationUseCase.
 *
 * <p><b>Backfill Gate Flow:</b></p>
 * This is the final step in the backfill gate flow:
 * <pre>
 * SimulationCreated -> BackfillOrchestratorHandler -> [check availability]
 *                                                      |
 *                      [LIVE or no gaps]              [gaps found]
 *                            |                             |
 *                            v                             v
 *                   SimulationDataReadyEvent         RequestBackfill
 *                            |                             |
 *                            |                             v
 *                            |                    BackfillExecutor (fills data)
 *                            |                             |
 *                            |                             v
 *                            |               BackfillStatusChangedEvent (COMPLETED)
 *                            |                             |
 *                            |                             v
 *                            |               BackfillCompletedHandler
 *                            |                             |
 *                            |                             v
 *                            +------ SimulationDataReadyEvent <------+
 *                                             |
 *                                             v
 *                               SimulationDataReadyEventHandler (this)
 *                                             |
 *                                             v
 *                                    StartSimulationUseCase
 * </pre>
 *
 * <p><b>Design Note:</b></p>
 * Uses @ObservesAsync for non-blocking event handling. This allows the simulation
 * to start in a background thread without blocking the caller.
 */
@ApplicationScoped
public class SimulationDataReadyEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationDataReadyEventHandler.class);

    @Inject
    StartSimulationUseCase startSimulationUseCase;

    /**
     * Observes SimulationDataReadyEvent asynchronously and starts the simulation.
     *
     * @param event the simulation data ready event containing simulation ID
     */
    void onDataReady(@ObservesAsync SimulationDataReadyEvent event) {
        LOG.info("Data ready for simulation: {} - starting execution", event.simulationId());
        try {
            startSimulationUseCase.execute(StartSimulationCommand.system(event.simulationId()));
            LOG.info("Successfully started simulation: {}", event.simulationId());
        } catch (Exception e) {
            // Catch all exceptions to prevent propagation to other event observers
            // Log error but don't rethrow - other events should still be processed
            LOG.error("Failed to start simulation: {}", event.simulationId(), e);
        }
    }
}
