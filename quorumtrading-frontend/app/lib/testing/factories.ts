/**
 * Test Data Factory
 *
 * Generates OpenAPI-compliant mock data for testing.
 * All types match the backend OpenAPI specification exactly.
 */

import type {
  StrategyResponse,
  CreateStrategyRequest,
  SimulationResponse,
  CreateSimulationRequest,
  MarketEvent,
  MarketDataResponse,
  LLMDecision,
  LLMDecisionsResponse,
  ConsensusDecision,
  ConsensusDecisionsResponse,
  PortfolioSnapshot,
  PortfolioSnapshotsResponse,
  SimulationStatus,
  EventType,
  ConsensusMethod,
} from '../types/api';

/**
 * Strategy Factory
 */
export const createMockStrategy = (overrides?: Partial<StrategyResponse>): StrategyResponse => ({
  id: '660f9511-f39c-52e5-b827-557766551111',
  name: 'BTC Momentum Strategy',
  version: 1, // Hibernate optimistic locking version
  markdownContent: `# BTC Momentum Strategy

## Consensus Method
MAJORITY

## Evaluation Interval Hours
6

## Rules
- Buy when BTC price increases by 5% in 24h
- Sell when BTC price decreases by 3% in 24h
- Maximum position size: 50% of portfolio`,
  createdAt: '2025-01-01T00:00:00Z',
  publicVisible: false,
  ...overrides,
});

export const createMockStrategyRequest = (overrides?: Partial<CreateStrategyRequest>): CreateStrategyRequest => ({
  name: 'BTC Momentum Strategy',
  markdownContent: `# BTC Momentum Strategy

## Consensus Method
MAJORITY

## Rules
- Buy when BTC price increases by 5%`,
  ...overrides,
});

/**
 * Simulation Factory
 */
export const createMockSimulation = (overrides?: Partial<SimulationResponse>): SimulationResponse => ({
  id: '550e8400-e29b-41d4-a716-446655440000',
  strategyId: '660f9511-f39c-52e5-b827-557766551111',
  startDate: '2023-01-01T00:00:00Z',
  endDate: '2023-12-31T23:59:59Z',
  status: 'COMPLETED' as SimulationStatus,
  name: 'BTC Momentum Strategy Backtest Q1 2023',
  progress: 100,
  createdAt: '2025-01-01T00:00:00Z',
  ...overrides,
});

export const createMockSimulationRequest = (overrides?: Partial<CreateSimulationRequest>): CreateSimulationRequest => ({
  strategyId: '660f9511-f39c-52e5-b827-557766551111',
  startDate: '2023-01-01T00:00:00Z',
  endDate: '2023-12-31T23:59:59Z',
  name: 'BTC Momentum Strategy Backtest Q1 2023',
  ...overrides,
});

/**
 * Market Data Factory
 */
export const createMockMarketEvent = (overrides?: Partial<MarketEvent>): MarketEvent => ({
  id: '123e4567-e89b-12d3-a456-426614174000',
  timestamp: '2025-01-01T12:00:00Z',
  eventType: 'PRICE' as EventType,
  source: 'coinbase',
  symbol: 'BTC/USD',
  rawData: { price: 50000, volume: 1500 },
  ingestedAt: '2025-01-01T12:00:01Z',
  ...overrides,
});

export const createMockMarketDataResponse = (overrides?: Partial<MarketDataResponse>): MarketDataResponse => ({
  events: [createMockMarketEvent()],
  total: 1,
  from: '2025-01-01T00:00:00Z',
  to: '2025-01-02T00:00:00Z',
  limit: 100,
  ...overrides,
});

/**
 * Decision Factory
 */
export const createMockLLMDecision = (overrides?: Partial<LLMDecision>): LLMDecision => ({
  id: '789e4567-e89b-12d3-a456-426614174001',
  simulationId: '550e8400-e29b-41d4-a716-446655440000',
  agentType: 'TECHNICAL',
  decisionTimestamp: '2025-01-01T12:00:00Z',
  reasoning: 'RSI indicates oversold conditions and MACD shows bullish crossover',
  suggestedAction: 'BUY',
  confidence: 0.85,
  ...overrides,
});

export const createMockLLMDecisionsResponse = (overrides?: Partial<LLMDecisionsResponse>): LLMDecisionsResponse => ({
  decisions: [createMockLLMDecision()],
  total: 1,
  simulationId: '550e8400-e29b-41d4-a716-446655440000',
  limit: 100,
  ...overrides,
});

export const createMockConsensusDecision = (overrides?: Partial<ConsensusDecision>): ConsensusDecision => ({
  id: '999e4567-e89b-12d3-a456-426614174002',
  simulationId: '550e8400-e29b-41d4-a716-446655440000',
  decisionTimestamp: '2025-01-01T12:00:00Z',
  consensusMethod: 'MAJORITY' as ConsensusMethod,  // Valid values: 'MAJORITY' | 'ORCHESTRATOR_OVERRIDE'
  finalAction: 'BUY',
  executed: true,
  notes: '3 out of 4 agents recommended BUY action',
  ...overrides,
});

export const createMockConsensusDecisionsResponse = (overrides?: Partial<ConsensusDecisionsResponse>): ConsensusDecisionsResponse => ({
  decisions: [createMockConsensusDecision()],
  total: 1,
  simulationId: '550e8400-e29b-41d4-a716-446655440000',
  limit: 100,
  ...overrides,
});

/**
 * Portfolio Factory
 */
export const createMockPortfolioSnapshot = (overrides?: Partial<PortfolioSnapshot>): PortfolioSnapshot => ({
  id: '111e4567-e89b-12d3-a456-426614174003',
  simulationId: '550e8400-e29b-41d4-a716-446655440000',
  timestamp: '2025-01-01T12:00:00Z',
  cashBalance: 50000,
  positions: { 'BTC/USD': 1.5 },
  totalValue: 125000,
  ...overrides,
});

export const createMockPortfolioSnapshotsResponse = (overrides?: Partial<PortfolioSnapshotsResponse>): PortfolioSnapshotsResponse => ({
  snapshots: [createMockPortfolioSnapshot()],
  total: 1,
  simulationId: '550e8400-e29b-41d4-a716-446655440000',
  limit: 100,
  ...overrides,
});

/**
 * Helper: Create multiple entities
 */
export const createMockStrategies = (count: number): StrategyResponse[] =>
  Array.from({ length: count }, (_, i) => createMockStrategy({
    id: `660f9511-f39c-52e5-b827-55776655${String(i).padStart(4, '0')}`,
    name: `Strategy ${i + 1}`,
    version: i + 1,
  }));

export const createMockSimulations = (count: number): SimulationResponse[] =>
  Array.from({ length: count }, (_, i) => createMockSimulation({
    id: `550e8400-e29b-41d4-a716-44665544${String(i).padStart(4, '0')}`,
    name: `Simulation ${i + 1}`,
    progress: Math.floor((i + 1) / count * 100),
  }));

export const createMockMarketEvents = (count: number): MarketEvent[] =>
  Array.from({ length: count }, (_, i) => createMockMarketEvent({
    id: `123e4567-e89b-12d3-a456-42661417${String(i).padStart(4, '0')}`,
    timestamp: new Date(Date.now() + i * 3600000).toISOString(),
    rawData: { price: 50000 + i * 100 },
  }));

export const createMockLLMDecisions = (count: number): LLMDecision[] =>
  Array.from({ length: count }, (_, i) => createMockLLMDecision({
    id: `789e4567-e89b-12d3-a456-42661417${String(i).padStart(4, '0')}`,
    agentType: ['TECHNICAL', 'FUNDAMENTAL', 'SENTIMENT'][i % 3],
    confidence: 0.7 + (i % 3) * 0.1,
  }));

export const createMockConsensusDecisions = (count: number): ConsensusDecision[] =>
  Array.from({ length: count }, (_, i) => createMockConsensusDecision({
    id: `999e4567-e89b-12d3-a456-42661417${String(i).padStart(4, '0')}`,
    finalAction: i % 2 === 0 ? 'BUY' : 'SELL',
  }));

export const createMockPortfolioSnapshots = (count: number): PortfolioSnapshot[] =>
  Array.from({ length: count }, (_, i) => createMockPortfolioSnapshot({
    id: `111e4567-e89b-12d3-a456-42661417${String(i).padStart(4, '0')}`,
    timestamp: new Date(Date.now() + i * 3600000).toISOString(),
    totalValue: 100000 + i * 5000,
    cashBalance: 50000 - i * 1000,
  }));
