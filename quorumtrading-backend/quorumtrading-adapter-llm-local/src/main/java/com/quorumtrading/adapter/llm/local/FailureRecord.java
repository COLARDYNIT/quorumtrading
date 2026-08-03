package com.quorumtrading.adapter.llm.local;

import java.time.Instant;

/**
 * Immutable record tracking CLI tool failure state.
 * <p>
 * Records the number of consecutive failures, last failure timestamp,
 * and the calculated exponential backoff period for a specific tool.
 * </p>
 *
 * @param toolName              Name of the CLI tool (e.g., "qwen", "claude")
 * @param failureCount          Number of consecutive failures
 * @param lastFailureTime       Timestamp of most recent failure
 * @param currentBackoffMinutes Current backoff period in minutes (exponential)
 * @author QuorumTrading
 * @since 1.0.0
 */
public record FailureRecord(
    String toolName,
    int failureCount,
    Instant lastFailureTime,
    long currentBackoffMinutes
) {
    /**
     * Compact constructor with validation.
     * <p>
     * Validates that:
     * <ul>
     *   <li>toolName is not null or blank</li>
     *   <li>failureCount is at least 1</li>
     *   <li>lastFailureTime is not null</li>
     *   <li>currentBackoffMinutes is non-negative</li>
     * </ul>
     * </p>
     *
     * @throws IllegalArgumentException if any validation fails
     */
    public FailureRecord {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName cannot be null or blank");
        }
        if (failureCount < 1) {
            throw new IllegalArgumentException("failureCount must be at least 1, got: " + failureCount);
        }
        if (lastFailureTime == null) {
            throw new IllegalArgumentException("lastFailureTime cannot be null");
        }
        if (currentBackoffMinutes < 0) {
            throw new IllegalArgumentException("currentBackoffMinutes must be non-negative, got: " + currentBackoffMinutes);
        }
    }

    /**
     * Checks if this failure record is still within its blacklist period.
     *
     * @param now Current timestamp
     * @return true if the tool should still be blacklisted
     */
    public boolean isBlacklisted(Instant now) {
        Instant expirationTime = lastFailureTime.plusSeconds(currentBackoffMinutes * 60);
        return now.isBefore(expirationTime);
    }

    /**
     * Returns minutes remaining until blacklist expires.
     *
     * @param now Current timestamp
     * @return Minutes until expiration, or 0 if already expired
     */
    public long minutesUntilExpiration(Instant now) {
        if (!isBlacklisted(now)) {
            return 0;
        }
        long secondsRemaining = lastFailureTime.plusSeconds(currentBackoffMinutes * 60)
            .getEpochSecond() - now.getEpochSecond();
        return (secondsRemaining + 59) / 60; // Round up
    }
}
