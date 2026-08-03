'use client';

import { createContext, useContext, useState, useMemo, ReactNode } from 'react';
import {
  useSimulation,
  useStrategy,
  usePortfolioSnapshots,
  useLLMDecisions,
  useConsensusDecisions,
  useMarketData,
  useMarketDataContext,
} from '@/app/lib/api/hooks';
import { useWebSocket, useSimulationStream, useStreamMessage } from '@/app/lib/websocket/hooks';
import { useTimeNavigation } from '@/app/contexts/TimeNavigationContext';
import { parseConfiguredTickers, parseTickerList, parseIntervalHours } from '../lib/strategyFrontmatter';
import type {
  SimulationResponse,
  StrategyResponse,
  PortfolioSnapshot,
  MarketEvent,
  MarketEventDTO,
  MarketDataContextResponse,
} from '@/app/lib/types/api';
import type {
  LLMDecisionMessage,
  PortfolioUpdateMessage,
  TradeExecutionMessage,
  ErrorMessage,
  ConsensusStartedMessage,
  ConsensusSkippedMessage,
  IndicatorUpdateMessage,
} from '@/app/lib/types/websocket';

// Portfolio data structure
interface PortfolioData {
  holdings: Array<{
    symbol: string;
    amount: number;
    buyPrice: number;
    currentPrice: number;
    change: number;
    type: 'crypto' | 'stock';
  }>;
  cash: number;
  totalValue: number;
  valueHistory: Array<{
    timestamp: string;
    fullTimestamp?: Date; // Full Date object for day separators
    tickers: Record<string, number>; // Per-ticker values (e.g., { AAPL: 15000, MSFT: 20000 })
    cash: number;
    total: number;
  }>;
}

// Market events structure
interface MarketEvents {
  crypto: Array<{
    symbol: string;
    events: Array<{ price: number; currency: string; timestamp: string }>;
  }>;
  stocks: Array<{
    symbol: string;
    events: Array<{ price: number; currency: string; timestamp: string }>;
  }>;
  news: Array<{ title: string; source: string; timestamp: string }>;
}

// Real-time ticker indicator data (from WebSocket INDICATOR_UPDATE)
export interface TickerIndicatorData {
  ticker: string;
  tickerType: 'STOCK' | 'CRYPTO';
  currentPrice: number;
  priceChange: number;
  priceChangePercent: number;
  indicators: {
    rsi?: { value: number; interpretation: 'oversold' | 'neutral' | 'overbought' };
    macd?: { line: number; signal: number; histogram: number; interpretation: 'bullish' | 'bearish' | 'neutral' };
    sma50?: number;
    sma200?: number;
    bollinger?: { upper: number; middle: number; lower: number };
    trend: 'UPTREND' | 'DOWNTREND' | 'SIDEWAYS';
  };
  lastUpdated: Date;
}

// Consensus marker data enriched with action type
export interface ConsensusMarkerData {
  id: string;
  timestamp: Date;
  action: 'BUY' | 'SELL' | 'HOLD';
  symbol: string;
  confidence: number | null;
  reasoning: string | null;
}

// Cooldown info for position cooldown tracking
export interface CooldownInfo {
  allTickersInCooldown: boolean;
  cooldownEndDate: Date | null;
  tickerCooldowns: Record<string, Date>; // ticker → cooldown expiry
}

// Consensus info structure
interface ConsensusInfo {
  timestamps: Date[];
  markers: ConsensusMarkerData[];
  lastConsensus: Date | null;
  nextConsensus: Date | null;
  cooldownInfo: CooldownInfo | null;
}

// Context interface
interface SimulationDataContextValue {
  // Core entities (from REST)
  simulation: SimulationResponse | null;
  strategy: StrategyResponse | null;
  portfolioSnapshots: PortfolioSnapshot[];
  marketEvents: MarketEvents;

  // Real-time updates (from WebSocket)
  livePortfolioData: PortfolioData | null;
  agentDecisions: Record<string, LLMDecisionMessage>;
  orchestratorDecisionsByTicker: Record<string, LLMDecisionMessage>;
  tradeExecutions: TradeExecutionMessage[];
  tickerIndicators: Record<string, TickerIndicatorData>;
  isProcessingConsensus: boolean;
  consensusRoundProgress: Record<string, LLMDecisionMessage>;

  // Computed/derived data (memoized)
  portfolioData: PortfolioData | null;
  consensusHistory: any[] | undefined; // ConsensusWithAgents[]
  executedConsensusDecisions: any[] | undefined;
  consensusInfo: ConsensusInfo;
  configuredTickers: { crypto: string[]; stock: string[] };
  enabledAgents: string[] | null;
  consensusMethod: string | null;
  evaluationIntervalHours: number | null;
  tradingModeInfo: TradingModeInfo | null;
  marketDataTimeWindow: { from: string; to: string; limit: number; currentTime?: Date };

  // Loading states
  isLoading: boolean;
  simulationLoading: boolean;
  strategyLoading: boolean;
  error: Error | null;
  isConnected: boolean;

  // Action filter (BUY/SELL/HOLD toggle)
  actionFilter: Set<string>;
  setActionFilter: (filter: Set<string>) => void;

  // Historical indicators from consensus agent decisions (for completed backtests)
  selectedConsensusIndicators: Record<string, TickerIndicatorData>;
  setSelectedConsensusIndicators: (indicators: Record<string, TickerIndicatorData>) => void;

  // Live stall evidence from the WebSocket (the polled health endpoint lags up to 15s)
  lastSkip: { reason: string; at: number } | null;
  lastEngineError: { message: string; at: number } | null;

  // Actions
  refetchConsensusHistory: () => void;
  refetchConsensusDecisions: () => void;
  setIsProcessingConsensus: (processing: boolean) => void;
}

const SimulationDataContext = createContext<SimulationDataContextValue | null>(null);

// Trading mode info - either interval-based (CRYPTO) or scheduled (STOCK)
interface TradingModeInfo {
  mode: 'CRYPTO' | 'STOCK';
  // For CRYPTO mode: evaluation_interval_seconds
  evaluationIntervalSeconds: number | null;
  evaluationIntervalHours: number | null;
  // For STOCK mode: scheduled evaluation times
  evaluationTimes: string[] | null; // e.g., ["10:00", "14:00"]
  timezone: string | null;
  tradingDays: string[] | null;
  marketOpen: string | null;
  marketClose: string | null;
  // Position cooldown (applies to both modes)
  positionCooldownDays: number | null;
}

// Helper: Parse trading mode and scheduling info from strategy markdown
function parseTradingModeInfo(markdownContent: string): TradingModeInfo {
  const defaultResult: TradingModeInfo = {
    mode: 'CRYPTO',
    evaluationIntervalSeconds: null,
    evaluationIntervalHours: null,
    evaluationTimes: null,
    timezone: null,
    tradingDays: null,
    marketOpen: null,
    marketClose: null,
    positionCooldownDays: null,
  };

  try {
    const yamlMatch = markdownContent.match(/^---\n([\s\S]*?)\n---/);
    if (!yamlMatch) return defaultResult;

    const yamlContent = yamlMatch[1];

    // Parse position_cooldown (e.g., "position_cooldown: 14d")
    const cooldownMatch = yamlContent.match(/position_cooldown:\s*(\d+)d/);
    const positionCooldownDays = cooldownMatch ? parseInt(cooldownMatch[1], 10) : null;

    // Check if STOCK mode (has stock_tickers and schedule with evaluation_times)
    // Strategy YAML uses live_schedule: (not market_hours:) for stock strategies
    // Flow-aware: `stock_tickers: [AAPL]` is as valid as a block list, and the
    // block-only test classified every inline stock strategy as CRYPTO mode.
    const hasStockTickers = parseTickerList(yamlContent, 'stock_tickers').length > 0;
    const hasSchedule = yamlContent.includes('live_schedule:') || yamlContent.includes('market_hours:');

    if (hasStockTickers && hasSchedule) {
      // STOCK mode - parse live_schedule / market_hours configuration
      const timezoneMatch = yamlContent.match(/timezone:\s*"([^"]+)"/);
      const evaluationTimesMatch = yamlContent.match(/evaluation_times:\s*\[([^\]]+)\]/);
      const tradingDaysMatch = yamlContent.match(/trading_days:\s*\[([^\]]+)\]/);
      const marketOpenMatch = yamlContent.match(/market_open:\s*"([^"]+)"/);
      const marketCloseMatch = yamlContent.match(/market_close:\s*"([^"]+)"/);

      const evaluationTimes = evaluationTimesMatch
        ? evaluationTimesMatch[1].split(',').map(t => t.trim().replace(/"/g, ''))
        : null;

      const tradingDays = tradingDaysMatch
        ? tradingDaysMatch[1].split(',').map(d => d.trim().replace(/"/g, ''))
        : null;

      return {
        mode: 'STOCK',
        evaluationIntervalSeconds: null,
        evaluationIntervalHours: null,
        evaluationTimes,
        timezone: timezoneMatch ? timezoneMatch[1] : 'America/New_York',
        tradingDays,
        marketOpen: marketOpenMatch ? marketOpenMatch[1] : '09:30',
        marketClose: marketCloseMatch ? marketCloseMatch[1] : '16:00',
        positionCooldownDays,
      };
    }

    // CRYPTO mode — `live_schedule.interval` (current format, e.g. `1w`), with
    // `evaluation_interval_seconds` kept as the legacy fallback.
    const intervalHours = parseIntervalHours(yamlContent);
    if (intervalHours != null) {
      return {
        ...defaultResult,
        mode: 'CRYPTO',
        evaluationIntervalSeconds: Math.round(intervalHours * 3600),
        evaluationIntervalHours: intervalHours,
        positionCooldownDays,
      };
    }

    // Fall back to legacy format
    const legacyMatch = markdownContent.match(/Every (\d+) hours/i);
    if (legacyMatch) {
      const hours = parseInt(legacyMatch[1], 10);
      return {
        ...defaultResult,
        mode: 'CRYPTO',
        evaluationIntervalSeconds: hours * 3600,
        evaluationIntervalHours: hours,
        positionCooldownDays,
      };
    }

    return { ...defaultResult, positionCooldownDays };
  } catch (error) {
    console.error('Failed to parse trading mode info:', error);
    return defaultResult;
  }
}

// Legacy helper: Parse evaluation interval from strategy markdown (for backward compatibility)
function parseEvaluationInterval(markdownContent: string): number | null {
  const info = parseTradingModeInfo(markdownContent);
  return info.evaluationIntervalHours;
}

// Helper: Parse configured tickers from strategy
// Ticker-list parsing lives in ../lib/strategyFrontmatter so it can be unit-tested
// without mounting this provider.

// Helper: Parse consensus method from strategy YAML frontmatter
function parseConsensusMethod(markdownContent: string): string | null {
  try {
    const yamlMatch = markdownContent.match(/^---\n([\s\S]*?)\n---/);
    if (!yamlMatch) return null;
    const yamlContent = yamlMatch[1];
    const match = yamlContent.match(/consensus_method:\s*(\S+)/);
    return match ? match[1].toUpperCase() : null;
  } catch {
    return null;
  }
}

// Helper: Parse enabled agents from strategy YAML frontmatter
function parseEnabledAgents(markdownContent: string): string[] | null {
  try {
    const yamlMatch = markdownContent.match(/^---\n([\s\S]*?)\n---/);
    if (!yamlMatch) return null;

    const yamlContent = yamlMatch[1];

    // Check for agents list in YAML
    const agentsMatch = yamlContent.match(/agents:\s*\n((?:\s+-\s+\w+\s*(?:\n|$))*)/);
    if (!agentsMatch) return null;

    const agents = agentsMatch[1]
      .split('\n')
      .map(line => line.trim().replace(/^-\s+/, ''))
      .filter(agent => agent.length > 0);

    return agents.length > 0 ? agents : null;
  } catch {
    return null;
  }
}

export function SimulationDataProvider({
  children,
  simulationId,
}: {
  children: ReactNode;
  simulationId: string;
}) {
  const timeNav = useTimeNavigation();

  // WebSocket connection
  const { isConnected } = useWebSocket();
  useSimulationStream(simulationId);

  // Local state for WebSocket updates
  const [agentDecisions, setAgentDecisions] = useState<Record<string, LLMDecisionMessage>>({});
  const [orchestratorDecisionsByTicker, setOrchestratorDecisionsByTicker] = useState<
    Record<string, LLMDecisionMessage>
  >({});
  const [livePortfolioData, setLivePortfolioData] = useState<PortfolioData | null>(null);
  const [tradeExecutions, setTradeExecutions] = useState<TradeExecutionMessage[]>([]);
  const [isProcessingConsensus, setIsProcessingConsensus] = useState(false);
  const [consensusRoundProgress, setConsensusRoundProgress] = useState<Record<string, LLMDecisionMessage>>({});
  const [tickerIndicators, setTickerIndicators] = useState<Record<string, TickerIndicatorData>>({});
  const [actionFilter, setActionFilter] = useState<Set<string>>(new Set(['BUY', 'SELL']));
  const [lastSkip, setLastSkip] = useState<{ reason: string; at: number } | null>(null);
  const [lastEngineError, setLastEngineError] = useState<{ message: string; at: number } | null>(null);
  const [selectedConsensusIndicators, setSelectedConsensusIndicators] = useState<Record<string, TickerIndicatorData>>({});

  // Fetch core entities
  const { data: simulation, isLoading: simulationLoading, error: simulationError } = useSimulation(simulationId);
  const { data: strategy, isLoading: strategyLoading, error: strategyError } = useStrategy(
    simulation?.strategyId || ''
  );

  // Fetch lightweight consensus decisions for timeline (no agent details, all BUY/SELL, no limit)
  const {
    data: consensusHistory,
    isLoading: historyLoading,
    error: historyError,
    refetch: refetchConsensusHistory,
  } = useConsensusDecisions(simulationId, undefined, 'BUY,SELL,HOLD', 1000);

  const {
    data: executedConsensusDecisions,
    refetch: refetchConsensusDecisions,
  } = useConsensusDecisions(simulationId, true, 'BUY,SELL');

  // Fetch portfolio snapshots
  // For LIVE simulations (endDate = null), use current time as end date
  // For historical simulations, use the endDate from the simulation
  // IMPORTANT: We need to recalculate this when livePortfolioData updates to fetch new snapshots
  const portfolioEndDate = useMemo(() => {
    if (!simulation) return '';

    // For LIVE simulations (endDate = null), use current time — QUANTIZED TO THE MINUTE.
    //
    // This value goes into the portfolio-snapshots React Query key. Returning a raw
    // new Date().toISOString() produced a millisecond-precision key that changed on every
    // recompute (each WebSocket portfolio update, each simulation refetch), so every render
    // minted a brand-new cache entry: the previous snapshots were dropped, the query refetched
    // from scratch, and while it was in flight portfolioSnapshots was undefined — which the chart
    // renders as "No portfolio data yet". A LIVE simulation therefore showed an empty chart most
    // of the time despite having hundreds of snapshots. Three different keys inside 42 seconds
    // were observed on one page load.
    //
    // Rounding to the minute keeps the key stable between ticks while still following the
    // simulation forward; LIVE snapshots arrive every few minutes, so nothing is lost.
    if (!simulation.endDate) {
      const now = Date.now();
      return new Date(now - (now % 60_000)).toISOString();
    }

    // For historical/completed simulations, use the fixed endDate
    return simulation.endDate;
  }, [simulation, livePortfolioData]); // Add livePortfolioData as dependency to trigger refetch

  // 'daily' keeps one snapshot per calendar day, which is right for a months-long backtest and
  // catastrophic for a young one: a simulation started today collapses to a SINGLE point, and the
  // chart needs two before it will draw anything. Every LIVE simulation therefore showed
  // "No portfolio data yet" for its first day despite having dozens of snapshots — 51 of them
  // reduced to 1 in the case that surfaced this. Ask for the full series over a short span and
  // let the existing client-side decimation (capped at 500 points) do the thinning.
  const portfolioResolution = useMemo<'all' | 'daily'>(() => {
    if (!simulation?.startDate) return 'daily';
    const spanMs = new Date(portfolioEndDate).getTime() - new Date(simulation.startDate).getTime();
    return Number.isFinite(spanMs) && spanMs < 3 * 24 * 60 * 60 * 1000 ? 'all' : 'daily';
    // `simulation`, not `simulation?.startDate`: the React Compiler infers the whole object as the
    // dependency and refuses to optimize the component when the declared list is narrower than
    // what it infers ("Inferred less specific property than source"), which is a lint error, not a
    // warning. Widening costs nothing here — portfolioEndDate above already recomputes on any
    // `simulation` change, so this memo was never going to be more stable than that anyway.
  }, [simulation, portfolioEndDate]);

  const { data: portfolioSnapshots, isLoading: portfolioLoading } = usePortfolioSnapshots(
    simulationId,
    simulation?.startDate || '',
    portfolioEndDate,
    { resolution: portfolioResolution }
  );

  // TODO: LLM decisions should be fetched on-demand when user selects a consensus,
  // not loaded upfront for all simulations. Removed wasteful useLLMDecisions() call.
  // When implementing on-demand loading, fetch via: useLLMDecisions(simulationId, consensusId)

  // Parse configured tickers from strategy
  const configuredTickers = useMemo(() => {
    if (!strategy?.markdownContent) return { crypto: [], stock: [] };
    return parseConfiguredTickers(strategy.markdownContent);
  }, [strategy]);

  // Parse enabled agents from strategy (null = all 4 agents)
  const enabledAgents = useMemo(() => {
    if (!strategy?.markdownContent) return null;
    return parseEnabledAgents(strategy.markdownContent);
  }, [strategy]);

  // Parse consensus method from strategy (null = default ORCHESTRATOR_OVERRIDE)
  const consensusMethod = useMemo(() => {
    if (!strategy?.markdownContent) return null;
    return parseConsensusMethod(strategy.markdownContent);
  }, [strategy]);

  // Parse evaluation interval (legacy - for backward compatibility)
  const evaluationIntervalHours = useMemo(() => {
    if (!strategy?.markdownContent) return null;
    return parseEvaluationInterval(strategy.markdownContent);
  }, [strategy]);

  // Parse full trading mode info (CRYPTO vs STOCK)
  const tradingModeInfo = useMemo(() => {
    if (!strategy?.markdownContent) return null;
    return parseTradingModeInfo(strategy.markdownContent);
  }, [strategy]);

  // Calculate consensus info - handles both CRYPTO (interval) and STOCK (scheduled) modes
  const consensusInfo = useMemo((): ConsensusInfo => {
    if (!simulation) {
      return { timestamps: [], markers: [], lastConsensus: null, nextConsensus: null, cooldownInfo: null };
    }

    const sorted = (consensusHistory || []).slice().sort(
      (a, b) => new Date(a.decisionTimestamp).getTime() - new Date(b.decisionTimestamp).getTime()
    );

    // Build timestamps from consensus decisions (lightweight, no agent data)
    const timestamps = sorted.map(c => new Date(c.decisionTimestamp));

    // Build enriched markers by parsing finalAction JSON
    const markers: ConsensusMarkerData[] = sorted.map(c => {
      let action: 'BUY' | 'SELL' | 'HOLD' = 'HOLD';
      let symbol = '';
      let confidence: number | null = null;
      let reasoning: string | null = null;
      try {
        const parsed = typeof c.finalAction === 'string' ? JSON.parse(c.finalAction) : c.finalAction;
        action = parsed?.action || 'HOLD';
        symbol = parsed?.symbol || '';
        confidence = parsed?.confidence != null ? parsed.confidence : null;
        reasoning = parsed?.reasoning || c.notes || null;
      } catch {
        // keep defaults
      }
      return { id: String(c.id), timestamp: new Date(c.decisionTimestamp), action, symbol, confidence, reasoning };
    });

    const lastConsensus = timestamps.length > 0 ? timestamps[timestamps.length - 1] : null;

    let nextConsensus: Date | null = null;

    if (tradingModeInfo?.mode === 'STOCK' && tradingModeInfo.evaluationTimes) {
      // STOCK mode: Calculate next scheduled evaluation time
      const evaluationTimes = tradingModeInfo.evaluationTimes;
      const timezone = tradingModeInfo.timezone || 'America/New_York';
      const tradingDays = tradingModeInfo.tradingDays || ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

      // Helper: Get day name from Date in target timezone
      const getDayName = (date: Date): string => {
        return date.toLocaleDateString('en-US', { weekday: 'long', timeZone: timezone }).toUpperCase();
      };

      // Helper: Parse time string to hours/minutes
      const parseTime = (timeStr: string): { hour: number; minute: number } => {
        const [hour, minute] = timeStr.split(':').map(Number);
        return { hour, minute };
      };

      // Helper: Create Date for specific time in target timezone on a given date
      const createDateInTimezone = (baseDate: Date, hour: number, minute: number): Date => {
        // Get the date parts in target timezone
        const dateStr = baseDate.toLocaleDateString('en-CA', { timeZone: timezone }); // YYYY-MM-DD format
        const [year, month, day] = dateStr.split('-').map(Number);

        // Create a date string in the target timezone and convert to local
        const targetTimeStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;

        // Use Intl to find the offset
        const formatter = new Intl.DateTimeFormat('en-US', {
          timeZone: timezone,
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: false,
        });

        // Create approximate date and adjust for timezone
        const approxDate = new Date(`${targetTimeStr}`);
        const parts = formatter.formatToParts(approxDate);
        const tzHour = parseInt(parts.find(p => p.type === 'hour')?.value || '0', 10);
        const tzMinute = parseInt(parts.find(p => p.type === 'minute')?.value || '0', 10);

        // Calculate offset and adjust
        const currentMinutes = approxDate.getHours() * 60 + approxDate.getMinutes();
        const targetMinutes = tzHour * 60 + tzMinute;
        const offsetMinutes = currentMinutes - targetMinutes;

        return new Date(approxDate.getTime() + offsetMinutes * 60 * 1000);
      };

      const now = new Date();
      // Reference time must be the max of lastConsensus and now to ensure:
      // 1. We don't re-trigger a past consensus
      // 2. The next consensus is actually in the future
      const referenceTime = new Date(Math.max(lastConsensus?.getTime() || 0, now.getTime()));

      // Find next scheduled time - start from today (not lastConsensus date which may be in past)
      for (let dayOffset = 0; dayOffset < 7; dayOffset++) {
        const checkDate = new Date(now.getTime() + dayOffset * 24 * 60 * 60 * 1000);
        const dayName = getDayName(checkDate);

        if (!tradingDays.includes(dayName)) continue;

        for (const evalTime of evaluationTimes) {
          const { hour, minute } = parseTime(evalTime);
          const scheduledTime = createDateInTimezone(checkDate, hour, minute);

          // Must be in the future (after both last consensus AND now)
          if (scheduledTime > referenceTime) {
            nextConsensus = scheduledTime;
            break;
          }
        }

        if (nextConsensus) break;
      }
    } else if (evaluationIntervalHours) {
      // CRYPTO mode: Calculate based on interval
      if (lastConsensus) {
        nextConsensus = new Date(lastConsensus.getTime() + evaluationIntervalHours * 60 * 60 * 1000);
      } else {
        const startTime = new Date(simulation.startDate);
        nextConsensus = new Date(startTime.getTime() + evaluationIntervalHours * 60 * 60 * 1000);
      }
    }

    // Compute position cooldown info
    let cooldownInfo: CooldownInfo | null = null;
    const cooldownDays = tradingModeInfo?.positionCooldownDays;

    if (cooldownDays && cooldownDays > 0 && markers.length > 0) {
      const allTickers = [...configuredTickers.crypto, ...configuredTickers.stock];
      const tickerCooldowns: Record<string, Date> = {};
      const now = new Date();

      // Find last BUY or SELL per ticker
      // Helper: compute midnight (start-of-day) in a timezone for a date + N days
      const calendarDayCooldownEnd = (tradeDate: Date, days: number, tz: string): Date => {
        // Get trade date string in target timezone (YYYY-MM-DD)
        const dateStr = tradeDate.toLocaleDateString('en-CA', { timeZone: tz });
        const [year, month, day] = dateStr.split('-').map(Number);
        // Add N calendar days
        const targetDate = new Date(year, month - 1, day + days);
        const targetYear = targetDate.getFullYear();
        const targetMonth = String(targetDate.getMonth() + 1).padStart(2, '0');
        const targetDay = String(targetDate.getDate()).padStart(2, '0');
        // Create midnight in the target timezone using the same Intl offset approach
        const midnightStr = `${targetYear}-${targetMonth}-${targetDay}T00:00:00`;
        const approxDate = new Date(midnightStr);
        const formatter = new Intl.DateTimeFormat('en-US', {
          timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit',
          hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
        });
        const parts = formatter.formatToParts(approxDate);
        const tzHour = parseInt(parts.find(p => p.type === 'hour')?.value || '0', 10);
        const tzMinute = parseInt(parts.find(p => p.type === 'minute')?.value || '0', 10);
        const currentMinutes = approxDate.getHours() * 60 + approxDate.getMinutes();
        const targetMinutes = tzHour * 60 + tzMinute;
        const offsetMinutes = currentMinutes - targetMinutes;
        return new Date(approxDate.getTime() + offsetMinutes * 60 * 1000);
      };

      for (const ticker of allTickers) {
        const lastTrade = [...markers]
          .reverse()
          .find(m => m.symbol === ticker && (m.action === 'BUY' || m.action === 'SELL'));
        if (lastTrade) {
          let cooldownExpiry: Date;
          if (tradingModeInfo?.mode === 'STOCK' && cooldownDays >= 1) {
            // Calendar-day snapping: trade date + N days at midnight in strategy TZ
            const tz = tradingModeInfo.timezone || 'America/New_York';
            cooldownExpiry = calendarDayCooldownEnd(lastTrade.timestamp, cooldownDays, tz);
          } else {
            // CRYPTO / sub-day: exact duration
            cooldownExpiry = new Date(lastTrade.timestamp.getTime() + cooldownDays * 24 * 60 * 60 * 1000);
          }
          if (cooldownExpiry > now) {
            tickerCooldowns[ticker] = cooldownExpiry;
          }
        }
      }

      const tickersWithCooldown = Object.keys(tickerCooldowns);
      const allTickersInCooldown = allTickers.length > 0 && tickersWithCooldown.length === allTickers.length;

      if (tickersWithCooldown.length > 0) {
        const latestExpiry = new Date(Math.max(...Object.values(tickerCooldowns).map(d => d.getTime())));
        cooldownInfo = {
          allTickersInCooldown,
          cooldownEndDate: latestExpiry,
          tickerCooldowns,
        };

        // If all tickers are in cooldown and the cooldown extends past the scheduled nextConsensus,
        // adjust nextConsensus to the first scheduled evaluation time AFTER the cooldown ends
        if (allTickersInCooldown && nextConsensus && latestExpiry > nextConsensus) {
          if (tradingModeInfo?.mode === 'STOCK' && tradingModeInfo.evaluationTimes) {
            // Reuse STOCK scheduling logic to find first eval time after cooldown
            const evaluationTimes = tradingModeInfo.evaluationTimes;
            const timezone = tradingModeInfo.timezone || 'America/New_York';
            const tradingDays = tradingModeInfo.tradingDays || ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

            const getDayName = (date: Date): string =>
              date.toLocaleDateString('en-US', { weekday: 'long', timeZone: timezone }).toUpperCase();

            const parseTime = (timeStr: string): { hour: number; minute: number } => {
              const [hour, minute] = timeStr.split(':').map(Number);
              return { hour, minute };
            };

            const createDateInTimezone = (baseDate: Date, hour: number, minute: number): Date => {
              const dateStr = baseDate.toLocaleDateString('en-CA', { timeZone: timezone });
              const [year, month, day] = dateStr.split('-').map(Number);
              const targetTimeStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;
              const approxDate = new Date(targetTimeStr);
              const formatter = new Intl.DateTimeFormat('en-US', {
                timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
                hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
              });
              const parts = formatter.formatToParts(approxDate);
              const tzHour = parseInt(parts.find(p => p.type === 'hour')?.value || '0', 10);
              const tzMinute = parseInt(parts.find(p => p.type === 'minute')?.value || '0', 10);
              const currentMinutes = approxDate.getHours() * 60 + approxDate.getMinutes();
              const targetMinutes = tzHour * 60 + tzMinute;
              const offsetMinutes = currentMinutes - targetMinutes;
              return new Date(approxDate.getTime() + offsetMinutes * 60 * 1000);
            };

            nextConsensus = null;
            for (let dayOffset = 0; dayOffset < 14; dayOffset++) {
              const checkDate = new Date(latestExpiry.getTime() + dayOffset * 24 * 60 * 60 * 1000);
              const dayName = getDayName(checkDate);
              if (!tradingDays.includes(dayName)) continue;

              for (const evalTime of evaluationTimes) {
                const { hour, minute } = parseTime(evalTime);
                const scheduledTime = createDateInTimezone(checkDate, hour, minute);
                if (scheduledTime > latestExpiry) {
                  nextConsensus = scheduledTime;
                  break;
                }
              }
              if (nextConsensus) break;
            }
          } else if (evaluationIntervalHours) {
            // CRYPTO mode: advance nextConsensus past cooldown end
            let candidate = nextConsensus;
            const intervalMs = evaluationIntervalHours * 60 * 60 * 1000;
            while (candidate <= latestExpiry) {
              candidate = new Date(candidate.getTime() + intervalMs);
            }
            nextConsensus = candidate;
          }
        }
      }
    }

    return { timestamps, markers, lastConsensus, nextConsensus, cooldownInfo };
  }, [consensusHistory, simulation, evaluationIntervalHours, tradingModeInfo, configuredTickers]);

  // Determine which timestamp to use for market data
  const selectedTimestamp = useMemo(() => {
    const isLiveMode = timeNav.state.mode === 'live';
    if (isLiveMode) return new Date().toISOString(); // Live: use current time
    // Historical mode - use selected timestamp from timeline
    return timeNav.state.selectedTimestamp;
  }, [timeNav.state.mode, timeNav.state.selectedTimestamp]);

  // Fetch market data
  const { data: marketDataContextResponse, isLoading: marketDataContextLoading } = useMarketDataContext(
    simulationId,
    selectedTimestamp ?? undefined,
    !!selectedTimestamp
  );

  const marketDataTimeWindow = useMemo(() => {
    if (!simulation) return { from: '', to: '', limit: 100 };

    const now = new Date();
    const simStart = new Date(simulation.startDate);
    const simEnd = new Date(simulation.endDate);

    let currentTime: Date;
    if (consensusInfo.lastConsensus) {
      // We have consensus history - use the last consensus timestamp
      currentTime = consensusInfo.lastConsensus;
    } else if (simulation.status === 'SIMULATING') {
      // Backtest with no consensus yet - use simulation start date
      currentTime = simStart;
    } else if (simulation.status === 'LIVE') {
      // LIVE mode with no consensus yet - use current time (capped by simEnd)
      currentTime = now < simEnd ? now : simEnd;
    } else {
      // PENDING, COMPLETED, FAILED - use simulation start date
      currentTime = simStart;
    }

    const from = new Date(currentTime.getTime() - 24 * 60 * 60 * 1000).toISOString();
    const to = currentTime.toISOString();

    return { from, to, limit: 100, currentTime };
  }, [simulation, consensusInfo.lastConsensus]);

  const fallbackParams = useMemo(() => {
    if (selectedTimestamp) {
      return { from: '', to: '', limit: 100 };
    }
    return marketDataTimeWindow;
  }, [selectedTimestamp, marketDataTimeWindow]);

  const { data: fallbackMarketDataResponse } = useMarketData(fallbackParams);

  // Transform market data
  const marketEvents = useMemo((): MarketEvents => {
    const cryptoMap: Record<string, Array<{ price: number; currency: string; timestamp: string }>> = {};
    const stocksMap: Record<string, Array<{ price: number; currency: string; timestamp: string }>> = {};
    const news: Array<{ title: string; source: string; timestamp: string }> = [];

    const processEvents = (events: any[]) => {
      events.forEach((event: any) => {
        const isConfiguredCrypto = configuredTickers.crypto.includes(event.symbol);
        const isConfiguredStock = configuredTickers.stock.includes(event.symbol);

        if (event.eventType === 'PRICE') {
          const price = (event.rawData as any)?.price || 0;
          const currency = (event.rawData as any)?.currency || 'USD';

          if (isConfiguredCrypto) {
            if (!cryptoMap[event.symbol]) cryptoMap[event.symbol] = [];
            cryptoMap[event.symbol].push({ price, currency, timestamp: event.timestamp });
          } else if (isConfiguredStock) {
            if (!stocksMap[event.symbol]) stocksMap[event.symbol] = [];
            stocksMap[event.symbol].push({ price, currency, timestamp: event.timestamp });
          }
        } else if (event.eventType === 'NEWS' || event.eventType === 'SOCIAL') {
          if (isConfiguredCrypto || isConfiguredStock) {
            const title = (event.rawData as any)?.title || (event.rawData as any)?.headline || 'No title';
            const source = event.source || 'Unknown source';
            news.push({ title, source, timestamp: event.timestamp });
          }
        }
      });
    };

    // Process market data context response
    if (marketDataContextResponse?.tickers) {
      Object.values(marketDataContextResponse.tickers).forEach(tickerData => {
        Object.values(tickerData.timeframes).forEach(timeframeData => {
          processEvents(timeframeData.events);
        });
      });
    } else if (fallbackMarketDataResponse?.events) {
      processEvents(fallbackMarketDataResponse.events);
    }

    // Sort by timestamp (newest first)
    const sortByTimestamp = (a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();

    const crypto = Object.keys(cryptoMap)
      .sort()
      .map(symbol => ({
        symbol,
        events: cryptoMap[symbol].sort(sortByTimestamp),
      }));

    const stocks = Object.keys(stocksMap)
      .sort()
      .map(symbol => ({
        symbol,
        events: stocksMap[symbol].sort(sortByTimestamp),
      }));

    news.sort(sortByTimestamp);

    return { crypto, stocks, news };
  }, [marketDataContextResponse, fallbackMarketDataResponse, configuredTickers]);

  // Stable value history — only recomputes when snapshot data changes, NOT on timeline navigation
  const portfolioValueHistory = useMemo(() => {
    const isLiveSimulation = simulation?.status === 'SIMULATING' || simulation?.status === 'LIVE';
    if (isLiveSimulation && livePortfolioData) return livePortfolioData.valueHistory;
    if (!portfolioSnapshots || portfolioSnapshots.length === 0) return null;

    // Decimate snapshots before expensive processing for long simulations
    let chartSnapshots = portfolioSnapshots;
    if (chartSnapshots.length > 500) {
      const stride = Math.ceil(chartSnapshots.length / 500);
      chartSnapshots = chartSnapshots.filter((_, i) => i === 0 || i === chartSnapshots.length - 1 || i % stride === 0);
    }

    // Use cached Intl.DateTimeFormat (much faster than per-call toLocaleTimeString)
    const isLongDuration = chartSnapshots.length > 30;
    const timestampFmt = isLongDuration
      ? new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: '2-digit', timeZone: 'America/New_York' })
      : new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'America/New_York' });

    return chartSnapshots
      .map(snapshot => {
        const positions = (snapshot.positions || {}) as Record<string, any>;
        const tickerValues: Record<string, number> = {};

        // Union with the snapshot's own symbols — see the note in `portfolioData`.
        const allTickers = Array.from(new Set([
          ...configuredTickers.crypto,
          ...configuredTickers.stock,
          ...Object.keys(positions),
        ]));
        allTickers.forEach(symbol => {
          const positionData = positions[symbol];
          if (positionData) {
            const price = positionData.currentPrice != null ? positionData.currentPrice : positionData.averagePrice;
            tickerValues[symbol] = (positionData.quantity || 0) * (price || 0);
          } else {
            tickerValues[symbol] = 0;
          }
        });

        const fullDate = new Date(snapshot.timestamp);
        return {
          timestamp: timestampFmt.format(fullDate),
          fullTimestamp: fullDate,
          tickers: tickerValues,
          cash: snapshot.cashBalance,
          total: snapshot.totalValue,
        };
      })
      .reverse();
  }, [portfolioSnapshots, configuredTickers, livePortfolioData, simulation?.status]);

  // Holdings + summary — depends on marketEvents (volatile on timeline nav), but cheap to compute
  const portfolioData = useMemo((): PortfolioData | null => {
    const isLiveSimulation = simulation?.status === 'SIMULATING' || simulation?.status === 'LIVE';
    if (isLiveSimulation && livePortfolioData) return livePortfolioData;
    if (!portfolioSnapshots || portfolioSnapshots.length === 0) return null;

    const latestSnapshot = portfolioSnapshots[0];
    const positions = (latestSnapshot.positions || {}) as Record<string, any>;

    const latestMarketPrices: Record<string, number> = {};
    marketEvents.crypto.forEach(ticker => {
      if (ticker.events.length > 0) {
        latestMarketPrices[ticker.symbol] = ticker.events[0].price;
      }
    });
    marketEvents.stocks.forEach(ticker => {
      if (ticker.events.length > 0) {
        latestMarketPrices[ticker.symbol] = ticker.events[0].price;
      }
    });

    // Drive holdings off what the portfolio actually holds, unioned with the
    // strategy's configured tickers. Listing only configured tickers meant a
    // position in a symbol the strategy no longer declares — or one the
    // frontmatter parser failed to read — vanished from the table while its
    // value stayed in Total equity, reading as "100% cash" next to a
    // non-cash balance.
    const allTickers = Array.from(new Set([
      ...configuredTickers.crypto,
      ...configuredTickers.stock,
      ...Object.keys(positions),
    ]));
    const holdings = allTickers.map(symbol => {
      const positionData = positions[symbol];
      const quantity = positionData?.quantity || 0;
      const averagePrice = positionData?.averagePrice || 0;
      const snapshotPrice = positionData?.currentPrice || averagePrice;
      const currentPrice = latestMarketPrices[symbol] || snapshotPrice;
      const change = currentPrice > 0 && averagePrice > 0 ? (currentPrice - averagePrice) / averagePrice : 0;
      // Prefer the asset class the backend recorded on the position; fall back
      // to the strategy config for tickers that are configured but unheld.
      const positionType = typeof positionData?.investmentType === 'string'
        ? positionData.investmentType.toUpperCase()
        : null;
      const type = positionType
        ? (positionType === 'CRYPTO' ? 'crypto' : 'stock')
        : (configuredTickers.crypto.includes(symbol) ? 'crypto' : 'stock');

      return {
        symbol, amount: quantity, buyPrice: averagePrice, currentPrice, change,
        type: type as 'crypto' | 'stock',
      };
    });

    return {
      holdings,
      cash: latestSnapshot.cashBalance,
      totalValue: latestSnapshot.totalValue,
      valueHistory: portfolioValueHistory || [],
    };
  }, [portfolioSnapshots, configuredTickers, livePortfolioData, marketEvents, simulation, portfolioValueHistory]);

  // WebSocket message handlers
  useStreamMessage<ConsensusStartedMessage>(
    'CONSENSUS_STARTED',
    message => {
      console.log('Consensus started:', message.consensusRequestId);
      timeNav.setActiveConsensus(message.consensusRequestId);
      setIsProcessingConsensus(true);
      setConsensusRoundProgress({});
    },
    [simulationId, timeNav]
  );

  useStreamMessage<LLMDecisionMessage>(
    'LLM_DECISION',
    message => {
      if (timeNav.state.activeConsensusId) {
        timeNav.setActiveConsensus(null);
      }

      // Track progress for the consensus banner (all agent types including orchestrator)
      setConsensusRoundProgress(prev => ({
        ...prev,
        [message.agentType]: message,
      }));

      if (message.agentType === 'ORCHESTRATOR') {
        const ticker = message.ticker || 'UNKNOWN';
        setOrchestratorDecisionsByTicker(prev => ({
          ...prev,
          [ticker]: message,
        }));
        refetchConsensusHistory();
        refetchConsensusDecisions();
        // Clear processing flag when orchestrator (final agent) submits
        setIsProcessingConsensus(false);
      } else {
        setAgentDecisions(prev => ({
          ...prev,
          [message.agentType]: message,
        }));
      }
    },
    [simulationId, timeNav, refetchConsensusHistory, refetchConsensusDecisions]
  );

  // The backend pushes "this round was skipped and here is why" over this socket; the live
  // provider never listened (only ~2900 lines of dead legacy code in page.tsx did), so a
  // stalling run's decisions table sat frozen on "No decisions yet" while SKIP rows piled up.
  useStreamMessage<ConsensusSkippedMessage>(
    'CONSENSUS_SKIPPED',
    message => {
      setLastSkip({ reason: message.reason, at: Date.now() });
      setIsProcessingConsensus(false);
      // Skip rows are persisted decisions — refresh so the table shows them as they happen
      refetchConsensusHistory();
      refetchConsensusDecisions();
    },
    [simulationId, refetchConsensusHistory, refetchConsensusDecisions]
  );

  useStreamMessage<ErrorMessage>(
    'ERROR',
    message => {
      if (message.code === 'CONSENSUS_FAILED') {
        setIsProcessingConsensus(false);
        // message.error used to be discarded here — the one live channel that carried the
        // engine's actual failure text, dropped on the floor.
        if (message.error) {
          setLastEngineError({ message: message.error, at: Date.now() });
        }
      }
    },
    [simulationId]
  );

  useStreamMessage<TradeExecutionMessage>(
    'TRADE_EXECUTION',
    message => {
      if (message.action !== 'HOLD') {
        setTradeExecutions(prev => [message, ...prev].slice(0, 5));
      }
      // Refetch timeline data after trade execution — consensus decision is now committed to DB.
      // This works for all consensus methods (MAJORITY and ORCHESTRATOR_OVERRIDE),
      // unlike the LLM_DECISION handler which only fires for ORCHESTRATOR_OVERRIDE.
      refetchConsensusHistory();
      refetchConsensusDecisions();
    },
    [simulationId, refetchConsensusHistory, refetchConsensusDecisions]
  );

  useStreamMessage<PortfolioUpdateMessage>(
    'PORTFOLIO_UPDATE',
    message => {
      console.log('Received PORTFOLIO_UPDATE:', message);

      const positions = message.positions || {};
      const allTickers = [...configuredTickers.crypto, ...configuredTickers.stock];

      const updatedHoldings = allTickers.map(symbol => {
        const positionData = positions[symbol];
        const quantity = positionData?.quantity || 0;
        const averagePrice = positionData?.averagePrice || 0;
        const currentPrice = positionData?.currentPrice || averagePrice;
        const change = currentPrice > 0 && averagePrice > 0 ? (currentPrice - averagePrice) / averagePrice : 0;
        const type = configuredTickers.crypto.includes(symbol) ? 'crypto' : 'stock';

        return {
          symbol,
          amount: quantity,
          buyPrice: averagePrice,
          currentPrice,
          change,
          type: type as 'crypto' | 'stock',
        };
      });

      // Calculate per-ticker values for value history
      const tickerValues: Record<string, number> = {};
      const tickerList = [...configuredTickers.crypto, ...configuredTickers.stock];

      tickerList.forEach(symbol => {
        const positionData = positions[symbol];
        if (positionData) {
          const price = positionData?.currentPrice != null ? positionData.currentPrice : positionData?.averagePrice;
          tickerValues[symbol] = (positionData?.quantity || 0) * (price || 0);
        } else {
          tickerValues[symbol] = 0;
        }
      });

      // Build new value history entry
      const fullDate = new Date(message.timestamp);
      const newHistoryEntry = {
        timestamp: fullDate.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }),
        fullTimestamp: fullDate, // Keep full Date object for day separators
        tickers: tickerValues,
        cash: message.cashBalance,
        total: message.totalValue,
      };

      // Append new entry to existing history (keep last 100 entries to avoid memory bloat)
      const existingHistory = livePortfolioData?.valueHistory || portfolioData?.valueHistory || [];
      const newHistory = [...existingHistory, newHistoryEntry].slice(-100);

      setLivePortfolioData({
        holdings: updatedHoldings,
        cash: message.cashBalance,
        totalValue: message.totalValue,
        valueHistory: newHistory,
      });
    },
    [simulationId, configuredTickers, portfolioData, livePortfolioData]
  );

  // Handle real-time indicator updates from backend
  useStreamMessage<IndicatorUpdateMessage>(
    'INDICATOR_UPDATE',
    message => {
      console.log('Received INDICATOR_UPDATE:', message.ticker, message.currentPrice);

      // Parse the indicators from the message format to our TickerIndicatorData format
      const indicators = message.indicators || {};

      const rsiData = indicators.RSI_14 as { value: number; interpretation: string } | undefined;
      const macdData = indicators.MACD as { line: number; signal: number; histogram: number; interpretation: string } | undefined;
      const sma50Data = indicators.SMA_50 as { value: number } | undefined;
      const sma200Data = indicators.SMA_200 as { value: number } | undefined;
      const bollingerData = indicators.BOLLINGER as { upper: number; middle: number; lower: number } | undefined;
      const trend = (indicators.trend as string) || 'SIDEWAYS';

      const tickerData: TickerIndicatorData = {
        ticker: message.ticker,
        tickerType: message.tickerType as 'STOCK' | 'CRYPTO',
        currentPrice: message.currentPrice,
        priceChange: message.priceChange,
        priceChangePercent: message.priceChangePercent,
        indicators: {
          rsi: rsiData ? {
            value: rsiData.value,
            interpretation: rsiData.interpretation as 'oversold' | 'neutral' | 'overbought',
          } : undefined,
          macd: macdData ? {
            line: macdData.line,
            signal: macdData.signal,
            histogram: macdData.histogram,
            interpretation: macdData.interpretation as 'bullish' | 'bearish' | 'neutral',
          } : undefined,
          sma50: sma50Data?.value,
          sma200: sma200Data?.value,
          bollinger: bollingerData,
          trend: trend as 'UPTREND' | 'DOWNTREND' | 'SIDEWAYS',
        },
        lastUpdated: new Date(message.timestamp),
      };

      setTickerIndicators(prev => ({
        ...prev,
        [message.ticker]: tickerData,
      }));
    },
    [simulationId]
  );

  const value: SimulationDataContextValue = {
    // Core entities
    simulation: simulation ?? null,
    strategy: strategy ?? null,
    portfolioSnapshots: portfolioSnapshots || [],
    marketEvents,

    // Real-time updates
    livePortfolioData,
    agentDecisions,
    orchestratorDecisionsByTicker,
    tradeExecutions,
    tickerIndicators,
    isProcessingConsensus,
    consensusRoundProgress,

    // Computed data
    portfolioData,
    consensusHistory,
    executedConsensusDecisions,
    consensusInfo,
    configuredTickers,
    enabledAgents,
    consensusMethod,
    evaluationIntervalHours,
    tradingModeInfo,
    marketDataTimeWindow,

    // Loading states
    isLoading: simulationLoading || strategyLoading || portfolioLoading,
    simulationLoading,
    strategyLoading,
    error: simulationError || strategyError || historyError || null,
    isConnected,

    // Action filter
    actionFilter,
    setActionFilter,

    // Historical indicators from consensus
    selectedConsensusIndicators,
    setSelectedConsensusIndicators,

    // Live stall evidence
    lastSkip,
    lastEngineError,

    // Actions
    refetchConsensusHistory,
    refetchConsensusDecisions,
    setIsProcessingConsensus,
  };

  return <SimulationDataContext.Provider value={value}>{children}</SimulationDataContext.Provider>;
}

export function useSimulationData() {
  const context = useContext(SimulationDataContext);
  if (!context) {
    throw new Error('useSimulationData must be used within SimulationDataProvider');
  }
  return context;
}
