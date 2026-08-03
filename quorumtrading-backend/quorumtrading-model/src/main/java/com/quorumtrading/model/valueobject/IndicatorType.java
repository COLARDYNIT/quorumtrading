package com.quorumtrading.model.valueobject;

/**
 * Enumeration of supported technical indicators.
 * Defines which indicator calculations are available for strategy analysis.
 *
 * <p><b>DDD Classification:</b> Value Object (Enum Type)</p>
 * Immutable classification of technical indicators used in trading strategies.
 * Each indicator type has a human-readable display name for UI/reporting.
 *
 * <p><b>Usage in Strategy Templates:</b></p>
 * Strategy templates declare required indicators using this enum.
 * The IndicatorCalculationService uses this to determine which calculations to perform.
 *
 * @see com.quorumtrading.model.valueobject.RSI
 * @see com.quorumtrading.model.valueobject.MovingAverage
 * @see com.quorumtrading.model.valueobject.MACD
 * @see com.quorumtrading.model.valueobject.BollingerBands
 */
public enum IndicatorType {
    /**
     * Relative Strength Index - momentum oscillator (0-100).
     * Measures magnitude of recent price changes to evaluate overbought/oversold conditions.
     * Typical thresholds: RSI &lt; 30 (oversold), RSI &gt; 70 (overbought).
     */
    RSI("Relative Strength Index"),

    /**
     * Moving Average - average price over N periods.
     * Smooths price data to identify trend direction.
     * Common periods: 20 (short-term), 50 (medium-term), 200 (long-term).
     */
    MOVING_AVERAGE("Moving Average"),

    /**
     * Moving Average Convergence Divergence - trend-following momentum indicator.
     * Shows relationship between two exponential moving averages (EMA).
     * Signals: Histogram &gt; 0 (bullish), Histogram &lt; 0 (bearish).
     */
    MACD("Moving Average Convergence Divergence"),

    /**
     * Bollinger Bands - volatility bands around moving average.
     * Upper/lower bands calculated using standard deviation.
     * Signals: Price below lower (oversold), price above upper (overbought).
     */
    BOLLINGER_BANDS("Bollinger Bands");

    private final String displayName;

    /**
     * Constructs an IndicatorType with a display name.
     *
     * @param displayName Human-readable name for UI/reporting
     */
    IndicatorType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable display name for this indicator type.
     * Used in UI, reports, and logs to provide clear indicator identification.
     *
     * @return Display name (e.g., "Relative Strength Index", "Moving Average")
     */
    public String getDisplayName() {
        return displayName;
    }
}
