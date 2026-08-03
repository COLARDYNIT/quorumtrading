package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.usecase.EndSimulationResult;
import com.quorumtrading.application.usecase.EndSimulationUseCase;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Scheduled job to automatically end simulations that have reached their end date.
 * Runs at a configurable interval (default: every 5 minutes).
 */
@ApplicationScoped
public class EndSimulationScheduler {

    private static final Logger LOG = Logger.getLogger(EndSimulationScheduler.class);

    @Inject
    EndSimulationUseCase endSimulationUseCase;

    @ConfigProperty(name = "quorumtrading.scheduler.end-simulation.enabled", defaultValue = "true")
    boolean schedulerEnabled;

    /**
     * End all simulations that have reached their end date.
     * Runs at the configured interval (default: 5 minutes).
     */
    @Scheduled(every = "${quorumtrading.scheduler.end-simulation.interval:5m}", concurrentExecution = ConcurrentExecution.SKIP)
    public void endExpiredSimulations() {
        if (!schedulerEnabled) {
            LOG.debug("End simulation scheduler is disabled");
            return;
        }

        LOG.info("Starting end simulation scheduler execution");

        try {
            EndSimulationResult result = endSimulationUseCase.endExpiredSimulations();

            if (result.simulationsEnded() > 0 || result.simulationsFailed() > 0
                    || result.simulationsPaused() > 0) {
                // Paused is reported separately and never folded into Ended: a stalled run that
                // was paused has NOT ended, and reporting it as ended is the exact lie this
                // scheduler used to tell (ADR-0004).
                LOG.infof("End simulation scheduler completed - Ended: %d, Paused (stalled): %d, "
                                + "Skipped: %d, Failed: %d",
                        result.simulationsEnded(),
                        result.simulationsPaused(),
                        result.simulationsSkipped(),
                        result.simulationsFailed());
            } else {
                LOG.info("No expired simulations to end and no stalled backtests to pause");
            }

            if (!result.errors().isEmpty()) {
                LOG.warnf("End simulation scheduler completed with %d error(s): %s",
                        result.errors().size(),
                        String.join("; ", result.errors()));
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to execute end simulation scheduler");
        }
    }
}
