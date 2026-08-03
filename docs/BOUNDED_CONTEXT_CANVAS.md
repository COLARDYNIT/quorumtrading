---
validation_status: validated
last_validated: 2026-07-07
last_updated: 2026-07-12
last_generated: 2026-05-17
squad: colardyn-it
service_type: monolith
see_also:
  - docs/CAPABILITIES.md   # Live feature inventory + backend gaps. The BCC owns
                           # the DDD model (ubiquitous language, domain events,
                           # external deps). CAPABILITIES.md owns the surface
                           # listing — what works, what's hedged, what's gated.
---

# Bounded Context Canvas: QuorumTrading - AI Trading Simulation Platform

## Name
**QuorumTrading - AI Trading Backtesting & Simulation Context**

## Description
QuorumTrading is a specialized bounded context for running AI-powered trading simulations and backtesting strategies. It orchestrates multiple LLM-based trading agents (Technical, Fundamental, Sentiment) to reach consensus on trading decisions, executes those decisions in simulated environments, and tracks portfolio performance over historical market data. The system enables strategy validation without real capital at risk.

## Strategic Classification
**Core Domain** - This is the heart of the QuorumTrading platform's competitive advantage. The multi-agent consensus mechanism and time-travel simulation capabilities are unique differentiators.

## Business Decisions
- **What consensus methods are supported?** *(exactly two — the `ConsensusMethod` enum; earlier WEIGHTED/CONFIDENCE_BASED drafts were never built)*
  - MAJORITY: Agent votes decide directly — no orchestrator LLM call; ties → HOLD
  - ORCHESTRATOR_OVERRIDE: Orchestrator LLM makes final decision based on agent inputs

- **What investment types can be simulated?**
  - Cryptocurrency (BTC, ETH, ADA, SOL, XRP, etc.)
  - Stocks (TSLA, NVDA, AMD, etc.)

- **How are strategies defined?**
  - Markdown-based DSL with YAML frontmatter
  - Versioned strategy documents
  - Human-readable trading rules interpreted by LLM agents

- **What is the simulation execution model?**
  - Time-travel simulation through historical data
  - Configurable evaluation intervals (e.g., every 6 hours)
  - Sequential ticker processing per evaluation point
  - Immediate consensus execution (not batch)

- **How is portfolio state managed?**
  - In-memory broker simulation (MockBrokerAdapter)
  - Persistent snapshots at each evaluation point
  - Position tracking with average cost basis
  - Real-time market price valuations

- **What data retention policies exist?**
  - Market events: 90-day rolling window
  - Portfolio snapshots: Indefinite (linked to simulation lifecycle)
  - LLM decisions: Indefinite (audit trail)
  - Cascade delete when strategy is removed

## Ubiquitous Language

### Core Concepts
- **Strategy**: A versioned trading rule set defined in markdown, specifying consensus method, evaluation interval, and trading logic
- **Simulation**: A backtest execution of a strategy against historical data over a specific time period
- **Agent**: Specialized LLM-powered analyst (Technical, Fundamental, Sentiment, Orchestrator) providing trading recommendations
- **Consensus**: The process of aggregating agent recommendations into a single trading decision
- **AgentContext**: Market data, portfolio state, and metadata provided to agents for analysis
- **Recommendation**: An agent's analysis output (BUY/HOLD/SELL with confidence score and reasoning)

### Trading Concepts
- **Position**: Ownership of a specific symbol (quantity + average price)
- **Portfolio**: Cash + Positions at a point in time
- **PortfolioSnapshot**: Immutable record of portfolio state at an evaluation timestamp
- **ExecutionOutcome**: Result of attempting to execute a consensus decision (PENDING, EXECUTED, NO_TRADE_REQUIRED, REJECTED, SKIPPED)
- **TradeResult**: Broker's response to a trade command (execution price, filled quantity, order status)

### Decision Making
- **LLMDecision**: Individual agent recommendation with reasoning and confidence
- **ConsensusDecision**: Final trading decision after consensus aggregation, linked to all contributing LLMDecisions
- **RecommendationType**: BUY, HOLD, SELL, SKIP
- **ConsensusMethod**: Algorithm for aggregating agent recommendations

### Market Data
- **MarketEvent**: Historical price, news, or sentiment data point for a specific symbol and timestamp
- **Timeframe**: Temporal window for market data aggregation (LAST_24H, LAST_7D, LAST_30D, LAST_90D)
- **MarketDataContext**: Aggregated market events organized by ticker and timeframe
- **Symbol**: Trading ticker identifier (e.g., BTC, ETH, TSLA)

### Leaderboard Concepts
- **LeaderboardSimulation**: Simulation with portfolio value history, current ranking metrics, and color assignment for chart visualization
- **LeaderboardDataPoint**: Portfolio value at a specific timestamp for a simulation
- **ChartDataPoint**: Unified timeline point containing portfolio values for all simulations (with null for inactive periods)
- **LeaderEntry**: Top or bottom performer with ranking position, current value, and return percentage
- **Forward-Fill Interpolation**: Carrying forward last known portfolio value to fill gaps in timeline, creating continuous chart lines
- **Y-Axis Domain**: Dynamic chart range calculated as ±$10k padding around min/max portfolio values (minimum 0)

### Execution
- **SimulationStatus**: PENDING, SIMULATING, LIVE, COMPLETED, FAILED, WAITING_FOR_DATA *(updated 2026-05 — adds SIMULATING/LIVE/WAITING_FOR_DATA to the old RUNNING/PAUSED states; PAUSED is now a separate `paused` flag with `externalPauseReason` discriminator)*
- **TradeAction**: BUY or SELL (HOLD doesn't require execution)
- **ExecuteTradeCommand**: Instruction to broker with simulation context, action, symbol, quantity, timestamp
- **InvestmentType**: STOCK or CRYPTO classification

### Recurring Investment (DCA)
- **InvestmentPlan**: Recurring DCA plan owned by one user. Budget mode (FIXED_AMOUNT / FIXED_OR_PARTIAL / ALL_AVAILABLE), day-of-month 1–28, picker policy (AI_PICK in v1), per-type ticker allow/block list.
- **InvestmentPlanStatus**: ACTIVE ⇄ PAUSED → TERMINATED.
- **Contribution**: Cash injected into a LIVE simulation after start (recurring-investment top-up or manual). Aggregate child of Simulation; persisted as its own row with `(planId, contributionDate)` UNIQUE for idempotent scheduler retries.
- **InvestmentAllocation**: Immutable audit row for one plan execution attempt — success, skip (with reason), or failure. One row per tick regardless of outcome.
- **AllocationStatus**: EXECUTED, EXECUTED_PARTIAL_DEPLOY (whole-share rounding left part of the budget undeployed), SKIPPED_* (with reason), FAILED_* (with reason).
- **StrategyPicker**: Output port that chooses a strategy each plan tick. `HeuristicStrategyPicker` is the default (most-recently-updated active in user's pool); `LlmStrategyPicker` is selectable via `quorumtrading.picker.mode=llm` and falls back to the heuristic on any LLM failure.

### Multi-Broker
- **BrokerAccount**: Per-user broker credential record. Types: `MOCK`, `ALPACA_PAPER`, `ALPACA_LIVE`. Status: `ACTIVE` or `INVALID_CREDENTIALS`.
- **BrokerType / TradingMode**: A simulation's broker dictates the trading mode. BACKTEST runs always use `MockBrokerAdapter`; LIVE runs use the configured broker; multiple concurrent live sims on Alpaca are allowed.
- **ConfigurableBrokerPort**: Outbound port hiding paper/live distinction from the application layer.

### Auth & Identity
- **User**: Account owner. Identified by OIDC subject. Fields: email, displayName, providers list, joined date.
- **ApiToken**: Personal-access token for REST + MCP. Fields: `id`, `name`, `tokenPrefix`, `createdAt`, `lastUsedAt`, `expiresAt`, `revoked`. Hash-stored; one-time reveal on creation. **No per-token scopes** — a token has the same access as the user who issued it. (Corrected 2026-05 — earlier BCC draft listed scope strings; those were canvas-fixture-only.)
- **OidcProvider**: External identity provider (Google, Apple, optionally GitHub/GitLab/Microsoft).
- **DeletedAccountHash**: Tombstone for account-deletion idempotency, separate from the User row.

### Benchmarks & Challenges
- **Benchmark**: A composition of N strategies + a time range, runnable in `BACKTEST` (mock) or `LIVE` mode. Used for comparison.
- **BenchmarkEntry**: One strategy within a benchmark with its calculated return %.
- **Challenge**: Single-ticker competition with start/end dates and per-participant starting cash.
- **ChallengeLeaderboard**: Sorted list of participant portfolios by current value.

### Market Research
- **MarketResearch**: User-prompted research run targeting a set of tickers at a given interval. State: PENDING → RUNNING → COMPLETED/FAILED.
- **ResearchExecution**: One execution attempt within a market research; multiple per research.
- **ViabilityScore**: Float 0–1 produced by the Viability Assessor agent; rendered in three buckets in UI (< 0.4 pink · 0.4–0.7 amber · > 0.7 brand).
- **Auto-Backtest**: Toggle that auto-creates a backtest sim with the strategy emitted by a completed research run.

### Notifications & Budget
- **Notification**: Per-channel row (IN_APP, EMAIL). Each notification type fans out into N rows so delivery state is tracked independently per channel.
- **NotificationPreference**: Keyed by `(userId, type)`; missing rows fall back to system defaults applied at dispatch time.
- **UnsubscribeToken**: HMAC-signed per-type unsubscribe payload — no DB lookup required at validation.
- **UserBudget**: Per-user lifetime prepaid LLM balance with threshold alerts — see [`docs/CAPABILITIES.md`](CAPABILITIES.md) § 10.4 for the exact thresholds and top-up surface.

### LLM & Cost
- **ModelPreference**: Per-user × per-trading-context × optional per-agent override. The context enum is `TradingContext` in the model layer — see [`docs/CAPABILITIES.md`](CAPABILITIES.md) § 10.3 for the current context list.
- **FallbackChain**: Ordered list of LLM providers tried on failure. LocalLLM CLI default: `claude → qwen` (codex/gemini deliberately skipped as too slow). LocalLLM blacklists failing providers with exponential backoff.
- **LLMUsage**: Per-call cost record (model, tokens in/out, USD, feature). Aggregated for the usage page.

## Use Cases

### Strategy Management
1. **Create Strategy**
   - Input: Name, markdown content (with YAML frontmatter)
   - Output: Persisted strategy with UUID
   - Validation: Parse markdown to ensure valid StrategyConfig

2. **Update Strategy**
   - Input: Strategy ID, new markdown content
   - Output: New strategy version with optimistic locking
   - Side effects: Existing simulations continue with old version

3. **Delete Strategy**
   - Input: Strategy ID
   - Output: Strategy removed
   - Side effects: CASCADE deletes all simulations, decisions, and portfolio snapshots

4. **List Strategies**
   - Input: Optional pagination parameters
   - Output: List of strategies with metadata

5. **Get Strategy**
   - Input: Strategy ID
   - Output: Full strategy details including markdown content

### Simulation Lifecycle
6. **Create Simulation**
   - Input: Strategy ID, name, start date, end date, initial capital
   - Output: Simulation entity with PENDING status
   - Validation: Date range must be historical (no future simulations)

7. **Start Simulation**
   - Input: Simulation ID
   - Output: Async execution begins, status → RUNNING
   - Process:
     - Initialize broker portfolio with starting capital
     - Time-step through date range at evaluation interval
     - At each timestamp: reach consensus → execute decision → save snapshot
     - Fire WebSocket progress events
     - Calculate final metrics (return %, Sharpe ratio)
     - Mark status → COMPLETED or FAILED

8. **Pause Simulation**
   - Input: Simulation ID
   - Output: Simulation marked as paused
   - Side effects: Scheduled consensus skips paused simulations

9. **Resume Simulation**
   - Input: Simulation ID
   - Output: Simulation status → RUNNING
   - Side effects: Scheduled consensus resumes processing

10. **Delete Simulation**
    - Input: Simulation ID
    - Output: Simulation removed
    - Side effects: CASCADE deletes decisions and portfolio snapshots

11. **Get Simulation**
    - Input: Simulation ID
    - Output: Simulation details with status, progress, metrics

12. **List Simulations**
    - Input: Optional filters (status, strategy ID)
    - Output: List of simulations with summary data

### Consensus & Decision Making
13. **Reach Consensus**
    - Input: Simulation ID, analysis timestamp
    - Process:
      - Fetch latest portfolio snapshot
      - Restore broker portfolio state (critical for position preservation)
      - Fetch multi-timeframe market data
      - FOR EACH ticker in strategy:
        - Build AgentContext (portfolio, market data, timestamp)
        - Query specialized agents in parallel (Technical, Fundamental, Sentiment)
        - Save all LLMDecisions
        - Resolve the final action per the strategy's consensus method (MAJORITY vote directly, or OrchestratorAgent for ORCHESTRATOR_OVERRIDE)
        - Create ConsensusDecision with the resolved final action
        - Execute consensus decision immediately
        - Update portfolio state for next ticker
    - Output: ConsensusDecisions created for all tickers
    - Side effects: Trades executed, portfolio snapshots saved, WebSocket events fired

14. **Execute Consensus Decision**
    - Input: ConsensusDecision ID
    - Process:
      - Extract action (BUY/SELL/HOLD) from JSON response
      - For HOLD: Mark as NO_TRADE_REQUIRED, fire event
      - For BUY/SELL:
        - Create ExecuteTradeCommand
        - Submit to broker
        - If rejected: Mark ExecutionOutcome → REJECTED
        - If executed:
          - Fetch current portfolio from broker
          - Fetch current market prices for all positions
          - Create portfolio snapshot with accurate valuations
          - Mark ExecutionOutcome → EXECUTED
          - Fire portfolio change event
    - Output: ExecutionResult with trade details and snapshot ID

15. **Get Consensus with Agents**
    - Input: Consensus decision ID
    - Output: ConsensusDecision + all contributing LLMDecisions
    - Purpose: Audit trail, debugging, transparency

### Market Data Management
16. **Trigger Data Collection**
    - Input: Optional provider filter
    - Process: Invoke all active MarketDataProvider adapters
    - Output: Collection statistics (events ingested, errors)
    - Side effects: MarketEvents persisted with caching metadata

17. **Query Market Events**
    - Input: Ticker filters, time range, event type, pagination
    - Output: List of MarketEvents
    - Use case: Feed market data to agents, historical analysis

18. **Get Market Data Context**
    - Input: Simulation ID, analysis timestamp
    - Output: MarketDataContext for each ticker (organized by timeframe)
    - Purpose: Show agents exactly what market data they see during consensus

### Portfolio Analytics
19. **Query Portfolio Snapshots**
    - Input: Simulation ID, time range, pagination
    - Output: List of PortfolioSnapshots with positions and valuations
    - Use case: Chart portfolio value over time, track position changes

20. **Evaluate Portfolio Value**
    - Input: Simulation ID (scheduled job triggers for all RUNNING simulations)
    - Process:
      - Fetch current portfolio from broker
      - Fetch latest market prices for all positions
      - Calculate total value (cash + position values at market price)
      - Create portfolio snapshot
      - Fire portfolio change event
    - Output: Updated portfolio valuation
    - Frequency: Every minute via scheduled job

### Decision Querying
21. **Query LLM Decisions**
    - Input: Simulation ID, agent role filter, ticker filter, time range, pagination
    - Output: List of LLMDecisions
    - Use case: Analyze agent behavior, compare agent recommendations

22. **Query Consensus Decisions**
    - Input: Simulation ID, ticker filter, time range, execution status filter, pagination
    - Output: List of ConsensusDecisions
    - Use case: Review final decisions, filter executed trades vs rejected trades

### Real-time Updates
23. **Subscribe to Simulation Progress**
    - Protocol: WebSocket connection to /ws/stream
    - Events:
      - SimulationProgressEvent: Status changes, progress percentage
      - PortfolioChangeEvent: Portfolio value updates, position changes
      - LLMDecisionCreatedEvent: Individual agent recommendations
      - TradeExecutionEvent: Trade success/failure notifications

### Leaderboard & Comparative Analytics
28. **View Live Leaderboard**
    - Input: Optional filters (status, date range)
    - Process:
      - Fetch all non-paused simulations
      - Retrieve portfolio snapshots for each simulation
      - Calculate current portfolio values from latest snapshots
      - Rank simulations by current value (descending)
      - Identify top 3 and bottom 3 performers
      - Transform data for time-series chart visualization
      - Apply forward-fill interpolation for continuous chart lines
    - Output: LeaderboardData with ranked simulations, chart data, timeline bounds
    - Frontend: Real-time chart showing all simulation portfolio values over unified timeline
    - Purpose: Compare strategy performance, identify winning/losing strategies

29. **Subscribe to Live Leaderboard Updates**
    - Protocol: WebSocket PORTFOLIO_UPDATE messages
    - Process:
      - Frontend maintains leaderboard state from initial REST API fetch
      - On PORTFOLIO_UPDATE event:
        - Append new data point to simulation's dataPoints array
        - Recalculate currentValue and returnPercent from latest data point
        - Recalculate leader rankings from updated values
        - Update chart with new data point (forward-fill interpolation)
        - Update Y-axis domain (±$10k padding, min 0)
    - Output: Live-updating chart and sidebar rankings
    - Purpose: Real-time leaderboard tracking without polling

30. **Navigate to Simulation from Leaderboard**
    - Input: Simulation ID from leaderboard entry
    - Output: Navigate to simulation viewer at /simulations/{id}/viewer
    - Purpose: Quick access to detailed simulation analysis from leaderboard rankings

### Scheduled Jobs
24. **Trigger Scheduled Consensus**
    - Frequency: Every 1 minute
    - Process:
      - Find all RUNNING simulations (not paused)
      - For each simulation:
        - Check if evaluation interval elapsed since last consensus
        - If due: Invoke ReachConsensusUseCase with current timestamp
        - Continue even if one simulation fails
    - Output: ScheduledConsensusResult with statistics

25. **Scheduled Market Data Collection**
    - Frequency: Configurable (e.g., every 5 minutes)
    - Process: Invoke all enabled MarketDataProvider adapters
    - Output: Collection statistics
    - Providers: Alpaca, Finnhub (stocks + news), Tiingo, Saxo, RSS, Reddit adapters (Coinbase/NewsAPI/AlphaVantage removed Jan 2026)

26. **Scheduled Portfolio Evaluation**
    - Frequency: Every 1 minute
    - Process: For all RUNNING simulations, update portfolio valuation with latest market prices
    - Output: Updated portfolio snapshots, WebSocket events

27. **Scheduled Data Cleanup**
    - Frequency: Daily at 3 AM (`MarketDataCleanupJob`, cron `0 0 3 * * ?`)
    - Process: Delete MarketEvents older than 90 days
    - Output: Cleanup statistics

### Use cases added since 2025-01

The platform has grown substantially since the original BCC. Use cases below are extracted by mapping `quorumtrading-application/src/main/java/.../service/*Service.java` against this canvas. Each one has a corresponding `*Service` class; the REST + MCP surface is auto-generated from there.

> ℹ️ **Cross-ref**: For the user-facing surface of each capability (what works, what's hedged behind `<HedgedValue/>`, what's gated by a flag), see [`docs/CAPABILITIES.md`](CAPABILITIES.md). The entries below are the **domain-side** view: which application service implements the use case.

#### Strategy management — added
- **List Public Strategies** — `ListPublicStrategiesService`. Returns strategies with `publicVisible=true`. Drives `/strategies/public` library page.
- **Toggle Strategy Visibility** — `ToggleStrategyVisibilityService`. Flips `publicVisible` on a strategy the user owns.
- **Clone Strategy** — `CloneStrategyService`. Forks a (private or public) strategy into the user's pool, preserving lineage (`StrategyLineageOrigin = FORK`).
- **Delete All Strategies (user)** — `DeleteAllStrategiesService`. Admin/account-deletion path; cascades through sims and decisions.
- **Strategy Health Check** — `StrategyHealthCheckerService`. Validates a strategy's markdown + frontmatter without persisting.
- **Calculate Strategy Performance** — `CalculateStrategyPerformanceService`. Aggregates returns across the strategy's sims for the dashboard / library cards.
- **Strategy Template Registry** — `StrategyTemplateRegistry`. Read-only catalog of starter-strategy templates used by `/strategies/new`.

#### Simulation lifecycle — added
- **End Simulation** — `EndSimulationService`. Graceful end (mark COMPLETED, settle final snapshot). Distinct from `Force End`.
- **Force End Simulation** — `ForceEndSimulationService`. Hard stop regardless of state — used when the sim is wedged.
- **Restart Simulation from Decision** — `RestartSimulationFromService`. Rewinds a sim to any historical decision and replays forward with the current strategy version.
- **Sell Position** — `SellPositionService`. Liquidates a single ticker from a sim's portfolio.
- **Liquidate All Positions** — `LiquidateAllPositionsService`. Emergency liquidation across all tickers.
- **Compare Simulations** — `CompareSimulationsService`. Side-by-side metrics for the chart-comparison view.
- **Delete Paused Simulations** — `DeletePausedSimulationsService`. Bulk-delete utility for the simulations list.
- **Get Simulation Chart Data** — `GetSimulationChartDataService`. Returns time-series for the portfolio chart.
- **Calculate Simulation Overlays** — `CalculateSimulationOverlaysService` + `BuyAndHoldOverlayCalculator`. Computes buy-and-hold + benchmark overlays for chart comparison.
- **Calculate Simulation Cost** — `CalculateSimulationCostService`. Aggregates LLM call cost across a sim.
- **Release Trading Capital** — `ReleaseTradingCapitalService`. Unreserves `cashReserved` when a buy attempt is rejected.

#### Recurring investment (DCA) — new domain area
- **Create Investment Plan** — `InvestmentPlanService.create`. Requires `confirmLive=true` for ALPACA_LIVE brokers.
- **Update / Pause / Resume / Terminate Plan** — `InvestmentPlanService.update|pause|resume|terminate`. State transitions ACTIVE ⇄ PAUSED → TERMINATED.
- **List Investment Plans** — `InvestmentPlanService.list`.
- **Execute Investment Plan** — `ExecuteInvestmentPlanService`. Manual `POST /investment-plans/{id}/execute`. Orchestration: budget check → picker → find-or-create LIVE sim → record contribution → write allocation row → dispatch notification → advance schedule.
- **Record Simulation Contribution** — `SimulationContributionService`. Idempotent on `(planId, contributionDate)` via partial UNIQUE.

#### Notifications — new domain area
- **Dispatch Notification** — `NotificationService`. Orchestrates IN_APP + EMAIL fan-out.
- **Render Email Template** — `EmailTemplateRegistry`. Per-type HTML templates; user copy is HTML-escaped.
- **Sign / Validate Unsubscribe Token** — `UnsubscribeTokenService`. HMAC payload; no DB lookup.

#### Auth / identity / account — new domain area
- **Authentication** — `AuthenticationService`. OIDC callback handling; session establishment.
- **Authorization** — `AuthorizationService`. Per-resource ownership + scope checks.
- **Resolve Current User** — `ResolveCurrentUserService`. Derives the active `User` from the request context.
- **Delete User Account** — `DeleteUserAccountService`. Cascades through all owned entities; writes `DeletedAccountHash` for replay idempotency.
- **API Token CRUD** — `ApiTokenService`. Create (one-time secret reveal), list, revoke. Hash-stored.

#### Multi-broker — new domain area
- **Create Broker Account** — `CreateBrokerAccountService`. Live broker requires `confirmLive=true` in the create payload.
- **Update Broker Account Credentials** — `UpdateBrokerAccountCredentialsService`.
- **Delete Broker Account** — `DeleteBrokerAccountService`. Blocks if any LIVE sim or plan is bound to it.
- **List / Get Broker Account** — `ListBrokerAccountsService`, `GetBrokerAccountService`.
- **Validate Broker Credentials** — `ValidateBrokerAccountCredentialsService` / `BrokerCredentialValidationPort`. Used on save and on background revalidation.
- **Broker Router** — `BrokerRouter`. Resolves a sim's `brokerAccountId` to the right adapter (mock / paper / live).

#### Market research — new domain area
- **Create / Update Market Research** — `CreateMarketResearchService`, `UpdateMarketResearchService`.
- **List / Get Market Research** — `ListMarketResearchService`, `GetMarketResearchDetailsService`.
- **Execute Market Research** — `ExecuteMarketResearchService` + `MarketResearchOrchestrator`. Multi-agent run: Strategy Designer + Viability Assessor + per-agent analysts.
- **Pause / Resume Market Research** — `PauseMarketResearchService`, `ResumeMarketResearchService`.
- **Delete Market Research / Delete Research Execution** — `DeleteMarketResearchService`, `DeleteResearchExecutionService`.
- **Analyze Results** — `ResultsAnalyzerService`. Post-execution viability + actionability extraction.

#### Benchmarks — new domain area
- **Create / Compose / Delete Benchmark** — `CreateBenchmarkService`, `ComposeBenchmarkService`, `DeleteBenchmarkService`.
- **Add / Remove Simulation to Benchmark** — `AddSimulationToBenchmarkService`, `RemoveSimulationFromBenchmarkService`.
- **Calculate Benchmark Overlays** — `CalculateBenchmarkOverlaysService`. Per-entry return % rendered client-side until `benchmark.entry.returnPct` ships from backend.

#### Challenges — new domain area
- **Create / Delete Challenge** — `CreateChallengeService`, `DeleteChallengeService`. Admin-curated single-ticker competitions.
- **Join Challenge** — `JoinChallengeService`. Spawns a sandboxed simulation per participant.

#### Indicators & market data — added
- **Calculate Indicators** — `IndicatorCalculationService`. RSI, MACD, SMA-50/200, Bollinger Bands, etc.
- **Enrich with Indicators** — `IndicatorEnrichmentService`. Used by AgentContext build for technical agent.
- **Get Ticker Performance** — `GetTickerPerformanceService`. Returns aggregate ticker-level stats.
- **Find Ticker** — `FindTickerService`. Symbol search.
- **List / Get Historical Event** — `ListHistoricalEventsService`, `GetHistoricalEventService`. Dividends, splits, earnings.
- **Get Dividend Info** — `GetDividendInfoService` via `DividendInfoPort`.
- **Ticker Registry / Validation Helper** — `TickerRegistryService`, `TickerValidationHelper`.

#### Backfill subsystem — new domain area
- **Request Backfill** — `RequestBackfillService`. User-triggered or auto from sim creation.
- **Orchestrate Backfill** — `BackfillOrchestratorService` + `BackfillExecutor`. Driven by `BackfillManagementPort` (one of the two true inbound use-case ports).
- **Backfill Ticker Price Data** — `BackFillTickerPriceDataService`.
- **Query Backfill Progress** — `QueryBackfillProgressService`. Powers the WAITING_FOR_DATA sim state.
- **Analyze Data Availability** — `AnalyzeDataAvailabilityService`. Driven by `DataAvailabilityPort`.

#### Model & cost management — new domain area
- **Get / Update Model Preferences** — `ModelResolutionService`. Per-context model selection + per-agent overrides + fallback chain.
- **Strategy Picker** — `HeuristicStrategyPicker` (default) and `LlmStrategyPicker` (behind `quorumtrading.picker.mode=llm`), both implementing `StrategyPickerPort`. Pickers for DCA.
- **Rate Limit Tracker** — `RateLimitTracker` + `InMemoryRateLimitTracker` (implements `FreeModelRateLimitPort`). Tracks per-user/per-provider call counts for free-tier enforcement.

#### Cleanup utilities — added
- **Prune Unused** — `PruneUnusedService`. Mass-cleanup for unused strategies / sims / benchmarks.

#### Scheduled jobs — added
- **Scheduled DCA Execution** — `SimulationSchedulerService` + the daily `DcaExecutionScheduler` cron. Probes `findDueForExecution(now)` and fires per-plan. **Off by default** behind `quorumtrading.scheduler.investments.enabled`.
- **Scheduled Trigger Consensus** — `TriggerScheduledConsensusService` (kept; reframed for the new sim states).
- **Get Scheduled Simulation by Strategy** — `GetScheduledSimulationByStrategyService`. Helps the UI surface "a plan funds this strategy" linkbacks.

## Domain Events (Inbound)
- **StrategyCreated**: Triggered by user action via REST API
- **SimulationCreated**: Triggered by user action via REST API
- **SimulationStartRequested**: Triggered by user action via REST API or scheduled job
- **DataCollectionTriggered**: Triggered by scheduled job or manual REST API call
- **ConsensusEvaluationDue**: Triggered by scheduled job checking evaluation intervals
- **PortfolioEvaluationScheduled**: Triggered every minute by scheduled job

## Domain Events (Outbound)
Published via CDI `Event<T>` mechanism, consumed by WebSocket adapter:

- **PortfolioChangeEvent**: Portfolio value or position change
  - Fired by: ExecuteConsensusDecisionService after trade execution
  - Contains: Simulation ID, total value, cash available, positions JSON, timestamp

- **LLMDecisionCreatedEvent**: Individual agent recommendation
  - Fired by: ConsensusOutcomeRecorder (consensus decomposition of ReachConsensusService) after saving agent decision; the WebSocket adapter converts it to its internal AgentDecisionEvent and broadcasts an LLMDecisionMessage
  - Contains: Simulation ID, decision ID, agent role, recommendation, reasoning, confidence, timestamp

- **TradeExecutionEvent**: Trade executed or rejected
  - Fired by: ExecuteConsensusDecisionService after trade attempt
  - Contains: Simulation ID, consensus decision ID, action, symbol, quantity, price, success flag, message, portfolio snapshot ID, timestamp

The full event catalog has grown well beyond these three — see `quorumtrading-application/src/main/java/.../application/event/` (SimulationCreated/Completed/DataReady, ConsensusStarted/Skipped, Backfill\*, Budget\*, Research\*, IndicatorUpdate, ApiTokenUsed, ...).

## External Dependencies

### Outbound Dependencies (Ports)

*Updated 2026-05. The original list mentioned 4 ports; the codebase has since grown to dozens of outbound ports — repositories + integration ports. Grouped below by domain area. Full enumeration in `quorumtrading-application/src/main/java/com/quorumtrading/application/port/out/`.*

**LLM + AI**
- **LLMPort** — Query LLM providers for agent analysis.
  - Implementations: `OpenRouterAdapter` (primary), `LocalLLMAdapter` (CLI-based with fallback chain `claude → qwen`, blacklisting failing providers with exponential backoff; codex/gemini deliberately skipped as too slow), fronted by `LLMRouterAdapter` (adapter-llm-router) with circuit breakers and per-provider metrics.
  - Bedrock removed (Jan 2026 — was unimplemented stub).
- **ConversationalLLMPort** — Streaming chat queries for the assistant surface.
- **FreeModelRateLimitPort** — Per-user free-tier call counting.
- **SystemModelDefaultsPort** — System-wide default fallback chain config.

**Brokers**
- **BrokerPort** — Original simulation broker port (in-process MockBrokerAdapter).
- **ConfigurableBrokerPort** — Multi-broker abstraction. Implementations: `MockBrokerAdapter`, `AlpacaBrokerAdapter` (one class covering paper + live), `SaxoBrokerAdapter` (EU, edition-gated). Each user can register multiple broker accounts; BACKTEST always uses Mock.
- **BrokerCredentialValidationPort** — Validates Alpaca API keys on save.
- **MockBrokerStatePort** — Snapshots Mock broker state for restart recovery.

**Market data**
- **MarketDataPort** — Real-time + historical price queries. Implemented by the Alpaca, Finnhub (stock + news), Tiingo, Saxo, RSS, and Reddit market-data adapters.
  - `CoinbaseAdapter` removed Jan 2026 (consolidated to Alpaca for single crypto source).
  - `NewsAPIAdapter` removed Jan 2026 (replaced by free Finnhub News + RSS).
  - `AlphaVantageAdapter` removed Jan 2026 (Alpaca handles backfill).
- **HistoricalPricePort** — Historical bar queries for backfill.
- **DividendInfoPort** — Dividend events per ticker.
- **TickerPerformancePort** — Aggregate per-ticker stats.
- **FinancialDataPort** — Fundamentals data (now via Finnhub).

**Persistence (repositories)**
- `StrategyRepository`, `SimulationRepository`, `PortfolioRepository`, `SimulationPortfolioRepository`
- `LLMDecisionRepository`, `ConsensusDecisionRepository`, `LlmParseFailureRepository`
- `MarketEventRepository`, `HistoricalEventRepository`
- `MarketResearchRepository`, `ResearchExecutionRepository`
- `BenchmarkRepository`, `ChallengeRepository`
- `BrokerAccountRepository`
- `InvestmentPlanRepository`, `InvestmentAllocationRepository`, `SimulationContributionRepository`
- `NotificationRepository`, `NotificationPreferenceRepository`
- `UserRepository`, `DeletedAccountHashRepository`, `ApiTokenRepository`
- `UserBudgetRepository`, `LLMUsageRepository`
- `BackfillJobRepository`, `ScheduledSimulationRepository`

**Cross-cutting infrastructure ports**
- **AsyncExecutionPort** — Background job dispatcher abstraction.
- **SimulationLockPort** — Per-sim mutex (Redis-backed) to serialize consensus + execution.
- **StrategyPickerPort** — DCA's strategy chooser. Default impl `HeuristicStrategyPicker`; `LlmStrategyPicker` selectable via `quorumtrading.picker.mode=llm`, falling back to the heuristic on any LLM failure.
- **NotificationDispatcherPort** — Per-channel dispatcher (in-app + email).
- **ChatSessionPort** — Persistence for assistant chat sessions.
- **PaymentPort** — Stripe integration. Shipped: `StripePaymentAdapter` (adapter-payment-stripe) + webhook endpoint (`PaymentResource`) with signature validation.

**WebSocket/event delivery** (unchanged)
- Real-time events still fired via CDI `Event<T>` mechanism, consumed by the WebSocket adapter.

### Inbound Dependencies (Adapters)
- **REST API Adapter**: HTTP endpoints for strategy/simulation CRUD
  - Framework: Quarkus REST (formerly RESTEasy Reactive)
  - Protocol: REST with OpenAPI spec
  - Format: JSON request/response DTOs

- **WebSocket Adapter**: Real-time event streaming to frontend
  - Framework: Quarkus WebSocket
  - Protocol: WebSocket with JSON payloads
  - Format: Typed event messages

- **MCP Adapter**: MCP server exposing platform tools/resources to AI clients (Claude Code, Cursor, etc.)
  - Module: `quorumtrading-adapter-mcp`
  - Auth: user API tokens (same access as the issuing user)

- **Scheduled Job Adapter**: Triggers periodic tasks
  - Framework: Quarkus @Scheduled
  - Triggers: Consensus evaluation, data collection, portfolio valuation, cleanup, backfill, DCA execution (flag-gated)

### Infrastructure Dependencies
- **PostgreSQL**: Primary persistence for strategies, simulations, decisions, snapshots, market events
  - Schema: Managed by Flyway migrations
  - Features: JSONB columns, CASCADE deletes, optimistic locking

- **Redis**: Redis-backed infrastructure via `quorumtrading-adapter-redis`
  - Current: rate-limit tracking, financial data cache, event bridge, chat sessions, OAuth state, simulation locks

## Assumptions & Constraints

### Business Assumptions
1. ~~**Historical Data Only**: Simulations run on past data only, no live trading~~ — **Superseded 2026**: paper + live trading via Alpaca now shipped. BACKTEST mode is always mock; LIVE mode uses the configured broker. Multiple concurrent live sims are allowed.
2. **Free LLM Tier**: OpenRouter free tier limits constrain agent query frequency. LocalLLM CLI fallback chain (`claude → qwen`) blacklists failing providers with exponential backoff. `FreeModelRateLimitPort` tracks per-user counts.
3. ~~**Single-Tenant**: No multi-tenancy, single user/team per deployment~~ — **Superseded 2026**: multi-user via OIDC (Google, Apple shipped; GitHub/GitLab/Microsoft configurable). Each user owns their own strategies, sims, plans, broker accounts, API tokens. No organizations or teams (deliberately — see CAPABILITIES.md § 17).
4. **Unlimited Storage**: No hard limits on simulation history or decision audit trail.
5. **Strategy Immutability**: Strategies are versioned; changing a strategy doesn't affect running simulations.
6. **DCA monthly only**: Day-of-month 1–28; no weekly/biweekly/quarterly schedules.
7. **No fractional position sizing**: Decisions fire BUY/SELL on whole positions; no partial-close, stop-loss, take-profit, OCO orders.

### Technical Constraints
1. **Sequential Ticker Processing**: Consensus processes one ticker at a time to maintain portfolio state consistency
2. **In-Memory Broker State**: Portfolio state is not persisted to database, only snapshots are (requires restoration on service restart)
3. **90-Day Market Data Window**: Older market events are deleted daily
4. **Synchronous Agent Execution**: Agents are queried in parallel but consensus waits for all responses (65s timeout)
5. **Single Simulation Process**: Only one simulation execution per JVM (no distributed simulation)

### Architectural Constraints
1. **Hexagonal Architecture**: Ports & adapters pattern enforced, adapters don't depend on other adapters
2. **Quarkus Framework**: Tied to Quarkus CDI, JAX-RS, Panache ORM
3. **PostgreSQL-Only**: No H2 for tests, PostgreSQL required even for test environment
4. **No Native Images**: Due to LLM provider SDK limitations (reflection-heavy)

## Open Questions & Future Considerations

### Recently shipped (2025-Q1 → 2026-Q2)
Items that lived in this section in the original BCC and are now in production:

- **Real Broker Integration** (was "future") — Alpaca paper + live both shipped. Multiple concurrent live sims allowed. Live broker requires `confirmLive=true` on plan/sim creation. See `ConfigurableBrokerPort`.
- **Authentication / Multi-Tenancy** (was "future") — OIDC via Google + Apple in production; GitHub/GitLab/Microsoft configurable per env. Multi-user with per-user resource ownership. Account deletion cascades through all owned data. No org/team layer (deliberate).
- **Notification Service** (was "future: Email/SMS alerts") — In-app + email shipped. Email gated by flag, off by default. Per-type unsubscribe via HMAC tokens. **No SMS** (intentionally — see CAPABILITIES.md § 17).
- **Recurring Investment Plans (DCA)** (new) — InvestmentPlan domain area; scheduler off by default. Manual execute works in any env.
- **Strategy library** (new) — `/strategies/public`, `publicVisible` flag, fork/clone via `CloneStrategyService`.
- **Market research subsystem** (new) — Multi-agent research runs with viability scoring + auto-backtest toggle.
- **Benchmarks** (new) — Compose N strategies + chart overlay.
- **Challenges** (new) — Admin-curated single-ticker competitions with per-participant sandboxed sims.
- **AI Assistant** (new) — Council-aware chat at `/assistant`.
- **MCP server** (new) — Trading platform as MCP for external AI agents (Claude Code, Cursor).
- **Per-context model preferences** (new) — Per-user × per-context × per-agent override + fallback chain.
- **Budget caps + usage tracking** (new) — Lifetime prepaid balance with threshold alerts, per-model + per-feature usage breakdown (see CAPABILITIES.md § 10.4).

### Still open

1. **Distributed Simulations**: Should we support running multiple simulations across cluster nodes?
   - Current: Single-node execution only.
   - Trade-off: Scalability vs complexity of distributed state management.

2. ~~**LLM-backed DCA picker**~~: **Shipped 2026-07** — `LlmStrategyPicker` sits behind the same `StrategyPickerPort`, selectable via `quorumtrading.picker.mode=llm` (default `heuristic`); falls back to `HeuristicStrategyPicker` on any LLM failure.

3. **Redis Utilization**: Beyond the simulation lock + rate-limit tracker that already use it, how much more cache value is there?
   - Current: Market data caching, consensus result caching still untapped.

4. **Agent Customization**: Should users define custom agent types or prompts?
   - Current: 4 fixed agent types with hardcoded prompts.
   - Trade-off: Flexibility vs complexity.

5. ~~**Stripe payment integration**~~: **Shipped** — `StripePaymentAdapter` + webhook endpoint (`PaymentResource`) with signature validation, hardened through production fixes (see Go-Live Checklist in `CLAUDE.md`).

6. **Per-decision manual override**: User-supplied "ignore consensus, do X" path. Currently impossible by design (decisions are immutable audit records). Worth revisiting only if a real user requests it.

7. **Risk profiles**: User-level "conservative / aggressive" persona that flavors agent prompts. Not in scope today; agents have no per-user risk persona.

### Known Technical Debt
1. **Position Deserialization**: Fixed Nov 5, 2025 - Previously used fragile string splitting instead of Jackson JSON parsing (caused position loss bug)
2. **Manual JSON Serialization**: Portfolio positions serialized with StringBuilder instead of Jackson (error-prone)
3. **Broker State Recovery**: MockBrokerAdapter state lost on restart, requires manual restoration from snapshots
4. **Missing Test Coverage**: No tests for deserializePositions() method (discovered via production bug)
5. **Scheduled Job Isolation**: All scheduled jobs run on same thread pool (potential resource contention)

### Business Questions
1. **Pricing Model**: How to monetize? Simulation count limits? Strategy complexity tiers?
2. **Market Data Costs**: Free tier limits for Alpaca/Finnhub - what's upgrade threshold?
3. **Agent Quality**: How to measure and improve agent recommendation accuracy?
4. **Strategy Marketplace**: Should users share strategies? Revenue share model?
5. **Performance Metrics**: What benchmarks matter for strategy comparison? Sharpe ratio only?

## Bounded Context Map (BCM)

### Context Map Overview
QuorumTrading operates as a **Core Domain** bounded context with strategic relationships to external systems and infrastructure. The system follows a hexagonal architecture with well-defined ports and adapters, maintaining clear boundaries between the core domain and external concerns.

### Upstream Dependencies (QuorumTrading as Customer)

#### 1. Market Data Providers → QuorumTrading
**Relationship Pattern**: **Customer-Supplier (Conformist)**

**Description**: QuorumTrading consumes market data from a small set of external providers without influencing their APIs. The system conforms to each provider's data model and must handle their specific quirks.

**Provider consolidation Jan 2026**: The original BCC listed four active providers (Coinbase, NewsAPI, Reddit, Finnhub). Three were retired:
- `CoinbaseAdapter` removed — consolidated to Alpaca for a single crypto price source.
- `NewsAPIAdapter` removed — $449/mo cost; replaced by **free Finnhub News + RSS feeds**.
- `AlphaVantageAdapter` removed — Alpaca handles backfill.
- Reddit adapter remains as a real implementation (social sentiment), enabled in dev but disabled in prod by default.

**Current providers**:
- **Alpaca Market Data API**: Primary source for stock + crypto prices + bars. Same vendor as the broker, so a single integration covers both.
- **Finnhub**: News stream + fundamentals (replaces NewsAPI).
- **Tiingo**: Additional price data source.
- **Saxo**: Market data for the EU/Saxo edition (edition-gated, LIVE-only for equities).
- **RSS feeds**: Curated set for news supplementation. Free, no contract.
- **Reddit**: Social sentiment (disabled in prod by default).

**Integration Details**:
- **Protocol**: REST API polling (pull model) + Alpaca WebSocket for live price updates.
- **Data Format**: JSON with provider-specific schemas.

**Anti-Corruption Layer**:
- **Interfaces**: `MarketDataPort`, `HistoricalPricePort`, `FinancialDataPort`, `DividendInfoPort`, `TickerPerformancePort` (separate ports per concern — see Outbound Dependencies above).
- **Adapter modules**: `quorumtrading-adapter-marketdata-alpaca`, `quorumtrading-adapter-marketdata-finnhub`, `quorumtrading-adapter-marketdata-rss`.
- **Purpose**: Protect domain model from external API changes; normalize the (now smaller) set of heterogeneous data sources.

**Data Flow**:
```
External API → Adapter.fetchData() → MarketEvent (domain) → MarketEventRepository.save()
→ Available for consensus queries
```

**Translation Rules**:
- Alpaca price bar → MarketEvent(type=PRICE_UPDATE, rawData={price, volume, change24h})
- Finnhub/RSS article → MarketEvent(type=NEWS, rawData={headline, sentiment, source})
- Reddit post → MarketEvent(type=SOCIAL_SENTIMENT, rawData={title, score, comments})

**Rate Limits & Resilience**:
- Finnhub: 60 requests/minute → Batched symbol queries
- Retry strategy: Exponential backoff on HTTP 429 (2s → 4s → 8s)
- Circuit breaker: After 5 consecutive failures, disable provider temporarily

**Shared Data Model Risks**:
- Provider API schema changes break adapters
- Mitigation: Version-specific adapter implementations, comprehensive integration tests
- Provider downtime halts data collection
- Mitigation: Multi-provider redundancy, cached fallback data

**Evolution Strategy**:
- Add new providers without touching core domain
- Graceful degradation when providers unavailable
- Provider-specific feature flags: `quorumtrading.providers.{name}.enabled=true/false`

---

#### 2. LLM Providers → QuorumTrading
**Relationship Pattern**: **Customer-Supplier (Conformist with Retry Logic)**

**Description**: QuorumTrading depends on external LLM APIs (OpenRouter primary, LocalLLM CLI fallback) for agent inference. The system must conform to provider prompt formats and handle rate limits gracefully.

**Integration Details**:
- **Protocol**: REST API (HTTPS POST)
- **Data Format**:
  - Request: JSON with `prompt`, `model`, `max_tokens`, `temperature`
  - Response: JSON with `choices[].message.content` (OpenAI-compatible format)
- **Models Used**: config-driven, per-user × per-context via `ModelPreference` (see `quorumtrading.llm.*` properties and `docs/configuration/LLM_CONFIGURATION.md`) — no model IDs hardcoded here

**Anti-Corruption Layer**:
- **Interface**: `LLMPort` (port in application layer)
- **Adapters**:
  - `OpenRouterAdapter`: Handles OpenRouter API specifics + rate limit retries
  - `LocalLLMAdapter`: Executes CLI process (fallback chain `claude → qwen`)
- **Router**: `LLMRouterAdapter` (adapter-llm-router) routes across providers discovered via CDI, with circuit breakers and per-provider metrics
- **Purpose**: Abstract provider-specific APIs, enable provider switching, handle rate limits

**Data Flow**:
```
Agent.analyze(context) → LLMPort.query(prompt, config) → Provider-specific adapter
→ HTTP POST to external LLM → JSON response → Parsed recommendation
→ LLMDecision entity persisted
```

**Translation Rules**:
- AgentContext (domain) → Structured prompt with market data + portfolio state
- LLM JSON response → LLMDecision(recommendation, reasoning, confidence, recommendedAction)
- Confidence score extraction: Parse JSON `"confidence": 0.85` → Percentage value object

**Rate Limits & Resilience**:
- **OpenRouter Free Tier**: 20 RPM, 50-1000 requests/day
  - Retry strategy: Exponential backoff on HTTP 429 (2s → 4s → 8s, max 3 retries)
  - Timeout: 30 seconds per agent query, 65 seconds for parallel batch
- **LocalLLM**: No rate limits, but slow (60s per query)
- **Fallback**: If primary provider fails, `LLMRouterAdapter` circuit-breaks and routes to the next provider; LocalLLM CLI chain falls back `claude → qwen`

**Shared Kernel Risks**:
- LLM response format changes break parsing
- Mitigation: Versioned prompt engineering, response schema validation
- LLM provider downtime blocks consensus
- Mitigation: Multi-provider failover, cached recommendations (planned)
- Prompt injection attacks from user strategies
- Mitigation: Sanitize strategy content, restricted prompt template

**Evolution Strategy**:
- Abstract prompt templates (separate from code)
- Provider-specific model selection per agent type
- A/B testing multiple providers for same agent role
- Cost optimization: Free tier → Paid tier migration path

---

### Downstream Dependencies (QuorumTrading as Supplier)

#### 3. QuorumTrading → Frontend (Next.js SPA)
**Relationship Pattern**: **Published Language (Open Host Service)**

**Description**: QuorumTrading publishes a well-defined API contract (OpenAPI spec) that the frontend must conform to. Backend owns the contract and evolves it via versioning.

**Integration Details**:
- **REST API Protocol**:
  - HTTP/1.1 with JSON payloads
  - Base URL: `http://localhost:8085/api/v1` in dev/local (8080 is the production container port — see `docs/RUN_MODES.md`)
  - Authentication: OIDC session cookie (Google/Apple, more configurable) or per-user API tokens
- **WebSocket Protocol**:
  - WebSocket connection: `ws://localhost:8085/ws/stream` in dev/local
  - Message format: JSON with `type` field discriminator
  - Reconnection: Exponential backoff (1s → 2s → 4s → 8s → 16s)

**Published Contract**:
- **OpenAPI Specification**: `quorumtrading-backend/openapi-spec/openapi.yaml`
- **Version**: 1.0.0-SNAPSHOT
- **Commitment Level**: Committed (breaking changes require frontend coordination)
- **Evolution**: Additive changes preferred, deprecation warnings for 3 months

**API Endpoints (Published Language)**:
```
Strategy Management:
  POST   /api/v1/strategies          → CreateStrategyRequest → StrategyResponse
  GET    /api/v1/strategies          → List<StrategyResponse>
  GET    /api/v1/strategies/{id}     → StrategyResponse
  PUT    /api/v1/strategies/{id}     → UpdateStrategyRequest → StrategyResponse
  DELETE /api/v1/strategies/{id}     → 204 No Content

Simulation Lifecycle:
  POST   /api/v1/simulations         → CreateSimulationRequest → SimulationResponse
  GET    /api/v1/simulations         → List<SimulationResponse>
  GET    /api/v1/simulations/{id}    → SimulationResponse
  POST   /api/v1/simulations/{id}/start   → 202 Accepted
  POST   /api/v1/simulations/{id}/pause   → SimulationResponse
  POST   /api/v1/simulations/{id}/resume  → SimulationResponse
  DELETE /api/v1/simulations/{id}    → 204 No Content

Decisions & Analytics:
  GET    /api/v1/decisions/llm?simulationId={id}        → List<LLMDecisionResponse>
  GET    /api/v1/decisions/consensus?simulationId={id}  → List<ConsensusDecisionResponse>
  GET    /api/v1/portfolios/snapshots?simulationId={id} → List<PortfolioSnapshotResponse>
  GET    /api/v1/decisions/market-data-context?simulationId={id}&timestamp={ts} → MarketDataContextResponse

Leaderboard:
  GET    /api/v1/simulations                  → List<SimulationResponse> (filtered by paused=false)
  GET    /api/v1/portfolios/snapshots?simulationId={id} → List<PortfolioSnapshotResponse>
  GET    /api/v1/strategies/{id}              → StrategyResponse (for strategy names)

Triggers:
  POST   /api/v1/market-data/collect                 → collection statistics
  POST   /api/v1/simulations/{id}/trigger-consensus  → 202 Accepted
```

**WebSocket Event Contracts**:
```typescript
// Discriminated union — the full set of wire types is registered on
// BaseStreamMessage (adapter-websocket): SIMULATION_STARTED, SIMULATION_PROGRESS,
// SIMULATION_COMPLETED, SIMULATION_FAILED, MARKET_EVENT, LLM_DECISION,
// CONSENSUS_DECISION, PORTFOLIO_UPDATE, TRADE_EXECUTION, INDICATOR_UPDATE, ERROR.
// Representative examples:
type WebSocketMessage =
  | SimulationProgressMessage
  | PortfolioUpdateMessage
  | LLMDecisionMessage
  | TradeExecutionMessage;

interface SimulationProgressMessage {
  type: "SIMULATION_PROGRESS";
  simulationId: string;
  progress: number; // 0-100
  currentTimestamp: string; // simulated clock position
  eventsProcessed: number;
  timestamp: string; // ISO-8601
}

interface PortfolioUpdateMessage {
  type: "PORTFOLIO_UPDATE";
  simulationId: string;
  totalValue: number;
  cashBalance: number;
  positions: Record<string, unknown>;
  timestamp: string;
  // Also used by leaderboard for live chart updates
}

interface LLMDecisionMessage {
  type: "LLM_DECISION";
  simulationId: string;
  decisionId: number;
  agentType: string;
  suggestedAction: string;
  reasoning: string;
  confidence: number;
  indicatorSnapshot: string;
  uiPayload: string;
  timestamp: string;
}

interface TradeExecutionMessage {
  type: "TRADE_EXECUTION";
  simulationId: string;
  consensusDecisionId: number;
  action: "BUY" | "SELL" | "HOLD";
  symbol: string;
  quantity: number;
  executionPrice: number;
  success: boolean;
  message: string;
  portfolioSnapshotId: number | null;
  timestamp: string;
}
```

**Translation Rules** (Domain → DTO):
- `Strategy` → `StrategyResponse`: UUID.toString(), content as-is
- `Simulation` → `SimulationResponse`: UUID.toString(), Instant.toString() (ISO-8601)
- `ConsensusDecision` → `ConsensusDecisionResponse`: Enum.name(), Money.amount (double)
- `PortfolioSnapshot` → `PortfolioSnapshotResponse`: JSON string → Map<String, Object>

**Contract Evolution Strategy**:
- **Versioning**: URL-based versioning (`/api/v1`, `/api/v2`)
- **Backwards Compatibility**:
  - Add optional fields (never required)
  - Deprecate fields before removal (3-month window)
  - New enum values: Frontend must handle unknown values gracefully
- **Breaking Changes**: Coordinate with frontend, bump major version
- **Communication**: OpenAPI spec changes trigger frontend type regeneration

**Frontend Conformance Requirements**:
- **Type Safety**: Generate TypeScript types from OpenAPI spec
- **Error Handling**: Handle HTTP 4xx/5xx with user-friendly messages
- **Validation**: Client-side validation matches backend validation rules
- **WebSocket Reconnection**: Exponential backoff, replay missed events (not implemented)

---

### Peer Relationships (Collaborator/Partnership)

#### 4. QuorumTrading ↔ Infrastructure Services
**Relationship Pattern**: **Separate Ways (via Abstraction Layers)**

**Description**: QuorumTrading uses infrastructure services (PostgreSQL, Redis) but maintains independence via repository abstractions. Infrastructure changes don't affect domain logic.

##### PostgreSQL Database
**Integration Details**:
- **Protocol**: JDBC via Quarkus Panache (Hibernate ORM)
- **Schema Management**: Flyway migrations (version-controlled)
- **Connection Pool**: Agroal (Quarkus default; sized via `quarkus.datasource.jdbc.max-size`)
- **Isolation**: Separate test database (`quorumtrading_test`)

**Anti-Corruption Layer**:
- **Repository Pattern**: Domain repositories (ports) → Panache DAOs (adapters)
- **Entity Mapping**: JPA entities separate from domain entities
- **Purpose**: Decouple domain from ORM concerns, enable database switching

**Data Flow**:
```
Domain Entity → Repository (port) → PanacheDAO (adapter) → JPA Entity
→ Hibernate → JDBC → PostgreSQL
```

**Schema Ownership**:
- **Tables**: `orchestrator_strategies`, `simulations`, `llm_decisions`, `consensus_decisions`, `market_events`, `portfolio_snapshots`
- **Constraints**: Foreign keys with CASCADE DELETE, unique constraints on content hashes
- **Indexes**: Composite indexes on (simulation_id, timestamp), (symbol, timestamp)
- **JSONB**: `positions` column in `portfolio_snapshots`, `raw_data` in `market_events`

**Migration Strategy**:
- Flyway versioned migrations in `db/migration/`
- Never edit existing migrations (append-only)
- Backward-compatible schema changes (add columns as nullable)
- Data migrations in separate scripts

**Resilience**:
- Connection pool exhaustion: Requests block until a connection frees or the Agroal acquisition timeout elapses
- Transaction deadlocks and database downtime: No retry, circuit breaker, or read-only degradation — errors surface as HTTP 500 via the ProblemDetails mapper; the application assumes PostgreSQL availability

##### Redis
**Integration Details**:
- **Status**: Actively used via the `quorumtrading-adapter-redis` module
- **Use Cases**:
  - Rate-limit tracking (distributed counter)
  - Financial data cache
  - Event bridge, chat sessions, OAuth state
  - Simulation locks (`SimulationLockPort`) serializing consensus + execution
- **Protocol**: Quarkus Redis extension

**Anti-Corruption Layer**:
- Redis-specific implementations live behind application ports (e.g. `SimulationLockPort`, `ChatSessionPort`, `FreeModelRateLimitPort`)
- **Purpose**: Keep the domain unaware of the cache/lock provider

---

### External System Landscape

```
┌─────────────────────────────────────────────────────────────────┐
│                     External Systems (Upstream)                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [Alpaca API]  [Finnhub API]  [Tiingo]  [RSS]  [Reddit API]    │
│        │             │            │        │        │           │
│        └─────────────┴────────────┴────────┴────────┘           │
│                         │                                       │
│                   Market Data Flow                              │
│                         │                                       │
│        ┌────────────────▼────────────────┐                      │
│        │  MarketDataPort adapters (ACL)  │                      │
│        │  - AlpacaMarketDataAdapter      │                      │
│        │  - FinnhubStock/NewsAdapter     │                      │
│        │  - Tiingo / Saxo / RSS / Reddit │                      │
│        └────────────────┬────────────────┘                      │
│                         │                                       │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│                    QuorumTrading Core Domain                        │
│                   (Bounded Context)                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐       │
│  │  Application Layer (Use Cases + Orchestration)      │       │
│  │  - ReachConsensusUseCase                            │       │
│  │  - ExecuteConsensusDecisionUseCase                  │       │
│  │  - TriggerScheduledConsensusUseCase                 │       │
│  └───────────────┬─────────────────────────────────────┘       │
│                  │                                             │
│  ┌───────────────▼─────────────────────────────────┐           │
│  │  Domain Layer (Entities + Value Objects)        │           │
│  │  - Strategy, Simulation, ConsensusDecision      │           │
│  │  - LLMDecision, PortfolioSnapshot, Portfolio    │           │
│  │  - MarketEvent, AgentContext                    │           │
│  └───────────────┬─────────────────────────────────┘           │
│                  │                                             │
│  ┌───────────────▼─────────────────────────────────┐           │
│  │  Ports (Interfaces)                             │           │
│  │  - LLMPort, BrokerPort, EventPublisher          │           │
│  │  - StrategyRepository, SimulationRepository     │           │
│  └──────────────────────────────────────────────────┘           │
│                                                                 │
└─────────────────┬───────────────────────────┬───────────────────┘
                  │                           │
    ┌─────────────▼──────────┐   ┌───────────▼────────────┐
    │  LLM Providers (ACL)   │   │  Adapters (Outbound)   │
    │  - OpenRouterAdapter   │   │  - Brokers (Mock/      │
    │  - LocalLLMAdapter     │   │    Alpaca/Saxo)        │
    │  - LLMRouterAdapter    │   │  - Persistence (JPA)   │
    └────────────────────────┘   │  - Redis / Stripe      │
                                 └────────────┬────────────┘
                                              │
                          ┌───────────────────▼──────────────────┐
                          │  Infrastructure (Downstream)         │
                          │  - PostgreSQL (schema owner)         │
                          │  - Redis (locks, cache, rate limits) │
                          └──────────────────────────────────────┘
                                              │
                          ┌───────────────────▼──────────────────┐
                          │  Client Applications (Downstream)    │
                          │  - Next.js Frontend (Published API)  │
                          │  - Future: Mobile App, CLI           │
                          └──────────────────────────────────────┘
```

---

### Communication Patterns Summary

| Relationship | Pattern | Direction | Protocol | Frequency | Resilience |
|--------------|---------|-----------|----------|-----------|------------|
| Market Data Providers → QuorumTrading | Customer-Supplier | Pull (polling) | REST/JSON | Every 5-15min | Retry + Circuit Breaker |
| LLM Providers → QuorumTrading | Customer-Supplier | Request/Response | REST/JSON | On-demand | Exponential Backoff |
| QuorumTrading → Frontend | Published Language | Push (WebSocket) + Pull (REST) | WebSocket/JSON + REST/JSON | Real-time + On-demand | Reconnection Logic |
| QuorumTrading → PostgreSQL | Separate Ways | Transactional | JDBC/SQL | Continuous | Connection Pool |
| QuorumTrading → Redis | Separate Ways | Locks + Cache Aside | Redis Protocol | On-demand | Graceful Degradation |

---

### Integration Risk Assessment

**High Risk**:
1. **LLM Provider Rate Limits**: Free tier constraints block consensus execution
   - Mitigation: Retry logic, multi-provider failover, paid tier upgrade path
2. **Market Data Provider Schema Changes**: Break adapters without warning
   - Mitigation: Version-specific adapters, comprehensive integration tests
3. **Frontend Contract Breaking Changes**: Uncoordinated backend updates break UI
   - Mitigation: OpenAPI spec versioning, deprecation warnings, frontend type generation

**Medium Risk**:
1. **PostgreSQL Schema Drift**: Flyway migrations conflict across environments
   - Mitigation: Migration version control, automated migration testing
2. **WebSocket Connection Instability**: Network issues cause missed events
   - Mitigation: Reconnection logic, event replay (planned)
3. **Broker State Loss on Restart**: In-memory portfolio state not persisted
   - Mitigation: Portfolio restoration from snapshots (implemented Nov 5, 2025)

**Low Risk**:
1. **Redis Unavailability**: Cache miss falls back to database
   - Mitigation: Graceful degradation, cache-aside pattern
2. **Provider Downtime**: Single provider failure doesn't block system
   - Mitigation: Multi-provider redundancy, cached fallback data

---

### Future Context Map Evolution

**Shipped since the original BCC** (see "Recently shipped" above):
1. **Real Broker Integration** — Alpaca (paper + live) and Saxo (EU, edition-gated) behind `ConfigurableBrokerPort`
2. **Authentication Service** — OIDC (Google/Apple, more configurable) + per-user API tokens
3. **Notification Service** — in-app + email (flag-gated) via CDI events and `NotificationDispatcherPort`; no message queue, no SMS (deliberate)

**Planned Additions**:
1. **Analytics Dashboard**: Separate bounded context for strategy performance analytics
   - Pattern: Customer-Supplier (QuorumTrading as supplier)
   - Integration: Read-only database replica or API queries

**Migration Paths**:
- **Monolith → Microservices**: Extract market data ingestion as separate service
- **In-Memory Broker → Persistent Broker**: Add BrokerStateRepository for state recovery
- **Single LLM → Multi-LLM Ensemble**: Parallel queries to multiple providers, consensus across LLM responses

## Validation Status
- **Last Validated**: 2026-07-07
- **Status**: validated
- **Validation Notes**: 2026-07-07 pass verified consensus methods (exactly MAJORITY + ORCHESTRATOR_OVERRIDE), agent roster (no Risk agent), LocalLLM fallback chain (`claude → qwen`), removed adapters (Bedrock/Coinbase/NewsAPI/AlphaVantage), shipped adapters (Saxo, Tiingo, Stripe, MCP, Redis), domain-event producers, dev ports, and simulation states against the codebase
- **Next Review**: After the next major architectural change

---

**Document Owner**: colardyn-it
**Created**: 2025-11-05
**Purpose**: Strategic domain modeling for QuorumTrading platform
