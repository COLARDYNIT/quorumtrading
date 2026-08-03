package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.UserId;

/**
 * Port for tracking free model usage rate limits.
 * Implementations should be cluster-safe (e.g., Redis-backed).
 *
 * <p>Implementations must fail-closed: if the backing store is unavailable,
 * {@link #isAllowed} must return {@code false} to prevent unlimited free-tier usage.</p>
 */
public interface FreeModelRateLimitPort {

    /**
     * Check if the user is allowed to make another free model call today.
     *
     * @param userId The user to check
     * @param dailyLimit The daily call limit
     * @return true if the user has remaining calls, false if limit reached
     */
    boolean isAllowed(UserId userId, int dailyLimit);

    /**
     * Record that the user made a free model call.
     *
     * @param userId The user who made the call
     */
    void recordUsage(UserId userId);
}
