package com.quorumtrading.application.service;

import com.quorumtrading.model.marketdata.RateLimit;

/**
 * Tracks API call timestamps per provider to enforce rate limiting.
 *
 * Uses a sliding window approach: keeps track of all API call timestamps
 * and checks if adding a new call would exceed the rate limit.
 *
 * <p><b>Implementations:</b></p>
 * <ul>
 *   <li>{@link InMemoryRateLimitTracker} - In-memory (single instance, default fallback)</li>
 *   <li>RedisRateLimitTracker - Redis-backed (cluster-safe, used when Redis is available)</li>
 * </ul>
 */
public interface RateLimitTracker {

    /**
     * Checks if calling this provider now would exceed its rate limit.
     *
     * @param providerName Name of the provider
     * @param rateLimit The provider's rate limit configuration
     * @return true if calling would exceed the limit, false otherwise
     */
    boolean wouldExceedLimit(String providerName, RateLimit rateLimit);

    /**
     * Records a successful API call for rate limiting tracking.
     *
     * @param providerName Name of the provider
     */
    void recordCall(String providerName);

    /**
     * Gets the number of calls made within the current rate limit window.
     *
     * @param providerName Name of the provider
     * @param rateLimit The provider's rate limit configuration
     * @return Number of calls in the current window
     */
    int getCallsInWindow(String providerName, RateLimit rateLimit);

    /**
     * Resets the call history for a provider.
     *
     * @param providerName Name of the provider
     */
    void reset(String providerName);

    /**
     * Resets all call histories.
     */
    void resetAll();
}
