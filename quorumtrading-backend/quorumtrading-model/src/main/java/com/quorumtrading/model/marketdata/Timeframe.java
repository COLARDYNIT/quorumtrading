package com.quorumtrading.model.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Represents standard timeframes for market data analysis.
 * Each timeframe defines a lookback period from a reference point.
 */
public enum Timeframe {
    LAST_24H(Duration.ofHours(24)),
    LAST_7D(Duration.ofDays(7)),
    LAST_30D(Duration.ofDays(30)),
    LAST_90D(Duration.ofDays(90));

    private final Duration duration;

    Timeframe(Duration duration) {
        this.duration = duration;
    }

    /**
     * Get the duration of this timeframe.
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Calculate the start instant for this timeframe from a reference point.
     *
     * @param referencePoint The point in time to look back from
     * @return Start instant for this timeframe
     */
    public Instant calculateStart(Instant referencePoint) {
        return referencePoint.minus(duration);
    }

    /**
     * Create a TimeRange for this timeframe from a reference point.
     *
     * @param referencePoint The point in time to look back from
     * @return TimeRange for this timeframe
     */
    public com.quorumtrading.model.valueobject.TimeRange toTimeRange(Instant referencePoint) {
        return new com.quorumtrading.model.valueobject.TimeRange(
            calculateStart(referencePoint),
            referencePoint
        );
    }

    /**
     * Get human-readable description.
     */
    public String getDescription() {
        return switch (this) {
            case LAST_24H -> "Last 24 hours";
            case LAST_7D -> "Last 7 days";
            case LAST_30D -> "Last 30 days";
            case LAST_90D -> "Last 90 days";
        };
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
