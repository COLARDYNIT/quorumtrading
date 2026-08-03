/**
 * API types matching backend DTOs from hexagonal architecture
 * These types align with the backend's request/response DTOs
 */

// Common types
export type SimulationStatus = 'PENDING' | 'WAITING_FOR_DATA' | 'SIMULATING' | 'LIVE' | 'COMPLETED' | 'FAILED';
export type ConsensusMethod = 'MAJORITY' | 'ORCHESTRATOR_OVERRIDE';
export type EventType = 'PRICE' | 'NEWS' | 'SOCIAL';

// ===== Strategy Types =====

/**
 * Request to create a new strategy.
 * Version is managed automatically by Hibernate @Version for optimistic locking.
 */
export interface CreateStrategyRequest {
  name: string;
  description?: string;
  markdownContent: string;
}

/**
 * Request to update an existing strategy.
 * Both fields are optional, but at least one must be provided.
 */
export interface UpdateStrategyRequest {
  name?: string;
  description?: string;
  markdownContent?: string;
}

/**
 * Request to update an existing market research configuration.
 * All fields are optional - only provided fields will be updated.
 */
export interface UpdateMarketResearchRequest {
  name?: string;
  description?: string;
  targetAssets?: string[];
  interval?: IntervalRequest;
}

/**
 * Strategy response from API.
 * Backend uses @Version for optimistic locking - version field is required.
 */
export interface StrategyResponse {
  id: string;
  name: string;
  description?: string;
  version: number;
  markdownContent: string;
  createdAt: string;
  ownerId?: string; // UUID of strategy owner
  publicVisible: boolean;
}

// Legacy alias for backward compatibility
export type Strategy = StrategyResponse;

/**
 * Strategy performance metrics response from GET /strategies/{id}/performance
 * Aggregate metrics calculated from all completed simulations of a strategy
 */
export interface StrategyPerformanceResponse {
  /** Total number of simulations for this strategy */
  totalSimulations: number;
  /** Number of completed simulations */
  completedSimulations: number;
  /** Average return percentage across completed simulations */
  avgReturn: number;
  /** Win rate (percentage of profitable simulations) */
  winRate: number;
  /**
   * Annualised Sharpe ratio, or null when it could not be computed — a short backtest rarely
   * has enough daily snapshots for a standard deviation. Null means unknown, NOT zero: the
   * backend used to send 0 here, which rendered as a measured risk-neutral result.
   */
  avgSharpeRatio: number | null;
  /** Best return percentage achieved */
  bestReturn: number;
  /** Worst return percentage */
  worstReturn: number;
  /** Recent trend (last 4 simulation returns) */
  recentTrend: number[];
  /**
   * How many completed runs made no trading decision at all, and were therefore excluded
   * from every metric above.
   *
   * A run that decided nothing ends with its portfolio equal to its starting cash, so it
   * reports an honest-looking 0.00%. Counting those in averaged untested strategies down
   * into looking like losing ones — and 0% win rate reads as "this loses", not "unknown".
   * Non-zero here means the metrics describe fewer runs than `completedSimulations`.
   */
  emptyRuns: number;
}

// ===== Simulation Types =====

/**
 * Request to create a new simulation.
 * Matches backend CreateSimulationRequest DTO.
 *
 * startDate is optional for LIVE mode - backend uses current time if not provided.
 */
export interface CreateSimulationRequest {
  strategyId: string;
  startDate?: string; // ISO 8601 - optional for LIVE mode (backend uses now())
  endDate: string;    // ISO 8601
  name?: string;
  initialCash?: number;
}

export interface PositionInfo {
  symbol: string;
  quantity: number;
  value?: number;
}

export interface PortfolioInfo {
  cash: number;
  positions?: PositionInfo[];
  totalValue?: number;
}

/**
 * Broker types supported by the system.
 */
export type BrokerType = 'MOCK' | 'ALPACA_PAPER' | 'ALPACA_LIVE' | 'SAXO_SIM' | 'SAXO_LIVE';

/**
 * Cost preset for mock broker.
 */
export type CostPreset = 'ZERO_FEES' | 'TYPICAL_RETAIL' | 'HIGH_FRICTION' | 'CUSTOM';

/**
 * Lineage origin of the strategy this simulation runs.
 * OWN — user authored it themselves.
 * PUBLIC — forked from the public catalog; parentStrategyId points at the upstream id.
 */
export type StrategyLineageOrigin = 'OWN' | 'PUBLIC';

export interface StrategyLineageResponse {
  origin: StrategyLineageOrigin;
  parentStrategyId: string | null;
  generation: number;
}

/**
 * Simulation response from API.
 * Matches backend SimulationResponse DTO.
 */
export interface SimulationResponse {
  id: string;
  strategyId: string;
  startDate: string;
  endDate: string;
  status: SimulationStatus;
  name?: string;
  progress?: number;
  paused?: boolean;
  pausedForExternalReason?: boolean;
  externalPauseReason?: string | null;
  // Why the run failed. Null unless status is FAILED. The backend has always sent this;
  // the type dropped it, so the FAILED page showed a hardcoded guess list instead.
  failureReason?: string | null;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  benchmarkId?: string; // Optional - only present if simulation belongs to a benchmark
  // Broker configuration (Phase 2+)
  brokerType?: BrokerType; // Type of broker used (MOCK, ALPACA_PAPER, ALPACA_LIVE)
  brokerPreset?: CostPreset; // For MOCK broker: cost preset (ZERO_FEES, TYPICAL_RETAIL, etc.)
  commissionRate?: number; // For MOCK with CUSTOM preset: stock commission rate as decimal
  cryptoCommissionRate?: number; // For MOCK with CUSTOM preset: crypto commission rate as decimal
  slippageBase?: number; // For MOCK with CUSTOM preset: base slippage rate
  // Backfill tracking (for WAITING_FOR_DATA status)
  backfillJobId?: string | null; // Linked backfill job ID when status is WAITING_FOR_DATA
  skippedEvaluationCount?: number; // Consecutive skipped evaluations (circuit breaker)
  totalEvaluationCount?: number; // Total evaluations attempted
  initialCash?: number; // Initial cash balance the simulation started with
  // Strategy lineage — null only when the underlying strategy row was deleted.
  strategyLineage?: StrategyLineageResponse | null;
  // --- Recurring-investment fields (only populated by detail endpoint) ---
  totalInvested?: number; // initialCash + Σ contributions
  twrPercent?: number; // Time-weighted return (industry-standard) as percentage
  mwrPercent?: number; // Money-weighted return (IRR), annualized %
  maxDrawdownPercent?: number; // Max drawdown % on the contribution-normalized equity curve (negative)
  contributions?: ContributionResponse[]; // Empty for backtests / unfunded sims
}

/**
 * A single cash contribution into a LIVE simulation (recurring-plan top-up or manual injection).
 * Matches backend ContributionResponse DTO.
 */
export interface ContributionResponse {
  id: string;
  amount: number;
  currency: string;
  contributionDate: string;
  sourcePlanId?: string | null;
  sourceAllocationId?: string | null;
  createdAt: string;
}

// Legacy alias for backward compatibility
export type Simulation = SimulationResponse;

// ===== Market Event Types =====

export interface MarketEvent {
  id: string; // Backend uses UUID (string)
  timestamp: string;
  eventType: EventType;
  source: string;
  symbol: string;
  rawData: Record<string, unknown>;
  ingestedAt: string;
}

export interface MarketDataQueryParams {
  from: string; // ISO date string
  to: string;
  symbol?: string;
  eventType?: EventType;
  source?: string;
  limit?: number;
}

export interface MarketDataResponse {
  events: MarketEvent[];
  total: number;
  from: string;
  to: string;
  limit?: number;
}

// ===== Market Data Context Types =====
// These types represent the exact market data that AI agents see during consensus

/**
 * Individual market event in the context (nested DTO).
 * Similar to MarketEvent but without id and ingestedAt fields.
 */
export interface MarketEventDTO {
  timestamp: string; // ISO-8601
  eventType: string;
  source: string;
  symbol: string;
  rawData: Record<string, unknown>;
}

/**
 * Market data for a specific timeframe.
 */
export interface TimeframeDataDTO {
  /** Timeframe identifier (e.g., 'LAST_24H', 'LAST_7D', 'LAST_30D', 'LAST_90D') */
  timeframe: string;
  /** Events in this timeframe */
  events: MarketEventDTO[];
  /** Start of the timeframe range */
  startTime: string; // ISO-8601
  /** End of the timeframe range */
  endTime: string; // ISO-8601
}

/**
 * Market data for a specific ticker, organized by timeframe.
 */
export interface TickerMarketDataDTO {
  /** Ticker symbol (e.g., 'BTC', 'ETH') */
  symbol: string;
  /** Market data organized by timeframe */
  timeframes: Record<string, TimeframeDataDTO>;
  /** Total number of events across all timeframes for this ticker */
  totalEvents: number;
}

/**
 * Market data context response from GET /api/v1/decisions/market-data-context.
 * Contains the market events grouped by ticker that were (or would be) available
 * to AI agents during consensus evaluation.
 */
export interface MarketDataContextResponse {
  /** Simulation ID this context belongs to */
  simulationId: string;
  /** Analysis timestamp (the point in time for which market data is retrieved) */
  analysisTimestamp: string; // ISO-8601
  /** Market data organized by ticker symbol */
  tickers: Record<string, TickerMarketDataDTO>;
}

// ===== Decision Types =====

export interface LLMDecision {
  id: string; // Backend uses UUID (string)
  simulationId: string;
  agentType: string;
  decisionTimestamp: string;
  reasoning: string;
  suggestedAction: string;
  confidence: number;
  ticker?: string; // Ticker symbol for the asset this decision is about
  modelUsed?: string; // LLM model used (e.g., "openai/gpt-4o")
  promptTokens?: number; // Number of prompt tokens
  completionTokens?: number; // Number of completion tokens
  totalTokens?: number; // Total tokens (prompt + completion)
  rawCostUsd?: number; // Raw cost in USD (before markup)
  executionTimeMs?: number; // LLM execution time in ms (0 = cached decision)
  indicatorSnapshot?: string; // JSON snapshot of technical indicators at decision time
  uiPayload?: string; // Optional A2UI declarative UI payload (JSON)
}

export interface ConsensusDecision {
  id: string; // Backend uses UUID (string)
  simulationId: string;
  decisionTimestamp: string;
  consensusMethod: ConsensusMethod;
  finalAction: string;
  executed: boolean;
  notes?: string;
  symbol?: string; // Ticker symbol for the asset this decision is about
  investmentType?: string; // Investment type (e.g., 'CRYPTO', 'STOCK')
  executionPrice?: number; // Price at which trade was executed (null if not executed)
  quantity?: number; // Quantity traded (null if not executed or HOLD)
  // PENDING | EXECUTED | NO_TRADE_REQUIRED | REJECTED | SKIPPED — was on the wire all along,
  // but absent from this type, so SKIP rows could not be told apart in the UI.
  executionOutcome?: string | null;
}

// ===== Simulation Execution Health =====

/** One normalised skip reason and how often it occurred in the inspected window. */
export interface SimulationHealthSkipReason {
  reason: string;
  count: number;
}

/**
 * Live execution health from GET /simulations/{id}/status — the answer to "why is this
 * run not progressing?" while it is still running. Matches SimulationHealthResponse.
 */
export interface SimulationHealth {
  status: SimulationStatus;
  verdict:
    | 'HEALTHY'
    | 'IDLE'
    | 'STALLED'
    | 'WAITING_FOR_DATA'
    | 'PAUSED_BY_USER'
    | 'PAUSED_EXTERNAL'
    | 'NOT_STARTED'
    | 'COMPLETED'
    | 'FAILED';
  detail: string;
  lastDecisionAt?: string | null;
  lastRoundAt?: string | null;
  recentRows: number;
  recentSkippedRows: number;
  consecutiveSkippedRows: number;
  totalEvaluations: number;
  totalSkippedEvaluations: number;
  topSkipReasons: SimulationHealthSkipReason[];
  externalPauseReason?: string | null;
  failureReason?: string | null;
}

// ===== Portfolio Types =====

export interface PortfolioSnapshot {
  id: string; // Backend uses UUID (string)
  simulationId: string;
  timestamp: string;
  cashBalance: number;
  positions: Record<string, unknown>;
  totalValue: number;
}

/**
 * Chart data point for portfolio value history.
 * Matches backend ChartDataPointResponse DTO.
 * Used for rendering sparkline charts in the dashboard.
 */
export interface ChartDataPoint {
  timestamp: string; // ISO-8601
  portfolioValue: number;
  cashAvailable: number;
}

// ===== Response Wrapper Types =====
// Backend wraps list responses with metadata

export interface LLMDecisionsResponse {
  decisions: LLMDecision[];
  total: number;
  simulationId: string;
  limit: number;
}

export interface ConsensusDecisionsResponse {
  decisions: ConsensusDecision[];
  total: number;
  simulationId: string;
  limit: number;
}

export interface PortfolioSnapshotsResponse {
  snapshots: PortfolioSnapshot[];
  total: number;
  simulationId: string;
  limit: number;
}

// Consensus with Agents (aggregated response)
export interface ConsensusWithAgentsResponse {
  consensusDecision: ConsensusDecision;
  agentDecisions: LLMDecision[];
}

// ===== Market Research Types =====

export type ResearchStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED';
export type ResearchOutcome = 'STRATEGY_CREATED' | 'NO_OPPORTUNITY' | 'FAILED';
export type ResearchAgentType = 'NEWS_SENTIMENT_ANALYST' | 'STRATEGY_DESIGNER' | 'VIABILITY_ASSESSOR';
export type IntervalUnit = 'DAYS' | 'WEEKS' | 'MONTHS';

/**
 * Interval configuration for market research scheduling.
 */
export interface IntervalRequest {
  amount: number;
  unit: IntervalUnit;
}

export interface IntervalResponse {
  amount: number;
  unit: IntervalUnit;
}

/**
 * Backtest configuration for auto-triggered backtests.
 */
export interface BacktestConfigRequest {
  backtestLength: string; // ISO-8601 duration (e.g., "P30D")
  periodStart?: string;   // ISO-8601 instant (nullable)
  periodEnd?: string;     // ISO-8601 instant (nullable)
}

export interface BacktestConfigResponse {
  backtestLength: string; // ISO-8601 duration
  periodStart?: string;   // ISO-8601 instant
  periodEnd?: string;     // ISO-8601 instant
}

/**
 * Request to create a new market research configuration.
 * Matches backend CreateMarketResearchRequest DTO.
 */
export interface CreateMarketResearchRequest {
  name: string;
  description?: string;
  targetAssets: string[]; // "TICKER[:TYPE]" tokens; bare = STOCK (e.g., ["AAPL", "BTC:CRYPTO"])
  interval: IntervalRequest;
  autoBacktest: boolean;
  backtestConfig?: BacktestConfigRequest;
}

/**
 * Market research configuration response.
 * Matches backend MarketResearchResponse DTO.
 */
export interface MarketResearchResponse {
  id: string; // UUID
  name: string;
  description?: string;
  targetAssets: string[];
  interval: IntervalResponse;
  status: ResearchStatus;
  autoBacktest: boolean;
  backtestConfig?: BacktestConfigResponse;
  createdAt: string;       // ISO-8601
  lastExecutedAt?: string; // ISO-8601
  nextScheduledRun?: string; // ISO-8601
  createdStrategiesCount: number; // Count of strategies created by this research
}

/**
 * Individual agent decision during market research consensus.
 */
export interface AgentDecisionResponse {
  id: string; // UUID
  agentType: ResearchAgentType;
  advice: string;
  reasoning: string;
  decidedAt: string; // ISO-8601
  modelUsed?: string; // LLM model used (e.g., "openai/gpt-4o")
  promptTokens?: number; // Number of prompt tokens
  completionTokens?: number; // Number of completion tokens
  totalTokens?: number; // Total tokens (prompt + completion)
  rawCostUsd?: number; // Raw cost in USD (before markup)
  toolCalls?: string; // JSON array of tool calls made during agent reasoning
}

/**
 * Research execution result.
 * Matches backend ResearchExecutionResponse DTO.
 */
export interface ResearchExecutionResponse {
  id: string; // UUID
  marketResearchId: string; // UUID
  outcome: ResearchOutcome;
  outcomeReasoning: string;
  viabilityScore?: number; // 0-100, only present if outcome is STRATEGY_CREATED
  createdStrategyId?: string; // UUID, only present if strategy was created
  simulationId?: string; // UUID, only present if backtest was triggered
  executedAt: string; // ISO-8601
  agentDecisions: AgentDecisionResponse[];
  billedCostUsd?: number; // Billed cost in USD (raw LLM cost with platform markup applied)
}

/**
 * Detailed market research response including execution history.
 * Matches backend MarketResearchDetailsResponse DTO.
 */
export interface MarketResearchDetailsResponse {
  research: MarketResearchResponse;
  executions: ResearchExecutionResponse[];
  latestExecution?: ResearchExecutionResponse;
  totalExecutions: number;
  successfulExecutions: number; // Count of STRATEGY_CREATED + NO_OPPORTUNITY outcomes
}

// ===== Position Management Types =====

/**
 * Response from POST /simulations/{id}/positions/{symbol}/sell
 * Matches backend SellPositionResponse DTO.
 */
export interface SellPositionResponse {
  success: boolean;
  message: string;
  filledQuantity: number | null;
  executionPrice: number | null;
}

/**
 * Response from POST /simulations/{id}/liquidate
 * Matches backend LiquidateAllResponse DTO.
 */
export interface LiquidateAllResponse {
  positionsLiquidated: number;
  positionsFailed: number;
  errors: string[];
  cashAfterLiquidation: number | null;
}

/**
 * Request body for POST /simulations/{id}/end
 * Matches backend EndSimulationRequest DTO.
 */
export interface EndSimulationRequest {
  liquidatePositions: boolean;
}

/**
 * Response from POST /simulations/{id}/end
 * Matches backend ForceEndSimulationResponse DTO.
 */
export interface ForceEndSimulationResponse {
  success: boolean;
  liquidated: boolean;
  liquidatedCount: number;
  message: string;
}

/**
 * Response from POST /simulations/{id}/restart-from/preview and /restart-from
 * Matches backend RestartFromPreviewResponse DTO.
 */
export interface RestartFromPreviewResponse {
  consensusDecisionsToDelete: number;
  llmDecisionsToDelete: number;
  portfolioSnapshotsToDelete: number;
  archivedCostUsd: number;
  archivedTokens: number;
  portfolioCashAtRestart: number;
  portfolioValueAtRestart: number;
  restartTimestamp: string;
}

/**
 * Check whether a simulation supports position selling/liquidation.
 * LIVE simulations and Alpaca-backed simulations can sell.
 * Pure backtests without a broker account cannot.
 */
export function canSellPositions(sim: SimulationResponse): boolean {
  if (sim.brokerType === 'ALPACA_PAPER' || sim.brokerType === 'ALPACA_LIVE') return true;
  return sim.status === 'LIVE';
}

// ===== Benchmark Types =====

/**
 * Benchmark response from public API.
 * Matches backend BenchmarkResponse DTO.
 *
 * A benchmark groups multiple simulations for competitive comparison.
 * Can be either a historical backtest period (with end date) or a live competition (no end date).
 */
export interface BenchmarkResponse {
  id: string; // UUID
  name: string; // Custom or auto-generated name
  startDate: string; // ISO-8601 timestamp
  endDate?: string; // ISO-8601 timestamp, null indicates live mode
  simulationIds: string[]; // UUIDs of participating simulations
  createdAt: string; // ISO-8601 timestamp
  isLive: boolean; // true = live competition, false = historical backtest
}

// ===== Historical Event Types =====

export interface HistoricalEventResponse {
  id: number;
  slug: string;
  name: string;
  startDate: string; // YYYY-MM-DD
  endDate: string; // YYYY-MM-DD
  category: string; // CRISIS, BULL_RUN, VOLATILITY, CATALYST, BEAR_MARKET
  assetClass: string; // STOCK, CRYPTO, BOTH
  sentiment: string; // BULLISH, BEARISH, MIXED
  description: string;
  impactSummary: string;
  suggestedAssets: string[];
  keywords: string[];
}

// ===== Financial Metrics Types =====

export interface FinancialMetricsResponse {
  companyName?: string;
  industry?: string;
  marketCapMillions?: number;
  valuation?: {
    peRatioTTM?: number;
    forwardPE?: number;
    priceToBook?: number;
    priceToSales?: number;
    evToEbitda?: number;
    pegRatio?: number;
  };
  growth?: {
    epsGrowthYoY?: number;
    epsGrowth5Y?: number;
    revenueGrowthYoY?: number;
    revenueGrowth5Y?: number;
  };
  profitability?: {
    grossMargin?: number;
    operatingMargin?: number;
    netProfitMargin?: number;
    returnOnEquity?: number;
    returnOnAssets?: number;
  };
  financialHealth?: {
    debtToEquity?: number;
    currentRatio?: number;
    quickRatio?: number;
  };
  perShare?: {
    epsTTM?: number;
    bookValue?: number;
    dividendYield?: number;
    beta?: number;
  };
  earnings?: Array<{
    period: string;
    quarter: number;
    year: number;
    actual?: number;
    estimate?: number;
    surprisePercent?: number;
    beat: boolean;
  }>;
  analystConsensus?: {
    strongBuy: number;
    buy: number;
    hold: number;
    sell: number;
    strongSell: number;
    total: number;
  };
}

// ===== Dividend Types =====

export interface DividendInfoItem {
  ticker: string;
  paysDividends: boolean;
  annualYieldEstimate: number | null;
  dividendCountLastYear: number;
  assetType: string; // "STOCK" | "CRYPTO"
}

export interface DividendInfoResponse {
  items: DividendInfoItem[];
}
