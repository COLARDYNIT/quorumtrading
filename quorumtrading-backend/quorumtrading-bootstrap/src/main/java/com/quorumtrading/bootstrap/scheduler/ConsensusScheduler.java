package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.usecase.ScheduledConsensusResult;
import com.quorumtrading.application.usecase.TriggerScheduledConsensusUseCase;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled job to automatically trigger consensus decisions for running, non-paused simulations.
 * Runs at a configurable interval (default: every 5 minutes).
 *
 * IMPORTANT: Uses a dedicated blocking executor to prevent blocking the Vert.x event loop.
 * Vert.x has a 60-second limit for blocking operations on worker threads.
 * This scheduler needs up to 5 minutes to complete all simulations.
 *
 * Uses Quartz JDBC clustering to ensure only one instance fires this job cluster-wide.
 */
@ApplicationScoped
public class ConsensusScheduler {

    private static final Logger LOG = Logger.getLogger(ConsensusScheduler.class);

    // Dedicated executor for blocking operations (not using Vert.x worker threads).
    // Instance field (not static) so @PreDestroy can shut it down with the bean lifecycle.
    private final ExecutorService blockingExecutor =
            Executors.newFixedThreadPool(1, r -> {
                Thread t = new Thread(r, "quorumtrading-consensus-scheduler");
                t.setDaemon(false);
                return t;
            });

    /**
     * True while a submitted run is still executing. Because the @Scheduled method returns
     * immediately after handing work to the executor, ConcurrentExecution.SKIP alone never
     * fires — this flag is what actually prevents slow ticks from queueing up back-to-back.
     */
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    @Inject
    TriggerScheduledConsensusUseCase triggerScheduledConsensusUseCase;

    @ConfigProperty(name = "quorumtrading.scheduler.consensus.enabled", defaultValue = "true")
    boolean schedulerEnabled;

    /**
     * Trigger consensus decisions for all running, non-paused simulations.
     * Runs at the configured interval (default: 5 minutes).
     *
     * This method submits the blocking work to a dedicated executor thread pool
     * instead of running on the Vert.x worker thread, preventing "Thread blocked" warnings.
     */
    @Scheduled(every = "${quorumtrading.scheduler.consensus.interval:5m}", concurrentExecution = ConcurrentExecution.SKIP)
    public void triggerConsensusDecisions() {
        if (!schedulerEnabled) {
            LOG.debug("Consensus scheduler is disabled");
            return;
        }

        // Only one run may be in flight at a time. The scheduled method returns immediately after
        // submitting, so Quarkus' SKIP policy cannot detect an ongoing run — this flag can.
        if (!inFlight.compareAndSet(false, true)) {
            LOG.debug("Previous consensus scheduler run still in flight - skipping this tick");
            return;
        }

        // Submit blocking operation to dedicated executor to avoid blocking Vert.x threads.
        // Manually activate CDI request context since raw ExecutorService threads don't inherit it.
        try {
            blockingExecutor.execute(() -> {
                try {
                    runConsensus();
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            // Executor already shut down (application stopping) - release the flag and move on.
            inFlight.set(false);
            LOG.debug("Consensus scheduler executor rejected task (shutting down?)", e);
        }
    }

    private void runConsensus() {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            LOG.info("Starting consensus scheduler execution");

            ScheduledConsensusResult result = triggerScheduledConsensusUseCase.executeForAllActive();

            if (result.simulationsProcessed() > 0 || result.simulationsFailed() > 0) {
                LOG.infof("Consensus scheduler completed - Processed: %d, Skipped: %d, Failed: %d",
                        result.simulationsProcessed(),
                        result.simulationsSkipped(),
                        result.simulationsFailed());
            } else {
                LOG.info("No active simulations to process");
            }

            if (!result.errors().isEmpty()) {
                LOG.infof("Consensus scheduler skipped %d simulation(s): %s",
                        result.errors().size(),
                        String.join("; ", result.errors()));
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to execute consensus scheduler");
        } finally {
            requestContext.terminate();
        }
    }

    @PreDestroy
    void shutdown() {
        blockingExecutor.shutdown();
        try {
            if (!blockingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOG.warn("Consensus scheduler executor did not terminate within 10s - forcing shutdown");
                blockingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            blockingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
