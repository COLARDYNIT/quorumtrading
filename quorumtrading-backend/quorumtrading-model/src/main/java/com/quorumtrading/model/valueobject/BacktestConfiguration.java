package com.quorumtrading.model.valueobject;

import java.time.Duration;
import java.time.Instant;

/**
 * Value object representing backtest configuration for MarketResearch.
 * Supports two modes:
 * 1. Length only: Backtest from (now - length) to now
 * 2. Length + period: Backtest specific historical period
 */
public record BacktestConfiguration(
    Duration backtestLength,
    Instant periodStart,
    Instant periodEnd
) {

    public BacktestConfiguration {
        if (backtestLength == null) {
            throw new IllegalArgumentException("Backtest length cannot be null");
        }
        if (backtestLength.isNegative() || backtestLength.isZero()) {
            throw new IllegalArgumentException("Backtest length must be positive, got: " + backtestLength);
        }

        // Both period start/end must be provided or both null
        if ((periodStart == null) != (periodEnd == null)) {
            throw new IllegalArgumentException("Both period start and end must be provided or both null");
        }

        // If period provided, validate end > start
        if (periodStart != null && periodEnd != null && !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("Period end must be after period start");
        }
    }
}
