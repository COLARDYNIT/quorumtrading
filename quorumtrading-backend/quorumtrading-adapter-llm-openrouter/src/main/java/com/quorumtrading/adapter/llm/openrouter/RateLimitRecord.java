package com.quorumtrading.adapter.llm.openrouter;

import java.time.Instant;

/**
 * Immutable record tracking OpenRouter model rate limit state.
 * <p>
 * Records the number of consecutive rate limit hits (HTTP 429 responses),
 * last hit timestamp, and the calculated exponential backoff period for a specific model.
 * </p>
 *
 * @param modelName              Name of the model (e.g., "qwen/qwen-2.5-coder-32b-instruct:free")
 * @param hitCount               Number of consecutive rate limit hits
 * @param lastHitTime            Timestamp of most recent rate limit hit
 * @param currentBackoffMinutes  Current backoff period in minutes (exponential)
 * @author QuorumTrading
 * @since 1.0.0
 */
public record RateLimitRecord(
    String modelName,
    int hitCount,
    Instant lastHitTime,
    long currentBackoffMinutes
) {
    /**
     * Compact constructor with validation.
     */
    public RateLimitRecord {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName cannot be null or blank");
        }
        if (hitCount < 1) {
            throw new IllegalArgumentException("hitCount must be at least 1, got: " + hitCount);
        }
        if (lastHitTime == null) {
            throw new IllegalArgumentException("lastHitTime cannot be null");
        }
        if (currentBackoffMinutes < 0) {
            throw new IllegalArgumentException("currentBackoffMinutes must be non-negative, got: " + currentBackoffMinutes);
        }
    }

    /**
     * Checks if this rate limit record is still within its blacklist period.
     *
     * @param now Current timestamp
     * @return true if the model should still be considered rate-limited
     */
    public boolean isRateLimited(Instant now) {
        Instant expirationTime = lastHitTime.plusSeconds(currentBackoffMinutes * 60);
        return now.isBefore(expirationTime);
    }

    /**
     * Returns minutes remaining until rate limit expires.
     *
     * @param now Current timestamp
     * @return Minutes until expiration, or 0 if already expired
     */
    public long minutesUntilExpiration(Instant now) {
        if (!isRateLimited(now)) {
            return 0;
        }
        long secondsRemaining = lastHitTime.plusSeconds(currentBackoffMinutes * 60)
            .getEpochSecond() - now.getEpochSecond();
        return (secondsRemaining + 59) / 60; // Round up
    }
}
