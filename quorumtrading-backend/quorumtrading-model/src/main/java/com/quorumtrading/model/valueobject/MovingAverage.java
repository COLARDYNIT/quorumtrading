package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Simple Moving Average (SMA) indicator value.
 * Average of closing prices over a specific period.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * Immutable snapshot of SMA calculation.
 *
 * <p><b>Common Periods:</b></p>
 * <ul>
 *   <li>SMA(20): Short-term trend</li>
 *   <li>SMA(50): Medium-term trend</li>
 *   <li>SMA(200): Long-term trend</li>
 * </ul>
 */
public record MovingAverage(BigDecimal value, int period, Instant calculatedAt) {

    public MovingAverage {
        Objects.requireNonNull(value, "MA value cannot be null");
        Objects.requireNonNull(calculatedAt, "Calculation timestamp cannot be null");

        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive, got: " + period);
        }
    }

    /**
     * Returns true if this MA value is above another MA value.
     * Used to detect crossovers (e.g., fast MA crosses above slow MA = buy signal).
     */
    public boolean isAbove(MovingAverage other) {
        Objects.requireNonNull(other, "Comparison MA cannot be null");
        return this.value.compareTo(other.value) > 0;
    }
}
