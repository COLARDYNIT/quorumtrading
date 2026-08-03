package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing a single data point in a chart.
 * <p>
 * Used for decimated/downsampled portfolio value data optimized for chart rendering.
 * Each point represents the portfolio state at a specific timestamp.
 * </p>
 * <p><b>DDD Classification:</b> Value Object</p>
 * Immutable and defined by its values rather than identity.
 */
public record ChartDataPoint(
    Instant timestamp,
    BigDecimal portfolioValue,
    BigDecimal cashAvailable
) {
    /**
     * Compact constructor with validation.
     */
    public ChartDataPoint {
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        Objects.requireNonNull(portfolioValue, "Portfolio value cannot be null");
        Objects.requireNonNull(cashAvailable, "Cash available cannot be null");

        if (portfolioValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Portfolio value cannot be negative");
        }
        if (cashAvailable.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cash available cannot be negative");
        }
    }

    /**
     * Factory method to create from Money value objects.
     */
    public static ChartDataPoint from(Instant timestamp, Money totalValue, Money cash) {
        return new ChartDataPoint(
            timestamp,
            totalValue.amount(),
            cash.amount()
        );
    }
}
