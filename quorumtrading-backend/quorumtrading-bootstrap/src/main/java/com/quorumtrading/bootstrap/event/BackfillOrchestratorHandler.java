package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.service.BackfillOrchestratorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI event handler that observes SimulationCreatedEvent and orchestrates backfill.
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * This is a thin adapter in the bootstrap layer that bridges CDI events to the
 * application layer BackfillOrchestratorService. All business logic lives in
 * the application service.
 *
 * <p><b>Flow:</b></p>
 * <pre>
 * SimulationCreatedEvent -> BackfillOrchestratorHandler -> BackfillOrchestratorService
 *                                                          |
 *                                                          v
 *                                   [LIVE] -> SimulationDataReadyEvent (immediate)
 *                                   [BACKTEST] -> Check data availability
 *                                                 |
 *                                                 v
 *                                   [No gaps] -> SimulationDataReadyEvent
 *                                   [Gaps found] -> RequestBackfill (links to simulation)
 * </pre>
 *
 * <p><b>Note:</b> This handler replaces the old SimulationCreatedEventHandler which
 * immediately started simulations. Now we check data availability first and trigger
 * backfill if needed.</p>
 */
@ApplicationScoped
public class BackfillOrchestratorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillOrchestratorHandler.class);

    @Inject
    BackfillOrchestratorService orchestratorService;

    /**
     * Observes SimulationCreatedEvent synchronously so the backfill check completes
     * before create_simulation returns. This prevents the MCP race condition where
     * start_simulation is called before the backfill check has set WAITING_FOR_DATA.
     *
     * @param event the simulation created event containing simulation ID
     */
    void onSimulationCreated(@Observes SimulationCreatedEvent event) {
        LOG.info("Received SimulationCreatedEvent, orchestrating backfill check: {}", event.simulationId());
        try {
            orchestratorService.handleSimulationCreated(event.simulationId());
        } catch (org.hibernate.exception.ConstraintViolationException e) {
            // Simulation was deleted between event dispatch and backfill creation — safe to ignore
            LOG.warn("Simulation {} was deleted before backfill could be created, skipping: {}",
                event.simulationId(), e.getMessage());
        } catch (Exception e) {
            // Catch all exceptions to prevent propagation to other event observers
            // Log error but don't rethrow - other events should still be processed
            LOG.error("Failed to orchestrate backfill for simulation: {}", event.simulationId(), e);
        }
    }
}
