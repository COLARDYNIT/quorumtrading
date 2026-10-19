package com.quorumtrading.adapter.mcp.resource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static guide content shared between MCP resources and tools.
 * Add new guides here — they are automatically available in both
 * the {@code get_guide} tool and the {@code quorumtrading://guides/*} resources.
 */
public final class GuideContent {

    private GuideContent() {
    }

    // ----- Static markdown content (must be declared before the map initializer) -----

    static final String PLATFORM_OVERVIEW = """
        # QuorumTrading Platform Overview

        ## What is QuorumTrading?

        QuorumTrading is a SaaS platform for multi-LLM trading consensus and backtesting.
        It orchestrates multiple AI agents to make collaborative trading decisions through
        consensus mechanisms, with historical backtesting capabilities.

        ## Core Concepts

        ### Strategies

        Strategies are markdown documents with optional YAML frontmatter that define trading rules.
        They describe how AI agents should analyze markets and make decisions. A strategy specifies
        which tickers to trade (via `stock_tickers` and `crypto_tickers` in the YAML frontmatter),
        the consensus method, and the trading rules. Strategies are ACTIVE when created and can be ARCHIVED when no longer needed.

        ### Simulations

        Simulations execute strategies against market data — a simulation does NOT trade a ticker
        directly. You always need a strategy first. When creating a simulation, the `strategyId` is
        the most important parameter: it defines what trading rules to run. There is no `symbol`
        parameter — a simulation trades every ticker its strategy declares, so choose the tickers
        when you write the strategy.

        Simulations follow a state machine:
        PENDING → WAITING_FOR_DATA → SIMULATING → LIVE → COMPLETED (or FAILED).

        Simulations support three trading modes, controlled by the `brokerAccountId` parameter:

        - **Backtest (default)** — No `brokerAccountId`. Uses a mock broker to replay historical price data.
          Ideal for validating strategies before risking real capital.
        - **Paper trading** — Pass a `brokerAccountId` of an ALPACA_PAPER account. Trades with fake money
          against real-time market data via Alpaca's paper trading environment.
        - **Live trading** — Pass a `brokerAccountId` of an ALPACA_LIVE account. Trades with REAL MONEY.
          Requires explicit user confirmation before creating the simulation.

        Broker accounts must be created via the QuorumTrading web UI (credentials cannot be transmitted through MCP).

        ### AI Agents

        Four specialized AI agents analyze markets from different perspectives:

        - **Technical Agent** — Chart patterns, indicators (RSI, MACD, SMA, Bollinger Bands)
        - **Fundamental Agent** — Company financials, earnings, valuations
        - **Sentiment Agent** — News headlines, social media, market mood
        - **Orchestrator Agent** — Synthesizes agent recommendations into a final decision

        ### Consensus Methods

        Agents vote on trading actions. The consensus method determines how the final decision is made:

        - **MAJORITY** — Agent votes determine the final action directly. No orchestrator LLM call. Ties default to HOLD. HOLD agents with confidence below `consensus_threshold` abstain (don't vote), so a single strong BUY/SELL signal can win when other agents have no strong opinion. Note the asymmetry: the threshold drops low-confidence HOLDs but never low-confidence BUY/SELL votes, so an agent voting BUY at 0.55 executes while an agent voting HOLD at 0.55 is discarded.
        - **ORCHESTRATOR_OVERRIDE** — An LLM orchestrator reviews all agent recommendations and makes the final decision.

        ### Benchmarks

        Benchmarks compare multiple simulations side-by-side. They group simulations that share the
        same date range and ticker universe, ranking them by return percentage. Use benchmarks to
        evaluate which strategy performs best under the same market conditions.

        ### Market Research

        Market research automates strategy discovery. It runs on a schedule, using AI agents to:
        1. Analyze current market conditions for target assets
        2. Identify trading opportunities
        3. Automatically create strategies for viable opportunities
        4. Optionally backtest created strategies immediately

        Research has an interval (e.g., every 7 days) and can be paused/resumed.

        ### Portfolio Management

        Portfolios track positions and cash for each simulation. Snapshots are immutable records
        of portfolio state at a point in time, enabling performance charting and P&L tracking.

        ## The Evaluation Loop

        1. **Market data ingestion** — Price, news, and social data flow into the system
        2. **Technical indicator calculation** — RSI, MACD, SMA, Bollinger Bands computed
        3. **Agent analysis** — Each agent evaluates the current situation
        4. **Consensus voting** — Agents vote on BUY/SELL/HOLD actions
        5. **Order execution** — Winning action is executed via the broker
        6. **Portfolio update** — Positions and cash are updated
        7. **Snapshot recording** — Immutable portfolio snapshot saved for history

        ## News Data Availability

        Historical news reaches back only so far, and how far depends on which sources are
        configured. A backtest starting before that horizon has **no news context**, so the
        Fundamental and Sentiment agents abstain and your consensus is decided by fewer voices
        than you configured. The Technical agent is unaffected.

        Do not assume a cut-off date — measure it. `get_price_coverage` reports what is actually
        stored for a ticker, and the `/api/v1/config/news-availability` endpoint reports the
        earliest news date available to this instance.

        ## Recommended Workflow

        1. **Backfill price data** for your target ticker(s) — and backfill enough history to cover
           the longest indicator your strategy uses. A 50-day moving average needs 50 trading days
           BEFORE your backtest starts, or it never computes and the rules built on it never fire.
        2. **Write a strategy**, or **run market research** to discover one — market research sits
           behind the `market-research` feature flag and is switched off by default on a
           self-hosted instance. If its tools are not listed, either ask an administrator to switch
           the flag on (see the `feature-flags` guide) or write the strategy directly; everything
           downstream works the same either way.
        3. **Create a backtest simulation** to validate the strategy historically
        4. **Review consensus decisions** and portfolio performance — see below for which tool
           answers which question
        5. When satisfied, **set up live trading** with a broker account

        ## Reading Your Results

        Step 4 above is where most people stall, because the run finishing tells you almost
        nothing on its own. Four questions, and the tool for each:

        | Question | Tool |
        |---|---|
        | Why is this run not progressing? | `get_simulation` — read the `health` block: verdict (HEALTHY/STALLED/PAUSED_EXTERNAL/…), skip streak, and the dominant skip reasons, live while it runs |
        | Is this result trustworthy at all? | `get_simulation` — check `noDecisionsReason` (set = the run made no trading decision and **the reported return is not a result**) and `decisionsNotExecuted` (large = the run decided plenty and traded little; read the decision rows first) |
        | What did it decide, and when? | `query_consensus_decisions` — one row per evaluation, with the action and the skip reason |
        | Why did it decide that? | `get_consensus_with_agents` with `includeReasoning=true` — per-agent reasoning, and `notes` names every agent that did not vote and why |
        | How did the portfolio move? | `query_portfolio_snapshots` — the value series behind the headline return |
        | What did it cost? | `get_simulation_cost` for a finished run; `estimate_backtest_cost` **before** creating one |

        Two things worth knowing before you read a number:

        - **A 0.00% return can mean two different things.** A run that evaluated normally and
          chose to hold throughout, and a run that skipped every evaluation, both finish
          COMPLETED at 0.00%. `noDecisionsReason` is what separates them — check it first.
        - **Fewer agents may have voted than you configured.** `get_consensus_with_agents`
          reports, per decision, who voted and names each absentee with its reason — a data gap
          it abstained over, a confidence below `consensus_threshold`, or an outright failure.
          A three-agent strategy running on one voter looks identical to unanimity otherwise.

        To compare strategies rather than runs, use `get_strategy_performance` — and read its
        `emptyRuns` count, which says how many of the completed runs decided nothing and were
        therefore excluded from the averages.
        """;

    static final String TRADING_CONCEPTS = """
        # Trading Concepts Guide

        ## Technical Indicators

        ### RSI (Relative Strength Index)

        RSI measures momentum on a 0–100 scale over 14 periods.

        - **Below 30** — Oversold (potential buy signal)
        - **Above 70** — Overbought (potential sell signal)
        - **30–70** — Neutral range

        ### MACD (Moving Average Convergence Divergence)

        MACD uses two exponential moving averages (12-period and 26-period) and a signal line (9-period EMA of MACD).

        - **Bullish crossover** — MACD crosses above signal line
        - **Bearish crossover** — MACD crosses below signal line
        - **Histogram** — Visual representation of MACD minus signal

        ### SMA (Simple Moving Average)

        - **SMA-50** — Short-term trend (50-day average)
        - **SMA-200** — Long-term trend (200-day average)
        - **Golden Cross** — SMA-50 crosses above SMA-200 (bullish signal)
        - **Death Cross** — SMA-50 crosses below SMA-200 (bearish signal)

        ### Bollinger Bands

        Three bands around a 20-period SMA, using 2 standard deviations.

        - **Squeeze** — Bands narrow, indicating low volatility and potential breakout
        - **Breakout** — Price moves outside bands, suggesting strong momentum
        - **Mean reversion** — Price tends to return to the middle band

        ## Market Hours

        - **NYSE/NASDAQ** — 9:30 AM – 4:00 PM Eastern Time (Monday–Friday)
        - **Crypto markets** — 24/7, no closing hours
        - **Pre-market** — 4:00 AM – 9:30 AM ET (limited liquidity)
        - **After-hours** — 4:00 PM – 8:00 PM ET (limited liquidity)

        ## Order Types

        - **Market order** — Execute immediately at current price
        - **Limit order** — Execute only at specified price or better
        - **Stop order** — Trigger a market order when price reaches a threshold
        - **Stop-limit order** — Trigger a limit order when price reaches a threshold

        ## Broker Slippage

        Slippage is the difference between expected and actual execution price.

        - **Bid-ask spread** — Difference between buy and sell prices
        - **Market impact** — Large orders move the price against you
        - **Volatility** — Higher volatility increases slippage
        - **QuorumTrading models** — Configurable slippage simulation (zero, typical retail, high friction)

        ## Backtesting Pitfalls

        - **Look-ahead bias** — Using future data in historical decisions
        - **Survivorship bias** — Only testing on assets that still exist
        - **Overfitting** — Strategy works on historical data but fails on new data
        - **Insufficient data** — Drawing conclusions from too few trades
        - **Transaction costs** — Ignoring commissions and slippage in backtests

        ## Risk Management

        - **Position sizing** — Never risk more than 1–2% of portfolio per trade
        - **Stop-loss orders** — Automatically exit losing positions at predefined levels
        - **Diversification** — Spread risk across multiple assets and strategies
        - **Sharpe ratio** — Risk-adjusted return metric (higher is better, > 1.0 is good)

        ## Minimum Trade Value

        The platform refuses orders below a per-asset-class **minimum trade value — $100 for
        both stocks and crypto by default** (`quorumtrading.trading.min-trade-amount.*`). A BUY
        or SELL below the floor converts to HOLD with outcome `BELOW_MINIMUM_TRADE_VALUE` and a
        note stating both numbers; nothing reaches the broker.

        This interacts with the strategy's own risk numbers: the largest BUY the engine will
        ever attempt for a ticker is `cash × (1 − reserve_cash_percentage) × position_limit`.
        On a small portfolio with tight limits that headroom can sit *permanently* below the
        floor, and every BUY converts to HOLD forever. `create_simulation` warns when its
        numbers imply exactly that.

        ## Dividends & ETF Types

        - **Distributing ETFs** pay dividends in cash (e.g., SPY, VYM)
        - **Accumulating ETFs** reinvest dividends automatically (common in European ETFs)
        - QuorumTrading backtests use price-only returns — dividend income is not tracked
        - When comparing strategies, be aware that dividend-paying stocks appear to underperform
          in price-only comparisons vs their actual total return
        - Use the dividend info shown on strategy pages, in ticker lookups, and in indicator
          snapshots to understand which of your holdings pay dividends
        """;

    static final String ALPACA_SETUP = """
        # Alpaca Broker Setup Guide

        ## Step 1: Create an Alpaca Account

        Visit alpaca.markets and create a free account. Alpaca offers commission-free
        trading for US stocks and crypto.

        ## Step 2: Paper vs Live Trading

        - **Paper trading** — Use fake money to test strategies risk-free
        - **Live trading** — Trade with real money (requires funded account)

        We recommend starting with paper trading to validate your strategies.

        ## Step 3: Generate API Keys

        1. Log into your Alpaca dashboard
        2. Navigate to "API Keys" section
        3. Generate a new API key pair (key + secret)
        4. Save both values securely — the secret is only shown once

        ## Step 4: Add Broker Account in QuorumTrading

        **Important:** Broker accounts must be created through the QuorumTrading web UI.
        API credentials cannot be transmitted through MCP for security reasons.

        1. Go to Settings → Broker Accounts in the QuorumTrading web app
        2. Click "Add Broker Account"
        3. Select broker type: ALPACA_PAPER or ALPACA_LIVE
        4. Enter your API key and secret
        5. Give the account a descriptive name

        ## Step 5: Verify Credentials

        After adding the account, verify the credentials work:
        - Use the `validate_broker_credentials` tool with the broker account ID
        - Status should change to ACTIVE if credentials are valid
        - If INVALID_CREDENTIALS, regenerate keys in Alpaca dashboard

        ## Step 6: Key Constraints

        - Multiple concurrent LIVE simulations are supported for both paper and live Alpaca accounts
        - Each simulation reserves a budget from the broker account's available balance
        - Mock broker accounts have no restrictions

        ## Step 7: Capital Reservation Model

        When a simulation starts:
        1. Initial cash is reserved from the broker account balance
        2. Reserved balance is locked for the simulation
        3. Available balance = total balance - reserved balance
        4. When simulation completes, reserved balance is released

        ## Common Issues

        - **INVALID_CREDENTIALS** — API key expired or revoked; regenerate in Alpaca
        - **Insufficient balance** — Available balance too low for simulation's initial cash
        - **Rate limits** — Alpaca limits API calls; the platform handles retries automatically
        - **Market hours** — Stock orders only execute during NYSE hours (9:30–16:00 ET)
        - **Pattern Day Trader** — Accounts under $25K may be restricted to 3 day trades per 5 business days
        """;

    static final String MARKET_DATA = """
        # Market Data

        Everything a strategy decides on comes from stored price bars and news. This page is
        about getting them, and about the one thing new users most often get wrong: **crypto
        needs no account, no API key, and no broker.**

        ## Crypto price data needs nothing

        Crypto candles come from a keyless public source. You do not need a broker account or any
        API key to backfill BTC or ETH.

        `get_guide('alpaca-setup')` is about **placing orders**, not about getting prices. If you
        are only backtesting crypto, you can ignore it entirely.

        ## But every backtest needs an LLM engine

        Price data is not the only prerequisite, and this is the one that catches people out:
        **the AI agents need a selected LLM engine, or every evaluation is skipped.** The run
        still reaches COMPLETED and still reports 0.00% — it simply decided nothing.

        - `get_llm_status` — is an engine selected, and can it actually answer right now?
        - `choose_llm_provider` — select one.

        Three kinds exist: a local CLI, a local OpenAI-compatible server, or a hosted API. Only
        the last costs money per call. See LOCAL_SETUP.md for how to configure each, including
        how to reach a host CLI from inside Docker.

        If a finished run reports `noDecisionsReason` — or a running one shows `health.verdict`
        STALLED — check `get_llm_status` first: an engine that cannot answer (or answers slower
        than the timeouts allow) is the most common cause. Parallel simulations share one
        serialized local engine; `pause_simulation` on one frees it for the other.

        ## Stocks need a data key

        US equity bars come from a licensed provider, so a self-hosted instance needs one key
        configured before `backfill_price_data` can return anything for a stock. The hosted
        edition at quorumtrading.com has this configured already.

        **Where to put it:** copy `.env.local.example` to `.env`, set the provider token there,
        and restart the backend. LOCAL_SETUP.md § Stocks names the exact variables and lists
        which providers are supported. If a stock backfill fails, its error names every provider
        it tried and says whether the problem was credentials or coverage — read it before
        changing your ticker.

        `get_market_data_status` answers the question directly: which providers this deployment
        has, which are switched on, which hold credentials, and what a live call to each returns
        right now. It is the market-data counterpart to `get_llm_status`, and it is what
        distinguishes "no key configured" from "this provider does not carry that symbol" — the
        two failures that look identical from a backfill error alone.

        `get_price_coverage` is the tool that tells you the truth for any ticker: how many bars
        are stored, over what range, and which sources supplied them. Call it before assuming
        anything about history — including how far back it goes. **Do not guess a start date
        for a backtest; measure it.**

        Pass `startDate` and `endDate` to measure a specific window. Without them you get a global
        first and last bar, which cannot answer "is my backtest window covered?" — BTC reports a
        decade between its first and last bar and has bars on well under half the days in it. With
        a window you get expected-vs-covered day counts and the list of gaps.

        ## How to write a ticker

        | Asset | Write | Do NOT write |
        |---|---|---|
        | US stock | `AAPL`, `MSFT`, `BRK.B` | `AAPL.US`, `AAPL:XNAS` |
        | Non-US company | its US-tradable line — use `find_ticker` | `ASML.AS`, `7203.T`, `SAP:XETR` |
        | Crypto | `BTC`, `ETH`, `SOL` — the bare asset | `BTC-USD`, `BTC/USD`, `BTCUSD`, `BTCUSDT`, `BTCEUR`, `ETHBTC` |

        Pair forms are rejected in every quote currency — `BTC-USD`, `BTCUSD`, `BTCUSDT`,
        `BTCEUR` and `ETHBTC` alike. Write the asset alone; nothing here trades a quote. A
        ticker's field decides how it is treated: `stock_tickers` vs `crypto_tickers` in the
        frontmatter, not the spelling.

        Non-US venues are not supported by any configured broker or price source. `find_ticker`
        takes a company name and returns the line you can actually trade, and tells you when
        that line is a depositary receipt carrying a currency component rather than the
        company's home listing.

        ## Warm-up: backfill more than your backtest window

        Indicators need history *before* the first evaluation. A strategy referencing SMA-200
        needs 200 trading days of bars before the start date or that rule never computes — and
        the agent will improvise around it rather than tell you. `create_simulation` warns when
        stored history is too short for the indicators your strategy names; take the warning
        seriously rather than starting the run anyway.

        ## News

        News reaches back only so far, and a backtest starting before that horizon runs with no
        news context — the Fundamental and Sentiment agents abstain and say so, and your
        consensus is decided by fewer voices than you configured. The Technical agent is
        unaffected. Crypto backtests frequently have no news at all; that is normal.

        ## Tools

        - `find_ticker` — company name → the ticker you can actually trade
        - `get_market_data_status` — which providers exist, which are on, which have keys, and whether they answer
        - `get_price_coverage` — what is stored right now, and from where; pass a date range to measure a window
        - `backfill_price_data` — fetch and store history; names every provider it tried on failure
        - `get_indicators` — the computed indicator values for a ticker
        """;

    static final String STRATEGY_FORMAT = """
        # Strategy Format Reference

        Strategies are markdown documents with YAML frontmatter. The frontmatter configures
        the trading system, and the markdown body is read by EVERY agent — Technical,
        Fundamental, Sentiment and the Orchestrator alike.

        ## Structure

        ```
        ---
        # YAML frontmatter (configuration)
        version: v1.0
        consensus_method: ORCHESTRATOR_OVERRIDE
        ...
        ---

        # Markdown body (read by every agent)
        Your strategic instructions here...
        ```

        ## YAML Frontmatter Fields

        ### Ticker Fields (at least one required — this is the only hard requirement)

        | Field | Type | Description |
        |-------|------|-------------|
        | `crypto_tickers` | list | Crypto assets to trade (e.g., `[BTC, ETH, SOL]`). Enables **Crypto Mode** (24/7). |
        | `stock_tickers` | list | Stock tickers to trade (e.g., `[AAPL, MSFT, NVDA]`). Enables **Stock Mode** (scheduled). US-listed tickers only — see below. |

        #### Ticker Syntax

        Use the bare US ticker: `AAPL`, `MSFT`, `BRK.B`.

        ⚠️ **Non-US exchange-qualified tickers are rejected.** Forms such as `RHM:XETR` or
        `AZN:XLON`, and dotted venue aliases such as `RHM.DE` or `AZN.L`, are refused when a
        strategy is created: no attachable broker trades a non-US venue and no price source covers
        one. (A dotted US name like `BRK.B` is unaffected — it is a share class, not a venue.)

        A **US** venue suffix is accepted and adds nothing: `AAPL:XNAS` is stored as `AAPL`, and
        the recognised US venues are `XNAS`, `XNYS`, `XASE`, `ARCX`, `BATS`, `IEXG`, `XCBO` and
        `OOTC`. Prefer the bare ticker — `position_limits` keys must match what you wrote.

        **To trade a European or Asian company, use its US listing.** Call `find_ticker` with the
        company name: it returns the tradable US route, tells you when that route is a depositary
        receipt rather than the company's home listing, and names the home listing separately so
        you can see what you are not getting. A receipt tracks the home listing through an
        exchange rate, so your returns carry a currency component you did not choose. Do not copy
        a receipt symbol out of documentation — which ones are tradable varies by broker and
        changes over time, so let `find_ticker` check against the broker you actually have.

        All simulations settle in USD; there is no per-ticker currency setting.

        `position_limits` keys must match the ticker exactly as written, e.g. `AAPL: 0.4`.

        ### Optional Fields

        | Field | Type | Default | Description |
        |-------|------|---------|-------------|
        | `consensus_method` | enum | `ORCHESTRATOR_OVERRIDE` | How the final decision is made: `MAJORITY` (the agents vote, no extra model call) or `ORCHESTRATOR_OVERRIDE` (a further model weighs their arguments). **Write it deliberately.** Omitting it does not fail — it selects `ORCHESTRATOR_OVERRIDE`, which adds one model call per evaluation round on top of the agents', so a backtest costs and takes noticeably more than the same strategy under `MAJORITY`. |
        | `version` | string | — | Version identifier (e.g., `v1.0`) |
        | `consensus_threshold` | decimal | `0.6` | In MAJORITY mode, HOLD agents with confidence below this value abstain from voting (0.0–1.0). **Asymmetric: BUY and SELL votes are never gated** — only HOLDs are dropped, so a low-confidence BUY outvotes a low-confidence HOLD |
        | `reserve_cash_percentage` | decimal | `0.2` | Minimum cash to keep in reserve (0.0–1.0). E.g., 0.2 = keep 20% cash |
        | `position_limits` | map | — | Max position size per asset as portfolio percentage (0.0–1.0). **A cap, not a target** — it limits how large a position may grow, it does not size orders. |
        | `max_order_fraction` | decimal | — | Max fraction of portfolio value a *single BUY order* may take (0.0–1.0, e.g. `0.15` = 15% per order). Applies to every BUY, including agent-sized ones. When the agents return no quantity, the platform sizes the order itself: to this fraction if set, otherwise to **25% of portfolio value** — never to the whole `position_limits` allowance. Prose like "enter in tranches" still does not size orders by itself — the agents must state a numeric quantity, or the platform default applies. The decision notes always say when the platform chose the size. |
        | `agents` | list | all 3 | Subset of AI agents to enable: `TECHNICAL`, `FUNDAMENTAL`, `SENTIMENT`. Omit to use all. |
        | `llm_model` | string | — | Pin a specific LLM model for ALL agents (e.g., `google/gemini-2.5-flash`). Disables fallback chain. |
        | `llm_models` | map | — | Per-agent LLM model overrides. Keys: `TECHNICAL`, `FUNDAMENTAL`, `SENTIMENT`, `ORCHESTRATOR`. Takes precedence over `llm_model`. |
        | `llm_temperature` | decimal | agent default | Temperature for LLM sampling (0.0–2.0). Lower = more deterministic. |
        | `position_cooldown` | duration | — | Cooldown after trade (e.g., `14d`, `2w`, `1h`). Skips consensus entirely for that ticker until elapsed. Prevents whipsawing. |
        | `portfolio_snapshot_interval` | duration | `6h` | How often to value the portfolio *between* consensus evaluations (e.g., `1h`, `6h`, `1d`). Costs nothing in AI calls — it only prices existing holdings — but a finer interval gives a smoother equity curve. |

        ### The minimum trade value

        Orders below the platform's per-asset-class minimum — **$100 for stocks and crypto by
        default** — convert to HOLD instead of executing (outcome `BELOW_MINIMUM_TRADE_VALUE`).
        Size your numbers so a BUY can actually clear it: the largest order the engine will ever
        attempt for a ticker is `cash × (1 − reserve_cash_percentage) × position_limit`, and if
        that sits below the floor, every BUY for that ticker converts to HOLD forever.

        ## Dual Schedule: live_schedule + backtest_schedule

        Strategies define **separate schedules** for live trading and backtesting. This allows frequent
        live evaluations while keeping backtests cost-efficient with fewer AI calls.

        ### Stock Mode Schedule

        ```yaml
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"

        backtest_schedule:                        # Optional — omit it and see the rule below
          timezone: "America/New_York"
          trading_days: [WEDNESDAY]               # Mid-week avoids Monday/Friday anomalies
          evaluation_times: ["10:00"]             # Post-open, after volatility settles
          market_open: "09:30"
          market_close: "16:00"
        ```

        | Field | Default | Description |
        |-------|---------|-------------|
        | `timezone` | `America/New_York` | YOUR timezone (IANA format). Every other field below is expressed in it. |
        | `trading_days` | `[MONDAY..FRIDAY]` | Days to trade. Controls evaluation frequency. |
        | `evaluation_times` | — | **Required.** Times to evaluate in HH:MM format. |
        | `market_open` | `09:30` | Start of YOUR window — not the exchange's hours |
        | `market_close` | `16:00` | End of YOUR window — not the exchange's hours |

        #### If you omit `backtest_schedule`

        It is **derived from your `live_schedule`**, thinned to one evaluation per week so a
        backtest costs a fraction of the LLM calls a live cadence would:

        - **timezone, `market_open`, `market_close`, and the evaluation time** — copied from your
          live schedule. The time is *yours*, not a fixed literal: a live schedule evaluating at
          16:00 Europe/Amsterdam backtests at 16:00 Amsterdam.
        - **day** — Wednesday when your live `trading_days` include it, otherwise your first
          live trading day.

        So a Mon–Fri live schedule backtests on Wednesdays only. That is deliberate — but it
        means a ten-trading-day window evaluates **twice**, not ten times. Write an explicit
        `backtest_schedule` if you want a denser backtest, and use `estimate_backtest_cost` to
        see how many evaluations you are actually buying before you create the run.

        (Older versions pinned a hard-coded `Wednesday 10:00` regardless of timezone, which is
        04:00 in New York for a European clock and was rejected by the venue check below. If you
        have read that anywhere, it is out of date.)

        ### Your schedule is validated against the venue

        Every stock tradable here is US-listed, so **each evaluation must land inside the US
        equity session — 09:30–16:00 America/New_York, Monday to Friday.** A schedule that
        cannot fire on that venue is rejected at `create_strategy`, at `update_strategy`, and
        again at `create_simulation`.

        You do **not** have to write your schedule in Eastern time. Times are converted, so a
        European or Asian clock is fine as long as it converts into the session:

        - `Europe/Paris` `16:00` → 10:00 ET ✅
        - `Asia/Tokyo` `10:00` → 21:00 ET the previous evening ❌ (the market has closed)
        - `Asia/Tokyo` `23:30` → 09:30/10:30 ET ✅

        Two things that catch people out:

        1. **`market_open`/`market_close` are your own window, in your own timezone.** They are
           not the exchange's hours and widening them does not extend trading. If you pick a
           late local evaluation time, widen them to contain it — otherwise the schedule is
           rejected before the venue check even runs.
        2. **Days are converted too.** `SATURDAY` `02:00` in Tokyo is *Friday* midday in New
           York and is accepted; `MONDAY` `10:00` in Tokyo is *Sunday* evening and is not.

        The check is run against both winter and summer offsets, so a time that only works for
        half the year is rejected rather than failing silently at the DST switch. **Exchange
        holidays are not modelled** — a schedule landing on Thanksgiving still passes.

        Crypto strategies are exempt: crypto trades 24/7, so any hours and any days are valid.

        ### Crypto Mode Schedule

        ```yaml
        live_schedule:
          interval: 1h                            # Human-readable duration

        backtest_schedule:                        # Optional — default: 1w
          interval: 1w
        ```

        The `interval` field supports human-readable durations: `30s`, `5m`, `10min`, `1h`, `6h`, `1d`, `1w`.
        When `backtest_schedule` is omitted, it defaults to `1w` (weekly).

        ## Trading Modes

        The mode is **automatically inferred** from which ticker fields you configure:

        - **Crypto Mode** — Only `crypto_tickers` specified. Uses `live_schedule.interval` for 24/7 evaluation.
        - **Stock Mode** — `stock_tickers` present. Uses `live_schedule` for scheduled evaluation.

        ## Strategy Name

        **Which name wins depends on how the strategy is created.** When you call `create_strategy`
        yourself, the `name` parameter is the strategy name and the `# H1` heading is body text like
        any other — so put the name you want in `name`, and keep the H1 matching it to avoid a
        strategy that lists under one name and reads as another. Only strategies produced by market
        research take their name from the first `# H1` after the closing `---`, and only those get
        the month/year appended automatically.

        Either way, name it for **what it trades and why** — the asset or sector plus the thesis —
        not the indicator technique.

        - GOOD: `Defense Primes — Budget Surge Momentum`, `Semiconductor Oversold Recovery`, `Bitcoin Support-Level Bounce`
        - AVOID: `Moving Average Crossover Strategy`, `MACD Signal Cross Strategy` (generic — every basket collapses to the same name)

        Research-generated strategies that emit a bare technique name are auto-prefixed with their
        lead tickers as a fallback.

        ## Markdown Body

        The markdown body after the `---` closing delimiter is injected into the prompt of
        **every agent** — Technical, Fundamental, Sentiment and the Orchestrator. Under
        `MAJORITY` there is no Orchestrator call at all, so treating the body as
        Orchestrator-only would mean writing it off entirely; it is in fact the text your
        voting agents read.

        One consequence worth knowing: a body written purely in technical terms is also read
        by the Fundamental and Sentiment agents, who cannot evaluate it and tend to answer
        with low-confidence HOLDs — which `consensus_threshold` then drops (see below). If you
        want three real opinions, give all three something they can act on.

        It should describe:

        - **Decision-making framework** — When to BUY, SELL, or HOLD
        - **Risk management rules** — Position sizing, stop-loss levels, drawdown limits
        - **Market context rules** — How to adjust in bull/bear/sideways markets
        - **Agent weighting** — Which agents to prioritize in different conditions

        The Orchestrator reads this guidance when synthesizing agent recommendations into final decisions.

        ## Agent Selection

        By default, all 3 specialized agents participate in every consensus round: Technical, Fundamental,
        and Sentiment. The Orchestrator always runs.

        Use the optional `agents` field to limit which agents participate. This saves LLM tokens when
        certain agents aren't relevant to your strategy.

        ```yaml
        agents:
          - TECHNICAL
          - FUNDAMENTAL
        ```

        Valid values: `TECHNICAL`, `FUNDAMENTAL`, `SENTIMENT`.
        `ORCHESTRATOR` always runs and should **not** be listed.

        ## Controlling Evaluation Frequency

        **Stock Mode** — Use `trading_days` and `evaluation_times` within `live_schedule`:
        - Weekly: `trading_days: [MONDAY]` — evaluate once per week on Monday
        - Twice per week: `trading_days: [MONDAY, THURSDAY]`
        - Daily (default): `trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]`

        **Crypto Mode** — Use `interval` within `live_schedule`:
        - Hourly: `interval: 1h`
        - Daily: `interval: 1d`
        - Weekly: `interval: 1w`

        ## Minimal Example (Crypto)

        ```yaml
        ---
        consensus_method: ORCHESTRATOR_OVERRIDE
        crypto_tickers:
          - BTC
          - ETH
        live_schedule:
          interval: 1h
        reserve_cash_percentage: 0.2
        position_limits:
          BTC: 0.4
          ETH: 0.3
        # position_cooldown: 7d  # Optional: skip consensus for a ticker after BUY/SELL
        ---
        ```

        ```markdown
        # Trading Strategy

        ## BUY when:
        - RSI < 30 (oversold) and at least 3 agents agree
        - MACD shows bullish crossover with rising histogram

        ## SELL when:
        - RSI > 70 (overbought) or agents flag high drawdown
        - Position is up 15%+ and momentum is fading

        ## HOLD when:
        - Agents are split or average confidence < 0.5
        - Market conditions are unclear
        ```

        ## Stock Mode Example

        ```yaml
        ---
        consensus_method: MAJORITY
        consensus_threshold: 0.6
        stock_tickers:
          - AAPL
          - MSFT
          - NVDA
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
        backtest_schedule:
          timezone: "America/New_York"
          trading_days: [WEDNESDAY]
          evaluation_times: ["10:00"]
        reserve_cash_percentage: 0.25
        position_limits:
          AAPL: 0.3
          MSFT: 0.3
          NVDA: 0.25
        # position_cooldown: 14d  # Optional: skip consensus for a ticker after BUY/SELL
        ---
        ```
        """;

    static final String MARKET_RESEARCH_INSTRUCTIONS = """
        # Market Research Instructions Guide

        > **Behind a feature flag.** Market research is gated by the `market-research` feature flag,
        > which is off by default on a self-hosted instance. If the `*_market_research` tools are
        > not listed, nothing in this guide can be executed until an administrator switches the flag
        > on — see the `feature-flags` guide, and note that on a self-hosted instance the signed-in
        > user is that administrator. You can also skip research entirely: write a strategy yourself
        > (see the `strategy-format` guide), backtest it, and trade it live through your own broker.
        >
        > Research quality depends on the conversational model behind it — its agents reason through
        > platform tool calls, so a weak or unconfigured model produces weak verdicts. Check the
        > `get_llm_status` tool if results look thin.

        Market research instructions guide the AI agents on **how** to find trading opportunities.
        They are passed to the `create_market_research` tool as the `markdownInstructions` parameter.

        ## What Are Research Instructions?

        Instructions tell the AI research agents what patterns, signals, and criteria to look for
        when scanning markets. Without instructions, agents default to generic analysis of the
        target assets. With good instructions, agents actively search for specific opportunities.

        ## How Instructions Are Used

        1. The **news-sentiment-analyst** agent uses them to guide web searches
        2. The **strategy-designer** considers them when creating trading strategies
        3. Agents use them to discover NEW tickers beyond the provided target assets
        4. Instructions focus the entire research pipeline on specific opportunity types

        ## Writing Effective Instructions

        ### Be Specific About Patterns

        Bad: "Find good stocks to buy"
        Good: "Find stocks showing unusual volume (3x above 20-day average) without significant
        price movement, suggesting institutional accumulation"

        ### Set Measurable Criteria

        Bad: "Look for oversold stocks"
        Good: "Search for profitable tech companies down 40%+ from 52-week highs with P/E ratios
        below sector average, showing RSI divergence on the daily chart"

        ### Allow Flexibility Beyond Target Assets

        Bad: "Analyze BTC and ETH"
        Good: "Start with BTC and ETH as reference points, but search for any cryptocurrency
        with fresh partnership announcements or major exchange listings in the last 7 days"

        ### Specify Time Horizons

        Bad: "Find momentum plays"
        Good: "Identify stocks breaking out of 3-month consolidation ranges on high volume,
        targeting 2-4 week swing trades"

        ## Example Instructions by Theme

        ### Partnership & Catalyst Driven
        ```
        Look for cryptocurrencies with major partnerships or integrations announced
        within the last 7 days. Focus on:
        - Payment processor integrations (PayPal, Visa, Mastercard)
        - Major exchange listings
        - Government or institutional adoption announcements
        - DeFi protocol integrations with significant TVL

        Target assets are examples — prioritize any crypto with fresh, market-moving news.
        ```

        ### Volume & Momentum Anomalies
        ```
        Search for stocks showing unusual volume patterns indicating institutional accumulation:
        - Volume 3x above 20-day average without significant price movement
        - Breakouts from 3-month consolidation ranges on high volume
        - Technology sector stocks with RSI divergence patterns
        - Companies with recent insider buying clusters

        Start with semiconductors but expand to entire tech sector if similar patterns appear.
        ```

        ### Sector Rotation
        ```
        Identify sectors showing early rotation signals:
        - Improving relative strength vs SPY over past 2 weeks
        - ETFs breaking above 50-day moving averages after extended downtrends
        - Sectors benefiting from recent economic data or Fed policy changes
        - Sector leaders showing accumulation while sector ETF consolidates
        ```

        ### Contrarian Value
        ```
        Search for oversold quality companies showing reversal signals:
        - Stocks down 40%+ from 52-week highs but with stable fundamentals
        - P/E ratios below sector average despite growth
        - Recent director/insider buying after extended decline
        - Forming potential double bottoms or bullish divergences
        ```

        ## Best Practices

        1. **Describe patterns, not conclusions** — Tell agents what to look for, not what to decide
        2. **Use measurable thresholds** — "volume 3x average" is better than "high volume"
        3. **Explain the why** — Include reasoning for why these patterns indicate opportunities
        4. **Allow discovery** — Make clear that agents should find the BEST matches, not just analyze provided tickers
        5. **Set time horizons** — Specify if looking for short-term catalysts or longer-term trends
        6. **Be concise** — Instructions should be focused and actionable, not a novel
        """;

    static final String STRATEGY_CHEATSHEET = """
        # Strategy Cheatsheet

        Quick reference for writing strategies. For the full specification, see the `strategy-format` guide.

        ## How It Works

        - Frontmatter (`---` block) = machine-parsed config. Exact field names and values matter.
        - Markdown body (after `---`) = free-form instructions. Write naturally — the AI agents read this to decide how to trade.

        ---

        ## Frontmatter Config

        ### Tickers (required — at least one list)

        There is no `tickers:` field. Name the asset class explicitly; the trading mode is inferred
        from which list you use.

        ```yaml
        crypto_tickers: [BTC, ETH]        # Crypto Mode — 24/7, uses `interval` schedules
        stock_tickers: [AAPL, MSFT]       # Stock Mode — uses trading_days + evaluation_times
        ```

        US tickers are bare. `position_limits` keys must match the ticker exactly as written.

        > ⚠️ **Non-US exchange-qualified tickers are refused at strategy creation.** Which venues
        > you can trade is a property of the broker you have attached, not of the strategy format,
        > and no currently attachable broker reaches a non-US venue — nor does any price source
        > cover one. A strategy containing `RHM:XETR` or `AZN:XLON` is therefore rejected outright.
        > A US suffix such as `AAPL:XNAS` is accepted and normalises to the bare `AAPL`; write the
        > bare ticker instead, since it adds nothing and `position_limits` keys must match it.
        >
        > The refusal used to depend on the root symbol having no US listing, which meant
        > `AZN:XLON` slipped through on a broker confirming `AZN` — AstraZeneca's New York
        > receipt — and then backtested against that instrument in USD while still displaying
        > `AZN:XLON`. That is fixed: the root is no longer accepted as evidence about the venue.
        >
        > To trade a European or Asian company today, use its US listing. Run `find_ticker` with
        > the company name: it resolves the tradable route, tells you whether that route is a
        > depositary receipt rather than the home listing, and reports the home listing separately
        > so you know what you are not getting. Do not copy an ADR symbol from documentation —
        > whether a given receipt is tradable varies by broker and changes over time, so let
        > `find_ticker` check it against the broker you actually have.

        ### Stock Schedule

        ```yaml
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
        ```

        `trading_days` accepts full names (MONDAY) or 3-letter abbreviations (MON). Omit for Mon–Fri default.

        ### Crypto Schedule

        ```yaml
        live_schedule:
          interval: 1h
        ```

        Valid intervals: `30s`, `5m`, `1h`, `6h`, `1d`, `1w`

        ### Backtest Schedule

        Optional. Omit it and stocks inherit your live schedule's timezone, window and
        evaluation TIME, thinned to one day a week (Wednesday if you trade it, else your first
        live trading day); crypto defaults to `1w`. So Mon–Fri live → Wednesdays only in
        backtest — two evaluations in a ten-day window, not ten. `estimate_backtest_cost` shows
        the real count before you commit.

        ```yaml
        backtest_schedule:
          timezone: "America/New_York"
          trading_days: [WEDNESDAY]
          evaluation_times: ["10:00"]
        ```

        ### Consensus & Agents

        ```yaml
        consensus_method: MAJORITY
        ```

        Consensus methods:
        - `MAJORITY` — agent votes determine the final action directly; no orchestrator LLM call. Ties default to HOLD. HOLD agents with confidence below `consensus_threshold` abstain (don't vote); BUY and SELL votes are not gated at all.
        - `ORCHESTRATOR_OVERRIDE` — an LLM orchestrator reviews all agent recommendations and makes the final decision.

        All 3 agents run by default (TECHNICAL, FUNDAMENTAL, SENTIMENT).
        Only add `agents:` to use a subset:

        ```yaml
        agents:
          - TECHNICAL
          - FUNDAMENTAL
        ```

        ORCHESTRATOR runs only with `ORCHESTRATOR_OVERRIDE` — don't list it in agents.

        An unrecognised agent name is rejected when the strategy is saved. It used to be dropped
        silently, and dropping the only entry left an empty list — which means "use all three", so
        one typo turned a deliberately cheap single-agent strategy into a three-agent one at three
        times the cost per evaluation.

        #### Listing an agent is not a guarantee it votes

        FUNDAMENTAL and SENTIMENT need stored news to work from, and abstain without it:

        | Agent | Needs, per evaluated period | Below that |
        |---|---|---|
        | TECHNICAL | price bars only | always votes |
        | FUNDAMENTAL | at least 2 stored news events | abstains |
        | SENTIMENT | at least 3 stored news/social events | abstains |

        Crypto backtests usually have no stored news for the period, so a strategy configured for
        three-agent consensus can run its whole course on TECHNICAL alone, with the orchestrator
        largely restating that single vote. Nothing fails and nothing warns you at creation time.

        Two ways to check: `get_strategy` reports `effectiveAgents` plus an
        `agentAvailabilityNote`, and `get_consensus_with_agents` shows, per decision, who actually
        voted and names anyone who abstained in its `notes`. If you want three real opinions,
        back-fill news for the period first — or configure `agents: [TECHNICAL]` deliberately and
        pay for one.

        ### Dividend Awareness

        When your strategy includes dividend-paying stocks, QuorumTrading will flag this
        on the strategy page, in benchmark comparisons, and in MCP tool responses.
        Backtest results reflect price returns only — dividend income is not tracked.

        ### Other Frontmatter Fields

        - `consensus_threshold: 0.6` — 0.0 to 1.0; HOLD agents below this confidence abstain in MAJORITY
          voting. It gates HOLDs ONLY: a BUY or SELL at the same confidence still votes. Raising the
          threshold therefore makes the strategy *more* willing to trade, not less
        - `reserve_cash_percentage: 0.2` — 0.0 to 1.0, cash reserve
        - `position_limits:` — ticker to max allocation (e.g., `AAPL: 0.25`). A cap on position size, not an order size
        - `max_order_fraction: 0.15` — max fraction of portfolio value per single BUY order. Unset, a
          quantity-less BUY is platform-sized to 25% of portfolio value (not the whole allowance)
        - Orders under the minimum trade value ($100 per order by default, stocks and crypto alike)
          convert to HOLD — keep `cash × (1 − reserve) × limit` above it or nothing ever trades
        - `position_cooldown:` — skip consensus after BUY/SELL (e.g., `14d`, `2w`, `1h`)
        - `llm_model:` — pin a specific model. Ids must come from the engine you actually run:
          OpenRouter ids (e.g., `anthropic/claude-sonnet-5.5`) on the hosted edition, the local
          server's own ids (e.g., `llama3.2`, whatever `get_available_models` lists) on a local
          engine — or omit it and the engine's default is used
        - `llm_models:` — per-agent model overrides (`TECHNICAL: "model-id"`)

        ---

        ## Agent Guidance (Markdown Body)

        The markdown body is free-form text that the AI agents read to understand your intent.
        Each agent looks for different kinds of guidance:

        ### Technical Agent
        Mention indicators and thresholds. These are the ONLY indicators the platform computes —
        naming any other one does not add it, and the agent will improvise a substitute rather
        than tell you the rule could not be evaluated:

        - **RSI-14** — 0–100. <30 oversold, >70 overbought
        - **MACD** — 12/26 EMAs with a 9-period signal line. Above signal line = bullish
        - **SMA-50 / SMA-200** — golden cross when SMA-50 crosses above SMA-200
        - **EMA-50 / EMA-200** — exponential equivalents of the above
        - **Bollinger Bands** — 2 standard deviations around a 20-period SMA; price below the
          lower band = oversold. The middle band IS the 20-period mean — write a rule against
          `Bollinger middle band`, not against a "20-day SMA", which does not exist here

        ### Fundamental Agent
        Describe what financial data matters: earnings surprises, P/E ratios, revenue growth, sector trends, macro conditions.

        ### Sentiment Agent
        Guide what sentiment sources to weigh: news headlines, social media buzz, fear & greed index, analyst upgrades/downgrades.
        """;

    static final String BACKTESTING = """
        # Backtesting: the run lifecycle

        A backtest replays market history against a strategy. This guide covers the part that
        surprises people: what happens *after* you press go.

        ## Creating a simulation starts it

        There is no dormant state. `create_simulation` begins the run as soon as its market data is
        ready — usually within seconds — and every evaluation round after that spends model calls.
        You cannot create one, inspect the cost, and then decide.

        **So price it first.** `estimate_backtest_cost` takes the same strategy and dates, respects
        the strategy's `backtest_schedule`, and creates nothing. If it reports zero trading days, the
        run would decide nothing at all — fix the window or the schedule instead of creating it.

        `start_simulation` exists only for the case where a run did *not* start itself (backfill
        stalled and it is still PENDING, or someone paused it). It is not a routine second step.

        ## The state machine

        ```
        PENDING → WAITING_FOR_DATA → SIMULATING → LIVE → COMPLETED
                                              ↘        ↘
                                                 FAILED
        ```

        | State | Meaning |
        |-------|---------|
        | `PENDING` | Created, not yet started |
        | `WAITING_FOR_DATA` | Backfilling the price history the run needs |
        | `SIMULATING` | Replaying history, evaluating on the strategy's `backtest_schedule` |
        | `LIVE` | Caught up with the present; now evaluating forward on `live_schedule` |
        | `COMPLETED` | Reached its end date |
        | `FAILED` | Stopped; read `failureReason` |

        ⚠️ **`LIVE` is a state, not a statement about money.** A mock-broker backtest that reaches
        the present reports `status: LIVE` too. The field that answers "is real money at stake" is
        **`realMoney`** — true only for an `ALPACA_LIVE` or `SAXO_LIVE` broker account. Never infer
        real money from `status`, and never from `tradingMode` alone.

        ## Polling a run

        Runs are asynchronous and nothing notifies you when they finish. Call `get_simulation`:

        - `progress` is coarse (a handful of steps) and can sit still for minutes while a model call
          is in flight. A frozen `progress` is not evidence of a hang.
        - `paused` means *you must resume this*. A run that has not started yet reports
          `paused: false` — if you see `paused: true`, `resume_simulation` is the right call.
        - To see that work is actually happening, call `get_consensus_with_agents`: new decision rows
          appearing is the reliable liveness signal.
        - When a run does not seem to progress, read the `health` block on `get_simulation`. Its
          verdict separates the cases that look identical from the outside: STALLED (rounds are
          running but every one is skipped — `topSkipReasons` says why, most often a slow or dead
          LLM engine), PAUSED_EXTERNAL (the system paused it; `externalPauseReason` says why),
          IDLE (nothing recorded yet), HEALTHY (it is deciding — `lastDecisionAt` moves).
        - `externalPauseReason` vocabulary: `BUDGET_DEPLETED` (top up the AI budget),
          `DATA_GAP_DETECTED` (a re-backfill was requested), `LLM_FAILURE_DETECTED` (fix or switch
          the engine), `EXECUTION_STALLED` (the run stopped making progress and no executor holds
          it — a hung or vanished evaluation). All four are recoverable: `resume_simulation`
          continues from the last decision. A stalled run is never completed for you and its
          progress is left where it actually got to, so the number you see is the truth about how
          much of the period was simulated.

        On a free local engine, expect roughly 20-40 seconds per agent call (small models on
        modest hardware can take minutes — `get_llm_status` reports the observed average).
        Multiply by `totalLLMCalls` from the estimate to get a realistic wall-clock figure — that,
        not money, is what a keyless backtest costs you; `estimate_backtest_cost` now projects it
        as `estimatedWallClockSeconds` once the engine has a few completed calls.

        **Parallel runs share one engine.** A local model server answers one call at a time, so
        two simulations running side by side do not halve the time — they double every call's
        queue. If both start timing out, `pause_simulation` on one frees the engine for the
        other; resume it afterwards.

        ## When it finishes

        Ask `get_simulation` for `noDecisionsReason` **before** you read any return figure. A run
        that skipped every evaluation still completes with a 0.00% return, which is byte-identical
        to one that evaluated properly and chose to hold. When `noDecisionsReason` is set, the
        reported return is not a result.

        Then check `decisionsNotExecuted` — the complement of `noDecisionsReason`. It counts the
        decisions where the council said BUY or SELL and **nothing traded**: refused by the
        strategy's own risk controls, below the minimum trade value, or rejected by the broker.
        A run can decide plenty and trade nothing, and its headline return is then not the
        strategy's return. When this count is a large share of the decisions, read the decision
        rows (`query_consensus_decisions`) before trusting the number.

        ## What executionOutcome means

        Every decision row carries an `executionOutcome` that says what actually happened, not
        just what was asked:

        | Value | Meaning |
        |---|---|
        | `EXECUTED` | The trade was filled by the broker |
        | `NO_TRADE_REQUIRED` | The council chose to HOLD — or SELL with nothing held. Genuinely nothing to do |
        | `BLOCKED_BY_RISK_LIMITS` | The council said BUY; the strategy's `position_limits` or `reserve_cash_percentage` left no room. The notes name the binding control and its numbers |
        | `BELOW_MINIMUM_TRADE_VALUE` | The council wanted to trade; the order was too small to execute (under the per-asset-class minimum, or under one whole share) |
        | `REJECTED` | The broker (or pre-flight sizing) refused the order — insufficient funds, missing market data |
        | `SKIPPED` | The evaluation never produced a tradable decision (data gap, cooldown) |
        | `PENDING` | Not executed yet |

        The distinction that matters for trust: `NO_TRADE_REQUIRED` is a choice, the blocked and
        below-minimum outcomes are refusals. Before these values existed, both wore the same label.

        Then see the `Reading Your Results` table in the `platform-overview` guide, which maps each
        question you might have to the tool that answers it.
        """;

    static final String COST_AND_MODELS = """
        # What things cost, and which model runs them

        ## The unit is US dollars

        Every money figure on this platform is USD. The cost tools each format it differently —
        `estimate_backtest_cost` returns a bare 4-decimal string, `get_simulation_cost` a
        `$`-prefixed 2-decimal one, per-decision rows an 8-decimal `llmCostUsd` — but they are all
        the same currency.

        ## On a free engine, money is the wrong question

        If your LLM engine is a local CLI or a local model server (see `get_llm_status`), model calls
        cost **nothing in money**, and every estimate will read `0.0000` regardless of how large the
        run is. What a free engine costs is **time**: roughly 20-40 seconds per agent call.

        So read `totalLLMCalls`, not `estimatedCost`. A 12-call run is a few minutes; a 180-call run
        is most of an hour. The dollar figure cannot tell those apart.

        ## Estimate versus actual

        They count different things, deliberately:

        | | Counts |
        |---|---|
        | `estimate_backtest_cost` → `totalConsensusRounds` | scheduled **evaluation dates** before the run |
        | `estimate_backtest_cost` → `totalLLMCalls` | rounds × tickers × agents, assuming every agent votes |
        | `get_simulation_cost` → `decisionCount` | **billable** rounds that actually happened |
        | `query_consensus_decisions` → `count` | all recorded rounds, including skipped ones |

        The estimate aims to be an upper bound: it counts calendar days the exchange may close for a
        holiday, so a stock backtest usually runs slightly cheaper than quoted.

        **An abstention does not save you a call.** It is tempting to assume a strategy whose
        `agentAvailabilityNote` warns that FUNDAMENTAL and SENTIMENT will abstain uses far fewer
        calls. It does not: a data-gap abstention is decided before the call, but a
        *confidence-threshold* abstention happens after — the agent is asked, answers, and its vote
        is then dropped for sitting below `consensus_threshold`. You pay for the answer either way.
        Measured on a three-agent strategy where two abstained on almost every round, the run still
        used 14 of a possible 15 calls.

        ## The spend ceiling

        Every account carries a platform AI-spend ceiling (a welcome balance), and it applies to
        self-hosted installs too. It caps *platform-tracked* spend, not your provider's billing.
        `GET /api/v1/budget` reports the limit, the current spend and what remains.

        A free local engine should not draw against it, and per-run costs on one do read `$0.00`.
        Do not be alarmed if the ledger nevertheless shows a small non-zero `currentSpend`: the
        balance is per account and survives engine changes, so it also carries anything spent before
        you switched to a free engine. No tool attributes a spend figure back to the run that caused
        it, so treat the ceiling as a coarse guard rather than a per-run accounting.

        ## Which model actually runs your agents

        These tools now agree, because they all read one computed value:

        - **`get_model_preferences`** — the *effective* model per context: what the active engine
          will actually run, plus where the choice came from. Start here.
        - **`get_llm_status`** — live-probes the selected engine. Use it to check the engine is up.
        - **`estimate_backtest_cost`** → `modelName` — what the estimator priced against.

        `get_available_models` lists the **selected engine's** catalogue — a local server's own
        models, a CLI tool's aliases, or OpenRouter's, depending on what you chose with
        `choose_llm_provider`. It is not an OpenRouter-only listing.

        Model preferences are validated against that same catalogue, so a preference the engine
        cannot serve is refused at save time, with a list of models that would work. If you switch
        engines afterwards, the switch tells you which stored preferences and strategy pins stopped
        working — they are not silently repaired, because picking a replacement for you is exactly
        the guesswork this platform avoids.

        A strategy can also pin a model per run via `llm_model` / `llm_models` frontmatter — **both
        are validated** — see the `strategy-format` guide.
        """;

    static final String INVESTMENT_PLANS = """
        # Recurring investment plans (DCA)

        A plan contributes a fixed amount on one day each month. Each tick picks one of *your*
        strategies, then either tops up an existing LIVE simulation or starts a new one.

        ## The one thing to check first

        **Automatic execution is disabled by default on every instance.** A plan with
        `status: ACTIVE` and a future `nextExecutionAt` will still never fire on its own if the
        scheduler is off — two confident forward-looking claims against one retracting boolean.

        Read **`automaticExecutionEnabled`** on any plan payload (or `automaticInvestmentsEnabled`
        from `GET /api/v1/instance`). When it is false, the only thing that funds a plan is you
        calling `execute_investment_plan_now`. A plan that silently never invests looks identical to
        one that is working.

        ## Creating one

        `create_investment_plan` takes:

        | Field | Notes |
        |-------|-------|
        | `brokerAccountId` | where the cash comes from |
        | `budgetMode` | `FIXED_AMOUNT` (exact, or skip), `FIXED_OR_PARTIAL` (deploy what is available), `ALL_AVAILABLE` |
        | `amount` | the monthly contribution |
        | `dayOfMonth` | 1-28 (28 is the highest day every month has) |
        | `pickerPolicy` | `AI_PICK` is the only accepted value — see the warning below |
        | `confirmLive` | **required true** for a live broker account |

        ⚠️ **`AI_PICK` overpromises.** The default picker runs no model at all: it chooses the
        most-recently-updated active strategy in your pool and reports exactly that in the
        allocation's `pickerReasoning`, at zero cost. `riskTolerance` and `maxStrategiesActive` are
        stored and echoed back but the default picker does not act on them. The ticker allow/block
        lists *are* enforced, as a hard filter after the picker returns.

        ⚠️ **Any strategy you own is eligible.** Creating a strategy immediately puts it in the pool
        an ACTIVE plan can select from — including one you set up weeks ago. If you want a plan
        confined to particular assets, set `allowedTickers`.

        ## Updating one

        `update_investment_plan` is a genuine partial update: a field you omit keeps its stored
        value, including individual preferences. To clear a ticker list, pass an empty string.

        ## Reading what happened

        Every tick writes an `InvestmentAllocation` row whether it funded, skipped or failed — that
        row, not the plan, is the audit trail.

        - `list_investment_allocations` / `get_investment_allocation`
        - A same-day repeat reports `SKIPPED_ALREADY_EXECUTED_TODAY` and names the prior allocation.
          Its explanation arrives in `failureReason`, but nothing failed — read the `status` first.
        - An allocation with action `NEW_SIMULATION` has `contributionId: null` on purpose. The cash
          became the new simulation's *initial capital*, and initial capital is deliberately not a
          contribution — so the funded simulation reports `contributions: []` while `totalInvested`
          shows the money. Only top-ups into an existing simulation create contribution rows.

        ## Notifications

        Each execution dispatches a notification. `list_notifications` and
        `count_unread_notifications` read the in-app inbox.

        ⚠️ **Outgoing email is disabled by default**, so an EMAIL channel listed in
        `get_notification_preferences` delivers nothing until an operator enables it. `GENERIC`
        notifications are email-only, which means on a default install they reach you on no channel.

        ## Deleting one

        `delete_investment_plan` terminates the plan and keeps its allocations for audit. It does
        **not** stop the simulations the plan created, and it does not release the broker cash
        reserved for them. To clean up fully, find those simulations (their names carry the plan's
        UUID) and call `force_end_simulation` on each, then confirm the broker's `reservedBalance`
        with `get_broker_account`.
        """;

    static final String BENCHMARKS_AND_CHALLENGES = """
        # Benchmarks and challenges

        ## Did the AI beat just buying and holding?

        That is what benchmark **overlays** answer: a buy-and-hold curve for a reference asset, over
        the same window and the same starting capital as your run.

        1. Put your simulations in a benchmark with `compose_benchmark` — it derives the dates from
           the simulations themselves, so they need not match.
        2. Call `get_benchmark` for the ranked table of member runs.
        3. Call `get_benchmark_overlays` for the reference-asset curve to compare against. Its
           `initialCapital` defaults to the first simulation's initial cash, so the comparison is
           like-for-like unless you override it.

        `create_benchmark` is the other entry point: it takes *strategies* and creates and starts a
        fresh simulation for each, on a window you supply. That means it spends model calls — price
        it with `estimate_backtest_cost` first.

        Pass **two or more** strategies. One strategy is accepted but produces no benchmark — there
        is nothing to rank it against — so you get a standalone simulation and a null `id`, which
        the response says plainly. That is a real run consuming real calls, so it is worth meaning
        to do.

        A benchmark's entry for a run reports the same return `get_simulation` does. If they ever
        disagree, that is a bug worth reporting, not a subtlety.

        ## Challenges

        Challenges are shared competitions on a single ticker: everyone enters a strategy, each entry
        gets a sandboxed simulation, and a leaderboard ranks them.

        🚦 **Challenges are off by default on a local instance** — gated by the `challenges`
        feature flag, not by the edition. A leaderboard needs other participants to mean anything,
        which a single-user install does not have, so the flag ships off and the challenge tools do
        not appear in the tool list at all. Switch it on from **Admin → Feature Flags** in the web
        UI, or `PUT /api/v1/admin/feature-flags/challenges` with `{"enabled":true}`; the change
        takes effect within about five seconds, no restart. See the `feature-flags` guide.

        With the flag on, a standalone install still ships only historical, already-closed
        challenges: `list_challenges` with `status=ACTIVE` returns nothing, and `join_challenge`
        reports the challenge is no longer accepting entries. There is no `create_challenge` MCP
        tool, and `POST /api/v1/challenges` is admin-only — so on your own instance you can read the
        seeded ones and little else.

        Use benchmarks to compete against yourself; that is what works on a single-user install.
        """;

    // ----- Lookup maps (must come AFTER the string constants to avoid forward-reference errors) -----

    /** Ordered map of topic slug → markdown content. Iteration order = display order. */
    public static final Map<String, String> GUIDES;

    /** Topic slug → short one-line description for listing. */
    public static final Map<String, String> DESCRIPTIONS;

    static final String FEATURE_FLAGS = """
        # Feature Flags

        Some parts of QuorumTrading can be switched off per instance. If a tool you expected is
        missing from the tool list, or a call came back saying a feature is "switched off on this
        instance", this is why — the feature exists and works, it is just not enabled here.

        ## The flags

        | Flag | Gates | Typical default |
        |---|---|---|
        | `challenges` | Public trading challenges and leaderboards (`list_challenges`, `get_challenge`, `join_challenge`) | off on production and self-hosted installs |
        | `market-research` | Market research (`create_market_research`, `execute_market_research`, and the rest) plus its hourly scheduler | on for the hosted product, off self-hosted |

        ## What a disabled flag does

        - its tools are **not advertised** in the tool list, so you will usually never see them
        - if you call one anyway (a cached tool list, or a direct call), it refuses and names the
          flag
        - the matching REST endpoints answer `403` with the same flag name
        - the web UI hides the navigation entry

        Nothing is silently degraded. A refusal always tells you which flag to switch on, so pass
        that name on to the user rather than retrying or working around it.

        ## Switching one on

        Flags live in this instance's own database, not in a configuration file, so a change takes
        effect within a few seconds and survives restarts.

        Changing a flag requires an administrator. **On a self-hosted instance the local owner is
        provisioned with the admin role at boot** — tell the user they can do this themselves:

        1. Web UI: **Admin → Feature Flags** (`/admin/feature-flags`)
        2. Or the API:
           ```
           PUT /api/v1/admin/feature-flags/challenges
           {"enabled": true}
           ```

        A flag can also be switched on for specific people only:
        ```
        PUT /api/v1/admin/feature-flags/challenges
        {"enabled": true, "enabledForEmails": ["someone@example.com"]}
        ```

        `GET /api/v1/features` reports what is currently available to the caller.

        ## Switching market research on where it ships off

        The `market-research` flag is the only thing gating research — switch it on and the tools,
        the REST endpoints and the hourly scheduler all come to life.

        Worth knowing before you do: research agents reason through platform tool calls, so their
        verdicts are only as good as the conversational model behind them. A weak or unconfigured
        model returns thin analysis rather than an obvious error, so check `get_llm_status` and
        `choose_llm_provider` first if the output looks unconvincing.
        """;

    static {
        var guides = new LinkedHashMap<String, String>();
        var descriptions = new LinkedHashMap<String, String>();

        guides.put("platform-overview", PLATFORM_OVERVIEW);
        descriptions.put("platform-overview",
            "How QuorumTrading works: strategies, simulations, AI agents, consensus methods, market"
                + " research, and portfolio management. Contains the 'Reading Your Results' table,"
                + " which maps each question about a finished run to the tool that answers it.");

        guides.put("trading-concepts", TRADING_CONCEPTS);
        descriptions.put("trading-concepts",
            "Quantitative analysis fundamentals: technical indicators, market hours, order types, backtesting pitfalls, and risk management.");

        guides.put("alpaca-setup", ALPACA_SETUP);
        descriptions.put("alpaca-setup",
            "Step-by-step guide for connecting an Alpaca broker account for paper or live trading.");

        guides.put("market-data", MARKET_DATA);
        descriptions.put("market-data",
            "Where prices come from, which assets need an API key and which do not, and how to write stock vs crypto tickers.");

        guides.put("strategy-format", STRATEGY_FORMAT);
        descriptions.put("strategy-format",
            "Complete reference for strategy YAML frontmatter fields, trading modes, and markdown body structure.");

        guides.put("market-research", MARKET_RESEARCH_INSTRUCTIONS);
        descriptions.put("market-research",
            "How to write effective market research instructions with examples and best practices."
                + " Gated by the 'market-research' feature flag, which is off by default on a"
                + " self-hosted instance.");

        guides.put("strategy-cheatsheet", STRATEGY_CHEATSHEET);
        descriptions.put("strategy-cheatsheet",
            "Quick reference for strategy frontmatter fields, schedule syntax, consensus methods, and agent guidance.");

        guides.put("backtesting", BACKTESTING);
        descriptions.put("backtesting",
            "The run lifecycle: creating a simulation starts it, what each state means, why LIVE is"
                + " not about money, how to poll a run, and what to check before trusting a result.");

        guides.put("cost-and-models", COST_AND_MODELS);
        descriptions.put("cost-and-models",
            "What a backtest costs before you commit to it, why a free local engine costs time"
                + " rather than money, how estimate and actual differ, and which model actually"
                + " runs your agents.");

        guides.put("investment-plans", INVESTMENT_PLANS);
        descriptions.put("investment-plans",
            "Recurring DCA-style plans: budget modes, how the picker really chooses, why automatic"
                + " execution is off by default, reading allocation rows, and cleaning up.");

        guides.put("benchmarks-and-challenges", BENCHMARKS_AND_CHALLENGES);
        descriptions.put("benchmarks-and-challenges",
            "Comparing a strategy against buy-and-hold with benchmark overlays, and what challenges"
                + " can and cannot do on a self-hosted instance.");

        guides.put("feature-flags", FEATURE_FLAGS);
        descriptions.put("feature-flags",
            "Why some features are switched off on this instance, and how to switch them on.");

        GUIDES = Collections.unmodifiableMap(guides);
        DESCRIPTIONS = Collections.unmodifiableMap(descriptions);
    }
}
