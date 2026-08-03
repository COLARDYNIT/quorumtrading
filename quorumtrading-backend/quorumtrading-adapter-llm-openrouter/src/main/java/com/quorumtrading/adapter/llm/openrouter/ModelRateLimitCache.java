package com.quorumtrading.adapter.llm.openrouter;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for tracking OpenRouter model rate limits with exponential backoff.
 * <p>
 * When a model hits rate limits (HTTP 429), it's temporarily blacklisted with increasing backoff periods:
 * 1st rate limit = 30 minutes, 2nd = 60 minutes, 3rd = 120 minutes, etc.
 * Backoff is capped at a configurable maximum (default 12 hours).
 * </p>
 * <p>
 * This allows the system to automatically fall back to paid models when free tiers are exhausted,
 * then retry the free models after the backoff period expires.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
public class ModelRateLimitCache {

    private static final Logger LOG = Logger.getLogger(ModelRateLimitCache.class);

    private final ConcurrentHashMap<String, RateLimitRecord> rateLimitCache = new ConcurrentHashMap<>();

    private final long initialBackoffMinutes;
    private final long maxBackoffMinutes;

    /**
     * Constructor for CDI injection with configuration.
     *
     * @param initialBackoffMinutes Initial backoff period in minutes
     * @param maxBackoffMinutes     Maximum backoff period in minutes (cap)
     */
    public ModelRateLimitCache(
        @ConfigProperty(name = "quorumtrading.llm.openrouter.cache.initial-backoff-minutes", defaultValue = "30")
        long initialBackoffMinutes,
        @ConfigProperty(name = "quorumtrading.llm.openrouter.cache.max-backoff-minutes", defaultValue = "720")
        long maxBackoffMinutes
    ) {
        this.initialBackoffMinutes = initialBackoffMinutes;
        this.maxBackoffMinutes = maxBackoffMinutes;
        LOG.infof("ModelRateLimitCache initialized: initial=%dm, max=%dm", initialBackoffMinutes, maxBackoffMinutes);
    }

    /**
     * Checks if a model is currently rate-limited (within backoff period).
     *
     * @param modelName Name of the model to check
     * @return true if model is rate-limited and backoff period hasn't expired
     */
    public boolean isModelRateLimited(String modelName) {
        RateLimitRecord record = rateLimitCache.get(modelName);
        if (record == null) {
            return false;
        }

        boolean rateLimited = record.isRateLimited(Instant.now());

        if (rateLimited) {
            LOG.debugf("Model %s rate-limited (expires in %d minutes, hit count: %d)",
                      modelName, record.minutesUntilExpiration(Instant.now()), record.hitCount());
        }

        return rateLimited;
    }

    /**
     * Records a rate limit hit (HTTP 429) for the specified model and updates backoff period.
     * <p>
     * Backoff calculation: min(initialBackoff * 2^(hitCount-1), maxBackoff)
     * </p>
     *
     * @param modelName Name of the model that was rate-limited
     */
    public void recordRateLimit(String modelName) {
        Instant now = Instant.now();

        rateLimitCache.compute(modelName, (key, existing) -> {
            int newHitCount = (existing == null) ? 1 : existing.hitCount() + 1;
            long newBackoff = calculateBackoff(newHitCount);

            LOG.warnf("Recording rate limit for model %s (count: %d, next backoff: %d minutes)",
                     modelName, newHitCount, newBackoff);

            return new RateLimitRecord(modelName, newHitCount, now, newBackoff);
        });
    }

    /**
     * Clears a specific model's rate limit record (manual reset).
     *
     * @param modelName Name of the model to clear
     */
    public void clearModel(String modelName) {
        RateLimitRecord removed = rateLimitCache.remove(modelName);
        if (removed != null) {
            LOG.infof("Cleared rate limit for model %s (was hit %d times)", modelName, removed.hitCount());
        }
    }

    /**
     * Clears all rate limit records from the cache.
     */
    public void clearAll() {
        LOG.info("Clearing all model rate limit records");
        rateLimitCache.clear();
    }

    /**
     * Returns current rate limit status for all models.
     *
     * @return Unmodifiable map of model names to rate limit records
     */
    public Map<String, RateLimitRecord> getRateLimitStatus() {
        return Map.copyOf(rateLimitCache);
    }

    /**
     * Calculates exponential backoff period in minutes.
     * <p>
     * Formula: min(initialBackoff * 2^(hitCount-1), maxBackoff)
     * </p>
     *
     * @param hitCount Number of consecutive rate limit hits
     * @return Backoff period in minutes
     */
    private long calculateBackoff(int hitCount) {
        long backoff = (long) (initialBackoffMinutes * Math.pow(2, hitCount - 1));
        return Math.min(backoff, maxBackoffMinutes);
    }
}
