package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Value object encapsulating strategy performance metrics.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * Immutable, compared by value, represents performance data.
 */
public record StrategyMetrics(
    BigDecimal sharpeRatio,
    BigDecimal totalReturnPercent,
    BigDecimal maxDrawdownPercent,
    BigDecimal winRate,
    int totalSimulations,
    int consecutiveFailures,
    Instant lastUpdated
) {
    public StrategyMetrics {
        Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
        if (totalSimulations < 0) {
            throw new IllegalArgumentException("totalSimulations cannot be negative");
        }
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("consecutiveFailures cannot be negative");
        }
    }

    /**
     * Create initial metrics for a strategy with no history.
     */
    public static StrategyMetrics initial() {
        return new StrategyMetrics(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            0,
            Instant.now()
        );
    }

    /**
     * Check if metrics meet minimum thresholds.
     *
     * @param minSharpe Minimum Sharpe ratio
     * @param maxDrawdown Maximum drawdown percent (positive number)
     * @param minWinRate Minimum win rate percent
     * @return true if all thresholds are met
     */
    public boolean meetsThresholds(BigDecimal minSharpe, BigDecimal maxDrawdown, BigDecimal minWinRate) {
        boolean sharpeOk = sharpeRatio == null || sharpeRatio.compareTo(minSharpe) >= 0;
        boolean drawdownOk = maxDrawdownPercent == null || maxDrawdownPercent.compareTo(maxDrawdown) <= 0;
        boolean winRateOk = winRate == null || winRate.compareTo(minWinRate) >= 0;
        return sharpeOk && drawdownOk && winRateOk;
    }

    /**
     * Create updated metrics with new simulation result.
     */
    public StrategyMetrics withNewResult(BigDecimal newReturn, boolean passed) {
        int newTotal = totalSimulations + 1;
        int newFailures = passed ? 0 : consecutiveFailures + 1;

        // Simple rolling average for return
        BigDecimal updatedReturn = totalSimulations == 0
            ? newReturn
            : totalReturnPercent.add(newReturn).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

        return new StrategyMetrics(
            sharpeRatio, // Updated separately with full calculation
            updatedReturn,
            maxDrawdownPercent,
            winRate,
            newTotal,
            newFailures,
            Instant.now()
        );
    }
}
