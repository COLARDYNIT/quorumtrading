package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Moving Average Convergence Divergence (MACD) indicator value.
 * Momentum indicator showing relationship between two EMAs.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * Immutable snapshot of MACD calculation at a specific time.
 *
 * <p><b>Components:</b></p>
 * <ul>
 *   <li>MACD Line: 12-period EMA - 26-period EMA</li>
 *   <li>Signal Line: 9-period EMA of MACD Line</li>
 *   <li>Histogram: MACD Line - Signal Line</li>
 * </ul>
 *
 * <p><b>Common Interpretation:</b></p>
 * <ul>
 *   <li>Bullish Crossover: MACD crosses above signal (histogram > 0)</li>
 *   <li>Bearish Crossover: MACD crosses below signal (histogram < 0)</li>
 *   <li>Divergence: Price trend differs from MACD trend (trend reversal signal)</li>
 * </ul>
 */
public record MACD(
    BigDecimal macdLine,      // MACD line (12-period EMA - 26-period EMA)
    BigDecimal signalLine,    // Signal line (9-period EMA of MACD line)
    BigDecimal histogram,     // Histogram (MACD line - signal line)
    Instant calculatedAt
) {

    public MACD {
        Objects.requireNonNull(macdLine, "MACD line cannot be null");
        Objects.requireNonNull(signalLine, "Signal line cannot be null");
        Objects.requireNonNull(histogram, "Histogram cannot be null");
        Objects.requireNonNull(calculatedAt, "Calculation timestamp cannot be null");
    }

    /**
     * Returns true if MACD shows bullish crossover.
     * Indicates MACD line crossed above signal line (histogram > 0).
     * Potential buy signal in momentum strategies.
     */
    public boolean isBullishCrossover() {
        return macdLine.compareTo(signalLine) > 0 && histogram.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Returns true if MACD shows bearish crossover.
     * Indicates MACD line crossed below signal line (histogram < 0).
     * Potential sell signal in momentum strategies.
     */
    public boolean isBearishCrossover() {
        return macdLine.compareTo(signalLine) < 0 && histogram.compareTo(BigDecimal.ZERO) < 0;
    }
}
