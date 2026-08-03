package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Bollinger Bands indicator value.
 * Volatility indicator showing price channels around a moving average.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * Immutable snapshot of Bollinger Bands calculation at a specific time.
 *
 * <p><b>Components:</b></p>
 * <ul>
 *   <li>Upper Band: Middle band (SMA) + (2 × standard deviation)</li>
 *   <li>Middle Band: Simple Moving Average (typically 20-period SMA)</li>
 *   <li>Lower Band: Middle band (SMA) - (2 × standard deviation)</li>
 * </ul>
 *
 * <p><b>Standard Configuration:</b></p>
 * <ul>
 *   <li>Period: 20 (for SMA calculation)</li>
 *   <li>Standard Deviation Multiplier: 2 (for band width)</li>
 * </ul>
 *
 * <p><b>Common Interpretation:</b></p>
 * <ul>
 *   <li>Price above upper band: Overbought signal (potential breakout or reversal)</li>
 *   <li>Price below lower band: Oversold signal (potential breakout or reversal)</li>
 *   <li>Price inside bands: Normal trading range</li>
 *   <li>Band squeeze (narrow bands): Low volatility, potential breakout pending</li>
 *   <li>Band expansion (wide bands): High volatility, strong trending market</li>
 * </ul>
 *
 * <p><b>Trading Signals:</b></p>
 * <ul>
 *   <li>Bollinger Bounce: Price touches lower band → buy, touches upper band → sell</li>
 *   <li>Bollinger Squeeze: Bands narrow → volatility breakout imminent</li>
 *   <li>Bollinger Breakout: Price breaks above upper or below lower band → trend continuation</li>
 * </ul>
 */
public record BollingerBands(
    BigDecimal upperBand,     // Middle band (SMA) + (2 × standard deviation)
    BigDecimal middleBand,    // Simple Moving Average (typically 20-period)
    BigDecimal lowerBand,     // Middle band (SMA) - (2 × standard deviation)
    Instant calculatedAt
) {

    public BollingerBands {
        Objects.requireNonNull(upperBand, "Upper band cannot be null");
        Objects.requireNonNull(middleBand, "Middle band cannot be null");
        Objects.requireNonNull(lowerBand, "Lower band cannot be null");
        Objects.requireNonNull(calculatedAt, "Calculation timestamp cannot be null");

        // Validate band ordering: lower <= middle <= upper
        if (lowerBand.compareTo(middleBand) > 0) {
            throw new IllegalArgumentException("Lower band must be <= middle band");
        }
        if (middleBand.compareTo(upperBand) > 0) {
            throw new IllegalArgumentException("Middle band must be <= upper band");
        }
    }

    /**
     * Returns true if price is above the upper band.
     * Indicates overbought condition or upward breakout.
     * Potential sell signal in mean reversion strategies.
     * Potential continuation signal in trend-following strategies.
     */
    public boolean isPriceAboveUpperBand(BigDecimal price) {
        Objects.requireNonNull(price, "Price cannot be null");
        return price.compareTo(upperBand) > 0;
    }

    /**
     * Returns true if price is below the lower band.
     * Indicates oversold condition or downward breakout.
     * Potential buy signal in mean reversion strategies.
     * Potential continuation signal in trend-following strategies.
     */
    public boolean isPriceBelowLowerBand(BigDecimal price) {
        Objects.requireNonNull(price, "Price cannot be null");
        return price.compareTo(lowerBand) < 0;
    }

    /**
     * Returns true if price is inside the bands (including boundaries).
     * Indicates normal trading range with no extreme volatility signals.
     * Price exactly on band boundaries is considered inside.
     */
    public boolean isPriceInsideBands(BigDecimal price) {
        Objects.requireNonNull(price, "Price cannot be null");
        return price.compareTo(lowerBand) >= 0 && price.compareTo(upperBand) <= 0;
    }
}
