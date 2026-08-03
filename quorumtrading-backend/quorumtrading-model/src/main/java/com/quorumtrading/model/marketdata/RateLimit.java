package com.quorumtrading.model.marketdata;

import java.time.Duration;

/**
 * Value object representing API rate limit configuration for a market data provider.
 *
 * Examples:
 * - NewsAPI free tier: 100 requests per day
 * - Coinbase: 10 requests per second (unlimited)
 * - Reddit: 60 requests per minute
 *
 * With scheduler running every 5 minutes (288 times/day), providers with daily limits
 * need careful management to avoid exhausting their quota.
 */
public record RateLimit(
    int maxRequests,
    Duration period
) {
    public RateLimit {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be positive duration");
        }
    }

    /**
     * Creates a rate limit with unlimited requests (no limiting).
     */
    public static RateLimit unlimited() {
        return new RateLimit(Integer.MAX_VALUE, Duration.ofDays(1));
    }

    /**
     * Creates a rate limit for X requests per day.
     */
    public static RateLimit perDay(int maxRequests) {
        return new RateLimit(maxRequests, Duration.ofDays(1));
    }

    /**
     * Creates a rate limit for X requests per hour.
     */
    public static RateLimit perHour(int maxRequests) {
        return new RateLimit(maxRequests, Duration.ofHours(1));
    }

    /**
     * Creates a rate limit for X requests per minute.
     */
    public static RateLimit perMinute(int maxRequests) {
        return new RateLimit(maxRequests, Duration.ofMinutes(1));
    }

    /**
     * Creates a rate limit for X requests per second.
     */
    public static RateLimit perSecond(int maxRequests) {
        return new RateLimit(maxRequests, Duration.ofSeconds(1));
    }

    /**
     * Checks if this is an unlimited rate limit.
     */
    public boolean isUnlimited() {
        return maxRequests == Integer.MAX_VALUE;
    }

    @Override
    public String toString() {
        if (isUnlimited()) {
            return "unlimited";
        }
        return maxRequests + " requests per " + formatPeriod();
    }

    private String formatPeriod() {
        long seconds = period.getSeconds();
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + " hour" + (hours == 1 ? "" : "s");
        } else {
            long days = seconds / 86400;
            return days + " day" + (days == 1 ? "" : "s");
        }
    }
}
