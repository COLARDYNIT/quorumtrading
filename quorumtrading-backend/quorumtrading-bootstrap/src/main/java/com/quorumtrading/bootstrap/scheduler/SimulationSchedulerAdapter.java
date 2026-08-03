package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.usecase.TriggerSimulationUseCase;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin adapter that wraps the scheduler service with Quarkus @Scheduled.
 *
 * <p><b>Hexagonal Architecture:</b> Bootstrap Layer Adapter</p>
 * The business logic lives in SimulationSchedulerService (application layer).
 * This adapter only handles the scheduling infrastructure.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Trigger scheduled simulation processing every hour</li>
 *   <li>Use SKIP to prevent overlapping executions</li>
 *   <li>Handle exceptions gracefully</li>
 * </ul>
 */
@ApplicationScoped
public class SimulationSchedulerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationSchedulerAdapter.class);

    private final TriggerSimulationUseCase schedulerService;

    public SimulationSchedulerAdapter(TriggerSimulationUseCase schedulerService) {
        this.schedulerService = schedulerService;
    }

    /**
     * Process scheduled simulations every hour.
     * Uses SKIP to prevent overlapping executions.
     */
    @Scheduled(every = "1h", concurrentExecution = ConcurrentExecution.SKIP)
    void processScheduledSimulations() {
        LOG.debug("Running scheduled simulation check");
        try {
            schedulerService.processScheduledSimulations();
        } catch (Exception e) {
            LOG.error("Error processing scheduled simulations", e);
        }
    }
}
