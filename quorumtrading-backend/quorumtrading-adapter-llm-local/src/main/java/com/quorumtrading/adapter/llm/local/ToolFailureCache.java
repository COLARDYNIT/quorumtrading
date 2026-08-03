package com.quorumtrading.adapter.llm.local;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for tracking CLI tool failures with exponential backoff.
 * <p>
 * When a tool fails, it's temporarily blacklisted with increasing backoff periods:
 * 1st failure = 60 minutes, 2nd = 120 minutes, 3rd = 240 minutes, etc.
 * Backoff is capped at a configurable maximum (default 24 hours).
 * </p>
 * <p>
 * Cache uses lazy expiration - blacklist status is checked on access rather than
 * using scheduled cleanup. This avoids background threads and simplifies lifecycle.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
public class ToolFailureCache {

    private static final Logger LOG = Logger.getLogger(ToolFailureCache.class);

    private final ConcurrentHashMap<String, FailureRecord> failureCache = new ConcurrentHashMap<>();

    private final long initialBackoffMinutes;
    private final long maxBackoffMinutes;

    /**
     * Constructor for CDI injection with configuration.
     *
     * @param initialBackoffMinutes Initial backoff period in minutes
     * @param maxBackoffMinutes     Maximum backoff period in minutes (cap)
     */
    public ToolFailureCache(
        @ConfigProperty(name = "quorumtrading.llm.local.cache.initial-backoff-minutes", defaultValue = "60")
        long initialBackoffMinutes,
        @ConfigProperty(name = "quorumtrading.llm.local.cache.max-backoff-minutes", defaultValue = "1440")
        long maxBackoffMinutes
    ) {
        this.initialBackoffMinutes = initialBackoffMinutes;
        this.maxBackoffMinutes = maxBackoffMinutes;
    }

    /**
     * Checks if a tool is currently blacklisted (within backoff period).
     * <p>
     * Performs lazy expiration check - if the cached record has expired,
     * it's considered not blacklisted without removing it from the cache.
     * </p>
     *
     * @param toolName Name of the tool to check
     * @return true if tool is blacklisted and backoff period hasn't expired
     */
    public boolean isToolBlacklisted(String toolName) {
        FailureRecord record = failureCache.get(toolName);
        if (record == null) {
            return false;
        }

        boolean blacklisted = record.isBlacklisted(Instant.now());

        if (blacklisted) {
            LOG.debugf("Tool %s blacklisted (expires in %d minutes, failure count: %d)",
                      toolName, record.minutesUntilExpiration(Instant.now()), record.failureCount());
        }

        return blacklisted;
    }

    /**
     * Records a failure for the specified tool and updates backoff period.
     * <p>
     * Backoff calculation: min(initialBackoff * 2^(failureCount-1), maxBackoff)
     * </p>
     *
     * @param toolName Name of the tool that failed
     */
    public void recordFailure(String toolName) {
        Instant now = Instant.now();

        failureCache.compute(toolName, (key, existing) -> {
            int newFailureCount = (existing == null) ? 1 : existing.failureCount() + 1;
            long newBackoff = calculateBackoff(newFailureCount);

            LOG.warnf("Recording failure for tool %s (count: %d, next backoff: %d minutes)",
                     toolName, newFailureCount, newBackoff);

            return new FailureRecord(toolName, newFailureCount, now, newBackoff);
        });
    }

    /**
     * Clears all failure records from the cache.
     * <p>
     * Useful for testing and manual administrative reset.
     * </p>
     */
    public void clearCache() {
        LOG.info("Clearing tool failure cache");
        failureCache.clear();
    }

    /**
     * Returns current failure status for all tools.
     * <p>
     * Useful for monitoring and debugging. Does not filter expired records.
     * </p>
     *
     * @return Unmodifiable map of tool names to failure records
     */
    public Map<String, FailureRecord> getFailureStatus() {
        return Map.copyOf(failureCache);
    }

    /**
     * Calculates exponential backoff period in minutes.
     * <p>
     * Formula: min(initialBackoff * 2^(failureCount-1), maxBackoff)
     * </p>
     *
     * @param failureCount Number of consecutive failures
     * @return Backoff period in minutes
     */
    private long calculateBackoff(int failureCount) {
        long backoff = (long) (initialBackoffMinutes * Math.pow(2, failureCount - 1));
        return Math.min(backoff, maxBackoffMinutes);
    }
}
