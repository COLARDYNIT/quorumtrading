package com.quorumtrading.model.strategy;

/**
 * Enumeration of pre-coded strategy templates.
 * Each template represents a proven trading pattern with structured entry/exit rules.
 *
 * <p><b>DDD Classification:</b> Value Object (Enum)</p>
 */
public enum StrategyTemplateType {
    MOVING_AVERAGE_CROSSOVER(
        "MA Crossover",
        "Trend following via moving average crosses"
    ),
    RSI_REVERSAL(
        "RSI Reversal",
        "Mean reversion on oversold/overbought conditions"
    ),
    BOLLINGER_BREAKOUT(
        "Bollinger Breakout",
        "Volatility-based breakout trading"
    ),
    MACD_SIGNAL_CROSS(
        "MACD Signal Cross",
        "Momentum trading via MACD signal line crosses"
    );

    private final String displayName;
    private final String description;

    StrategyTemplateType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
