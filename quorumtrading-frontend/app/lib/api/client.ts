import {
  CreateStrategyRequest,
  UpdateStrategyRequest,
  StrategyResponse,
  StrategyPerformanceResponse,
  CreateSimulationRequest,
  SimulationResponse,
  SimulationHealth,
  ContributionResponse,
  MarketEvent,
  MarketDataQueryParams,
  MarketDataResponse,
  MarketDataContextResponse,
  LLMDecision,
  LLMDecisionsResponse,
  ConsensusDecision,
  ConsensusDecisionsResponse,
  ConsensusWithAgentsResponse,
  PortfolioSnapshot,
  PortfolioSnapshotsResponse,
  ChartDataPoint,
  SimulationStatus,
  CreateMarketResearchRequest,
  UpdateMarketResearchRequest,
  MarketResearchResponse,
  MarketResearchDetailsResponse,
  ResearchExecutionResponse,
  SellPositionResponse,
  LiquidateAllResponse,
  EndSimulationRequest,
  ForceEndSimulationResponse,
  RestartFromPreviewResponse,
  HistoricalEventResponse,
  DividendInfoResponse,
  FinancialMetricsResponse,
} from '../types/api';
import { LLMUsageStatistics } from '../types/usage';
import { LlmProviderStatus, SelectLlmProviderRequest } from '../types/llmProvider';
import { InstanceInfo } from '../types/instance';
import type {
  FeatureAvailability,
  FeatureFlag,
  UpdateFeatureFlagRequest,
} from '../types/features';
import { UserBudget, TopUpBudgetRequest, UpdateDailySpendLimitRequest } from '../types/budget';
import { LLMModel, ModelPreferences, UpdateModelPreferencesRequest, TradingContext, FallbackConfig, EffectiveModelsResponse, type ModelTier, CostEstimateRequest, CostEstimateResponse, ModelReliabilityResponse, NewsAvailabilityResponse } from '../types/config';
import { ApiToken, ApiTokenCreated, CreateApiTokenRequest } from '../types/apiToken';
import {
  Notification,
  NotificationPreference,
  UnreadCountResponse,
  UpdateNotificationPreferenceRequest,
  NotificationType,
} from '../types/notification';
import {
  InvestmentPlan,
  InvestmentAllocation,
  CreateInvestmentPlanRequest,
  UpdateInvestmentPlanRequest,
  InvestmentPlanStatus,
} from '../types/investmentPlan';
import { CreateCheckoutRequest, CheckoutSessionResponse, PaymentConfig } from '../types/payment';
import type { StrategySchemaResponse } from '../types/schema';
import {
  CreateBenchmarkRequest,
  CreateBenchmarkResponse,
  BenchmarkApiResponse,
  BenchmarkEntry,
  OverlayResponse,
  CompactChartData,
} from '../types/benchmark';
import {
  ChallengeResponse,
  ChallengeChartData,
  CreateChallengeRequest,
  EligibleStrategy,
} from '../types/challenge';
import {
  DataAvailabilityResponse,
  BackfillRequest,
  BackfillResponse,
  DataHorizonResponse,
} from '../types/dataAvailability';
import {
  BrokerAccount as BrokerAccountResponse,
  CreateBrokerAccountRequest as CreateBrokerAccountRequestType,
  UpdateBrokerAccountCredentialsRequest as UpdateBrokerAccountCredentialsRequestType,
  ValidateBrokerAccountResponse as ValidateBrokerAccountResponseType,
} from '../types/brokerAccount';
import { ProblemDetail, isProblemDetail } from '../types/error';
import type { AdminDashboardResponse, AdminUserSearchResponse, AdminHealthResponse } from '../types/admin';
import { notifySessionExpired, checkSessionValidity } from './sessionExpiry';
import { triggerRateLimit } from '../../components/RateLimitModal';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8085/api/v1';

// Default timeout for API calls (15 seconds) - prevents hanging when backend is down
const API_TIMEOUT_MS = 15000;

/**
 * API Error with RFC 9457 Problem Details support
 */
export class APIError extends Error {
  constructor(
    message: string,
    public status: number,
    public problemDetail?: ProblemDetail
  ) {
    super(message);
    this.name = 'APIError';
  }
}

/**
 * Error thrown when session has expired (401 Unauthorized)
 * Triggers redirect to login page with return URL preserved
 */
export class AuthSessionExpiredError extends Error {
  constructor(message: string = 'Session expired') {
    super(message);
    this.name = 'AuthSessionExpiredError';
  }
}

/**
 * Error thrown when network request fails (timeout, connection refused, etc.)
 */
export class NetworkError extends Error {
  constructor(
    message: string,
    public isTimeout: boolean = false
  ) {
    super(message);
    this.name = 'NetworkError';
  }
}

/**
 * Legacy alias for backward compatibility
 */
export class ApiClientError extends APIError {}

/**
 * Check if an error indicates budget depletion (402 Payment Required)
 */
export function isBudgetDepletedError(error: unknown): error is APIError {
  return error instanceof APIError && error.status === 402;
}

/**
 * Check if an error indicates rate limit exceeded (429 Too Many Requests)
 */
export function isRateLimitError(error: unknown): error is APIError {
  return error instanceof APIError && error.status === 429;
}

/**
 * Fetch wrapper with RFC 9457 error handling and timeout
 */
async function apiFetch<T>(
  endpoint: string,
  options?: RequestInit,
  timeoutMs: number = API_TIMEOUT_MS
): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;

  const defaultHeaders = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };

  // Set up timeout with AbortController
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  let response: Response;
  try {
    response = await fetch(url, {
      ...options,
      credentials: 'include', // Required for cross-origin cookie-based auth
      signal: controller.signal,
      headers: {
        ...defaultHeaders,
        ...options?.headers,
      },
    });
  } catch (error) {
    clearTimeout(timeoutId);
    // Handle network errors (timeout, connection refused, etc.)
    if (error instanceof Error) {
      if (error.name === 'AbortError') {
        throw new NetworkError(
          'Request timed out. The server may be unavailable.',
          true
        );
      }
      throw new NetworkError(
        `Unable to connect to server: ${error.message}`
      );
    }
    throw new NetworkError('Unable to connect to server');
  } finally {
    clearTimeout(timeoutId);
  }

  // Handle 401 Unauthorized - session expired
  // Show notification and auto-redirect after a brief delay
  if (response.status === 401) {
    notifySessionExpired();
    throw new AuthSessionExpiredError('Session expired');
  }

  // Handle 429 Too Many Requests — show rate limit modal
  if (response.status === 429) {
    const retryAfter = parseInt(response.headers.get('Retry-After') || '60', 10);
    triggerRateLimit(isNaN(retryAfter) ? 60 : retryAfter);
    throw new ApiClientError(
      'Rate limit exceeded. Please wait before retrying.',
      429
    );
  }

  // Handle error responses
  if (!response.ok) {
    // Most endpoints use optional auth and return 403/404 instead of 401
    // when the session expires. Probe /auth/me in the background to detect this.
    if (response.status === 403 || response.status === 404) {
      checkSessionValidity();
    }

    const contentType = response.headers.get('content-type');

    // Try to parse RFC 9457 Problem Detail
    if (contentType?.includes('application/json') || contentType?.includes('application/problem+json')) {
      try {
        const problemDetail = await response.json();

        if (isProblemDetail(problemDetail)) {
          throw new ApiClientError(
            problemDetail.detail || problemDetail.title,
            response.status,
            problemDetail
          );
        }
      } catch (parseError) {
        // If it's already an APIError, rethrow it
        if (parseError instanceof APIError) {
          throw parseError;
        }
        // Fall through to generic error
      }
    }

    throw new ApiClientError(
      `HTTP ${response.status}: ${response.statusText}`,
      response.status
    );
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return null as T;
  }

  // Parse successful response
  const contentType = response.headers.get('content-type');
  if (contentType?.includes('application/json')) {
    return response.json();
  }

  return null as T;
}

/**
 * Legacy alias for backward compatibility
 */
const fetchJSON = apiFetch;

export const apiClient = {
  // Strategies
  strategies: {
    list: () => apiFetch<StrategyResponse[]>('/strategies'),

    get: (id: string) => apiFetch<StrategyResponse>(`/strategies/${id}`),

    /**
     * Get aggregate performance metrics for a strategy
     * Calculates statistics from all completed simulations
     * GET /api/v1/strategies/{id}/performance
     */
    getPerformance: (id: string) =>
      apiFetch<StrategyPerformanceResponse>(`/strategies/${id}/performance`),

    create: (data: CreateStrategyRequest) =>
      apiFetch<StrategyResponse>('/strategies', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    update: (id: string, data: UpdateStrategyRequest) =>
      apiFetch<StrategyResponse>(`/strategies/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),

    delete: (id: string) =>
      apiFetch<void>(`/strategies/${id}`, {
        method: 'DELETE',
      }),

    /**
     * Get the strategy configuration schema for client-side validation.
     * Schema is cached by browser (1 hour) and describes all YAML fields,
     * enums, and conditional validation rules.
     * GET /api/v1/strategies/schema
     */
    getSchema: () => apiFetch<StrategySchemaResponse>('/strategies/schema'),

    listPublic: () => apiFetch<StrategyResponse[]>('/strategies/public'),
    getPublic: (id: string) => apiFetch<StrategyResponse>(`/strategies/public/${id}`),
    clone: (id: string) =>
      apiFetch<StrategyResponse>(`/strategies/${id}/clone`, { method: 'POST' }),
    toggleVisibility: (id: string, publicVisible: boolean) =>
      apiFetch<StrategyResponse>(`/strategies/${id}/visibility`, {
        method: 'PUT',
        body: JSON.stringify({ publicVisible }),
      }),
  },

  // Simulations
  simulations: {
    list: () => apiFetch<SimulationResponse[]>('/simulations'),

    get: (id: string) => apiFetch<SimulationResponse>(`/simulations/${id}`),

    /**
     * Live execution health — verdict, skip streak, dominant skip reasons.
     * GET /api/v1/simulations/{id}/status
     */
    health: (id: string) => apiFetch<SimulationHealth>(`/simulations/${id}/status`),

    create: (data: CreateSimulationRequest) =>
      apiFetch<SimulationResponse>('/simulations', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    start: (id: string) =>
      apiFetch<SimulationResponse>(`/simulations/${id}/start`, {
        method: 'POST',
      }),

    pause: (id: string) =>
      apiFetch<SimulationResponse>(`/simulations/${id}/pause`, {
        method: 'POST',
      }),

    resume: (id: string) =>
      apiFetch<SimulationResponse>(`/simulations/${id}/resume`, {
        method: 'POST',
      }),

    delete: (id: string) =>
      apiFetch<void>(`/simulations/${id}`, {
        method: 'DELETE',
      }),

    /**
     * Start the next queued simulation.
     * Finds the first PENDING simulation and starts it.
     * POST /api/v1/simulations/queue/start-next
     */
    startNextQueued: () =>
      apiFetch<SimulationResponse>('/simulations/queue/start-next', {
        method: 'POST',
      }),

    /**
     * Reorder the simulation queue.
     * Updates the execution order of pending simulations.
     * PATCH /api/v1/simulations/queue/reorder
     *
     * @param orderedSimulationIds List of simulation IDs in desired execution order (first = next to run)
     * @returns Array of reordered simulations
     */
    reorderQueue: (orderedSimulationIds: string[]) =>
      apiFetch<SimulationResponse[]>('/simulations/queue/reorder', {
        method: 'PATCH',
        body: JSON.stringify({ orderedSimulationIds }),
      }),

    /**
     * Get decimated chart data for simulation portfolio history.
     * Returns portfolio value data points optimized for chart rendering.
     * Large datasets are automatically decimated while preserving visual shape.
     *
     * GET /api/v1/simulations/{id}/chart-data
     *
     * @param id Simulation unique identifier
     * @param resolution Target number of data points (10-10000, default: 500)
     * @returns Array of chart data points with timestamp and portfolio value
     */
    chartData: (id: string, resolution: number = 500) =>
      apiFetch<ChartDataPoint[]>(`/simulations/${id}/chart-data?resolution=${resolution}`),

    /**
     * List contributions (DCA top-ups) for a simulation, oldest first.
     * Lightweight — just the contribution rows, no TWR/MWR recomputation.
     * GET /api/v1/simulations/{id}/contributions
     */
    contributions: (id: string) =>
      apiFetch<ContributionResponse[]>(`/simulations/${id}/contributions`),

    /**
     * Delete all paused simulations.
     * Returns the count of deleted simulations.
     * DELETE /api/v1/simulations/paused
     */
    clearPaused: () =>
      apiFetch<number>('/simulations/paused', {
        method: 'DELETE',
      }),

    /**
     * Sell an entire position at market price.
     * POST /api/v1/simulations/{id}/positions/{symbol}/sell
     */
    sellPosition: (id: string, symbol: string) =>
      apiFetch<SellPositionResponse>(`/simulations/${id}/positions/${encodeURIComponent(symbol)}/sell`, {
        method: 'POST',
      }),

    /**
     * Liquidate all positions (cancel open orders + sell everything at market).
     * POST /api/v1/simulations/{id}/liquidate
     */
    liquidateAll: (id: string) =>
      apiFetch<LiquidateAllResponse>(`/simulations/${id}/liquidate`, {
        method: 'POST',
      }),

    /**
     * Trigger a manual consensus round for a LIVE simulation.
     * POST /api/v1/simulations/{id}/trigger-consensus
     */
    triggerConsensus: (id: string) =>
      apiFetch<void>(`/simulations/${id}/trigger-consensus`, {
        method: 'POST',
      }),

    /**
     * Force-end a simulation (optionally liquidating positions first).
     * POST /api/v1/simulations/{id}/end
     */
    forceEnd: (id: string, liquidatePositions: boolean) =>
      apiFetch<ForceEndSimulationResponse>(`/simulations/${id}/end`, {
        method: 'POST',
        body: JSON.stringify({ liquidatePositions } as EndSimulationRequest),
      }),

    /**
     * Preview what will be deleted when restarting from a consensus decision.
     * POST /api/v1/simulations/{id}/restart-from/preview
     */
    restartFromPreview: (id: string, consensusDecisionId: number) =>
      apiFetch<RestartFromPreviewResponse>(`/simulations/${id}/restart-from/preview`, {
        method: 'POST',
        body: JSON.stringify({ consensusDecisionId }),
      }),

    /**
     * Restart a simulation from a specific consensus decision.
     * POST /api/v1/simulations/{id}/restart-from
     */
    restartFrom: (id: string, consensusDecisionId: number) =>
      apiFetch<RestartFromPreviewResponse>(`/simulations/${id}/restart-from`, {
        method: 'POST',
        body: JSON.stringify({ consensusDecisionId }),
      }),

    /**
     * Get buy-and-hold overlays for a simulation
     * GET /api/v1/simulations/{id}/overlays
     */
    overlays: (id: string, symbols: string[]) => {
      const params = new URLSearchParams({ symbols: symbols.join(',') });
      return apiFetch<OverlayResponse[]>(`/simulations/${id}/overlays?${params}`);
    },
  },

  // Market Data
  marketData: {
    query: (params: MarketDataQueryParams) => {
      const searchParams = new URLSearchParams();
      searchParams.append('from', params.from);
      searchParams.append('to', params.to);
      if (params.symbol) searchParams.append('symbol', params.symbol);
      if (params.eventType) searchParams.append('eventType', params.eventType);
      if (params.source) searchParams.append('source', params.source);
      if (params.limit) searchParams.append('limit', params.limit.toString());

      return apiFetch<MarketDataResponse>(`/market-data/events?${searchParams}`);
    },

    collect: () =>
      apiFetch<{ status: string; message: string; eventsCollected: number; timestamp: string }>(
        '/market-data/collect',
        { method: 'POST' }
      ),

    financialMetrics: (symbol: string) => {
      return apiFetch<FinancialMetricsResponse>(`/market-data/financial-metrics?symbol=${encodeURIComponent(symbol)}`);
    },
  },

  // Decisions
  decisions: {
    llm: {
      list: (simulationId: string, limit?: number) => {
        const params = new URLSearchParams({ simulationId });
        if (limit !== undefined) params.append('limit', limit.toString());
        return apiFetch<LLMDecisionsResponse>(`/decisions/llm?${params}`);
      },

      get: (id: string) =>
        apiFetch<LLMDecision>(`/decisions/llm/${id}`),
    },

    consensus: {
      list: (simulationId: string, executed?: boolean, action?: string, limit?: number) => {
        const params = new URLSearchParams({ simulationId });
        if (executed !== undefined) params.append('executed', executed.toString());
        if (action) params.append('action', action);
        if (limit !== undefined) params.append('limit', limit.toString());
        const url = `/decisions/consensus?${params}`;
        console.log('[API] Fetching consensus decisions:', url, { simulationId, executed, action, limit });
        return apiFetch<ConsensusDecisionsResponse>(url);
      },

      get: (id: string) =>
        apiFetch<ConsensusDecision>(`/decisions/consensus/${id}`),
    },

    consensusWithAgents: {
      list: (simulationId: string, ticker?: string, limit?: number, action?: string) => {
        const params = new URLSearchParams({ simulationId });
        if (ticker) params.append('ticker', ticker);
        if (limit) params.append('limit', limit.toString());
        if (action) params.append('action', action);
        const url = `/decisions/consensus-with-agents?${params}`;
        console.log('[API] Fetching consensus with agents:', url, { simulationId, ticker, limit, action });
        return apiFetch<ConsensusWithAgentsResponse[]>(url);
      },

      get: (consensusId: number) =>
        apiFetch<ConsensusWithAgentsResponse>(`/decisions/consensus-with-agents/${consensusId}`),
    },

    /**
     * Get market data context for a simulation at a specific timestamp.
     * Returns the exact market data that was available to AI agents during consensus evaluation,
     * grouped by ticker and timeframe.
     *
     * @param simulationId - Simulation unique identifier
     * @param timestamp - Analysis timestamp in ISO-8601 format (e.g., '2025-11-04T14:07:04.349775Z')
     * @returns Market data context organized by ticker and timeframe
     */
    marketDataContext: (simulationId: string, timestamp: string) => {
      const params = new URLSearchParams({ simulationId, timestamp });
      return apiFetch<MarketDataContextResponse>(`/decisions/market-data-context?${params}`);
    },
  },

  // Portfolio
  portfolio: {
    snapshots: (simulationId: string, from: string, to: string, options?: { resolution?: 'all' | 'daily'; excludePositions?: boolean }) => {
      const searchParams = new URLSearchParams({ simulationId, from, to });
      if (options?.resolution && options.resolution !== 'all') {
        searchParams.set('resolution', options.resolution);
      }
      if (options?.excludePositions) {
        searchParams.set('excludePositions', 'true');
      }
      return apiFetch<PortfolioSnapshotsResponse>(`/portfolios/snapshots?${searchParams}`);
    },

    latest: (simulationId: string) =>
      apiFetch<PortfolioSnapshot>(`/portfolios/snapshots/latest?simulationId=${simulationId}`),
  },

  // Benchmarks
  benchmarks: {
    /**
     * Create a new benchmark with multiple strategies
     * POST /api/v1/benchmarks
     * @returns HTTP 202 ACCEPTED - Simulations start asynchronously
     */
    create: (data: CreateBenchmarkRequest) =>
      apiFetch<CreateBenchmarkResponse>('/benchmarks', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    /**
     * Get benchmark by ID
     * GET /api/v1/benchmarks/{id}
     */
    get: (id: string) =>
      apiFetch<BenchmarkApiResponse>(`/benchmarks/${id}`),

    /**
     * List all benchmarks
     * GET /api/v1/benchmarks
     */
    list: () =>
      apiFetch<BenchmarkApiResponse[]>('/benchmarks'),

    /**
     * Get benchmark entries (top performers)
     * GET /api/v1/benchmarks/{id}/entries
     */
    entries: (id: string) =>
      apiFetch<BenchmarkEntry[]>(`/benchmarks/${id}/entries?limit=3`),

    /**
     * Get compact chart data for a benchmark
     * GET /api/v1/benchmarks/{id}/chart-data
     */
    chartData: (id: string) =>
      apiFetch<CompactChartData>(`/benchmarks/${id}/chart-data`),

    /**
     * Get buy-and-hold overlays for a benchmark
     * GET /api/v1/benchmarks/{id}/overlays
     */
    overlays: (id: string, symbols: string[], initialCapital?: number) => {
      const params = new URLSearchParams({ symbols: symbols.join(',') });
      if (initialCapital != null) params.set('initialCapital', String(initialCapital));
      return apiFetch<OverlayResponse[]>(`/benchmarks/${id}/overlays?${params}`);
    },

    /**
     * Delete a benchmark and all associated simulations
     * DELETE /api/v1/benchmarks/{id}
     */
    delete: (id: string) =>
      apiFetch<void>(`/benchmarks/${id}`, {
        method: 'DELETE',
      }),
  },

  // Challenges
  challenges: {
    list: (status?: string) => {
      const params = status ? `?status=${status}` : '';
      return apiFetch<ChallengeResponse[]>(`/challenges${params}`);
    },

    get: (id: string) =>
      apiFetch<ChallengeResponse>(`/challenges/${id}`),

    chartData: (id: string) =>
      apiFetch<ChallengeChartData>(`/challenges/${id}/chart-data`),

    eligibleStrategies: (id: string) =>
      apiFetch<EligibleStrategy[]>(`/challenges/${id}/eligible-strategies`),

    join: (id: string, strategyId: string) =>
      apiFetch<{ message: string; simulationId: string }>(`/challenges/${id}/join`, {
        method: 'POST',
        body: JSON.stringify({ strategyId }),
      }),

    create: (data: CreateChallengeRequest) =>
      apiFetch<ChallengeResponse>('/challenges', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    delete: (id: string) =>
      apiFetch<void>(`/challenges/${id}`, {
        method: 'DELETE',
      }),
  },

  /**
   * Market Research API
   */
  marketResearch: {
    /**
     * List all market research configurations
     * GET /api/v1/market-research
     */
    list: () =>
      apiFetch<MarketResearchResponse[]>('/market-research'),

    /**
     * Get market research details with execution history
     * GET /api/v1/market-research/{id}
     */
    get: (id: string) =>
      apiFetch<MarketResearchDetailsResponse>(`/market-research/${id}`),

    /**
     * Create a new market research configuration
     * POST /api/v1/market-research
     */
    create: (data: CreateMarketResearchRequest) =>
      apiFetch<MarketResearchResponse>('/market-research', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    /**
     * Update an existing market research configuration
     * PUT /api/v1/market-research/{id}
     */
    update: (id: string, data: UpdateMarketResearchRequest) =>
      apiFetch<MarketResearchResponse>(`/market-research/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),

    /**
     * Pause a market research configuration
     * POST /api/v1/market-research/{id}/pause
     */
    pause: (id: string) =>
      apiFetch<void>(`/market-research/${id}/pause`, {
        method: 'POST',
      }),

    /**
     * Resume a paused market research configuration
     * POST /api/v1/market-research/{id}/resume
     */
    resume: (id: string) =>
      apiFetch<void>(`/market-research/${id}/resume`, {
        method: 'POST',
      }),

    /**
     * Manually execute a market research (trigger now)
     * POST /api/v1/market-research/{id}/execute
     */
    execute: (id: string) =>
      apiFetch<ResearchExecutionResponse>(`/market-research/${id}/execute`, {
        method: 'POST',
      }),

    /**
     * Delete a market research configuration
     * DELETE /api/v1/market-research/{id}
     */
    delete: (id: string) =>
      apiFetch<void>(`/market-research/${id}`, {
        method: 'DELETE',
      }),

    /**
     * Delete a specific execution from market research history
     * DELETE /api/v1/market-research/{researchId}/executions/{executionId}
     */
    deleteExecution: (researchId: string, executionId: string) =>
      apiFetch<void>(`/market-research/${researchId}/executions/${executionId}`, {
        method: 'DELETE',
      }),
  },

  /**
   * Data Availability API
   */
  dataAvailability: {
    /**
     * Analyze market data coverage for given symbols and time range.
     * Returns data availability metrics including coverage percentages,
     * daily breakdowns, and identified gaps.
     *
     * GET /api/v1/data-availability/coverage
     *
     * @param params Query parameters for coverage analysis
     * @returns Coverage analysis with metrics and gaps
     */
    analyzeCoverage: (params: {
      symbols: string[];
      dataTypes: string[];
      from: string;
      to: string;
      strategyId?: string;
    }) => {
      const queryParams = new URLSearchParams({
        symbols: params.symbols.join(','),
        dataTypes: params.dataTypes.join(','),
        from: params.from,
        to: params.to,
      });
      if (params.strategyId) {
        queryParams.append('strategyId', params.strategyId);
      }
      return apiFetch<DataAvailabilityResponse>(
        `/data-availability/coverage?${queryParams}`
      );
    },

    /**
     * Request backfill for missing market data.
     * Creates a backfill job that runs asynchronously.
     * Returns 202 ACCEPTED if backfill triggered, 200 OK if data already sufficient.
     *
     * POST /api/v1/data-availability/backfill
     *
     * @param request Backfill request with symbols, data types, and time range
     * @returns Backfill job information with job ID and status
     */
    requestBackfill: (request: BackfillRequest) =>
      apiFetch<BackfillResponse>('/data-availability/backfill', {
        method: 'POST',
        body: JSON.stringify(request),
      }),

    /**
     * Get backfill job status and progress.
     * Use this to poll for job completion or subscribe via WebSocket for real-time updates.
     *
     * GET /api/v1/data-availability/backfill/{jobId}
     *
     * @param jobId Unique backfill job identifier (UUID)
     * @returns Current job status and progress
     */
    getBackfillStatus: (jobId: string) =>
      apiFetch<BackfillResponse>(`/data-availability/backfill/${jobId}`),

    /**
     * Get the provider data horizon (earliest available date) per (symbol, dataType).
     * Horizons change slowly — callers should cache aggressively.
     *
     * GET /api/v1/data-availability/horizon
     *
     * @param params Symbols and data types to probe
     * @returns One horizon entry per (symbol, dataType) requested
     */
    getHorizon: (params: { symbols: string[]; dataTypes: string[] }) => {
      const queryParams = new URLSearchParams({
        symbols: params.symbols.join(','),
        dataTypes: params.dataTypes.join(','),
      });
      return apiFetch<DataHorizonResponse>(
        `/data-availability/horizon?${queryParams}`
      );
    },
  },

  /**
   * Broker Accounts API
   */
  brokerAccounts: {
    /**
     * List all broker accounts for the current user
     * GET /api/v1/broker-accounts
     */
    list: () =>
      apiFetch<BrokerAccountResponse[]>('/broker-accounts'),

    /**
     * Get a specific broker account by ID
     * GET /api/v1/broker-accounts/{id}
     */
    get: (id: string) =>
      apiFetch<BrokerAccountResponse>(`/broker-accounts/${id}`),

    /**
     * Begin Saxo (EU) onboarding — mints a consent URL for the browser OAuth flow.
     * POST /api/v1/broker-accounts/saxo/onboard/start (authenticated; SIM-only in v1)
     */
    saxoOnboardStart: () =>
      apiFetch<{ authorizationUrl: string }>('/broker-accounts/saxo/onboard/start', {
        method: 'POST',
        body: JSON.stringify({}),
      }),

    /**
     * Create a new broker account
     * POST /api/v1/broker-accounts
     */
    create: (data: CreateBrokerAccountRequestType) =>
      apiFetch<BrokerAccountResponse>('/broker-accounts', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    /**
     * Update broker account credentials
     * PUT /api/v1/broker-accounts/{id}
     */
    update: (id: string, data: UpdateBrokerAccountCredentialsRequestType) =>
      apiFetch<BrokerAccountResponse>(`/broker-accounts/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),

    /**
     * Delete a broker account
     * DELETE /api/v1/broker-accounts/{id}
     */
    delete: (id: string) =>
      apiFetch<void>(`/broker-accounts/${id}`, {
        method: 'DELETE',
      }),

    /**
     * Validate broker account credentials
     * POST /api/v1/broker-accounts/{id}/validate
     */
    validate: (id: string) =>
      apiFetch<ValidateBrokerAccountResponseType>(`/broker-accounts/${id}/validate`, {
        method: 'POST',
      }),
  },

  /**
   * Usage Statistics API
   */
  usage: {
    /**
     * Get LLM usage statistics for the current user.
     * Returns daily breakdown for last 30 days plus weekly and monthly totals.
     * GET /api/v1/usage/llm
     */
    getLLMUsage: () =>
      apiFetch<LLMUsageStatistics>('/usage/llm'),
  },

  /**
   * Budget Management API
   */
  budget: {
    /**
     * Get the current user's budget information.
     * Returns budget limit, current spend, and remaining balance.
     * GET /api/v1/budget
     */
    get: () =>
      apiFetch<UserBudget>('/budget'),

    /**
     * Top up the user's budget.
     * This is a mock implementation - no actual payment is processed.
     * POST /api/v1/budget/top-up
     *
     * @param amount The amount to add in USD
     * @returns Updated budget information
     */
    topUp: (request: TopUpBudgetRequest) =>
      apiFetch<UserBudget>('/budget/top-up', {
        method: 'POST',
        body: JSON.stringify(request),
      }),

    /**
     * Update the user's daily spend limit.
     * PUT /api/v1/budget/daily-limit
     *
     * @param request The new daily limit (null to clear)
     * @returns Updated budget information
     */
    updateDailyLimit: (request: UpdateDailySpendLimitRequest) =>
      apiFetch<UserBudget>('/budget/daily-limit', {
        method: 'PUT',
        body: JSON.stringify(request),
      }),
  },

  /**
   * Payment API
   */
  payment: {
    /**
     * Get payment provider configuration.
     * Returns whether Stripe (or other provider) is configured.
     * GET /api/v1/payment/config
     */
    getConfig: () =>
      apiFetch<PaymentConfig>('/payment/config'),

    /**
     * Create a Stripe checkout session.
     * Returns a checkout URL to redirect the user to.
     * POST /api/v1/payment/checkout
     *
     * @param request Checkout request with amount, success/cancel URLs
     * @returns Checkout session with redirect URL
     */
    createCheckout: (request: CreateCheckoutRequest) =>
      apiFetch<CheckoutSessionResponse>('/payment/checkout', {
        method: 'POST',
        body: JSON.stringify(request),
      }),
  },

  /**
   * Runtime LLM provider selection (self-host edition).
   * Endpoints 404 when the feature is disabled (hosted SaaS) — treat as "not present".
   */
  llm: {
    /** GET /api/v1/llm/providers — selection status + per-provider availability. */
    getProviders: () => apiFetch<LlmProviderStatus>('/llm/providers'),

    /** POST /api/v1/llm/providers/select — activate a provider (no restart needed). */
    selectProvider: (request: SelectLlmProviderRequest) =>
      apiFetch<LlmProviderStatus>('/llm/providers/select', {
        method: 'POST',
        body: JSON.stringify(request),
      }),
  },

  /**
   * Instance descriptor — edition (saas/standalone) and offered broker regions.
   * Anonymous and present on every edition; drives the index page and broker gating.
   */
  instance: {
    /** GET /api/v1/instance */
    get: () => apiFetch<InstanceInfo>('/instance'),
  },

  /**
   * Which flagged features are switched on for the caller.
   * Anonymous-friendly; evaluated per request because a flag can target individual users.
   */
  features: {
    /** GET /api/v1/features */
    get: () => apiFetch<FeatureAvailability>('/features'),
  },

  /**
   * AI Configuration API
   */
  config: {
    /**
     * Get available LLM models, optionally filtered by trading context.
     * GET /api/v1/config/models
     *
     * @param context Optional trading context to filter models
     * @returns List of available models
     */
    getModels: (context?: TradingContext, tiers?: ModelTier[]) => {
      const params = new URLSearchParams();
      if (context) params.set('context', context);
      if (tiers?.length) params.set('tiers', tiers.join(','));
      const qs = params.toString();
      return apiFetch<LLMModel[]>(`/config/models${qs ? `?${qs}` : ''}`);
    },

    /**
     * Get the current user's model preferences.
     * GET /api/v1/config/preferences
     */
    getPreferences: () =>
      apiFetch<ModelPreferences>('/config/preferences'),

    /**
     * Update the current user's model preferences.
     * PUT /api/v1/config/preferences
     *
     * @param preferences Model preferences to update
     * @returns Updated preferences
     */
    updatePreferences: (preferences: UpdateModelPreferencesRequest) =>
      apiFetch<ModelPreferences>('/config/preferences', {
        method: 'PUT',
        body: JSON.stringify(preferences),
      }),

    /**
     * Get the LLM model fallback chain configuration.
     * This is a public endpoint that doesn't require authentication.
     * GET /api/v1/config/fallback
     *
     * @returns Fallback chain configuration
     */
    getFallbackConfig: () =>
      apiFetch<FallbackConfig>('/config/fallback'),

    /**
     * The model each trading context will actually run on the active engine.
     * GET /api/v1/config/effective-models
     */
    getEffectiveModels: () =>
      apiFetch<EffectiveModelsResponse>('/config/effective-models'),

    /**
     * Get model reliability statistics (success/failure rates).
     * GET /api/v1/config/model-reliability
     *
     * @param days Number of days to look back (default 30)
     * @returns Model reliability entries
     */
    getModelReliability: (days?: number) =>
      apiFetch<ModelReliabilityResponse>(`/config/model-reliability${days ? `?days=${days}` : ''}`),

    /**
     * Estimate the AI budget cost for a backtest or benchmark.
     * POST /api/v1/config/cost-estimate
     *
     * @param request Strategy IDs and date range
     * @returns Cost estimate with breakdown
     */
    estimateCost: (request: CostEstimateRequest) =>
      apiFetch<CostEstimateResponse>('/config/cost-estimate', {
        method: 'POST',
        body: JSON.stringify(request),
      }),

    /**
     * Get the earliest date from which historical news data is available.
     * GET /api/v1/config/news-availability
     *
     * @returns News availability with earliest date
     */
    getNewsAvailability: () =>
      apiFetch<NewsAvailabilityResponse>('/config/news-availability'),
  },

  /**
   * API Tokens API
   */
  apiTokens: {
    /**
     * List all API tokens for the current user
     * GET /api/v1/settings/api-tokens
     */
    list: () =>
      apiFetch<ApiToken[]>('/settings/api-tokens'),

    /**
     * Create a new API token
     * POST /api/v1/settings/api-tokens
     */
    create: (data: CreateApiTokenRequest) =>
      apiFetch<ApiTokenCreated>('/settings/api-tokens', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    /**
     * Revoke an API token
     * DELETE /api/v1/settings/api-tokens/{id}
     */
    revoke: (id: string) =>
      apiFetch<void>(`/settings/api-tokens/${id}`, {
        method: 'DELETE',
      }),
  },

  /**
   * Account management API
   */
  account: {
    /**
     * Permanently delete the authenticated user's account and all associated data.
     * DELETE /api/v1/auth/account
     */
    delete: () =>
      apiFetch<void>('/auth/account', {
        method: 'DELETE',
      }),
  },

  /**
   * Admin API (requires ADMIN role)
   */
  admin: {
    getDashboard: () =>
      apiFetch<AdminDashboardResponse>('/admin/dashboard'),

    searchUsers: (query: string, limit: number = 20) =>
      apiFetch<AdminUserSearchResponse>(`/admin/users/search?q=${encodeURIComponent(query)}&limit=${limit}`),

    getHealth: () =>
      apiFetch<AdminHealthResponse>('/admin/health'),

    /** GET /api/v1/admin/feature-flags */
    listFeatureFlags: () =>
      apiFetch<FeatureFlag[]>('/admin/feature-flags'),

    /** PUT /api/v1/admin/feature-flags/{name} */
    updateFeatureFlag: (name: string, request: UpdateFeatureFlagRequest) =>
      apiFetch<FeatureFlag>(`/admin/feature-flags/${encodeURIComponent(name)}`, {
        method: 'PUT',
        body: JSON.stringify(request),
      }),
  },

  /**
   * Historical Events API
   */
  historicalEvents: {
    /**
     * List all historical events, optionally filtered.
     * GET /api/v1/historical-events
     */
    list: (params?: { category?: string; assetClass?: string; sentiment?: string }) => {
      const searchParams = new URLSearchParams();
      if (params?.category) searchParams.set('category', params.category);
      if (params?.assetClass) searchParams.set('assetClass', params.assetClass);
      if (params?.sentiment) searchParams.set('sentiment', params.sentiment);
      const qs = searchParams.toString();
      return apiFetch<HistoricalEventResponse[]>(`/historical-events${qs ? `?${qs}` : ''}`);
    },

    /**
     * Get historical event(s) by slug or keyword.
     * GET /api/v1/historical-events/:slug
     */
    get: (slug: string) =>
      apiFetch<HistoricalEventResponse[]>(`/historical-events/${slug}`),
  },

  /**
   * Dividends API
   */
  dividends: {
    /**
     * Get dividend info for one or more ticker symbols.
     * Returns whether each ticker pays dividends, estimated yield, and payment count.
     * GET /api/v1/dividends/info?symbols=AAPL,MSFT,BTC
     */
    getInfo: (symbols: string[]) =>
      apiFetch<DividendInfoResponse>(`/dividends/info?symbols=${encodeURIComponent(symbols.join(','))}`),
  },

  /**
   * Notifications API — in-app inbox and per-type channel preferences.
   */
  notifications: {
    /**
     * List notifications, newest first. Pass status='unread' for unread only.
     * GET /api/v1/notifications?status=unread&limit=50
     */
    list: (params?: { status?: 'unread' | 'all'; limit?: number }) => {
      const searchParams = new URLSearchParams();
      if (params?.status) searchParams.set('status', params.status);
      if (params?.limit) searchParams.set('limit', String(params.limit));
      const qs = searchParams.toString();
      return apiFetch<Notification[]>(`/notifications${qs ? `?${qs}` : ''}`);
    },

    /** GET /api/v1/notifications/unread-count */
    unreadCount: () =>
      apiFetch<UnreadCountResponse>('/notifications/unread-count'),

    /** GET /api/v1/notifications/:id */
    get: (id: string) => apiFetch<Notification>(`/notifications/${id}`),

    /** POST /api/v1/notifications/:id/read */
    markRead: (id: string) =>
      apiFetch<void>(`/notifications/${id}/read`, { method: 'POST' }),

    /** POST /api/v1/notifications/read-all */
    markAllRead: () =>
      apiFetch<UnreadCountResponse>('/notifications/read-all', { method: 'POST' }),
  },

  notificationPreferences: {
    /** GET /api/v1/notification-preferences */
    list: () => apiFetch<NotificationPreference[]>('/notification-preferences'),

    /** PUT /api/v1/notification-preferences/:type */
    update: (type: NotificationType, body: UpdateNotificationPreferenceRequest) =>
      apiFetch<NotificationPreference>(`/notification-preferences/${type}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
  },

  /**
   * Recurring investment plans (DCA-style).
   */
  investmentPlans: {
    list: (status?: InvestmentPlanStatus) => {
      const qs = status ? `?status=${status}` : '';
      return apiFetch<InvestmentPlan[]>(`/investment-plans${qs}`);
    },
    get: (id: string) => apiFetch<InvestmentPlan>(`/investment-plans/${id}`),
    create: (body: CreateInvestmentPlanRequest) =>
      apiFetch<InvestmentPlan>('/investment-plans', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    update: (id: string, body: UpdateInvestmentPlanRequest) =>
      apiFetch<InvestmentPlan>(`/investment-plans/${id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    pause: (id: string) =>
      apiFetch<InvestmentPlan>(`/investment-plans/${id}/pause`, { method: 'POST' }),
    resume: (id: string) =>
      apiFetch<InvestmentPlan>(`/investment-plans/${id}/resume`, { method: 'POST' }),
    terminate: (id: string) =>
      apiFetch<void>(`/investment-plans/${id}`, { method: 'DELETE' }),
    executeNow: (id: string) =>
      apiFetch<InvestmentAllocation>(`/investment-plans/${id}/execute`, { method: 'POST' }),
    listAllocations: (id: string, limit = 50) =>
      apiFetch<InvestmentAllocation[]>(`/investment-plans/${id}/allocations?limit=${limit}`),
  },

  investmentAllocations: {
    list: (limit = 100) =>
      apiFetch<InvestmentAllocation[]>(`/investment-allocations?limit=${limit}`),
    get: (id: string) =>
      apiFetch<InvestmentAllocation>(`/investment-allocations/${id}`),
  },

  /**
   * Cleanup API
   */
  cleanup: {
    /**
     * Prune unused strategies and/or research.
     * DELETE /api/v1/cleanup/unused
     */
    pruneUnused: (params: { strategies?: boolean; research?: boolean }) => {
      const searchParams = new URLSearchParams();
      if (params.strategies !== undefined) searchParams.set('strategies', String(params.strategies));
      if (params.research !== undefined) searchParams.set('research', String(params.research));
      return apiFetch<{ deletedStrategies: number; deletedResearch: number }>(
        `/cleanup/unused?${searchParams}`,
        { method: 'DELETE' }
      );
    },
  },
};

// Re-export types
export type {
  CreateStrategyRequest,
  UpdateStrategyRequest,
  StrategyResponse,
  StrategyPerformanceResponse,
  CreateSimulationRequest,
  SimulationResponse,
  MarketEvent,
  MarketDataQueryParams,
  MarketDataResponse,
  LLMDecision,
  ConsensusDecision,
  PortfolioSnapshot,
  SimulationStatus,
  CreateMarketResearchRequest,
  UpdateMarketResearchRequest,
  MarketResearchResponse,
  MarketResearchDetailsResponse,
  ResearchExecutionResponse,
  SellPositionResponse,
  LiquidateAllResponse,
  EndSimulationRequest,
  ForceEndSimulationResponse,
  HistoricalEventResponse,
} from '../types/api';

export type {
  CreateBenchmarkRequest,
  CreateBenchmarkResponse,
  BenchmarkApiResponse,
} from '../types/benchmark';

export type {
  DataAvailabilityResponse,
  BackfillRequest,
  BackfillResponse,
  DailyCoverage,
  DataGap,
  BackfillProgressMessage,
  EventType as DataEventType,
  BackfillStatus,
  BackfillEventType,
} from '../types/dataAvailability';

export type {
  BrokerAccount,
  BrokerType,
  AccountStatus,
  CostPreset,
  MockBrokerConfig,
  CreateBrokerAccountRequest,
  UpdateBrokerAccountCredentialsRequest,
  ValidateBrokerAccountResponse,
} from '../types/brokerAccount';

export type {
  LLMUsageStatistics,
  DailyUsage,
} from '../types/usage';

export type {
  UserBudget,
  BudgetStatus,
  TopUpBudgetRequest,
  UpdateDailySpendLimitRequest,
} from '../types/budget';

export type {
  LLMModel,
  ModelPreferences,
  UpdateModelPreferencesRequest,
  TradingContext,
  ContextInfo,
  TRADING_CONTEXTS,
  FallbackConfig,
  CostEstimateRequest,
  CostEstimateResponse,
  NewsAvailabilityResponse,
} from '../types/config';

export type {
  CreateCheckoutRequest,
  CheckoutSessionResponse,
  PaymentConfig,
} from '../types/payment';

export type {
  AdminDashboardResponse,
  AdminUserSearchResponse,
  AdminHealthResponse,
} from '../types/admin';

export type {
  DividendInfoItem,
  DividendInfoResponse,
  FinancialMetricsResponse,
} from '../types/api';

export type {
  LlmProviderId,
  LlmProviderEntry,
  LlmProviderStatus,
  SelectLlmProviderRequest,
} from '../types/llmProvider';

export type {
  InstanceEdition,
  BrokerRegionId,
  InstanceInfo,
} from '../types/instance';

// Legacy type aliases for backward compatibility
export type { Strategy, Simulation } from '../types/api';
