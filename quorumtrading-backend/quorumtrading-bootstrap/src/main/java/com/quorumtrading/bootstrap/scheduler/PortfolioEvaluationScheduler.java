package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.usecase.EvaluatePortfolioValueUseCase;
import com.quorumtrading.application.usecase.PortfolioEvaluationResult;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Scheduled job to automatically evaluate portfolio values for SIMULATING (historical) simulations.
 * Updates portfolio snapshots with current market prices during historical catch-up.
 * Skips LIVE simulations to prevent race conditions with consensus execution.
 * Runs every 5 minutes by default.
 */
@ApplicationScoped
public class PortfolioEvaluationScheduler {

    private static final Logger LOG = Logger.getLogger(PortfolioEvaluationScheduler.class);

    @Inject
    EvaluatePortfolioValueUseCase evaluatePortfolioValueUseCase;

    @ConfigProperty(name = "quorumtrading.scheduler.portfolio-evaluation.enabled", defaultValue = "true")
    boolean schedulerEnabled;

    /**
     * Evaluate portfolio values for all SIMULATING (historical) non-paused simulations.
     * Skips LIVE simulations to prevent overwriting consensus execution snapshots.
     * Runs every 5 minutes by default.
     */
    @Scheduled(every = "${quorumtrading.scheduler.portfolio-evaluation.interval:5m}", concurrentExecution = ConcurrentExecution.SKIP)
    public void evaluatePortfolioValues() {
        if (!schedulerEnabled) {
            LOG.info("Portfolio evaluation scheduler is disabled");
            return;
        }

        LOG.info("Starting portfolio evaluation scheduler execution");

        try {
            PortfolioEvaluationResult result = evaluatePortfolioValueUseCase.evaluateAllActive();

            if (result.processed() > 0 || result.failed() > 0) {
                LOG.infof("Portfolio evaluation completed - Processed: %d, Skipped: %d, Failed: %d",
                        result.processed(),
                        result.skipped(),
                        result.failed());
            } else {
                LOG.info("Portfolio evaluation: No active simulations to evaluate");
            }

            if (!result.errors().isEmpty()) {
                LOG.warnf("Portfolio evaluation completed with %d error(s): %s",
                        result.errors().size(),
                        String.join("; ", result.errors()));
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to execute portfolio evaluation scheduler");
        }
    }
}
