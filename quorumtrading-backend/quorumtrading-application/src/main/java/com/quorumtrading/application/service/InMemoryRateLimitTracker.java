package com.quorumtrading.application.service;

import com.quorumtrading.model.marketdata.RateLimit;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory implementation of {@link RateLimitTracker}.
 * Uses a sliding window approach with ConcurrentLinkedDeque for thread-safe tracking.
 *
 * <p><b>Limitation:</b> Per-instance tracking only. In a horizontally scaled
 * environment, each instance tracks independently, which may allow total calls
 * to exceed provider limits. Use RedisRateLimitTracker for cluster-safe rate limiting.</p>
 *
 * <p>Annotated with {@link DefaultBean} so it is automatically overridden when a
 * higher-priority implementation (e.g., RedisRateLimitTracker) is available.</p>
 */
@ApplicationScoped
@DefaultBean
public class InMemoryRateLimitTracker implements RateLimitTracker {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRateLimitTracker.class);

    private final Map<String, ConcurrentLinkedDeque<Instant>> callHistory = new ConcurrentHashMap<>();

    @Override
    public boolean wouldExceedLimit(String providerName, RateLimit rateLimit) {
        if (rateLimit.isUnlimited()) {
            return false;
        }

        Instant now = Instant.now();
        ConcurrentLinkedDeque<Instant> history = getOrCreateHistory(providerName);

        Instant windowStart = now.minus(rateLimit.period());
        removeExpiredCalls(history, windowStart);

        int callsInWindow = history.size();
        boolean wouldExceed = callsInWindow >= rateLimit.maxRequests();

        if (wouldExceed) {
            log.debug("Provider {} would exceed rate limit: {}/{} calls in {}",
                providerName, callsInWindow, rateLimit.maxRequests(), rateLimit.period());
        }

        return wouldExceed;
    }

    @Override
    public void recordCall(String providerName) {
        Instant now = Instant.now();
        ConcurrentLinkedDeque<Instant> history = getOrCreateHistory(providerName);
        history.addLast(now);

        log.debug("Recorded API call for provider {}, total calls in history: {}",
            providerName, history.size());
    }

    @Override
    public int getCallsInWindow(String providerName, RateLimit rateLimit) {
        if (rateLimit.isUnlimited()) {
            return 0;
        }

        Instant now = Instant.now();
        ConcurrentLinkedDeque<Instant> history = getOrCreateHistory(providerName);

        Instant windowStart = now.minus(rateLimit.period());
        removeExpiredCalls(history, windowStart);

        return history.size();
    }

    @Override
    public void reset(String providerName) {
        callHistory.remove(providerName);
        log.debug("Reset rate limit tracker for provider {}", providerName);
    }

    @Override
    public void resetAll() {
        callHistory.clear();
        log.debug("Reset all rate limit trackers");
    }

    private ConcurrentLinkedDeque<Instant> getOrCreateHistory(String providerName) {
        return callHistory.computeIfAbsent(providerName, k -> new ConcurrentLinkedDeque<>());
    }

    private void removeExpiredCalls(ConcurrentLinkedDeque<Instant> history, Instant windowStart) {
        while (!history.isEmpty() && history.peekFirst().isBefore(windowStart)) {
            history.pollFirst();
        }
    }
}
