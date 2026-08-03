package com.quorumtrading.model.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Value object representing price statistics for a ticker in a specific timeframe.
 * Immutable and calculated from a list of price data points.
 */
public record PriceStatistics(
    BigDecimal minPrice,
    BigDecimal maxPrice,
    BigDecimal earliestPrice,
    BigDecimal latestPrice,
    BigDecimal priceChangePercent,
    BigDecimal volatility,
    Trend trend,
    int dataPointCount
) {

    public enum Trend {
        UP, DOWN, FLAT;

        public static Trend from(BigDecimal changePercent) {
            if (changePercent == null) {
                return FLAT;
            }
            if (changePercent.compareTo(BigDecimal.ZERO) > 0) {
                return UP;
            } else if (changePercent.compareTo(BigDecimal.ZERO) < 0) {
                return DOWN;
            }
            return FLAT;
        }
    }

    public enum VolatilityLevel {
        LOW, MEDIUM, HIGH;

        public static VolatilityLevel from(BigDecimal volatility) {
            if (volatility == null) {
                return LOW;
            }
            // Thresholds: > 5% high, > 2% medium, else low
            if (volatility.compareTo(new BigDecimal("5.0")) > 0) {
                return HIGH;
            } else if (volatility.compareTo(new BigDecimal("2.0")) > 0) {
                return MEDIUM;
            }
            return LOW;
        }
    }

    /**
     * Compact constructor with validation.
     */
    public PriceStatistics {
        if (dataPointCount < 0) {
            throw new IllegalArgumentException("dataPointCount cannot be negative");
        }
        // Allow nulls for optional fields when no data available
    }

    /**
     * Get volatility level classification.
     */
    public VolatilityLevel getVolatilityLevel() {
        return VolatilityLevel.from(volatility);
    }

    /**
     * Check if price data is available.
     */
    public boolean hasData() {
        return dataPointCount > 0 && latestPrice != null;
    }

    /**
     * Factory method to create statistics from a list of prices.
     * Prices should be ordered chronologically (oldest first).
     *
     * @param prices List of price values (oldest to newest)
     * @return PriceStatistics calculated from the prices
     */
    public static PriceStatistics fromPrices(List<BigDecimal> prices) {
        if (prices == null || prices.isEmpty()) {
            return emptyStatistics();
        }

        // Remove null prices
        List<BigDecimal> validPrices = prices.stream()
            .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
            .toList();

        if (validPrices.isEmpty()) {
            return emptyStatistics();
        }

        // Calculate min/max
        BigDecimal min = validPrices.stream()
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        BigDecimal max = validPrices.stream()
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        BigDecimal earliest = validPrices.get(0);
        BigDecimal latest = validPrices.get(validPrices.size() - 1);

        // Calculate price change percentage
        BigDecimal priceChangePercent = null;
        if (earliest.compareTo(BigDecimal.ZERO) > 0) {
            priceChangePercent = latest.subtract(earliest)
                .divide(earliest, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
        }

        // Calculate volatility (standard deviation of price changes)
        BigDecimal volatility = calculateVolatility(validPrices);

        Trend trend = Trend.from(priceChangePercent);

        return new PriceStatistics(
            min,
            max,
            earliest,
            latest,
            priceChangePercent,
            volatility,
            trend,
            validPrices.size()
        );
    }

    /**
     * Create empty statistics when no data available.
     */
    public static PriceStatistics emptyStatistics() {
        return new PriceStatistics(
            null, null, null, null, null, null,
            Trend.FLAT,
            0
        );
    }

    /**
     * Calculate volatility as standard deviation of percentage price changes.
     * Returns null if insufficient data (< 2 points).
     */
    private static BigDecimal calculateVolatility(List<BigDecimal> prices) {
        if (prices.size() < 2) {
            return null;
        }

        // Calculate percentage changes
        List<BigDecimal> changes = new java.util.ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal prev = prices.get(i - 1);
            BigDecimal current = prices.get(i);

            if (prev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = current.subtract(prev)
                    .divide(prev, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
                changes.add(change);
            }
        }

        if (changes.isEmpty()) {
            return null;
        }

        // Calculate mean
        BigDecimal sum = changes.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(
            new BigDecimal(changes.size()),
            4,
            RoundingMode.HALF_UP
        );

        // Calculate variance
        BigDecimal variance = changes.stream()
            .map(change -> {
                BigDecimal diff = change.subtract(mean);
                return diff.multiply(diff);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(changes.size()), 4, RoundingMode.HALF_UP);

        // Standard deviation (square root of variance)
        double volatilityDouble = Math.sqrt(variance.doubleValue());
        return BigDecimal.valueOf(volatilityDouble).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        if (!hasData()) {
            return "PriceStatistics[no data]";
        }
        return String.format(
            "PriceStatistics[%s: $%.2f → $%.2f (%.2f%%), volatility: %.2f%% (%s)]",
            trend,
            earliestPrice,
            latestPrice,
            priceChangePercent,
            volatility != null ? volatility : BigDecimal.ZERO,
            getVolatilityLevel()
        );
    }
}
