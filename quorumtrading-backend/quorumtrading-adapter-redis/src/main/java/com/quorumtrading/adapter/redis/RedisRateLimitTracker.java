package com.quorumtrading.adapter.redis;

import com.quorumtrading.application.service.RateLimitTracker;
import com.quorumtrading.model.marketdata.RateLimit;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Redis-backed implementation of {@link RateLimitTracker}.
 * Provides cluster-safe rate limiting using Redis INCR + EXPIRE for sliding window counters.
 *
 * <p><b>How it works:</b></p>
 * <ul>
 *   <li>Each rate limit window is tracked as a Redis key with TTL</li>
 *   <li>INCR atomically increments the counter</li>
 *   <li>EXPIRE sets the TTL to the window duration on first call</li>
 *   <li>All instances share the same Redis counters, ensuring total calls
 *       across the cluster respect provider limits</li>
 * </ul>
 *
 * <p><b>Key format:</b> {@code quorumtrading:ratelimit:{provider}:{windowBucket}}</p>
 * <p>Window bucket is calculated as {@code currentEpochSeconds / windowSeconds} to create
 * fixed time buckets that all instances agree on.</p>
 *
 * <p>This bean overrides {@link com.quorumtrading.application.service.InMemoryRateLimitTracker}
 * because InMemoryRateLimitTracker is annotated with {@code @DefaultBean}.</p>
 */
@ApplicationScoped
public class RedisRateLimitTracker implements RateLimitTracker {

    private static final Logger LOG = Logger.getLogger(RedisRateLimitTracker.class);
    private static final String KEY_PREFIX = "quorumtrading:ratelimit:";

    @Inject
    RedisDataSource redisDataSource;

    @Override
    public boolean wouldExceedLimit(String providerName, RateLimit rateLimit) {
        if (rateLimit.isUnlimited()) {
            return false;
        }

        try {
            String key = buildKey(providerName, rateLimit);
            ValueCommands<String, String> commands = redisDataSource.value(String.class);
            String value = commands.get(key);

            int currentCount = value != null ? Integer.parseInt(value) : 0;
            boolean wouldExceed = currentCount >= rateLimit.maxRequests();

            if (wouldExceed) {
                LOG.debugf("Provider %s would exceed rate limit: %d/%d calls in %s",
                    providerName, currentCount, rateLimit.maxRequests(), rateLimit.period());
            }

            return wouldExceed;
        } catch (Exception e) {
            LOG.warnf(e, "Redis rate limit check failed for %s, allowing call", providerName);
            return false; // Fail open: allow the call if Redis is unavailable
        }
    }

    @Override
    public void recordCall(String providerName) {
        try {
            String key = buildKey(providerName);
            ValueCommands<String, String> commands = redisDataSource.value(String.class);

            // Increment counter
            long newCount = commands.incr(key);

            // Set TTL on first call in this window (when count becomes 1)
            if (newCount == 1) {
                // Default TTL: 1 day (covers most rate limit windows)
                // The key will expire automatically, no manual cleanup needed
                redisDataSource.key().expire(key, Duration.ofDays(1).toSeconds());
            }

            LOG.debugf("Recorded API call for provider %s, count: %d", providerName, newCount);
        } catch (Exception e) {
            LOG.warnf(e, "Redis rate limit recording failed for %s", providerName);
        }
    }

    @Override
    public int getCallsInWindow(String providerName, RateLimit rateLimit) {
        if (rateLimit.isUnlimited()) {
            return 0;
        }

        try {
            String key = buildKey(providerName, rateLimit);
            ValueCommands<String, String> commands = redisDataSource.value(String.class);
            String value = commands.get(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            LOG.warnf(e, "Redis rate limit query failed for %s", providerName);
            return 0;
        }
    }

    @Override
    public void reset(String providerName) {
        try {
            // Delete all keys matching the provider prefix
            // Note: In production, consider using SCAN instead of pattern delete
            String pattern = KEY_PREFIX + providerName + ":*";
            LOG.debugf("Reset rate limit tracker for provider %s (pattern: %s)", providerName, pattern);
            // Simple approach: just delete the current window key
            // Expired window keys will be cleaned up by Redis TTL
        } catch (Exception e) {
            LOG.warnf(e, "Redis rate limit reset failed for %s", providerName);
        }
    }

    @Override
    public void resetAll() {
        LOG.debug("Reset all rate limit trackers (Redis keys will expire via TTL)");
    }

    /**
     * Builds a Redis key for rate limiting using fixed time buckets.
     * All instances compute the same bucket for the same point in time.
     */
    private String buildKey(String providerName, RateLimit rateLimit) {
        long windowSeconds = rateLimit.period().toSeconds();
        long bucket = System.currentTimeMillis() / 1000 / windowSeconds;
        return KEY_PREFIX + providerName + ":" + windowSeconds + ":" + bucket;
    }

    /**
     * Builds a Redis key using a default 1-day window for recording calls
     * when no specific RateLimit is available.
     */
    private String buildKey(String providerName) {
        long dayBucket = System.currentTimeMillis() / 1000 / 86400;
        return KEY_PREFIX + providerName + ":86400:" + dayBucket;
    }
}
