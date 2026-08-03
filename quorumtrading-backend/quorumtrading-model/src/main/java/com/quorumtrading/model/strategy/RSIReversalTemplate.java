package com.quorumtrading.model.strategy;

import com.quorumtrading.model.valueobject.IndicatorType;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RSI Reversal Strategy Template.
 *
 * <p><b>Strategy Type:</b> Mean Reversion</p>
 * <p><b>Core Concept:</b> Buy oversold conditions (RSI < 30), sell overbought conditions (RSI > 70)</p>
 *
 * <p><b>Required Indicators:</b> RSI (Relative Strength Index)</p>
 *
 * <p><b>Parameters:</b></p>
 * <ul>
 *   <li>rsiPeriod - RSI calculation period (default: 14)</li>
 *   <li>oversoldThreshold - RSI level considered oversold (default: 30)</li>
 *   <li>overboughtThreshold - RSI level considered overbought (default: 70)</li>
 *   <li>stopLossPercent - Stop loss percentage (default: 5.0)</li>
 *   <li>takeProfitPercent - Take profit percentage (default: 10.0)</li>
 * </ul>
 *
 * <p><b>DDD Classification:</b> Domain Service</p>
 */
public class RSIReversalTemplate implements StrategyTemplate {

    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final BigDecimal DEFAULT_OVERSOLD_THRESHOLD = new BigDecimal("30");
    private static final BigDecimal DEFAULT_OVERBOUGHT_THRESHOLD = new BigDecimal("70");
    private static final BigDecimal DEFAULT_STOP_LOSS = new BigDecimal("5");
    private static final BigDecimal DEFAULT_TAKE_PROFIT = new BigDecimal("10");

    @Override
    public StrategyTemplateType getType() {
        return StrategyTemplateType.RSI_REVERSAL;
    }

    @Override
    public Set<IndicatorType> getRequiredIndicators() {
        return Set.of(IndicatorType.RSI);
    }

    @Override
    public String generateStrategyMarkdown(MarketContext context, TemplateParameters parameters) {
        // Extract parameters with defaults
        int rsiPeriod = parameters.getInt("rsiPeriod", DEFAULT_RSI_PERIOD);
        BigDecimal oversoldThreshold = parameters.getDecimal("oversoldThreshold", DEFAULT_OVERSOLD_THRESHOLD);
        BigDecimal overboughtThreshold = parameters.getDecimal("overboughtThreshold", DEFAULT_OVERBOUGHT_THRESHOLD);
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
        markdown.append("# RSI Reversal Strategy\n\n");

        markdown.append("## Introduction\n\n");
        markdown.append("Mean reversion strategy using ").append(rsiPeriod).append("-period RSI indicator. ");
        markdown.append("Targets oversold (< ").append(oversoldThreshold).append(") and overbought (> ")
            .append(overboughtThreshold).append(") conditions for reversal trades.\n\n");
        markdown.append("Market Context: ").append(context.rationale()).append("\n\n");

        String tickerList = formatTickerList(context);
        markdown.append("Target Assets: ").append(tickerList).append("\n\n");

        markdown.append("## Entry Rules\n\n");
        markdown.append("Enter LONG position when ALL conditions are met:\n\n");
        markdown.append("1. **RSI Oversold**: ").append(rsiPeriod).append("-period RSI falls below ")
            .append(oversoldThreshold).append("\n");
        markdown.append("2. **Price Confirmation**: Price shows reversal candle pattern (bullish engulfing or hammer)\n");
        markdown.append("3. **Volume Surge**: Volume exceeds 150% of 10-day average on entry day\n\n");

        markdown.append("## Exit Rules\n\n");
        markdown.append("Exit position when ANY condition is met:\n\n");
        markdown.append("1. **RSI Overbought**: ").append(rsiPeriod).append("-period RSI rises above ")
            .append(overboughtThreshold).append("\n");
        markdown.append("2. **Stop Loss**: Price falls ").append(stopLoss).append("% below entry price\n");
        markdown.append("3. **Take Profit**: Price rises ").append(takeProfit).append("% above entry price\n");
        markdown.append("4. **Time-Based**: Exit after 14 days if no other exit condition triggered\n\n");

        markdown.append("## Risk Management\n\n");
        int positionSizePerTicker = calculatePositionSize(context);
        markdown.append("- **Position Size**: ").append(positionSizePerTicker)
            .append("% portfolio per ticker\n");
        markdown.append("- **Max Loss Per Position**: ").append(stopLoss).append("%\n");
        markdown.append("- **Take Profit Target**: ").append(takeProfit).append("%\n");
        markdown.append("- **Maximum Concurrent Positions**: ")
            .append(context.stockTickers().size() + context.cryptoTickers().size()).append("\n");
        markdown.append("- **RSI Monitoring**: Evaluate RSI every 4 hours during market hours\n");

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
        // Allocate 50% of portfolio across all tickers (more conservative for mean reversion)
        return 50 / totalTickers;
    }
}
