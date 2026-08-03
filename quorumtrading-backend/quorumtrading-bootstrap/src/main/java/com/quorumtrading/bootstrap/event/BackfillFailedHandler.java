package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.BackfillJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI event handler that observes backfill failures and fails linked simulations.
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * Bootstrap layer adapter that bridges CDI events. When a backfill fails,
 * if it has a linked simulation, transitions the simulation to FAILED state
 * with a detailed error message.
 *
 * <p><b>Flow:</b></p>
 * <pre>
 * BackfillStatusChangedEvent (FAILED) -> BackfillFailedHandler
 *                                         |
 *                                         v
 *                          [Has linkedSimulationId?]
 *                                         |
 *                         Yes             |            No
 *                          |              v             |
 *                          v         (no action)       v
 *                 Simulation.fail() with error message
 * </pre>
 *
 * <p><b>Design Note:</b></p>
 * This ensures simulations don't stay stuck in WAITING_FOR_DATA forever when
 * backfill fails. The user can see the failure reason and take corrective action.
 */
@ApplicationScoped
public class BackfillFailedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillFailedHandler.class);

    @Inject
    BackfillJobRepository backfillJobRepository;

    @Inject
    SimulationRepository simulationRepository;

    /**
     * Observes BackfillStatusChangedEvent and fails linked simulations when backfill fails.
     *
     * @param event the backfill status changed event
     */
    void onBackfillFailed(@Observes BackfillStatusChangedEvent event) {
        if (event.newStatus() != BackfillStatus.FAILED) {
            return;
        }

        LOG.warn("Backfill failed: {} - {}", event.jobId(), event.errorMessage());

        backfillJobRepository.findById(event.jobId())
            .flatMap(BackfillJob::getLinkedSimulationId)
            .ifPresent(simId -> {
                try {
                    Simulation simulation = simulationRepository.findById(simId)
                        .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simId));

                    // Only fail if simulation is still waiting for data
                    if (simulation.isWaitingForData()) {
                        simulation.fail("Market-data backfill failed: "
                            + (event.errorMessage() != null ? event.errorMessage() : "unknown error")
                            + ". No provider returned data for this range.");
                        simulationRepository.updateStatus(simId,
                            SimulationStatus.WAITING_FOR_DATA, SimulationStatus.FAILED,
                            simulation.getCompletedAt(), simulation.getFailureReason());

                        String errorMessage = event.errorMessage() != null
                            ? event.errorMessage()
                            : "Backfill failed with unknown error";

                        LOG.error("Simulation {} failed due to backfill failure: {}",
                            simId, errorMessage);
                    } else {
                        LOG.warn("Simulation {} is not in WAITING_FOR_DATA state (current: {}), " +
                            "not failing due to backfill failure",
                            simId, simulation.getStatus());
                    }
                } catch (Exception e) {
                    LOG.error("Failed to handle backfill failure for simulation {}: {}",
                        simId, e.getMessage(), e);
                }
            });
    }
}
