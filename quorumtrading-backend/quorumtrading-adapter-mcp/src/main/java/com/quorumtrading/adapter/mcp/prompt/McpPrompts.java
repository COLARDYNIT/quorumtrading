package com.quorumtrading.adapter.mcp.prompt;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * MCP prompts that guide LLM clients through common QuorumTrading workflows.
 * These provide structured instructions for multi-step analysis tasks.
 */
@Singleton
public class McpPrompts {

    @Prompt(name = "analyze_simulation",
            description = "Guided workflow to analyze a simulation's performance. Walks through fetching simulation details, consensus decisions, portfolio evolution, and generating insights.")
    public List<PromptMessage> analyzeSimulation(
        @PromptArg(description = "UUID of the simulation to analyze") String simulationId
    ) {
        String instructions = """
            Analyze the trading simulation with ID: %s

            Follow these steps:
            1. Call get_simulation to get simulation details (status, dates, strategy)
            2. FIRST check its noDecisionsReason. A run that skipped every evaluation still completes
               with a 0.00%% return, which is byte-identical to one that evaluated properly and chose
               to hold throughout. When noDecisionsReason is set, the reported return is not a result
               — say so plainly and diagnose that instead of reporting performance.
            3. Call get_strategy_performance with the strategy ID to see overall strategy metrics.
               Read its emptyRuns count, and treat statistics drawn from one or two runs as
               indicative rather than significant.
            4. Call get_consensus_with_agents to examine trading decisions and agent agreement.
               Note which agents abstained and why — the notes name the exact threshold that caused
               it, and a 1/1 vote on a three-agent strategy is not unanimity.
            5. Call query_consensus_decisions for the full per-evaluation record, including the
               rounds that were skipped.
            6. Call query_portfolio_snapshots to track portfolio value over time
            7. Summarize findings:
               - Whether the result is trustworthy at all (step 2)
               - Overall P&L and return percentage
               - Agent agreement patterns (did agents frequently disagree? who abstained?)
               - Key turning points (largest gains/losses)
               - Risk assessment based on portfolio volatility
               - Recommendations for strategy improvement
            """.formatted(simulationId);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "create_backtest",
            description = "Guided workflow to create and start a new backtest simulation. Helps select a strategy, configure parameters, and launch the backtest.")
    public List<PromptMessage> createBacktest(
        @PromptArg(description = "Trading symbol to backtest (e.g., BTC, AAPL)") String symbol,
        @PromptArg(description = "Backtest period description (e.g., '3 months', '2025-Q1', 'last year')") String period
    ) {
        String instructions = """
            Create a backtest for %s over the period: %s

            KEY CONCEPT: A simulation executes a STRATEGY — it does not trade a ticker directly. \
            You must select (or create) a strategy first, then run a simulation with that strategy.

            Follow these steps:
            1. Call list_strategies to show available strategies
               - Look for strategies whose tickers include %s
               - Show the user matching strategies with their tickers and trading mode
            2. Ask the user which strategy to use
               - If no strategy matches %s, offer to create one (read quorumtrading://guides/strategy-format first for the full YAML frontmatter reference)
               - If exactly one strategy matches, suggest it but let the user confirm
            3. Convert the period description to ISO-8601 start/end dates
            4. Call estimate_backtest_cost with the strategy and those dates BEFORE creating anything,
               and tell the user the result. If it reports zero trading days, the run would decide
               nothing — fix the dates or the strategy's backtest_schedule instead of creating it.
            5. Call create_simulation with:
               - The selected strategy ID (this is the most important parameter — the strategy defines the trading rules)
               - Calculated start and end dates
               - Initial cash (suggest $10,000 USD as default, ask user to confirm)
               - userInformedOfCost=true once you have quoted step 4 to the user
               Do NOT pass a symbol: the strategy's YAML frontmatter decides what is traded, and
               create_simulation accepts no symbol argument.
            6. Creating the simulation STARTS it — there is no separate start call and no dormant
               state to inspect. Poll get_simulation for status and progress.
            7. Confirm the backtest is running and provide the simulation ID for monitoring
            """.formatted(symbol, period, symbol, symbol);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "compare_strategies",
            description = "Guided workflow to compare performance across all trading strategies. Ranks strategies by return, win rate, and risk-adjusted metrics.")
    public List<PromptMessage> compareStrategies() {
        String instructions = """
            Compare all available trading strategies:

            Follow these steps:
            1. Call list_strategies to get all strategies
            2. For each strategy, call get_strategy_performance to get metrics
            3. Create a comparison table with columns:
               - Strategy Name
               - Completed Simulations
               - Avg Return %
               - Win Rate %
               - Sharpe Ratio
               - Best/Worst Return
            4. Rank strategies by risk-adjusted return (Sharpe ratio)
            5. Identify:
               - Best overall performer
               - Most consistent (highest win rate)
               - Highest risk-reward (best Sharpe)
               - Strategies that need improvement
            6. Provide recommendations on which strategies to use for future simulations
            """;

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "analyze_market_research",
            description = "Guided workflow to analyze a market research execution. Reviews agent decisions, viability scores, and created strategies.")
    public List<PromptMessage> analyzeMarketResearch(
        @PromptArg(description = "UUID of the market research to analyze") String marketResearchId
    ) {
        String instructions = """
            Analyze the market research with ID: %s

            Follow these steps:
            1. Call get_market_research_details to fetch the research configuration and all executions
            2. Review the research configuration:
               - Target assets and interval
               - Auto-backtest settings
               - Current status (ACTIVE/PAUSED/COMPLETED)
            3. For the latest execution (if any):
               - Review each agent's decision (advice, reasoning, model used)
               - Check the viability score and researcher conclusion
               - Note the outcome (STRATEGY_CREATED, NO_OPPORTUNITY, FAILED)
               - If a strategy was created, call get_strategy with the strategy ID
            4. Across all executions:
               - Count successful vs failed executions
               - Track viability score trends
               - Identify which agents most frequently find opportunities
            5. Summarize findings:
               - Research effectiveness (hit rate for strategy creation)
               - Agent agreement patterns
               - LLM cost analysis (raw cost per execution)
               - Recommendations for improving the research configuration
            """.formatted(marketResearchId);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "setup_live_trading",
            description = "Guided workflow to set up live trading. Verifies broker account, selects strategy, and creates a LIVE simulation.")
    public List<PromptMessage> setupLiveTrading() {
        String instructions = """
            Guide the user through setting up live trading:

            Follow these steps:
            1. Call list_broker_accounts to check available broker accounts
               - IGNORE every account whose brokerType is MOCK. Mock accounts are created
                 automatically for backtests, they cannot trade, and an install can hold dozens of
                 them all reading status ACTIVE. "16 active accounts" is not "ready to trade live";
                 only ALPACA_PAPER, ALPACA_LIVE, SAXO_SIM and SAXO_LIVE can place an order.
               - Verify at least one NON-MOCK account has status ACTIVE
               - If there are none, say so plainly and explain they need to add a broker account via
                 the web UI (credentials cannot be passed through MCP). Do not proceed.
               - If accounts exist but status is INVALID_CREDENTIALS, suggest running validate_broker_credentials
            2. Call list_simulations with status=LIVE to check for existing live simulations
               - CAREFUL: status LIVE is a lifecycle state, NOT a statement about money. A backtest
                 that has caught up with the present also reports status LIVE. Read each row's
                 realMoney field — only realMoney=true is trading real money. Ignore the rest here.
               - Show the real-money simulations and their reserved budgets
               - Verify the broker account has sufficient available balance for the new simulation
            3. Call list_strategies to show available strategies
               - Any ACTIVE strategy can be used for live trading; there is no separate publish step
               - Show strategy performance metrics via get_strategy_performance
            4. Ask the user to confirm:
               - Which strategy to use
               - Which trading symbol and investment type
               - Initial cash amount (must not exceed broker's available balance)
               - Which broker account to use
            5. IMPORTANT: Before calling create_simulation, get explicit user confirmation that they
               want to proceed with live trading. State the broker account name, strategy name, symbol,
               and initial cash amount clearly and wait for an unambiguous "yes" from the user.
            6. Call create_simulation with:
               - The selected strategy
               - Start date = now, end date = desired end date
               - Initial cash from user confirmation
               - brokerAccountId = the confirmed broker account UUID
            7. create_simulation starts the run itself — there is no separate start call. Confirm via
               get_simulation that status is LIVE and realMoney is true, then provide the simulation
               ID for monitoring
            """;

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "diagnose_simulation",
            description = "Troubleshoot a failing or underperforming simulation. Checks status, recent decisions, data availability, and identifies issues.")
    public List<PromptMessage> diagnoseSimulation(
        @PromptArg(description = "UUID of the simulation to diagnose") String simulationId
    ) {
        String instructions = """
            Diagnose issues with simulation: %s

            Follow these steps:
            1. Call get_simulation and read its `health` block FIRST — it answers "why is this
               run not progressing?" directly, while the run is still in flight:
               - verdict STALLED: the engine is not producing decisions. `topSkipReasons` names
                 why (a slow/failed LLM engine is the most common — then check get_llm_status,
                 and remember parallel simulations share one serialized local engine; pausing
                 one with pause_simulation frees it for the other).
               - verdict PAUSED_EXTERNAL: externalPauseReason says who paused it and why
                 (BUDGET_DEPLETED, DATA_GAP_DETECTED, LLM_FAILURE_DETECTED, EXECUTION_STALLED).
                 EXECUTION_STALLED means the run stopped making progress and no executor holds
                 it — a hung or vanished evaluation. Nothing was completed and the progress
                 shown is where it genuinely got to; `resume_simulation` continues it from its
                 last decision.
               - verdict WAITING_FOR_DATA: a backfill is still running — check get_price_coverage.
               - verdict FAILED: failureReason carries the recorded cause.
               - verdict IDLE with rounds recorded: too few rows to judge yet; lastRoundAt shows
                 whether anything is happening at all.
            2. Call get_consensus_with_agents to examine recent trading decisions
               - Are agents returning errors?
               - Are all agents recommending HOLD? (may indicate insufficient data or overly conservative strategy)
               - Is the orchestrator overriding agent recommendations?
            3. Call query_portfolio_snapshots to check portfolio evolution
               - Is the portfolio value declining? Identify when the decline started
               - Are there any positions? (empty portfolio with cash only = no trades executed)
               - Check for sudden value drops (possible bad trade or data issue)
            4. Call get_strategy with the simulation's strategy ID to review the rules
               - Are the rules appropriate for the trading symbol?
               - Is the consensus method suitable?
            5. Summarize diagnosis:
               - Root cause identification
               - Impact assessment
               - Recommended actions (pause, adjust strategy, check data, etc.)
            """.formatted(simulationId);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "explain_indicators",
            description = "Calculate and explain technical indicators for a ticker in plain language with buy/sell/hold implications.")
    public List<PromptMessage> explainIndicators(
        @PromptArg(description = "Trading symbol ticker (e.g., AAPL, BTC)") String ticker,
        @PromptArg(description = "Investment type: STOCK or CRYPTO") String investmentType
    ) {
        String instructions = """
            Calculate and explain technical indicators for %s (%s):

            Follow these steps:
            1. Call get_indicators with ticker=%s and investmentType=%s
            2. If warnings are present (insufficient data), explain what's missing and suggest running backfill_price_data
            3. For each available indicator, explain in plain language:

               **RSI-14:**
               - Current value and what it means
               - Is it overbought (>70), oversold (<30), or neutral?
               - Trading implication: buy/sell/hold signal strength

               **SMA-50 and SMA-200:**
               - Current values
               - Is there a golden cross (SMA-50 > SMA-200) or death cross?
               - What does the trend suggest about momentum?

               **MACD:**
               - MACD line, signal line, and histogram values
               - Is there a bullish or bearish crossover?
               - What does the histogram trend suggest?

               **Bollinger Bands:**
               - Upper, middle, and lower band values
               - Is the price near the upper band (potential reversal) or lower band (potential bounce)?
               - Is there a squeeze (bands narrowing, volatility compression)?

            4. Provide an overall technical summary:
               - Bullish, bearish, or neutral outlook
               - Key signals to watch
               - Confidence level based on indicator agreement
            """.formatted(ticker, investmentType, ticker, investmentType);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "full_research_cycle",
            description = "End-to-end workflow: backfill data, check indicators, create market research, execute, and review results.")
    public List<PromptMessage> fullResearchCycle(
        @PromptArg(description = "Trading symbol (e.g., AAPL, BTC)") String symbol,
        @PromptArg(description = "Historical period for backfill (e.g., '6 months', '1 year')") String period
    ) {
        String instructions = """
            Run a full research cycle for %s over %s:

            Follow these steps:
            1. **Backfill data:**
               - Determine investment type (CRYPTO for BTC/ETH/etc, STOCK for equity tickers)
               - Convert period to ISO-8601 start/end dates
               - Call backfill_price_data for the ticker
               - Verify success (check recordsInserted > 0)

            2. **Check indicators:**
               - Call get_indicators for the ticker
               - Review RSI, MACD, SMA, Bollinger Bands
               - Note any warnings about insufficient data

            3. **Create market research:**
               - Read quorumtrading://guides/market-research-instructions for best practices on writing instructions
               - Call create_market_research with:
                 - Name: "%s Research Cycle"
                 - Instructions: Write specific, criteria-driven instructions (not just "analyze %s") — describe patterns, thresholds, and signals to look for
                 - Target assets: %s with appropriate investment type
                 - Interval: 7 DAYS
                 - Enable auto-backtest: true
                 - Backtest length: 90 days

            4. **Execute research:**
               - Call execute_market_research with the new research ID
               - Wait briefly, then call get_market_research_details to check execution results

            5. **Review results:**
               - Check if a strategy was created (outcome = STRATEGY_CREATED)
               - Review agent decisions and viability score
               - If a strategy was created, review it with get_strategy
               - If auto-backtest was triggered, check simulation status with list_simulations filtered by the strategy ID

            6. **Summary:**
               - Data quality assessment
               - Technical indicator signals
               - Research outcome and agent consensus
               - Strategy viability (if created)
               - Recommended next steps
            """.formatted(symbol, period, symbol, symbol, symbol);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "setup_recurring_investment",
            description = "Walk the user through configuring a recurring (DCA-style) investment plan.")
    public List<io.quarkiverse.mcp.server.PromptMessage> setupRecurringInvestment(
        @io.quarkiverse.mcp.server.PromptArg(description = "Monthly amount (e.g., 200)") String monthlyAmount,
        @io.quarkiverse.mcp.server.PromptArg(description = "Optional broker account UUID; if omitted, list and ask the user to pick") String brokerAccountId
    ) {
        String instructions = """
            The user wants to set up a recurring investment plan that fires every month and lets the
            AI picker choose a strategy to fund. Walk them through it methodically — recurring plans
            move REAL CASH on paper and live broker accounts, so be deliberate.

            Target amount per month: %s

            ## Step-by-step

            1. **Pick a broker account.**
               %s
               - If brokerAccountId was provided, call get_broker_account to verify it exists, is ACTIVE,
                 and report the brokerType to the user.
               - If not provided, call list_broker_accounts and present the options. Default to MOCK
                 for first-time setup, ALPACA_PAPER for risk-free practice, ALPACA_LIVE only when the
                 user has explicitly asked for real-money trading.
               - **ALPACA_LIVE warning:** If chosen, remind the user that each fired execution will spend
                 real money and that you'll pass confirmLive=true on their explicit confirmation only.

            2. **Confirm the budget rule with the user.**
               - FIXED_AMOUNT: invest exactly the monthly amount; skip the month if balance is short.
               - FIXED_OR_PARTIAL: invest up to the monthly amount, accept less if balance is short.
               - ALL_AVAILABLE: invest the entire available balance each month (advanced).

            3. **Collect preferences (optional but helpful).**
               - Risk tolerance: conservative, moderate, or aggressive.
               - Allowed / blocked tickers — restrict the strategy pool to assets the user is comfortable with.
               - maxStrategiesActive — cap the number of distinct live simulations the plan can spawn.

            4. **Pick a day of month (1-28).** The 1st is conventional for monthly DCA;
               days 29-31 are not supported because they don't exist in every month.

            5. **Create the plan.**
               - Call create_investment_plan with the collected fields.
               - Pass confirmLive=true ONLY for ALPACA_LIVE brokers after explicit user confirmation.

            6. **Offer to fire the first execution now.**
               - Ask the user: "Should I run the first execution now, or wait for the scheduled day?"
               - If yes: call execute_investment_plan_now with the new planId.
               - Then call get_investment_allocation with the returned allocation ID and read the picker
                 reasoning back to the user so they understand what the AI chose and why.

            7. **Summary to the user.**
               - Plan ID, broker, cadence, budget.
               - Any preferences applied.
               - Next scheduled execution (nextExecutionAt from the response).
               - Where to find the audit trail (list_investment_allocations).
            """.formatted(monthlyAmount,
                    brokerAccountId != null && !brokerAccountId.isBlank()
                            ? "User-provided brokerAccountId: " + brokerAccountId
                            : "No broker account provided yet — ask the user.");

        return List.of(io.quarkiverse.mcp.server.PromptMessage.withUserRole(instructions));
    }

    @Prompt(name = "review_investment_plans",
            description = "Walk through the user's recurring investment plans and produce a digest of "
                    + "the last few executions per plan.")
    public List<io.quarkiverse.mcp.server.PromptMessage> reviewInvestmentPlans() {
        String instructions = """
            Produce a monthly-standup-style digest of the user's recurring investment plans.

            1. **List the plans.**
               - Call list_investment_plans (no status filter) to get every plan.
               - For each ACTIVE plan, note the cadence + next-execution-at; for PAUSED, why is it paused?
                 For TERMINATED, mention briefly but skip details.

            2. **For each plan, list the last 6 allocations.**
               - Call list_investment_allocations with the plan ID and limit=6.
               - Summarize: total intended vs actual amount, count of executed vs skipped vs failed,
                 and any picker reasoning that stands out.

            3. **Cross-reference simulations.**
               - For unique target_simulation_id values across the allocations, call get_simulation to
                 fetch TWR/MWR + total_invested. This shows whether the plan is actually paying off.

            4. **Produce the digest.**
               - One paragraph per plan: cadence, last few months at a glance, performance trend,
                 anything weird (e.g. picker keeps choosing the same strategy because the user only
                 has one — flag that as a diversification gap).
               - End with "next month's scheduled fires" — a simple bullet list ordered by nextExecutionAt.

            Keep it terse and skim-friendly. The user wants the numbers + one signal of "is this working".
            """;
        return List.of(io.quarkiverse.mcp.server.PromptMessage.withUserRole(instructions));
    }
}
