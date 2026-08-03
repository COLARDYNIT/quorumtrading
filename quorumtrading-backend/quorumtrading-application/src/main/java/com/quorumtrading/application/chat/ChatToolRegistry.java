package com.quorumtrading.application.chat;

import com.quorumtrading.model.valueobject.ToolDefinition;
import com.quorumtrading.model.valueobject.ToolDefinition.ToolParameter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all tools available to the chat LLM.
 * Tool definitions match the MCP tool schemas to ensure consistency.
 */
public class ChatToolRegistry {

    private ChatToolRegistry() {}

    public static List<ToolDefinition> allTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        // Strategy tools
        tools.add(new ToolDefinition("list_strategies",
            "List all trading strategies with summaries.", Map.of(), List.of()));

        tools.add(new ToolDefinition("list_public_strategies",
            "List publicly available strategies from the community library. " +
            "Use this when users want inspiration, ask what strategies exist, or need a starting point. " +
            "Users can clone any public strategy to their account.",
            Map.of(), List.of()));

        tools.add(new ToolDefinition("get_strategy",
            "Get a specific strategy by ID with full markdown content.",
            Map.of("strategyId", new ToolParameter("string", "The strategy UUID")),
            List.of("strategyId")));

        tools.add(new ToolDefinition("get_strategy_performance",
            "Get performance metrics for a strategy across all its simulations.",
            Map.of("strategyId", new ToolParameter("string", "The strategy UUID")),
            List.of("strategyId")));

        // Simulation tools
        tools.add(new ToolDefinition("list_simulations",
            "List trading simulations. Returns: id, name, strategyId, status, progress, startDate, endDate.",
            params("status", "string", "Filter by status: PENDING, SIMULATING, LIVE, COMPLETED, FAILED",
                   "limit", "integer", "Maximum results (default 20, max 100)"),
            List.of()));

        tools.add(new ToolDefinition("get_simulation",
            "Get simulation details. Returns: id, name, strategyId, status, progress, startDate, endDate, initialCash, currency, startedAt, completedAt.",
            Map.of("simulationId", new ToolParameter("string", "The simulation UUID")),
            List.of("simulationId")));

        // Portfolio tools
        tools.add(new ToolDefinition("query_portfolio_snapshots",
            "Query portfolio snapshots for a simulation over time.",
            params("simulationId", "string", "The simulation UUID",
                   "from", "string", "Start of time range in ISO-8601 format",
                   "to", "string", "End of time range in ISO-8601 format",
                   "limit", "integer", "Maximum results (default 20, max 200)"),
            List.of("simulationId")));

        // Consensus tools
        tools.add(new ToolDefinition("get_consensus_with_agents",
            "Get consensus decisions with agent breakdown for a simulation.",
            params("simulationId", "string", "The simulation UUID",
                   "ticker", "string", "Filter by ticker symbol",
                   "actions", "string", "Filter by actions: BUY, SELL, HOLD (comma-separated)",
                   "limit", "integer", "Maximum results (default 5, max 50)",
                   "includeReasoning", "string", "Include full reasoning text (true/false, default false)"),
            List.of("simulationId")));

        tools.add(new ToolDefinition("query_consensus_decisions",
            "Query consensus decision history with optional time range and action filters.",
            params("simulationId", "string", "The simulation UUID",
                   "from", "string", "Start of time range in ISO-8601 format",
                   "to", "string", "End of time range in ISO-8601 format",
                   "actions", "string", "Filter by actions: BUY, SELL, HOLD (comma-separated)",
                   "limit", "integer", "Maximum results (default 20, max 200)"),
            List.of("simulationId")));

        // Market data tools
        tools.add(new ToolDefinition("get_indicators",
            "Calculate technical indicators (RSI-14, SMA-50/200, MACD, Bollinger Bands) for a ticker. "
            + "Automatically backfills price data if missing.",
            params("ticker", "string", "Trading symbol ticker (e.g., AAPL, BTC)",
                   "investmentType", "string", "Investment type: STOCK or CRYPTO"),
            List.of("ticker", "investmentType")));

        tools.add(new ToolDefinition("query_market_events",
            "Query market events (price, news, social) within a time range.",
            params("from", "string", "Start of time range in ISO-8601 format",
                   "to", "string", "End of time range in ISO-8601 format",
                   "symbol", "string", "Filter by trading symbol",
                   "eventType", "string", "Filter by: PRICE, NEWS, or SOCIAL",
                   "limit", "integer", "Maximum results (default 20, max 200)"),
            List.of("from", "to")));

        tools.add(new ToolDefinition("backfill_price_data",
            "Backfill historical price data for a ticker.",
            params("ticker", "string", "Trading symbol ticker",
                   "assetType", "string", "Asset type: STOCK or CRYPTO",
                   "startDate", "string", "Start date (YYYY-MM-DD, e.g., 2026-01-01)",
                   "endDate", "string", "End date (YYYY-MM-DD, e.g., 2026-01-31)",
                   "granularity", "string", "Bar granularity (default: 1Day)"),
            List.of("ticker", "assetType", "startDate", "endDate")));

        tools.add(new ToolDefinition("get_ticker_performance",
            "Get historical price performance for a ticker over a date range. "
            + "Returns start/end price, period high/low, and percentage change. "
            + "Automatically backfills price data if missing. "
            + "Use this for simple 'what did X do between date A and B?' questions.",
            params("ticker", "string", "Trading symbol ticker (e.g., QQQ, AAPL, BTC)",
                   "startDate", "string", "Start date (YYYY-MM-DD, e.g., 2024-01-01)",
                   "endDate", "string", "End date (YYYY-MM-DD, e.g., 2024-12-31)",
                   "investmentType", "string", "Asset type: STOCK or CRYPTO (optional, auto-detected if omitted)"),
            List.of("ticker", "startDate", "endDate")));

        // Market research tools
        tools.add(new ToolDefinition("list_market_research",
            "List all market research configurations with status and strategy count.",
            Map.of(), List.of()));

        tools.add(new ToolDefinition("get_market_research_details",
            "Get detailed information about a specific market research.",
            params("marketResearchId", "string", "UUID of the market research",
                   "includeExecutions", "string", "Include execution list (true/false, default false)",
                   "includeAgentDecisions", "string", "Include agent decisions (true/false, default false)"),
            List.of("marketResearchId")));

        // Broker account tools
        tools.add(new ToolDefinition("list_broker_accounts",
            "List broker accounts. Returns: id, name, type (MOCK/ALPACA_PAPER/ALPACA_LIVE), status, totalBalance, availableBalance, currency.",
            Map.of(), List.of()));

        // Create tools
        tools.add(new ToolDefinition("create_strategy",
            """
            Create a new trading strategy with markdown content.

            REQUIRED FORMAT - Strategy must have YAML frontmatter with at least one ticker:

            ```yaml
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE  # MAJORITY, WEIGHTED, CONFIDENCE_BASED, or ORCHESTRATOR_OVERRIDE
            crypto_tickers:           # At least one ticker required (crypto_tickers or stock_tickers)
              - BTC
            live_schedule:            # Required: how often to evaluate in live/paper mode
              interval: 1h            # Crypto evaluation interval: 30m, 1h, 6h, 1d, 1w (or seconds, e.g. 3600)
            backtest_schedule:        # Required: how often to evaluate in backtests
              interval: 1w
            reserve_cash_percentage: 0.2       # 0.0-1.0, cash to keep in reserve
            position_limits:                   # Optional: max portfolio % per ticker (a cap, not an order size)
              BTC: 0.5
            max_order_fraction: 0.15           # Optional: max portfolio % per single BUY order.
                                               # Unset, a quantity-less BUY is platform-sized to 25%.
                                               # Tranche strategies still need agents to state quantities.
            # agents:                           # Optional: limit which agents participate
            #   - TECHNICAL                     # Valid: TECHNICAL, FUNDAMENTAL, SENTIMENT
            #   - FUNDAMENTAL                   # Omit to use all 3 agents. ORCHESTRATOR always runs.
            ---

            # Strategy Name

            ## BUY Signals
            - RSI < 30 AND MACD bullish crossover
            - Volume spike with price breakout

            ## SELL Signals
            - RSI > 70 OR position up 15%+
            - Stop loss at -5%

            ## HOLD Conditions
            - Signals unclear or low confidence
            ```

            FOR STOCKS, use stock_tickers and time-of-day schedules instead of intervals:
            ```yaml
            stock_tickers:
              - AAPL
              - MSFT
            live_schedule:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00", "14:00"]
              market_open: "09:30"
              market_close: "16:00"
            backtest_schedule:
              timezone: "America/New_York"
              trading_days: [WEDNESDAY]
              evaluation_times: ["10:00"]
              market_open: "09:30"
              market_close: "16:00"
            ```
            """,
            params("name", "string", "Display name for the strategy",
                   "description", "string", "Optional human-readable description for the strategy library (max 500 chars)",
                   "markdownContent", "string", "Full markdown content with YAML frontmatter (see format above)"),
            List.of("name", "markdownContent")));

        tools.add(new ToolDefinition("create_simulation",
            "Create a new trading simulation. Supports three modes: " +
            "(1) Backtest (default) — no brokerAccountId, uses mock broker to replay historical data. " +
            "(2) Paper trading — pass brokerAccountId of an ALPACA_PAPER account. " +
            "(3) Live trading — pass brokerAccountId of an ALPACA_LIVE account, trades with REAL MONEY. " +
            "IMPORTANT: For live trading, confirm with the user before proceeding. " +
            "Paper trading must be explicitly requested — never assume paper mode.",
            params("name", "string", "Optional display name",
                   "strategyId", "string", "UUID of the strategy",
                   "symbol", "string", "Trading symbol (e.g., BTC, AAPL)",
                   "investmentType", "string", "CRYPTO or STOCK",
                   "startDate", "string", "Start date (YYYY-MM-DD, e.g., 2026-01-01)",
                   "endDate", "string", "End date (YYYY-MM-DD, e.g., 2026-01-31)",
                   "initialCash", "string", "Initial cash in USD",
                   "brokerAccountId", "string", "Optional broker account UUID for paper or live trading. Omit for backtest."),
            List.of("strategyId", "symbol", "investmentType", "startDate", "endDate", "initialCash")));

        tools.add(new ToolDefinition("start_simulation",
            "Start a pending simulation. The simulation begins executing automatically — no further action needed.",
            Map.of("simulationId", new ToolParameter("string", "The simulation UUID")),
            List.of("simulationId")));

        tools.add(new ToolDefinition("create_market_research",
            """
            Create a new market research configuration.

            Instructions guide AI agents on what patterns to look for. Be SPECIFIC with measurable criteria:

            BAD (too vague):
            - "Find good stocks"
            - "Look for momentum"

            GOOD (specific and measurable):
            - "Find stocks with volume 3x above 20-day average, RSI below 30, showing accumulation"
            - "Identify crypto assets with bullish MACD crossover and price above 200-day SMA"
            - "Screen for oversold conditions: RSI < 25 with positive divergence on daily chart"

            Include:
            - Specific technical indicators and thresholds
            - Time horizons (intraday, swing, position)
            - Risk parameters (max drawdown, position sizing hints)
            - Allow discovery beyond target assets when patterns emerge
            """,
            params("name", "string", "Name for the research",
                   "markdownInstructions", "string", "Detailed instructions with specific criteria (see examples above)",
                   "targetAssets", "string", "Comma-separated assets (e.g., BTC:CRYPTO,AAPL:STOCK)",
                   "intervalAmount", "integer", "Interval amount (e.g., 7 for weekly)",
                   "intervalUnit", "string", "Interval unit: DAYS, WEEKS, or MONTHS"),
            List.of("name", "markdownInstructions", "intervalAmount", "intervalUnit")));

        tools.add(new ToolDefinition("execute_market_research",
            "Trigger an immediate execution of a market research.",
            Map.of("marketResearchId", new ToolParameter("string", "UUID of the market research")),
            List.of("marketResearchId")));

        // Simulation control tools
        tools.add(new ToolDefinition("pause_simulation",
            "Pause a running simulation. The simulation must be in SIMULATING or LIVE status.",
            Map.of("simulationId", new ToolParameter("string", "The simulation UUID")),
            List.of("simulationId")));

        tools.add(new ToolDefinition("sell_position",
            "Sell an entire position in a specific ticker for a simulation. " +
            "The simulation must be actively running (SIMULATING or LIVE). " +
            "Returns the filled quantity and execution price.",
            params("simulationId", "string", "The simulation UUID",
                   "ticker", "string", "The ticker symbol to sell (e.g., BTC, AAPL)",
                   "investmentType", "string", "Investment type: STOCK or CRYPTO"),
            List.of("simulationId", "ticker", "investmentType")));

        tools.add(new ToolDefinition("liquidate_all_positions",
            "Sell ALL positions in a simulation. Returns count of positions liquidated and any errors.",
            Map.of("simulationId", new ToolParameter("string", "The simulation UUID")),
            List.of("simulationId")));

        tools.add(new ToolDefinition("force_end_simulation",
            "Force-end a simulation that is stuck or needs to be stopped immediately. " +
            "By default liquidates all positions before ending. " +
            "Use liquidatePositions=false to end without selling.",
            params("simulationId", "string", "The simulation UUID",
                   "liquidatePositions", "string", "Whether to liquidate positions first (true/false, default true)"),
            List.of("simulationId")));

        // Benchmark tools
        tools.add(new ToolDefinition("list_benchmarks",
            "List all benchmarks with summaries. Returns: id, name, mode (backtest/live), startDate, endDate, createdAt.",
            params("limit", "integer", "Maximum results (default 20, max 50)"),
            List.of()));

        tools.add(new ToolDefinition("get_benchmark",
            "Get detailed information about a benchmark including its simulations. " +
            "Returns benchmark metadata and a list of simulations with their status and strategy IDs.",
            Map.of("benchmarkId", new ToolParameter("string", "The benchmark UUID")),
            List.of("benchmarkId")));

        tools.add(new ToolDefinition("compare_simulations",
            "Group 2 or more existing simulations into a benchmark for side-by-side comparison. " +
            "Use this when the user wants to compare simulations that are already created. " +
            "The benchmark will be visible at /benchmarks/<id> with a chart overlaying all simulation equity curves. " +
            "Simulations with different start dates, end dates, capital, or broker types are allowed " +
            "but will produce warnings that scenarios are not identical. " +
            "Prefer this over create_benchmark when simulations already exist.",
            params("simulationIds", "string", "Comma-separated simulation UUIDs (at least 1)",
                   "name", "string", "Optional display name for the benchmark"),
            List.of("simulationIds")));

        tools.add(new ToolDefinition("create_benchmark",
            "Create a benchmark to compare multiple strategies head-to-head by creating new simulations. " +
            "Use this when the user wants to compare strategies that don't have simulations yet. " +
            "Creates one simulation per strategy over the same time period. " +
            "For backtests, provide both startDate and endDate. For live mode, omit endDate. " +
            "The benchmark will be visible at /benchmarks/<id>.",
            params("strategyIds", "string", "Comma-separated strategy UUIDs",
                   "startDate", "string", "Start date (YYYY-MM-DD)",
                   "endDate", "string", "End date (YYYY-MM-DD). Omit for live mode.",
                   "name", "string", "Optional display name",
                   "initialCapital", "string", "Initial capital per strategy in USD (default: 100000)",
                   "costPreset", "string", "Cost preset: ZERO_FEES, TYPICAL_RETAIL, or HIGH_FRICTION (default: TYPICAL_RETAIL)"),
            List.of("strategyIds", "startDate")));

        // Market research control tools
        tools.add(new ToolDefinition("pause_market_research",
            "Pause an active market research. Prevents future scheduled executions until resumed.",
            Map.of("marketResearchId", new ToolParameter("string", "UUID of the market research")),
            List.of("marketResearchId")));

        tools.add(new ToolDefinition("resume_market_research",
            "Resume a paused market research. Re-enables scheduled executions.",
            Map.of("marketResearchId", new ToolParameter("string", "UUID of the market research")),
            List.of("marketResearchId")));

        // Ticker lookup tools
        tools.add(new ToolDefinition("find_ticker",
            "Find the trading ticker symbol for a company or asset. " +
            "Returns ticker, company name, exchange, investment type, and whether it's tradable through the broker (tradableOnBroker).",
            params("companyName", "string", "Company or asset name to look up (e.g., 'Apple', 'Tesla', 'Bitcoin')",
                   "brokerAccountId", "string", "Optional broker account UUID to validate tradability against. Omit to use the default broker."),
            List.of("companyName")));

        // Delete tools
        tools.add(new ToolDefinition("delete_strategy",
            "Delete a strategy and all associated simulations, decisions, and snapshots. Cannot delete if used by active simulations.",
            Map.of("strategyId", new ToolParameter("string", "The strategy UUID")),
            List.of("strategyId")));

        tools.add(new ToolDefinition("delete_simulation",
            "Delete a simulation and all associated consensus decisions, portfolio snapshots, and trade history.",
            Map.of("simulationId", new ToolParameter("string", "The simulation UUID")),
            List.of("simulationId")));

        tools.add(new ToolDefinition("delete_market_research",
            "Delete a market research configuration and all its execution history, agent decisions, and created strategies.",
            Map.of("marketResearchId", new ToolParameter("string", "UUID of the market research")),
            List.of("marketResearchId")));

        tools.add(new ToolDefinition("delete_benchmark",
            "Delete a benchmark and all associated simulations, portfolio snapshots, and trade history.",
            Map.of("benchmarkId", new ToolParameter("string", "The benchmark UUID")),
            List.of("benchmarkId")));

        tools.add(new ToolDefinition("get_benchmark_overlays",
            "Compute buy-and-hold reference overlays for a benchmark to compare strategy performance "
            + "against market indices like S&P 500 (SPY), Nasdaq (QQQ), Bitcoin (BTC), etc. "
            + "Shows what the portfolio would be worth if you simply bought and held a reference asset.",
            params("benchmarkId", "string", "The benchmark UUID",
                   "symbols", "string", "Comma-separated ticker symbols (e.g., SPY,QQQ,BTC)",
                   "initialCapital", "string", "Optional initial capital in USD (defaults to first simulation's initial cash)"),
            List.of("benchmarkId", "symbols")));

        // Historical events tools
        tools.add(new ToolDefinition("list_historical_events",
            "List historical market events for backtesting period selection. "
            + "Events include COVID Crash, FTX Collapse, AI Boom, GameStop Squeeze, etc. "
            + "Each returns date range, suggested assets, and keywords for AI matching.",
            params("category", "string", "Filter by: CRISIS, BULL_RUN, VOLATILITY, CATALYST, BEAR_MARKET",
                   "assetClass", "string", "Filter by: STOCK, CRYPTO, BOTH",
                   "sentiment", "string", "Filter by: BULLISH, BEARISH, MIXED"),
            List.of()));

        tools.add(new ToolDefinition("get_historical_event",
            "Get a historical event by slug or keyword. "
            + "Tries exact slug match (e.g., 'covid-crash'), then keyword search (e.g., 'pandemic', 'meme stocks').",
            Map.of("slugOrKeyword", new ToolParameter("string", "Event slug or search keyword")),
            List.of("slugOrKeyword")));

        // Challenge tools
        tools.add(new ToolDefinition("list_challenges",
            "List all trading challenges with status, ticker, dates, and participant count. " +
            "Challenges are competitions where users compete on a single ticker with fixed initial cash.",
            Map.of(), List.of()));

        tools.add(new ToolDefinition("get_challenge",
            "Get details about a specific challenge including rules and participant count.",
            Map.of("challengeId", new ToolParameter("string", "The challenge UUID")),
            List.of("challengeId")));

        tools.add(new ToolDefinition("join_challenge",
            "Join a challenge with a strategy. A simulation is created automatically with the challenge's constraints " +
            "(ticker, dates, initial cash). If the user already has an entry, the old one is replaced. " +
            "IMPORTANT: Use this instead of create_simulation when the user wants to participate in a challenge.",
            params("challengeId", "string", "The challenge UUID",
                   "strategyId", "string", "The strategy UUID to use"),
            List.of("challengeId", "strategyId")));

        // Cleanup tools
        tools.add(new ToolDefinition("prune_unused",
            "Remove unused strategies (with 0 simulations) and unused research (with 0 created strategies). Returns count of deleted items.",
            params("removeStrategies", "string", "Remove unused strategies (true/false, default true)",
                   "removeResearch", "string", "Remove unused research (true/false, default true)"),
            List.of()));

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
