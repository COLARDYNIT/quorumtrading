package com.quorumtrading.model.strategy;

import com.quorumtrading.model.valueobject.IndicatorType;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bollinger Breakout Strategy Template.
 *
 * <p><b>Strategy Type:</b> Volatility Breakout</p>
 * <p><b>Core Concept:</b> Enter when price breaks above upper band (bullish breakout),
 * exit when price returns inside bands or hits stop/profit targets</p>
 *
 * <p><b>Required Indicators:</b> Bollinger Bands (upper, middle, lower)</p>
 *
 * <p><b>Parameters:</b></p>
 * <ul>
 *   <li>period - Bollinger Bands period (default: 20)</li>
 *   <li>stdDevMultiplier - Standard deviation multiplier (default: 2.0)</li>
 *   <li>stopLossPercent - Stop loss percentage (default: 4.0)</li>
 *   <li>takeProfitPercent - Take profit percentage (default: 10.0)</li>
 * </ul>
 *
 * <p><b>DDD Classification:</b> Domain Service</p>
 */
public class BollingerBreakoutTemplate implements StrategyTemplate {

    private static final int DEFAULT_PERIOD = 20;
    private static final BigDecimal DEFAULT_STD_DEV_MULTIPLIER = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_STOP_LOSS = new BigDecimal("4");
    private static final BigDecimal DEFAULT_TAKE_PROFIT = new BigDecimal("10");

    @Override
    public StrategyTemplateType getType() {
        return StrategyTemplateType.BOLLINGER_BREAKOUT;
    }

    @Override
    public Set<IndicatorType> getRequiredIndicators() {
        return Set.of(IndicatorType.BOLLINGER_BANDS);
    }

    @Override
    public String generateStrategyMarkdown(MarketContext context, TemplateParameters parameters) {
        // Extract parameters with defaults
        int period = parameters.getInt("period", DEFAULT_PERIOD);
        BigDecimal stdDevMultiplier = parameters.getDecimal("stdDevMultiplier", DEFAULT_STD_DEV_MULTIPLIER);
        BigDecimal stopLoss = parameters.getDecimal("stopLossPercent", DEFAULT_STOP_LOSS);
        BigDecimal takeProfit = parameters.getDecimal("takeProfitPercent", DEFAULT_TAKE_PROFIT);

        // Build YAML frontmatter
        StringBuilder markdown = new StringBuilder();
        markdown.append("---\n");
        markdown.append("version: v1.0\n");
        markdown.append("consensus_method: MAJORITY_VOTE\n");
        markdown.append("consensus_threshold: 0.66\n");
        markdown.append("\n");

        // Detect trading mode based on presence of stock_tickers
        boolean isStockMode = !context.stockTickers().isEmpty();

        // Stock tickers
        if (context.stockTickers().isEmpty()) {
            markdown.append("stock_tickers: []\n");
        } else {
            markdown.append("stock_tickers:\n");
            context.stockTickers().stream()
                .sorted((a, b) -> a.ticker().compareTo(b.ticker()))
                .forEach(ticker -> markdown.append("  - ").append(ticker.ticker()).append("\n"));
        }

        // Crypto tickers
        if (context.cryptoTickers().isEmpty()) {
            markdown.append("crypto_tickers: []\n");
        } else {
            markdown.append("crypto_tickers:\n");
            context.cryptoTickers().stream()
                .sorted((a, b) -> a.ticker().compareTo(b.ticker()))
                .forEach(ticker -> markdown.append("  - ").append(ticker.ticker()).append("\n"));
        }

        markdown.append("\n");

        // Add live_schedule + backtest_schedule
        if (isStockMode) {
            markdown.append("live_schedule:\n");
            markdown.append("  timezone: \"America/New_York\"\n");
            markdown.append("  trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]\n");
            markdown.append("  evaluation_times: [\"10:00\", \"14:00\"]\n");
            markdown.append("  market_open: \"09:30\"\n");
            markdown.append("  market_close: \"16:00\"\n");
            markdown.append("backtest_schedule:\n");
            markdown.append("  timezone: \"America/New_York\"\n");
            markdown.append("  trading_days: [WEDNESDAY]\n");
            markdown.append("  evaluation_times: [\"10:00\"]\n");
            markdown.append("  market_open: \"09:30\"\n");
            markdown.append("  market_close: \"16:00\"\n");
        } else {
            markdown.append("live_schedule:\n");
            markdown.append("  interval: 1h\n");
            markdown.append("backtest_schedule:\n");
            markdown.append("  interval: 1w\n");
        }

        markdown.append("\n");
        markdown.append("position_limits:\n");
        context.stockTickers().forEach(ticker ->
            markdown.append("  ").append(ticker.ticker()).append(": 0.3\n"));
        context.cryptoTickers().forEach(ticker ->
            markdown.append("  ").append(ticker.ticker()).append(": 0.3\n"));
        markdown.append("\n");
        markdown.append("reserve_cash_percentage: 0.2\n");

        markdown.append("---\n\n");

        // Strategy content
        markdown.append("# Bollinger Breakout Strategy\n\n");

        markdown.append("## Introduction\n\n");
        markdown.append("Volatility-based breakout strategy using ").append(period)
            .append("-period Bollinger Bands with ").append(stdDevMultiplier)
            .append(" standard deviations. ");
        markdown.append("Targets expansion phase breakouts when price breaches upper band with strong momentum.\n\n");
        markdown.append("Market Context: ").append(context.rationale()).append("\n\n");

        String tickerList = formatTickerList(context);
        markdown.append("Target Assets: ").append(tickerList).append("\n\n");

        markdown.append("## Entry Rules\n\n");
        markdown.append("Enter LONG position when ALL conditions are met:\n\n");
        markdown.append("1. **Upper Band Breakout**: Price closes ABOVE upper Bollinger Band\n");
        markdown.append("2. **Momentum Confirmation**: Price rises at least 2% in single session\n");
        markdown.append("3. **Volume Spike**: Volume exceeds 200% of ").append(period).append("-day average\n");
        markdown.append("4. **Band Expansion**: Current band width > 20% wider than previous day\n\n");

        markdown.append("## Exit Rules\n\n");
        markdown.append("Exit position when ANY condition is met:\n\n");
        markdown.append("1. **Return to Bands**: Price closes back INSIDE upper and lower bands\n");
        markdown.append("2. **Middle Band Touch**: Price touches or crosses middle band (")
            .append(period).append("-day SMA)\n");
        markdown.append("3. **Stop Loss**: Price falls ").append(stopLoss).append("% below entry price\n");
        markdown.append("4. **Take Profit**: Price rises ").append(takeProfit).append("% above entry price\n");
        markdown.append("5. **Time-Based**: Exit after 10 days if no other exit condition triggered\n\n");

        markdown.append("## Risk Management\n\n");
        int positionSizePerTicker = calculatePositionSize(context);
        markdown.append("- **Position Size**: ").append(positionSizePerTicker)
            .append("% portfolio per ticker\n");
        markdown.append("- **Max Loss Per Position**: ").append(stopLoss).append("%\n");
        markdown.append("- **Take Profit Target**: ").append(takeProfit).append("%\n");
        markdown.append("- **Maximum Concurrent Positions**: ")
            .append(context.stockTickers().size() + context.cryptoTickers().size()).append("\n");
        markdown.append("- **Volatility Filter**: Only enter when ATR (Average True Range) is increasing\n");

        return markdown.toString();
    }

    private String formatTickerList(MarketContext context) {
        Set<String> allTickers = context.stockTickers().stream()
            .map(Symbol::ticker)
            .collect(Collectors.toSet());
        allTickers.addAll(context.cryptoTickers().stream()
            .map(Symbol::ticker)
            .collect(Collectors.toSet()));

        return String.join(", ", allTickers);
    }

    private int calculatePositionSize(MarketContext context) {
        int totalTickers = context.stockTickers().size() + context.cryptoTickers().size();
        if (totalTickers == 0) {
            return 0;
        }
        // Allocate 40% of portfolio across all tickers (conservative for breakout volatility)
        return 40 / totalTickers;
    }
}
