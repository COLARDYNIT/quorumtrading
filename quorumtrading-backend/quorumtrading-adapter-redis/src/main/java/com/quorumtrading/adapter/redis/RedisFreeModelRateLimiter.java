package com.quorumtrading.adapter.redis;

import com.quorumtrading.application.port.out.FreeModelRateLimitPort;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Redis-backed implementation of free model rate limiting.
 * Uses a daily counter per user with automatic expiry.
 *
 * <p>Key format: {@code quorumtrading:freelimit:{userId}:{date}}</p>
 * <p>TTL: 25 hours (ensures cleanup even with timezone drift)</p>
 * <p>Fail-closed: if Redis is unavailable, calls are denied to prevent unlimited free-tier usage.</p>
 */
@ApplicationScoped
public class RedisFreeModelRateLimiter implements FreeModelRateLimitPort {

    private static final Logger LOG = Logger.getLogger(RedisFreeModelRateLimiter.class);
    private static final String KEY_PREFIX = "quorumtrading:freelimit:";
    private static final long TTL_SECONDS = 25 * 3600; // 25 hours

    @Inject
    RedisDataSource redisDataSource;

    @Override
    public boolean isAllowed(UserId userId, int dailyLimit) {
        try {
            String key = buildKey(userId);
            ValueCommands<String, String> commands = redisDataSource.value(String.class);
            String value = commands.get(key);

            int currentCount = value != null ? Integer.parseInt(value) : 0;
            boolean allowed = currentCount < dailyLimit;

            if (!allowed) {
                LOG.debugf("Free model rate limit reached for user %s: %d/%d",
                    userId, currentCount, dailyLimit);
            }

            return allowed;
        } catch (Exception e) {
            LOG.errorf(e, "Redis free model rate limit check failed for user %s, denying call (fail-closed)", userId);
            return false;
        }
    }

    @Override
    public void recordUsage(UserId userId) {
        try {
            String key = buildKey(userId);
            ValueCommands<String, String> commands = redisDataSource.value(String.class);

            long newCount = commands.incr(key);

            // Set TTL on first call of the day
            if (newCount == 1) {
                redisDataSource.key().expire(key, TTL_SECONDS);
            }

            LOG.debugf("Recorded free model usage for user %s, daily count: %d", userId, newCount);
        } catch (Exception e) {
            LOG.warnf(e, "Redis free model usage recording failed for user %s", userId);
        }
    }

    private String buildKey(UserId userId) {
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        return KEY_PREFIX + userId.value() + ":" + date;
    }
}
