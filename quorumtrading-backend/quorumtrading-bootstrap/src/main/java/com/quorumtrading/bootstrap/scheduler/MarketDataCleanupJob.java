package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.adapter.persistence.repository.MarketEventRepositoryImpl;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job to clean up old cached market events.
 * Runs daily at 3 AM to enforce 90-day retention policy.
 */
@ApplicationScoped
public class MarketDataCleanupJob {

    private static final Logger LOG = Logger.getLogger(MarketDataCleanupJob.class);

    @Inject
    MarketEventRepositoryImpl repository;

    /**
     * Delete cached market events older than 90 days.
     * Runs daily at 3:00 AM server time.
     */
    @Scheduled(cron = "0 0 3 * * ?", concurrentExecution = ConcurrentExecution.SKIP)
    @Transactional
    public void cleanupOldCachedEvents() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);

        LOG.infof("Starting cleanup of cached market events older than %s", cutoff);

        long deleted = repository.deleteOlderThanCachedAt(cutoff);

        LOG.infof("Cleaned up %d cached market events", deleted);
    }
}
