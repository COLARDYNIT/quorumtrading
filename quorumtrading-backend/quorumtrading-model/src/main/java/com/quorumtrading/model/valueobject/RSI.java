package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Relative Strength Index (RSI) indicator value.
 * RSI measures momentum by comparing magnitude of recent gains vs losses.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * Immutable snapshot of RSI calculation at a specific time.
 *
 * <p><b>Valid Range:</b> 0-100</p>
 * <ul>
 *   <li>RSI < 30: Oversold (potential buy signal)</li>
 *   <li>RSI > 70: Overbought (potential sell signal)</li>
 * </ul>
 */
public record RSI(BigDecimal value, Instant calculatedAt) {

    private static final BigDecimal OVERSOLD_THRESHOLD = new BigDecimal("30");
    private static final BigDecimal OVERBOUGHT_THRESHOLD = new BigDecimal("70");
    private static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALUE = new BigDecimal("100");

    public RSI {
        Objects.requireNonNull(value, "RSI value cannot be null");
        Objects.requireNonNull(calculatedAt, "Calculation timestamp cannot be null");

        if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException(
                "RSI must be between 0 and 100, got: " + value
            );
        }
    }

    /**
     * Returns true if RSI indicates oversold condition (< 30).
     * Potential buy signal in mean reversion strategies.
     */
    public boolean isOversold() {
        return value.compareTo(OVERSOLD_THRESHOLD) < 0;
    }

    /**
     * Returns true if RSI indicates overbought condition (> 70).
     * Potential sell signal in mean reversion strategies.
     */
    public boolean isOverbought() {
        return value.compareTo(OVERBOUGHT_THRESHOLD) > 0;
    }
}
