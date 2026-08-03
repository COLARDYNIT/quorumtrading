package com.quorumtrading.adapter.redis;

import com.quorumtrading.application.port.out.OAuthStateStorePort;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link OAuthStateStorePort}.
 *
 * <p>The OAuth2 authorization-code {@code state} value is stored as a single Redis string key with
 * an expiry, and consumed atomically with {@code GETDEL} ({@link ValueCommands#getdel}) so a
 * concurrent (or replayed) callback can never observe the same state twice:</p>
 * <ul>
 *   <li><strong>{@link #put}</strong> — {@code SET key value EX ttl} ({@link ValueCommands#set}
 *       with {@link SetArgs#ex(Duration)}); the entry self-expires when the TTL elapses.</li>
 *   <li><strong>{@link #takeOnce}</strong> — atomic {@code GETDEL}; returns the value exactly once,
 *       then the key is gone, so a second take (or one after TTL) yields empty.</li>
 * </ul>
 *
 * <p>Key format: {@code quorumtrading:oauth:state:{state}}. The stored value is opaque (an
 * already-serialized onboarding context owned by the calling service); this adapter never
 * interprets it.</p>
 *
 * <p>Unlike the fail-closed rate limiter, failures here propagate: a lost OAuth state must surface
 * as an onboarding error (the user can simply restart onboarding) rather than be silently
 * swallowed, which would risk consuming a state without honoring single-use semantics.</p>
 */
@ApplicationScoped
public class RedisOAuthStateStore implements OAuthStateStorePort {

    private static final Logger LOG = Logger.getLogger(RedisOAuthStateStore.class);
    private static final String KEY_PREFIX = "quorumtrading:oauth:state:";

    @Inject
    RedisDataSource redisDataSource;

    @Override
    public void put(String state, String value, Duration ttl) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state cannot be null or blank");
        }
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive, was: " + ttl);
        }

        String key = buildKey(state);
        ValueCommands<String, String> commands = redisDataSource.value(String.class);
        commands.set(key, value, new SetArgs().ex(ttl));

        LOG.debugf("Stored OAuth state: key=%s, ttl=%s", key, ttl);
    }

    @Override
    public Optional<String> takeOnce(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state cannot be null or blank");
        }

        String key = buildKey(state);
        ValueCommands<String, String> commands = redisDataSource.value(String.class);

        // GETDEL is atomic: fetch-and-remove in a single round-trip, so concurrent callbacks
        // cannot both observe the value (single-use invariant).
        String value = commands.getdel(key);

        if (value == null) {
            LOG.debugf("OAuth state absent or already consumed: key=%s", key);
            return Optional.empty();
        }

        LOG.debugf("Consumed OAuth state: key=%s", key);
        return Optional.of(value);
    }

    private String buildKey(String state) {
        return KEY_PREFIX + state;
    }
}
