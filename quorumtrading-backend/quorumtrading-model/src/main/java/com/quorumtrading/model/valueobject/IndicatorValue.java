package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;

/**
 * Type-safe wrapper for calculated indicator values.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * <p>Sealed interface ensures exhaustive pattern matching.</p>
 *
 * <p><b>Note:</b> No JPA/JSON annotations - this is a pure domain object.</p>
 */
public sealed interface IndicatorValue {

    /**
     * Moving Average values (SMA and EMA).
     * @param sma50 50-day Simple Moving Average (null if insufficient data)
     * @param sma200 200-day Simple Moving Average (null if insufficient data)
     * @param ema50 50-day Exponential Moving Average (null if insufficient data)
     * @param ema200 200-day Exponential Moving Average (null if insufficient data)
     */
    record MovingAverageValue(BigDecimal sma50, BigDecimal sma200, BigDecimal ema50, BigDecimal ema200) implements IndicatorValue {

        /**
         * Convenience constructor for SMA-only (backwards compatibility).
         */
        public MovingAverageValue(BigDecimal sma50, BigDecimal sma200) {
            this(sma50, sma200, null, null);
        }
    }

    /**
     * Relative Strength Index value.
     * @param rsi14 14-period RSI (0-100 scale)
     */
    record RSIValue(BigDecimal rsi14) implements IndicatorValue {}

    /**
     * MACD indicator values.
     * @param macdLine MACD line (fast EMA - slow EMA)
     * @param signalLine Signal line (EMA of MACD line)
     * @param histogram Difference between MACD and signal
     */
    record MACDValue(BigDecimal macdLine, BigDecimal signalLine, BigDecimal histogram) implements IndicatorValue {}

    /**
     * Bollinger Bands values.
     * @param upper Upper band (middle + 2*stddev)
     * @param middle Middle band (20-day SMA)
     * @param lower Lower band (middle - 2*stddev)
     */
    record BollingerValue(BigDecimal upper, BigDecimal middle, BigDecimal lower) implements IndicatorValue {}
}
