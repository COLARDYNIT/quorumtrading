// WebSocket message types
export type StreamMessageType =
  | 'SIMULATION_STARTED'
  | 'SIMULATION_PROGRESS'
  | 'SIMULATION_COMPLETED'
  | 'SIMULATION_FAILED'
  | 'MARKET_EVENT'
  | 'LLM_DECISION'
  | 'CONSENSUS_DECISION'
  | 'CONSENSUS_STARTED'
  | 'CONSENSUS_SKIPPED'
  | 'PORTFOLIO_UPDATE'
  | 'TRADE_EXECUTION'
  | 'INDICATOR_UPDATE'
  | 'BACKFILL_PROGRESS'
  | 'RESEARCH_EXECUTION_PROGRESS'
  | 'ERROR';

export interface BaseStreamMessage {
  type: StreamMessageType;
  timestamp: string;
  simulationId: string;
}

export interface SimulationStartedMessage extends BaseStreamMessage {
  type: 'SIMULATION_STARTED';
  strategyId: string;
  initialCapital: number;
}

export interface SimulationProgressMessage extends BaseStreamMessage {
  type: 'SIMULATION_PROGRESS';
  progress: number; // 0-100
  currentTimestamp: string;
  eventsProcessed: number;
}

export interface SimulationCompletedMessage extends BaseStreamMessage {
  type: 'SIMULATION_COMPLETED';
  finalCapital: number;
  returnPercentage: number;
  totalTrades: number;
  duration: number; // milliseconds
}

export interface SimulationFailedMessage extends BaseStreamMessage {
  type: 'SIMULATION_FAILED';
  error: string;
  stackTrace?: string;
}

export interface MarketEventMessage extends BaseStreamMessage {
  type: 'MARKET_EVENT';
  eventId: number;
  eventType: string;
  symbol: string;
  data: Record<string, any>;
}

export interface LLMDecisionMessage extends BaseStreamMessage {
  type: 'LLM_DECISION';
  decisionId: number | string; // Can be number (WebSocket) or string (REST API)
  agentType: string;
  reasoning: string;
  suggestedAction: string;
  confidence: number;
  ticker?: string; // Ticker symbol for the asset this decision is about
  indicatorSnapshot?: string; // JSON snapshot of technical indicators at decision time
  uiPayload?: string; // Optional A2UI declarative UI payload (JSON)
}

export interface ConsensusDecisionMessage extends BaseStreamMessage {
  type: 'CONSENSUS_DECISION';
  decisionId: number;
  finalAction: string;
  executed: boolean;
  executionPrice?: number;
}

export interface ConsensusStartedMessage extends BaseStreamMessage {
  type: 'CONSENSUS_STARTED';
  consensusRequestId: string;
  decisionTimestamp: string;
}

export interface ConsensusSkippedMessage extends BaseStreamMessage {
  type: 'CONSENSUS_SKIPPED';
  consensusRequestId: string;
  decisionTimestamp: string;
  reason: string;
}

export interface PortfolioUpdateMessage extends BaseStreamMessage {
  type: 'PORTFOLIO_UPDATE';
  cashBalance: number;
  positions: Record<string, any>;
  totalValue: number;
}

export interface TradeExecutionMessage extends BaseStreamMessage {
  type: 'TRADE_EXECUTION';
  consensusDecisionId: number;
  action: string; // BUY | SELL | HOLD
  symbol: string | null;
  quantity: number;
  executionPrice: number;
  success: boolean;
  message: string;
  portfolioSnapshotId: number | null;
}

/**
 * Real-time technical indicator update message.
 * Sent when a ticker's price is updated and indicators are recalculated.
 */
export interface IndicatorUpdateMessage extends BaseStreamMessage {
  type: 'INDICATOR_UPDATE';
  ticker: string;
  tickerType: 'STOCK' | 'CRYPTO';
  currentPrice: number;
  priceChange: number;
  priceChangePercent: number;
  indicators: {
    RSI_14?: {
      value: number;
      interpretation: 'oversold' | 'neutral' | 'overbought';
    };
    SMA_50?: {
      value: number;
    };
    SMA_200?: {
      value: number;
    };
    MACD?: {
      line: number;
      signal: number;
      histogram: number;
      interpretation: 'bullish' | 'bearish' | 'neutral';
    };
    BOLLINGER?: {
      upper: number;
      middle: number;
      lower: number;
    };
    trend: 'UPTREND' | 'DOWNTREND' | 'SIDEWAYS';
  };
}

export interface ErrorMessage extends BaseStreamMessage {
  type: 'ERROR';
  error: string;
  code?: string;
}

/**
 * Backfill progress message (optionally linked to a simulation).
 * Note: This message does NOT extend BaseStreamMessage because backfill
 * operations can be global or tied to a specific simulation via linkedSimulationId.
 */
export interface BackfillProgressMessage {
  type: 'BACKFILL_PROGRESS';
  timestamp: string;
  jobId: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  eventType: 'CREATED' | 'STARTED' | 'PROGRESS' | 'COMPLETED' | 'FAILED';
  progressPercentage: number | null;
  message: string;
  totalRecords: number | null;
  processedRecords: number | null;
  /** Optional simulation ID if this backfill is linked to a simulation */
  linkedSimulationId?: string | null;
}

/**
 * Research execution progress message (NOT tied to simulation).
 * Note: This message does NOT extend BaseStreamMessage because research
 * executions are independent from simulations.
 */
export interface ResearchExecutionProgressMessage {
  type: 'RESEARCH_EXECUTION_PROGRESS';
  timestamp: string;
  researchId: string;
  executionId: string;
  stage: 'STARTED' | 'AGENT_STARTED' | 'AGENT_COMPLETED' | 'COMPLETED' | 'FAILED';
  agentType?: string; // NEWS_SENTIMENT_ANALYST, STRATEGY_DESIGNER, VIABILITY_ASSESSOR
  durationMs?: number;
  outcome?: string; // STRATEGY_CREATED, NO_OPPORTUNITY, FAILED
  strategyId?: string;
  errorMessage?: string;
}

export type StreamMessage =
  | SimulationStartedMessage
  | SimulationProgressMessage
  | SimulationCompletedMessage
  | SimulationFailedMessage
  | MarketEventMessage
  | LLMDecisionMessage
  | ConsensusDecisionMessage
  | ConsensusStartedMessage
  | ConsensusSkippedMessage
  | PortfolioUpdateMessage
  | TradeExecutionMessage
  | IndicatorUpdateMessage
  | BackfillProgressMessage
  | ResearchExecutionProgressMessage
  | ErrorMessage;

// Subscription request for simulations
export interface SubscriptionRequest {
  action: 'subscribe' | 'unsubscribe';
  simulationId: string;
}

// Subscription request for research executions
export interface ResearchSubscriptionRequest {
  action: 'subscribeResearch' | 'unsubscribeResearch';
  researchId: string;
}
