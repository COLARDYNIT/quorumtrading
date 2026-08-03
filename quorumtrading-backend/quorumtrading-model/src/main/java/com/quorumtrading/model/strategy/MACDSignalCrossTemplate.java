package com.quorumtrading.model.strategy;

import com.quorumtrading.model.valueobject.IndicatorType;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MACD Signal Cross Strategy Template.
 *
 * <p><b>Strategy Type:</b> Momentum Trading</p>
 * <p><b>Core Concept:</b> Enter when MACD line crosses above signal line (bullish momentum),
 * exit when MACD line crosses below signal line (bearish momentum)</p>
 *
 * <p><b>Required Indicators:</b> MACD (MACD line, Signal line, Histogram)</p>
 *
 * <p><b>Parameters:</b></p>
 * <ul>
 *   <li>fastPeriod - Fast EMA period for MACD calculation (default: 12)</li>
 *   <li>slowPeriod - Slow EMA period for MACD calculation (default: 26)</li>
 *   <li>signalPeriod - Signal line EMA period (default: 9)</li>
 *   <li>stopLossPercent - Stop loss percentage (default: 5.0)</li>
 *   <li>takeProfitPercent - Take profit percentage (default: 12.0)</li>
 * </ul>
 *
 * <p><b>DDD Classification:</b> Domain Service</p>
 */
public class MACDSignalCrossTemplate implements StrategyTemplate {

    private static final int DEFAULT_FAST_PERIOD = 12;
    private static final int DEFAULT_SLOW_PERIOD = 26;
    private static final int DEFAULT_SIGNAL_PERIOD = 9;
    private static final BigDecimal DEFAULT_STOP_LOSS = new BigDecimal("5");
    private static final BigDecimal DEFAULT_TAKE_PROFIT = new BigDecimal("12");

    @Override
    public StrategyTemplateType getType() {
        return StrategyTemplateType.MACD_SIGNAL_CROSS;
    }

    @Override
    public Set<IndicatorType> getRequiredIndicators() {
        return Set.of(IndicatorType.MACD);
    }

    @Override
    public String generateStrategyMarkdown(MarketContext context, TemplateParameters parameters) {
        // Extract parameters with defaults
        int fastPeriod = parameters.getInt("fastPeriod", DEFAULT_FAST_PERIOD);
        int slowPeriod = parameters.getInt("slowPeriod", DEFAULT_SLOW_PERIOD);
        int signalPeriod = parameters.getInt("signalPeriod", DEFAULT_SIGNAL_PERIOD);
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
        markdown.append("# MACD Signal Cross Strategy\n\n");

        markdown.append("## Introduction\n\n");
        markdown.append("Momentum-based strategy using MACD(").append(fastPeriod).append(", ")
            .append(slowPeriod).append(", ").append(signalPeriod).append(") crossovers. ");
        markdown.append("Captures trend momentum shifts when MACD line crosses signal line, ");
        markdown.append("providing early entry signals for trending moves.\n\n");
        markdown.append("Market Context: ").append(context.rationale()).append("\n\n");

        String tickerList = formatTickerList(context);
        markdown.append("Target Assets: ").append(tickerList).append("\n\n");

        markdown.append("## Entry Rules\n\n");
        markdown.append("Enter LONG position when ALL conditions are met:\n\n");
        markdown.append("1. **Bullish MACD Crossover**: MACD line crosses ABOVE signal line\n");
        markdown.append("2. **Positive Histogram**: MACD histogram is positive and increasing\n");
        markdown.append("3. **Price Confirmation**: Price is above ").append(slowPeriod)
            .append("-day EMA (trend alignment)\n");
        markdown.append("4. **Volume Validation**: Volume exceeds 130% of 20-day average\n\n");

        markdown.append("## Exit Rules\n\n");
        markdown.append("Exit position when ANY condition is met:\n\n");
        markdown.append("1. **Bearish MACD Crossover**: MACD line crosses BELOW signal line\n");
        markdown.append("2. **Histogram Reversal**: MACD histogram turns negative\n");
        markdown.append("3. **Stop Loss**: Price falls ").append(stopLoss).append("% below entry price\n");
        markdown.append("4. **Take Profit**: Price rises ").append(takeProfit).append("% above entry price\n");
        markdown.append("5. **Time-Based**: Exit after 20 days if no other exit condition triggered\n\n");

        markdown.append("## Risk Management\n\n");
        int positionSizePerTicker = calculatePositionSize(context);
        markdown.append("- **Position Size**: ").append(positionSizePerTicker)
            .append("% portfolio per ticker\n");
        markdown.append("- **Max Loss Per Position**: ").append(stopLoss).append("%\n");
        markdown.append("- **Take Profit Target**: ").append(takeProfit).append("%\n");
        markdown.append("- **Maximum Concurrent Positions**: ")
            .append(context.stockTickers().size() + context.cryptoTickers().size()).append("\n");
        markdown.append("- **MACD Monitoring**: Re-evaluate crossover status every 6 hours\n");
        markdown.append("- **Divergence Alert**: Exit if price makes new high but MACD doesn't (bearish divergence)\n");

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
        // Allocate 60% of portfolio across all tickers (same as trend following)
        return 60 / totalTickers;
    }
}
