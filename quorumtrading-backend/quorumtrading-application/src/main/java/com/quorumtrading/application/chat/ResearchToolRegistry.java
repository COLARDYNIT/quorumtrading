package com.quorumtrading.application.chat;

import com.quorumtrading.model.valueobject.ToolDefinition;
import com.quorumtrading.model.valueobject.ToolDefinition.ToolParameter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of tools available to market research agents.
 * <p>
 * Exposes a curated subset of platform tools that are useful for research:
 * technical indicators, price data, market events, and ticker lookup.
 * <p>
 * Mutating tools (create/delete/update) are intentionally excluded to prevent
 * research agents from modifying platform state — they should only read data.
 */
public class ResearchToolRegistry {

    private ResearchToolRegistry() {}

    /**
     * Returns the curated list of tools available to research agents.
     * These are read-only data tools — no create/delete/update operations.
     */
    public static List<ToolDefinition> researchTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(new ToolDefinition("get_indicators",
            "Calculate technical indicators for a ticker. Returns: RSI-14, SMA-50, SMA-200, MACD (value, signal, histogram), "
            + "Bollinger Bands (upper, middle, lower), latest close price, and dividend data for stocks. "
            + "Automatically backfills price data if missing. "
            + "Use this to ground your analysis in real numbers instead of guessing.",
            params("ticker", "string", "Trading symbol ticker (e.g., AAPL, BTC)",
                   "investmentType", "string", "Investment type: STOCK or CRYPTO"),
            List.of("ticker", "investmentType")));

        tools.add(new ToolDefinition("get_ticker_performance",
            "Get historical price performance for a ticker over a date range. "
            + "Returns start/end price, period high/low, and percentage change. "
            + "Automatically backfills price data if missing. "
            + "Use this for 'what did X do between date A and B?' questions.",
            params("ticker", "string", "Trading symbol ticker (e.g., QQQ, AAPL, BTC)",
                   "startDate", "string", "Start date (YYYY-MM-DD)",
                   "endDate", "string", "End date (YYYY-MM-DD)",
                   "investmentType", "string", "Asset type: STOCK or CRYPTO (optional, auto-detected if omitted)"),
            List.of("ticker", "startDate", "endDate")));

        tools.add(new ToolDefinition("query_market_events",
            "Query market events (price, news, social) within a time range from the platform's database.",
            params("from", "string", "Start of time range in ISO-8601 format",
                   "to", "string", "End of time range in ISO-8601 format",
                   "symbol", "string", "Filter by trading symbol",
                   "eventType", "string", "Filter by: PRICE, NEWS, or SOCIAL",
                   "limit", "integer", "Maximum results (default 20, max 200)"),
            List.of("from", "to")));

        tools.add(new ToolDefinition("find_ticker",
            "Find the trading ticker symbol for a company or asset. "
            + "Returns ticker, company name, exchange, investment type, and whether it's tradable through the broker (tradableOnBroker). "
            + "Use this to verify that a ticker exists before including it in a strategy.",
            Map.of("companyName", new ToolParameter("string", "Company or asset name to look up (e.g., 'Apple', 'Tesla', 'Bitcoin')")),
            List.of("companyName")));

        tools.add(new ToolDefinition("list_historical_events",
            "List historical market events for context. "
            + "Events include COVID Crash, FTX Collapse, AI Boom, GameStop Squeeze, etc. "
            + "Each returns date range, suggested assets, and keywords.",
            params("category", "string", "Filter by: CRISIS, BULL_RUN, VOLATILITY, CATALYST, BEAR_MARKET",
                   "assetClass", "string", "Filter by: STOCK, CRYPTO, BOTH",
                   "sentiment", "string", "Filter by: BULLISH, BEARISH, MIXED"),
            List.of()));

        tools.add(new ToolDefinition("get_strategy_performance",
            "Get performance metrics for an existing strategy across all its simulations. "
            + "Returns average return, win rate, Sharpe ratio. "
            + "Use this to check how similar strategies have performed historically.",
            Map.of("strategyId", new ToolParameter("string", "The strategy UUID")),
            List.of("strategyId")));

        tools.add(new ToolDefinition("list_strategies",
            "List all existing trading strategies with summaries. "
            + "Use this to check what strategies already exist and avoid duplicates.",
            Map.of(), List.of()));

        return tools;
    }

    /**
     * Build a parameters map from varargs key-type-description triples.
     */
    private static Map<String, ToolParameter> params(String... args) {
        Map<String, ToolParameter> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 3) {
            map.put(args[i], new ToolParameter(args[i + 1], args[i + 2]));
        }
        return map;
    }
}
