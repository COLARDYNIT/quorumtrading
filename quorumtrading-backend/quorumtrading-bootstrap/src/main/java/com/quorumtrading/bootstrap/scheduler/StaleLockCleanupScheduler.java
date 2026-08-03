package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.SimulationLockPort;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Periodically cleans up stale simulation locks left behind by crashed instances.
 * Runs every 5 minutes and force-unlocks any lock older than 15 minutes.
 */
@ApplicationScoped
public class StaleLockCleanupScheduler {

    private static final Logger LOG = Logger.getLogger(StaleLockCleanupScheduler.class);
    private static final Duration STALE_DURATION = Duration.ofMinutes(15);

    @Inject
    SimulationLockPort simulationLockPort;

    @Scheduled(every = "5m", concurrentExecution = ConcurrentExecution.SKIP)
    public void cleanupStaleLocks() {
        try {
            int cleaned = simulationLockPort.forceUnlockStale(STALE_DURATION);
            if (cleaned > 0) {
                LOG.infof("Cleaned up %d stale simulation locks", cleaned);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to clean up stale simulation locks");
        }
    }
}
