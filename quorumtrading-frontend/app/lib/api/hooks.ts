'use client';

import { useState, useEffect, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { apiClient, APIError, ApiClientError } from './client';
import type {
  CreateStrategyRequest,
  UpdateStrategyRequest,
  CreateSimulationRequest,
  StrategyResponse,
  StrategyPerformanceResponse,
  SimulationResponse,
  MarketDataQueryParams,
  SimulationStatus,
  CreateBenchmarkRequest,
  CreateBenchmarkResponse,
  BenchmarkApiResponse,
  CreateMarketResearchRequest,
  UpdateMarketResearchRequest,
  MarketResearchResponse,
  MarketResearchDetailsResponse,
  ResearchExecutionResponse,
  CreateBrokerAccountRequest,
  UpdateBrokerAccountCredentialsRequest,
  BrokerAccount as BrokerAccountType,
} from './client';
import { getErrorMessage, formatValidationErrors } from '../types/error';

// Query keys
export const queryKeys = {
  llmUsage: ['usage', 'llm'] as const,
  budget: ['budget'] as const,
  strategies: ['strategies'] as const,
  strategy: (id: string) => ['strategies', id] as const,
  strategyPerformance: (id: string) => ['strategies', id, 'performance'] as const,
  strategySchema: ['strategies', 'schema'] as const,
  simulations: ['simulations'] as const,
  simulation: (id: string) => ['simulations', id] as const,
  simulationChartData: (id: string, resolution?: number) => ['simulations', id, 'chart-data', resolution] as const,
  simulationContributions: (id: string) => ['simulations', id, 'contributions'] as const,
  marketData: (params: MarketDataQueryParams) => ['market-data', params] as const,
  llmDecisions: (simulationId: string, limit?: number) => ['llm-decisions', simulationId, limit] as const,
  consensusDecisions: (simulationId: string) => ['consensus-decisions', simulationId] as const,
  consensusWithAgents: (simulationId: string, ticker?: string, limit?: number, action?: string) =>
    ['consensus-with-agents', simulationId, ticker, limit, action] as const,
  portfolioSnapshots: (simulationId: string, from: string, to: string, resolution?: string) =>
    ['portfolio-snapshots', simulationId, from, to, resolution ?? 'daily'] as const,
  portfolioLatest: (simulationId: string) => ['portfolio-latest', simulationId] as const,
  benchmarks: ['benchmarks'] as const,
  benchmark: (id: string) => ['benchmarks', id] as const,
  challenges: ['challenges'] as const,
  challenge: (id: string) => ['challenges', id] as const,
  marketResearch: ['market-research'] as const,
  marketResearchDetails: (id: string) => ['market-research', id] as const,
  dataAvailability: ['data-availability'] as const,
  dataCoverage: (params: {
    symbols: string[];
    dataTypes: string[];
    from: string;
    to: string;
    strategyId?: string;
  }) => ['data-availability', 'coverage', params] as const,
  backfillStatus: (jobId: string) => ['data-availability', 'backfill', jobId] as const,
  horizon: (params: { symbols: string[]; dataTypes: string[] }) =>
    ['data-availability', 'horizon', params] as const,
  brokerAccounts: ['broker-accounts'] as const,
  brokerAccount: (id: string) => ['broker-accounts', id] as const,
  apiTokens: ['api-tokens'] as const,
  historicalEvents: ['historical-events'] as const,
  publicStrategies: ['strategies', 'public'] as const,
  publicStrategy: (id: string) => ['strategies', 'public', id] as const,
  notifications: (status?: 'unread' | 'all') => ['notifications', status ?? 'all'] as const,
  notification: (id: string) => ['notifications', id] as const,
  notificationsUnreadCount: ['notifications', 'unread-count'] as const,
  notificationPreferences: ['notification-preferences'] as const,
};

// Strategies
export function useStrategies(options?: { refetchInterval?: number }) {
  return useQuery({
    queryKey: queryKeys.strategies,
    queryFn: () => apiClient.strategies.list(),
    ...options,
  });
}

export function useStrategy(id: string) {
  return useQuery({
    queryKey: queryKeys.strategy(id),
    queryFn: () => apiClient.strategies.get(id),
    enabled: !!id,
  });
}

/**
 * Fetch aggregate performance metrics for a strategy.
 * Returns statistics from all completed simulations.
 */
export function useStrategyPerformance(id: string) {
  return useQuery({
    queryKey: queryKeys.strategyPerformance(id),
    queryFn: () => apiClient.strategies.getPerformance(id),
    enabled: !!id,
    staleTime: 5 * 60 * 1000, // 5 minutes - performance metrics don't change frequently
  });
}

/**
 * Fetch the strategy configuration schema for client-side validation.
 * Schema is cached for 1 hour (matches backend Cache-Control header).
 * Used to drive real-time linting in the strategy editor.
 */
export function useStrategySchema() {
  return useQuery({
    queryKey: queryKeys.strategySchema,
    queryFn: () => apiClient.strategies.getSchema(),
    staleTime: 60 * 60 * 1000, // 1 hour - schema rarely changes
    gcTime: 24 * 60 * 60 * 1000, // Keep in cache for 24 hours
  });
}

export function useCreateStrategy() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateStrategyRequest) => apiClient.strategies.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.strategies });
      toast.success('Strategy created successfully');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to create strategy: ${message}`);
    },
  });
}

export function useUpdateStrategy() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateStrategyRequest }) =>
      apiClient.strategies.update(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.strategies });
      queryClient.invalidateQueries({ queryKey: queryKeys.strategy(id) });
      toast.success('Strategy updated successfully');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to update strategy: ${message}`);
    },
  });
}

export function useDeleteStrategy() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.strategies.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.strategies });
      toast.success('Strategy deleted successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to delete strategy: ${getErrorMessage(error)}`);
    },
  });
}

// Simulations
export function useSimulations(options?: { refetchInterval?: number }) {
  return useQuery({
    queryKey: queryKeys.simulations,
    queryFn: () => apiClient.simulations.list(),
    ...options,
  });
}

export function useSimulation(id: string) {
  return useQuery({
    queryKey: queryKeys.simulation(id),
    queryFn: () => apiClient.simulations.get(id),
    enabled: !!id,
    // Poll while simulation is in a non-terminal state so status changes are picked up
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      const isActive = status === 'PENDING' || status === 'WAITING_FOR_DATA'
        || status === 'SIMULATING' || status === 'LIVE';
      return isActive ? 3000 : false;
    },
  });
}

/**
 * Live execution health of a simulation (GET /simulations/{id}/status): verdict, skip
 * streak, dominant skip reasons. This is the field set that tells a user WHY a run is
 * not progressing — the detail page's four empty panels cannot.
 */
export function useSimulationHealth(id: string, enabled: boolean = true) {
  return useQuery({
    queryKey: [...queryKeys.simulation(id), 'health'],
    queryFn: () => apiClient.simulations.health(id),
    enabled: !!id && enabled,
    // Slower than useSimulation's 3s: the verdict aggregates decision rows, so it only
    // changes on the round cadence, not per tick.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      const isActive = status === 'PENDING' || status === 'WAITING_FOR_DATA'
        || status === 'SIMULATING' || status === 'LIVE';
      return isActive ? 15000 : false;
    },
  });
}

export function useCreateSimulation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateSimulationRequest) => apiClient.simulations.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      toast.success('Simulation created successfully');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to create simulation: ${message}`);
    },
  });
}

export function useStartSimulation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.simulations.start(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulation(id) });
      toast.success('Simulation started successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to start simulation: ${getErrorMessage(error)}`);
    },
  });
}

export function usePauseSimulation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.simulations.pause(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulation(id) });
      toast.success('Simulation paused');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to pause simulation: ${getErrorMessage(error)}`);
    },
  });
}

export function useResumeSimulation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.simulations.resume(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulation(id) });
      toast.success('Simulation resumed');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to resume simulation: ${getErrorMessage(error)}`);
    },
  });
}

export function useDeleteSimulation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.simulations.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      toast.success('Simulation deleted successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to delete simulation: ${getErrorMessage(error)}`);
    },
  });
}

export function useStartNextSimulation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => apiClient.simulations.startNextQueued(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      toast.success('Started next queued simulation');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to start next simulation: ${getErrorMessage(error)}`);
    },
  });
}

export function useReorderQueue() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (orderedSimulationIds: string[]) =>
      apiClient.simulations.reorderQueue(orderedSimulationIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      toast.success('Queue reordered successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to reorder queue: ${getErrorMessage(error)}`);
    },
  });
}

export function useClearPausedSimulations() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => apiClient.simulations.clearPaused(),
    onSuccess: (deletedCount) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      toast.success(`Deleted ${deletedCount} paused simulation${deletedCount === 1 ? '' : 's'}`);
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to clear paused simulations: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Sell an entire position at market price.
 * POST /api/v1/simulations/{id}/positions/{symbol}/sell
 */
export function useSellPosition() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ simulationId, symbol }: { simulationId: string; symbol: string }) =>
      apiClient.simulations.sellPosition(simulationId, symbol),
    onSuccess: (data, { simulationId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulation(simulationId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.portfolioLatest(simulationId) });
      if (data.success) {
        toast.success(`Sold ${data.filledQuantity} @ $${data.executionPrice}`);
      } else {
        toast.error(`Sell failed: ${data.message}`);
      }
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to sell position: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Liquidate all positions (cancel open orders + sell everything).
 * POST /api/v1/simulations/{id}/liquidate
 */
export function useLiquidateAll() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (simulationId: string) => apiClient.simulations.liquidateAll(simulationId),
    onSuccess: (data, simulationId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulation(simulationId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.portfolioLatest(simulationId) });
      // Invalidate all portfolio snapshot queries for this simulation (any from/to params)
      queryClient.invalidateQueries({ queryKey: ['portfolio-snapshots', simulationId] });
      if (data.positionsFailed === 0) {
        toast.success(`Liquidated ${data.positionsLiquidated} position(s)`);
      } else {
        toast.error(`Liquidated ${data.positionsLiquidated}, failed ${data.positionsFailed}: ${data.errors.join(', ')}`);
      }
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to liquidate positions: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Force-end a simulation (optionally liquidating positions first).
 * POST /api/v1/simulations/{id}/end
 */
export function useForceEndSimulation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ simulationId, liquidatePositions }: { simulationId: string; liquidatePositions: boolean }) =>
      apiClient.simulations.forceEnd(simulationId, liquidatePositions),
    onSuccess: (data, { simulationId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulation(simulationId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      queryClient.invalidateQueries({ queryKey: queryKeys.portfolioLatest(simulationId) });
      if (data.success) {
        toast.success(data.message);
      } else {
        toast.error(`End simulation failed: ${data.message}`);
      }
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to end simulation: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Preview restart from a specific consensus decision (fetches deletion/archival counts).
 */
export function useRestartFromPreview(simulationId: string | null, consensusDecisionId: number | null) {
  return useQuery({
    queryKey: ['restartFromPreview', simulationId, consensusDecisionId],
    queryFn: () => apiClient.simulations.restartFromPreview(simulationId!, consensusDecisionId!),
    enabled: !!simulationId && !!consensusDecisionId,
  });
}

/**
 * Execute restart from a specific consensus decision.
 */
export function useRestartFrom() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ simulationId, consensusDecisionId }: { simulationId: string; consensusDecisionId: number }) =>
      apiClient.simulations.restartFrom(simulationId, consensusDecisionId),
    onSuccess: (data, { simulationId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simulation(simulationId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      queryClient.invalidateQueries({ queryKey: queryKeys.portfolioLatest(simulationId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.consensusDecisions(simulationId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulationChartData(simulationId) });
      toast.success(`Simulation restarted from ${new Date(data.restartTimestamp).toLocaleDateString()}. Resume when ready.`);
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to restart simulation: ${getErrorMessage(error)}`);
    },
  });
}

// Market Data
export function useMarketData(params: MarketDataQueryParams) {
  return useQuery({
    queryKey: queryKeys.marketData(params),
    queryFn: () => apiClient.marketData.query(params),
    enabled: !!params.from && !!params.to,
  });
}

/**
 * Hook to fetch market data context for a simulation at a specific timestamp.
 * Returns the exact market data that was available to AI agents during consensus evaluation.
 *
 * @param simulationId - Simulation unique identifier
 * @param timestamp - Analysis timestamp in ISO-8601 format
 * @param enabled - Whether the query should run (default: true if both params provided)
 */
export function useMarketDataContext(
  simulationId: string | undefined,
  timestamp: string | undefined,
  enabled: boolean = true
) {
  return useQuery({
    queryKey: ['market-data-context', simulationId, timestamp],
    queryFn: () => {
      if (!simulationId || !timestamp) {
        throw new Error('simulationId and timestamp are required');
      }
      return apiClient.decisions.marketDataContext(simulationId, timestamp);
    },
    enabled: enabled && !!simulationId && !!timestamp,
    // Cache for 5 minutes since historical market data doesn't change
    staleTime: 5 * 60 * 1000,
  });
}

export function useFinancialMetrics(symbol: string | undefined, enabled: boolean = true) {
  return useQuery({
    queryKey: ['financial-metrics', symbol],
    queryFn: () => {
      if (!symbol) throw new Error('symbol is required');
      return apiClient.marketData.financialMetrics(symbol);
    },
    enabled: enabled && !!symbol,
    staleTime: 24 * 60 * 60 * 1000, // 24h cache — financial data rarely changes
  });
}

export function useCollectMarketData() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => apiClient.marketData.collect(),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['market-data'] });
      toast.success(`Collected ${data.eventsCollected} market events`);
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to collect market data: ${getErrorMessage(error)}`);
    },
  });
}

// Decisions
export function useLLMDecisions(simulationId: string, limit?: number) {
  return useQuery({
    queryKey: queryKeys.llmDecisions(simulationId, limit),
    queryFn: async () => {
      const response = await apiClient.decisions.llm.list(simulationId, limit);
      return response.decisions; // Extract array from wrapper
    },
    enabled: !!simulationId,
  });
}

export function useConsensusDecisions(simulationId: string, executed?: boolean, action?: string, limit?: number) {
  return useQuery({
    queryKey: [...queryKeys.consensusDecisions(simulationId), executed, action, limit],
    queryFn: async () => {
      const response = await apiClient.decisions.consensus.list(simulationId, executed, action, limit);
      return response.decisions; // Extract array from wrapper
    },
    enabled: !!simulationId,
  });
}

export function useConsensusWithAgents(simulationId: string, ticker?: string, limit?: number, action?: string) {
  return useQuery({
    queryKey: queryKeys.consensusWithAgents(simulationId, ticker, limit, action),
    queryFn: () => apiClient.decisions.consensusWithAgents.list(simulationId, ticker, limit, action),
    enabled: !!simulationId,
  });
}

/**
 * Fetch a single consensus decision with its agent details, on-demand.
 * Only fires when consensusId is provided (non-null).
 */
export function useConsensusWithAgentsById(consensusId: number | null) {
  return useQuery({
    queryKey: ['consensus-with-agents-by-id', consensusId],
    queryFn: () => apiClient.decisions.consensusWithAgents.get(consensusId!),
    enabled: consensusId !== null,
    staleTime: 5 * 60 * 1000, // Cache agent details for 5 minutes
  });
}

// Portfolio
export function usePortfolioSnapshots(simulationId: string, from: string, to: string, options?: { resolution?: 'all' | 'daily'; excludePositions?: boolean }) {
  return useQuery({
    // resolution is part of the key: it flips from 'all' to 'daily' as a simulation ages, and
    // without it the coarser series would be served from the finer one's cache entry.
    queryKey: queryKeys.portfolioSnapshots(simulationId, from, to, options?.resolution),
    queryFn: async () => {
      const response = await apiClient.portfolio.snapshots(simulationId, from, to, options);
      return response.snapshots; // Extract array from wrapper
    },
    enabled: !!simulationId && !!from && !!to,
    // A LIVE simulation advances `to` as it runs, so the key changes periodically. Without this
    // the data would blank out on every roll-over and the chart would flash its "no data yet"
    // empty state at a user who has plenty of history.
    placeholderData: keepPreviousData,
  });
}

export function usePortfolioLatest(simulationId: string) {
  return useQuery({
    queryKey: queryKeys.portfolioLatest(simulationId),
    queryFn: () => apiClient.portfolio.latest(simulationId),
    enabled: !!simulationId,
  });
}

/**
 * Fetch decimated chart data for simulation portfolio history.
 * Returns portfolio value data points optimized for chart rendering.
 * Automatically decimates large datasets while preserving visual shape.
 *
 * @param simulationId Simulation unique identifier
 * @param resolution Target number of data points (10-10000, default: 50 for sparkline)
 * @param refetchInterval Auto-refresh interval in milliseconds (default: 10000ms = 10s)
 */
export function usePortfolioChartData(
  simulationId: string,
  resolution: number = 50,
  refetchInterval: number = 10000
) {
  return useQuery({
    queryKey: queryKeys.simulationChartData(simulationId, resolution),
    queryFn: () => apiClient.simulations.chartData(simulationId, resolution),
    enabled: !!simulationId,
    refetchInterval: refetchInterval,
    refetchIntervalInBackground: false, // Only refetch when tab is active
    staleTime: 5000, // Consider data stale after 5 seconds
  });
}

// Benchmarks
/**
 * Hook to list all benchmarks
 * GET /api/v1/benchmarks
 */
export function useBenchmarks() {
  return useQuery({
    queryKey: queryKeys.benchmarks,
    queryFn: () => apiClient.benchmarks.list(),
  });
}

export function useBenchmarkEntries(benchmarkId: string | undefined) {
  return useQuery({
    queryKey: ['benchmarks', benchmarkId, 'entries'],
    queryFn: () => benchmarkId ? apiClient.benchmarks.entries(benchmarkId) : Promise.resolve([]),
    enabled: !!benchmarkId,
  });
}

/**
 * Hook to get a single benchmark by ID
 * GET /api/v1/benchmarks/{id}
 */
export function useBenchmark(id: string) {
  return useQuery({
    queryKey: queryKeys.benchmark(id),
    queryFn: () => apiClient.benchmarks.get(id),
    enabled: !!id,
  });
}

/**
 * Hook to create a new benchmark with multiple strategies
 * POST /api/v1/benchmarks
 * Returns HTTP 202 ACCEPTED - simulations start asynchronously
 */
export function useCreateBenchmark() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateBenchmarkRequest) => apiClient.benchmarks.create(data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.benchmarks });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      if (response.benchmarkId) {
        toast.success(`Benchmark "${response.benchmarkName}" created with ${response.simulationIds.length} simulations`);
      } else {
        toast.success(`Simulation "${response.benchmarkName}" created`);
      }
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to create benchmark: ${message}`);
    },
  });
}

/**
 * Hook to delete a benchmark and all associated simulations
 * DELETE /api/v1/benchmarks/{id}
 */
export function useDeleteBenchmark() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.benchmarks.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.benchmarks });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      toast.success('Benchmark deleted successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to delete benchmark: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Hook to fetch compact chart data for a benchmark in a single request.
 * GET /api/v1/benchmarks/{id}/chart-data
 */
export function useBenchmarkChartData(benchmarkId: string | undefined, enabled: boolean = true) {
  return useQuery({
    queryKey: ['benchmarks', benchmarkId, 'chart-data'],
    queryFn: () => benchmarkId ? apiClient.benchmarks.chartData(benchmarkId) : Promise.resolve(null),
    enabled: !!benchmarkId && enabled,
    staleTime: 30_000,
  });
}

/**
 * Hook to fetch buy-and-hold overlays for a benchmark
 * GET /api/v1/benchmarks/{id}/overlays
 */
export function useBenchmarkOverlays(benchmarkId: string | undefined, symbols: string[], initialCapital?: number, isCompleted?: boolean) {
  return useQuery({
    queryKey: ['benchmarks', benchmarkId, 'overlays', symbols.sort().join(','), initialCapital],
    queryFn: () => benchmarkId ? apiClient.benchmarks.overlays(benchmarkId, symbols, initialCapital) : Promise.resolve([]),
    enabled: !!benchmarkId && symbols.length > 0,
    staleTime: isCompleted ? Infinity : 60_000,
    gcTime: isCompleted ? 30 * 60 * 1000 : undefined,
  });
}

/**
 * Hook to fetch buy-and-hold overlays for a simulation
 * GET /api/v1/simulations/{id}/overlays
 */
export function useSimulationOverlays(simulationId: string | undefined, symbols: string[], isCompleted?: boolean) {
  return useQuery({
    queryKey: ['simulations', simulationId, 'overlays', [...symbols].sort().join(',')],
    queryFn: () => simulationId ? apiClient.simulations.overlays(simulationId, symbols) : Promise.resolve([]),
    enabled: !!simulationId && symbols.length > 0,
    staleTime: isCompleted ? Infinity : 60_000,
    gcTime: isCompleted ? 30 * 60 * 1000 : undefined,
  });
}

// ===== Challenges =====

export function useChallenges(status?: string) {
  return useQuery({
    queryKey: [...queryKeys.challenges, status] as const,
    queryFn: () => apiClient.challenges.list(status),
  });
}

export function useChallenge(id: string) {
  return useQuery({
    queryKey: queryKeys.challenge(id),
    queryFn: () => apiClient.challenges.get(id),
    enabled: !!id,
  });
}

export function useChallengeChartData(challengeId: string | undefined, enabled: boolean = true, hasActiveEntries: boolean = false) {
  return useQuery({
    queryKey: ['challenges', challengeId, 'chart-data'],
    queryFn: () => challengeId ? apiClient.challenges.chartData(challengeId) : Promise.resolve(null),
    enabled: !!challengeId && enabled,
    staleTime: hasActiveEntries ? 10_000 : 30_000,
    refetchInterval: hasActiveEntries ? 15_000 : false,
  });
}

export function useEligibleStrategies(challengeId: string | undefined) {
  return useQuery({
    queryKey: ['challenges', challengeId, 'eligible-strategies'],
    queryFn: () => challengeId ? apiClient.challenges.eligibleStrategies(challengeId) : Promise.resolve([]),
    enabled: !!challengeId,
  });
}

export function useJoinChallenge() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ challengeId, strategyId }: { challengeId: string; strategyId: string }) =>
      apiClient.challenges.join(challengeId, strategyId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.challenges });
      queryClient.invalidateQueries({ queryKey: queryKeys.challenge(variables.challengeId) });
      queryClient.invalidateQueries({ queryKey: ['challenges', variables.challengeId, 'chart-data'] });
      queryClient.invalidateQueries({ queryKey: queryKeys.simulations });
      toast.success('Joined challenge — simulation created and starting!');
    },
    onError: (error: Error) => {
      toast.error(`Failed to join challenge: ${error.message}`);
    },
  });
}

// ===== Market Research =====

/**
 * List all market research configurations
 * GET /api/v1/market-research
 */
export function useMarketResearch(options?: { refetchInterval?: number }) {
  // Suppressed while the feature is off: the backend answers 403, and without this the dashboard's
  // 30-second poll would fail forever in the background on every instance that has the flag
  // switched off. This is a courtesy to the network tab, not a security control — the API refuses
  // regardless.
  //
  // Note this waits for the flags to load rather than using useFeatureEnabled's optimistic
  // default. Guessing "on" is right for navigation, where the cost of guessing wrong is a menu
  // entry that flickers; here it would fire a request we already suspect will 403. Different
  // trade-off, so a different default.
  const { data: features } = useFeatures();
  const available = features ? features.features['market-research'] !== false : false;

  return useQuery({
    queryKey: queryKeys.marketResearch,
    queryFn: () => apiClient.marketResearch.list(),
    ...options,
    enabled: available,
  });
}

/**
 * Get market research details with execution history
 * GET /api/v1/market-research/{id}
 */
export function useMarketResearchDetails(id: string) {
  return useQuery({
    queryKey: queryKeys.marketResearchDetails(id),
    queryFn: () => apiClient.marketResearch.get(id),
    enabled: !!id,
  });
}

/**
 * Create a new market research configuration
 * POST /api/v1/market-research
 */
export function useCreateMarketResearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateMarketResearchRequest) => apiClient.marketResearch.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
      toast.success('Market research created successfully');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to create market research: ${message}`);
    },
  });
}

/**
 * Update an existing market research configuration
 * PUT /api/v1/market-research/{id}
 */
export function useUpdateMarketResearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateMarketResearchRequest }) =>
      apiClient.marketResearch.update(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearchDetails(id) });
      toast.success('Market research updated successfully');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to update market research: ${message}`);
    },
  });
}

/**
 * Pause a market research configuration
 * POST /api/v1/market-research/{id}/pause
 */
export function usePauseMarketResearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.marketResearch.pause(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearchDetails(id) });
      toast.success('Market research paused');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to pause market research: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Resume a paused market research configuration
 * POST /api/v1/market-research/{id}/resume
 */
export function useResumeMarketResearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.marketResearch.resume(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearchDetails(id) });
      toast.success('Market research resumed');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to resume market research: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Manually execute a market research (trigger now)
 * POST /api/v1/market-research/{id}/execute
 */
export function useExecuteMarketResearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.marketResearch.execute(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearchDetails(id) });
      toast.success('Market research execution started');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to execute market research: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Delete a market research configuration
 * DELETE /api/v1/market-research/{id}
 */
export function useDeleteMarketResearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.marketResearch.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
      queryClient.invalidateQueries({ queryKey: queryKeys.strategies });
      toast.success('Market research deleted successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to delete market research: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Delete a specific execution from market research history
 * DELETE /api/v1/market-research/{researchId}/executions/{executionId}
 */
export function useDeleteResearchExecution() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ researchId, executionId }: { researchId: string; executionId: string }) =>
      apiClient.marketResearch.deleteExecution(researchId, executionId),
    onSuccess: (_, { researchId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearchDetails(researchId) });
      toast.success('Execution deleted successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to delete execution: ${getErrorMessage(error)}`);
    },
  });
}

// ===== Data Availability =====

/**
 * Hook to analyze market data coverage.
 * Returns coverage metrics, daily breakdowns, and identified gaps.
 *
 * GET /api/v1/data-availability/coverage
 *
 * @param params Coverage analysis parameters
 * @param enabled Whether the query should run (default: true if params valid)
 */
export function useDataCoverage(
  params: {
    symbols: string[];
    dataTypes: string[];
    from: string;
    to: string;
    strategyId?: string;
  },
  enabled: boolean = true
) {
  return useQuery({
    queryKey: queryKeys.dataCoverage(params),
    queryFn: () => apiClient.dataAvailability.analyzeCoverage(params),
    enabled: enabled && params.symbols.length > 0 && params.dataTypes.length > 0 && !!params.from && !!params.to,
    staleTime: 5 * 1000, // 5 seconds - allow quick updates during backfill
    gcTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch the provider data horizon (earliest available date) per symbol.
 * Horizons change slowly, so this caches for ~1 hour.
 *
 * GET /api/v1/data-availability/horizon
 *
 * Enabled only when at least one symbol is selected and PRICE is among the
 * requested data types (the horizon UI is PRICE-only today).
 */
export function useDataHorizon(params: { symbols: string[]; dataTypes: string[] }) {
  return useQuery({
    queryKey: queryKeys.horizon(params),
    queryFn: () => apiClient.dataAvailability.getHorizon(params),
    enabled: params.symbols.length > 0 && params.dataTypes.includes('PRICE'),
    staleTime: 60 * 60 * 1000, // 1 hour - horizons change slowly
    gcTime: 2 * 60 * 60 * 1000, // 2 hours
  });
}

/**
 * Hook to request backfill for missing market data.
 * Creates an asynchronous backfill job.
 * Returns 202 if backfill triggered, 200 if data already sufficient.
 *
 * POST /api/v1/data-availability/backfill
 */
export function useRequestBackfill() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: {
      symbols: string[];
      dataTypes: string[];
      from: string;
      to: string;
      forceBackfill?: boolean;
    }) => apiClient.dataAvailability.requestBackfill(request),
    onSuccess: (data) => {
      // Invalidate coverage queries as backfill will change data availability
      queryClient.invalidateQueries({
        queryKey: queryKeys.dataAvailability,
        refetchType: 'none', // Don't refetch immediately, let WebSocket updates trigger refetch
      });

      if (data.backfillTriggered) {
        toast.success(`Backfill job started: ${data.jobId}`);
      } else {
        toast.success(data.message);
      }
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to request backfill: ${message}`);
    },
  });
}

/**
 * Hook to get backfill job status.
 * Use this to poll for job completion or combine with WebSocket for real-time updates.
 *
 * GET /api/v1/data-availability/backfill/{jobId}
 *
 * @param jobId Backfill job ID (UUID)
 * @param enabled Whether to enable polling (default: true if jobId provided)
 * @param refetchInterval Poll interval in milliseconds (default: 2000ms)
 */
export function useBackfillStatus(
  jobId: string | null,
  enabled: boolean = true,
  refetchInterval: number = 2000
) {
  return useQuery({
    queryKey: queryKeys.backfillStatus(jobId!),
    queryFn: () => apiClient.dataAvailability.getBackfillStatus(jobId!),
    enabled: enabled && !!jobId,
    refetchInterval: (query) => {
      // Stop polling if job is completed or failed
      const data = query.state.data;
      if (data && (data.status === 'COMPLETED' || data.status === 'FAILED')) {
        return false;
      }
      return refetchInterval;
    },
    refetchIntervalInBackground: true,
    staleTime: 0, // Always consider stale to enable frequent polling
  });
}

// ===== Broker Accounts =====

/**
 * List all broker accounts for the current user
 * GET /api/v1/broker-accounts
 */
export function useBrokerAccounts() {
  return useQuery({
    queryKey: queryKeys.brokerAccounts,
    queryFn: () => apiClient.brokerAccounts.list(),
  });
}

/**
 * Get a specific broker account by ID
 * GET /api/v1/broker-accounts/{id}
 */
export function useBrokerAccount(id: string) {
  return useQuery({
    queryKey: queryKeys.brokerAccount(id),
    queryFn: () => apiClient.brokerAccounts.get(id),
    enabled: !!id,
  });
}

/**
 * Create a new broker account
 * POST /api/v1/broker-accounts
 */
export function useCreateBrokerAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateBrokerAccountRequest) => apiClient.brokerAccounts.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.brokerAccounts });
      toast.success('Broker account created successfully');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to create broker account: ${message}`);
    },
  });
}

/**
 * Update broker account credentials (and optionally name)
 * PUT /api/v1/broker-accounts/{id}
 */
export function useUpdateBrokerAccountCredentials() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateBrokerAccountCredentialsRequest }) =>
      apiClient.brokerAccounts.update(id, data),
    onSuccess: (data, { id }) => {
      // Immediately update caches with the returned account data (includes equity)
      queryClient.setQueryData(queryKeys.brokerAccount(id), data);
      queryClient.setQueryData<BrokerAccountType[]>(queryKeys.brokerAccounts, (old) =>
        old?.map(a => a.id === id ? data : a),
      );
      queryClient.invalidateQueries({ queryKey: queryKeys.brokerAccounts });
      toast.success('Broker account credentials updated successfully');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to update credentials: ${message}`);
    },
  });
}

/**
 * Delete a broker account
 * DELETE /api/v1/broker-accounts/{id}
 */
export function useDeleteBrokerAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.brokerAccounts.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.brokerAccounts });
      toast.success('Broker account deleted successfully');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to delete broker account: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Sync broker account credentials and balance from broker
 * POST /api/v1/broker-accounts/{id}/validate
 */
export function useSyncBrokerAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.brokerAccounts.validate(id),
    onSuccess: (data, id) => {
      // Immediately update the single-account cache with the response (includes equity)
      queryClient.setQueryData(queryKeys.brokerAccount(id), data);
      // Update the account in the list cache if present
      queryClient.setQueryData<BrokerAccountType[]>(queryKeys.brokerAccounts, (old) =>
        old?.map(a => a.id === id ? data : a),
      );
      // Also invalidate to ensure consistency with server state
      queryClient.invalidateQueries({ queryKey: queryKeys.brokerAccounts });
      if (data.status === 'ACTIVE') {
        toast.success('Broker account synced successfully');
      } else {
        toast.error(`Sync failed: Account status is ${data.status || 'unknown'}`);
      }
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to sync broker account: ${getErrorMessage(error)}`);
    },
  });
}

// ===== Usage Statistics =====

/**
 * Get LLM usage statistics for the current user.
 * Returns daily breakdown for last 30 days plus weekly and monthly totals.
 * GET /api/v1/usage/llm
 */
export function useLLMUsage() {
  return useQuery({
    queryKey: queryKeys.llmUsage,
    queryFn: () => apiClient.usage.getLLMUsage(),
    staleTime: 5 * 60 * 1000, // 5 minutes - usage doesn't change rapidly
  });
}

// ===== Budget Management =====

/**
 * Get the current user's budget information.
 * Returns budget limit, current spend, and remaining balance.
 * GET /api/v1/budget
 */
export function useBudget() {
  return useQuery({
    queryKey: queryKeys.budget,
    queryFn: () => apiClient.budget.get(),
    staleTime: 60 * 1000, // 1 minute - budget can change with usage
  });
}

/**
 * Top up the user's budget.
 * This is a mock implementation - no actual payment is processed.
 * POST /api/v1/budget/top-up
 */
export function useTopUpBudget() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (amount: number) => apiClient.budget.topUp({ amount }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.budget });
      queryClient.invalidateQueries({ queryKey: queryKeys.llmUsage });
      toast.success('Budget topped up successfully!');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to top up budget: ${message}`);
    },
  });
}

/**
 * Update the user's daily spend limit.
 * PUT /api/v1/budget/daily-limit
 */
export function useUpdateDailyLimit() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (dailySpendLimit: number | null) =>
      apiClient.budget.updateDailyLimit({ dailySpendLimit }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.budget });
      toast.success('Daily spend limit updated!');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to update daily limit: ${message}`);
    },
  });
}

// ===== Payment =====

/**
 * Check if Stripe payment is configured.
 * GET /api/v1/payment/config
 */
export function usePaymentConfig() {
  return useQuery({
    queryKey: ['payment', 'config'] as const,
    queryFn: () => apiClient.payment.getConfig(),
    staleTime: 60 * 60 * 1000, // 1 hour - config doesn't change at runtime
  });
}

/**
 * Create a Stripe checkout session and redirect to the checkout URL.
 * POST /api/v1/payment/checkout
 */
export function useCreateCheckout() {
  return useMutation({
    mutationFn: (request: { amount: number; successUrl: string; cancelUrl: string }) =>
      apiClient.payment.createCheckout(request),
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to start checkout: ${message}`);
    },
  });
}

// ===== AI Configuration =====

import type { TradingContext, UpdateModelPreferencesRequest, ModelTier, CostEstimateRequest, ModelReliabilityResponse } from '../types/config';
import type { LlmProviderStatus } from '../types/llmProvider';
import type { InstanceInfo } from '../types/instance';
import type {
  FeatureAvailability,
  FeatureFlag,
  FeatureName,
  UpdateFeatureFlagRequest,
} from '../types/features';

/** Backend-rendered LLM setup page (self-host edition) — change the active engine here. */
export const LLM_SETUP_URL =
  (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8085/api/v1') + '/llm/setup';

/**
 * Runtime LLM provider selection status (self-host edition).
 * GET /api/v1/llm/providers — resolves to null on deployments where the selection feature is
 * disabled (hosted SaaS, endpoint 404s), so callers can simply check for null.
 */
export function useLlmProviderStatus() {
  return useQuery<LlmProviderStatus | null>({
    queryKey: ['llm', 'providers'] as const,
    queryFn: async () => {
      try {
        return await apiClient.llm.getProviders();
      } catch (e) {
        if (e instanceof APIError && e.status === 404) {
          return null;
        }
        throw e;
      }
    },
    staleTime: 60 * 1000,
    retry: false,
  });
}

/**
 * Instance descriptor — edition + offered broker regions.
 * GET /api/v1/instance. Any failure resolves to the SaaS shape so the hosted marketing
 * experience is the fail-safe default (the index page must never show an error state).
 */
export function useInstanceInfo() {
  return useQuery<InstanceInfo>({
    queryKey: ['instance'] as const,
    queryFn: async () => {
      try {
        return await apiClient.instance.get();
      } catch {
        // Fail-safe shape. automaticInvestmentsEnabled is false here on purpose: if we cannot
        // reach the instance, we do not know that anything will run a plan, and the safe error
        // is warning about a scheduler that turns out to be on — not promising one that is off.
        return { edition: 'saas', brokers: ['ALPACA'], automaticInvestmentsEnabled: false };
      }
    },
    staleTime: Infinity,
    retry: false,
  });
}

/**
 * Which flagged features are switched on for the current user.
 * GET /api/v1/features.
 *
 * Not folded into `useInstanceInfo`: flags can be targeted at individual people, so this is a
 * property of the caller rather than of the deployment, and it must be re-evaluated when the
 * signed-in user changes rather than cached for the life of the tab.
 *
 * A failure resolves to "everything on". Hiding navigation is a courtesy — the API refuses a
 * disabled feature regardless — so the harm in guessing wrong is a menu entry that 403s, which is
 * a great deal better than hiding features a user paid for because one request failed.
 */
export function useFeatures() {
  return useQuery<FeatureAvailability>({
    queryKey: ['features'] as const,
    queryFn: async () => {
      try {
        return await apiClient.features.get();
      } catch {
        return { features: { challenges: true, 'market-research': true } };
      }
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

/**
 * Whether one feature is available to the current user.
 * Defaults to true while loading, so navigation does not flicker out and back in.
 */
export function useFeatureEnabled(feature: FeatureName): boolean {
  const { data } = useFeatures();
  return data?.features?.[feature] ?? true;
}

/**
 * Admin: every registered feature flag with its current state.
 * GET /api/v1/admin/feature-flags. 403 for non-administrators.
 */
export function useFeatureFlags(enabled: boolean = true) {
  return useQuery<FeatureFlag[]>({
    queryKey: ['admin', 'feature-flags'] as const,
    queryFn: () => apiClient.admin.listFeatureFlags(),
    enabled,
    retry: false,
  });
}

/**
 * Admin: change a flag.
 *
 * Invalidates the caller's own feature availability too — an administrator switching a feature on
 * expects their sidebar to follow without a reload.
 */
export function useUpdateFeatureFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ name, request }: { name: string; request: UpdateFeatureFlagRequest }) =>
      apiClient.admin.updateFeatureFlag(name, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'feature-flags'] });
      queryClient.invalidateQueries({ queryKey: ['features'] });
    },
  });
}

/**
 * Get available LLM models, optionally filtered by trading context and tiers.
 * GET /api/v1/config/models
 */
export function useAvailableModels(context?: TradingContext, tiers?: ModelTier[]) {
  return useQuery({
    queryKey: ['config', 'models', context, tiers] as const,
    queryFn: () => apiClient.config.getModels(context, tiers),
    staleTime: 60 * 60 * 1000, // 1 hour - model list doesn't change often
  });
}

/**
 * Get the current user's model preferences.
 * GET /api/v1/config/preferences
 */
export function useModelPreferences() {
  return useQuery({
    queryKey: ['config', 'preferences'] as const,
    queryFn: () => apiClient.config.getPreferences(),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Update the current user's model preferences.
 * PUT /api/v1/config/preferences
 */
export function useUpdateModelPreferences() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (preferences: UpdateModelPreferencesRequest) =>
      apiClient.config.updatePreferences(preferences),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['config', 'preferences'] });
      toast.success('Model preferences saved!');
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to save preferences: ${message}`);
    },
  });
}

/**
 * Estimate the AI budget cost for a backtest or benchmark.
 * Only fires when all required fields are present.
 * POST /api/v1/config/cost-estimate
 *
 * @param request Strategy IDs and date range (null to disable)
 */
export function useCostEstimate(request: CostEstimateRequest | null) {
  return useQuery({
    queryKey: ['config', 'cost-estimate', request] as const,
    queryFn: () => apiClient.config.estimateCost(request!),
    enabled: !!request && request.strategyIds.length > 0 && !!request.startDate && !!request.endDate,
    staleTime: 5 * 60 * 1000, // 5 minutes - estimate doesn't change rapidly
  });
}

/**
 * Get the LLM model fallback chain configuration.
 * This is a public endpoint that doesn't require authentication.
 * GET /api/v1/config/fallback
 */
export function useFallbackConfig() {
  return useQuery({
    queryKey: ['config', 'fallback'] as const,
    queryFn: () => apiClient.config.getFallbackConfig(),
    staleTime: 60 * 60 * 1000, // 1 hour - fallback config rarely changes
  });
}

/**
 * The model each trading context will ACTUALLY run on the active engine.
 *
 * Replaces the hardcoded `TRADING_CONTEXTS[].defaultModel` constant, which had no owner and drifted
 * away from the backend it was copied from.
 * GET /api/v1/config/effective-models
 */
export function useEffectiveModels() {
  return useQuery({
    queryKey: ['config', 'effective-models'] as const,
    queryFn: () => apiClient.config.getEffectiveModels(),
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Get model reliability statistics (success/failure rates per model).
 * GET /api/v1/config/model-reliability
 */
export function useModelReliability(days = 30) {
  return useQuery({
    queryKey: ['config', 'model-reliability', days] as const,
    queryFn: () => apiClient.config.getModelReliability(days),
    staleTime: 5 * 60 * 1000, // 5 min — backend caches for 1 hour
  });
}

/**
 * Get the earliest date from which historical news data is available.
 * GET /api/v1/config/news-availability
 */
export function useNewsAvailability() {
  return useQuery({
    queryKey: ['config', 'news-availability'] as const,
    queryFn: () => apiClient.config.getNewsAvailability(),
    staleTime: 60 * 60 * 1000, // 1 hour - doesn't change often
  });
}

// ===== API Tokens =====

import type { CreateApiTokenRequest } from '../types/apiToken';

/**
 * List all API tokens for the current user
 * GET /api/v1/settings/api-tokens
 */
export function useApiTokens() {
  return useQuery({
    queryKey: queryKeys.apiTokens,
    queryFn: () => apiClient.apiTokens.list(),
  });
}

/**
 * Create a new API token
 * POST /api/v1/settings/api-tokens
 */
export function useCreateApiToken() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateApiTokenRequest) => apiClient.apiTokens.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.apiTokens });
    },
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to create API token: ${message}`);
    },
  });
}

/**
 * Revoke an API token
 * DELETE /api/v1/settings/api-tokens/{id}
 */
export function useRevokeApiToken() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.apiTokens.revoke(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.apiTokens });
      toast.success('API token revoked');
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to revoke API token: ${getErrorMessage(error)}`);
    },
  });
}

// ===== Account Management =====

/**
 * Permanently delete the authenticated user's account and all associated data.
 * DELETE /api/v1/auth/account
 */
export function useDeleteAccount() {
  return useMutation({
    mutationFn: () => apiClient.account.delete(),
    onError: (error: APIError | Error) => {
      const message = error instanceof APIError
        ? formatValidationErrors(error.problemDetail)
        : error.message;
      toast.error(`Failed to delete account: ${message}`);
    },
  });
}

// ===== Admin =====

/**
 * Get admin dashboard data (counts, status breakdown, recent users, budget summary).
 * GET /api/v1/admin/dashboard
 */
export function useAdminDashboard() {
  return useQuery({
    queryKey: ['admin', 'dashboard'] as const,
    queryFn: () => apiClient.admin.getDashboard(),
    staleTime: 30 * 1000, // 30 seconds
  });
}

/**
 * Search users by email/name with debounced input.
 * GET /api/v1/admin/users/search?q=...
 */
export function useAdminUserSearch(query: string, limit: number = 20) {
  const [debouncedQuery, setDebouncedQuery] = useState(query);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  return useQuery({
    queryKey: ['admin', 'users', 'search', debouncedQuery, limit] as const,
    queryFn: () => apiClient.admin.searchUsers(debouncedQuery, limit),
    enabled: debouncedQuery.length >= 2,
    staleTime: 10 * 1000, // 10 seconds
  });
}

/**
 * Get system health telemetry with auto-refresh.
 * GET /api/v1/admin/health
 */
export function useAdminHealth(refreshInterval: number = 15000) {
  return useQuery({
    queryKey: ['admin', 'health'] as const,
    queryFn: () => apiClient.admin.getHealth(),
    refetchInterval: refreshInterval,
    staleTime: 5 * 1000, // 5 seconds
  });
}

// ===== Cleanup =====

/**
 * Prune unused strategies (with 0 simulations).
 * DELETE /api/v1/cleanup/unused?strategies=true&research=false
 */
export function usePruneUnusedStrategies() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => apiClient.cleanup.pruneUnused({ strategies: true, research: false }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.strategies });
      if (data.deletedStrategies > 0) {
        toast.success(`Removed ${data.deletedStrategies} unused strateg${data.deletedStrategies === 1 ? 'y' : 'ies'}`);
      } else {
        toast.success('No unused strategies found');
      }
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to prune strategies: ${getErrorMessage(error)}`);
    },
  });
}

/**
 * Prune unused research (with 0 created strategies).
 * DELETE /api/v1/cleanup/unused?strategies=false&research=true
 */
export function usePruneUnusedResearch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => apiClient.cleanup.pruneUnused({ strategies: false, research: true }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.marketResearch });
      if (data.deletedResearch > 0) {
        toast.success(`Removed ${data.deletedResearch} unused research`);
      } else {
        toast.success('No unused research found');
      }
    },
    onError: (error: APIError | Error) => {
      toast.error(`Failed to prune research: ${getErrorMessage(error)}`);
    },
  });
}

// ===== Dividends =====

/**
 * Get dividend info for a list of ticker symbols.
 * Returns whether each ticker pays dividends, estimated yield, and payment count.
 * Cached for 24 hours — dividend data changes infrequently.
 *
 * GET /api/v1/dividends/info?symbols=AAPL,MSFT,BTC
 *
 * @param symbols Ticker symbols to query
 * @param enabled Whether the query should run (default: true if symbols provided)
 */
export function useDividendInfo(symbols: string[], enabled: boolean = true) {
  // Sort and dedupe for stable query key
  const normalizedSymbols = useMemo(
    () => [...new Set(symbols.map(s => s.toUpperCase()))].sort(),
    [symbols]
  );

  return useQuery({
    queryKey: ['dividends', 'info', normalizedSymbols] as const,
    queryFn: () => apiClient.dividends.getInfo(normalizedSymbols),
    enabled: enabled && normalizedSymbols.length > 0,
    staleTime: 24 * 60 * 60 * 1000, // 24 hours — dividends declared quarterly
    gcTime: 24 * 60 * 60 * 1000,
  });
}

// Historical Events
export function useHistoricalEvents() {
  return useQuery({
    queryKey: queryKeys.historicalEvents,
    queryFn: () => apiClient.historicalEvents.list(),
    staleTime: 1000 * 60 * 60, // 1 hour — reference data changes rarely
  });
}

// ===== Public Strategies =====

export function usePublicStrategies() {
  return useQuery({
    queryKey: queryKeys.publicStrategies,
    queryFn: () => apiClient.strategies.listPublic(),
  });
}

export function usePublicStrategy(id: string) {
  return useQuery({
    queryKey: queryKeys.publicStrategy(id),
    queryFn: () => apiClient.strategies.getPublic(id),
    enabled: !!id,
  });
}

export function useCloneStrategy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.strategies.clone(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.strategies });
      toast.success('Strategy cloned to your account');
    },
    onError: (error) => {
      toast.error(`Failed to clone strategy: ${getErrorMessage(error)}`);
    },
  });
}

export function useToggleStrategyVisibility() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, publicVisible }: { id: string; publicVisible: boolean }) =>
      apiClient.strategies.toggleVisibility(id, publicVisible),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.strategies });
      queryClient.invalidateQueries({ queryKey: queryKeys.strategy(id) });
      queryClient.invalidateQueries({ queryKey: queryKeys.publicStrategies });
      toast.success('Strategy visibility updated');
    },
    onError: (error) => {
      toast.error(`Failed to update visibility: ${getErrorMessage(error)}`);
    },
  });
}

// -----------------------------------------------------------------------------
// Notifications
// -----------------------------------------------------------------------------

import type {
  Notification,
  NotificationPreference,
  NotificationType,
  NotificationChannel,
} from '../types/notification';

export function useNotifications(params?: { status?: 'unread' | 'all'; limit?: number }) {
  return useQuery({
    queryKey: queryKeys.notifications(params?.status),
    queryFn: () => apiClient.notifications.list(params),
  });
}

/**
 * Polled by the bell icon. Default poll cadence: 60s.
 * Pass {enabled: false} for unauthenticated pages.
 */
export function useUnreadNotificationCount(options?: {
  enabled?: boolean;
  refetchInterval?: number;
}) {
  return useQuery({
    queryKey: queryKeys.notificationsUnreadCount,
    queryFn: () => apiClient.notifications.unreadCount(),
    refetchInterval: options?.refetchInterval ?? 60_000,
    enabled: options?.enabled ?? true,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.notifications.markRead(id),
    onMutate: async (id) => {
      // Optimistic: bump status to READ in any cached list.
      await queryClient.cancelQueries({ queryKey: ['notifications'] });
      const lists = queryClient.getQueriesData<Notification[]>({ queryKey: ['notifications'] });
      lists.forEach(([key, data]) => {
        if (!Array.isArray(data)) return;
        queryClient.setQueryData(
          key,
          data.map((n) => (n.id === id ? { ...n, status: 'READ' } : n))
        );
      });
      return { lists };
    },
    onError: (_err, _id, ctx) => {
      ctx?.lists.forEach(([key, data]) => queryClient.setQueryData(key, data));
      toast.error('Failed to mark notification as read');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: queryKeys.notificationsUnreadCount });
    },
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.notifications.markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: queryKeys.notificationsUnreadCount });
    },
    onError: (error) => {
      toast.error(`Failed to mark all read: ${getErrorMessage(error)}`);
    },
  });
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: queryKeys.notificationPreferences,
    queryFn: () => apiClient.notificationPreferences.list(),
  });
}

export function useUpdateNotificationPreference() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ type, channels }: { type: NotificationType; channels: NotificationChannel[] }) =>
      apiClient.notificationPreferences.update(type, { channels }),
    onSuccess: (updated: NotificationPreference) => {
      queryClient.setQueryData<NotificationPreference[]>(
        queryKeys.notificationPreferences,
        (prev) => {
          if (!prev) return [updated];
          return prev.map((p) => (p.type === updated.type ? updated : p));
        }
      );
      toast.success('Notification preferences updated');
    },
    onError: (error) => {
      toast.error(`Failed to update preferences: ${getErrorMessage(error)}`);
    },
  });
}

// -----------------------------------------------------------------------------
// Investment Plans (recurring DCA)
// -----------------------------------------------------------------------------

import type {
  InvestmentPlan,
  InvestmentAllocation,
  CreateInvestmentPlanRequest,
  UpdateInvestmentPlanRequest,
  InvestmentPlanStatus as PlanStatus,
} from '../types/investmentPlan';

export const investmentPlanKeys = {
  list: (status?: PlanStatus) => ['investment-plans', status ?? 'all'] as const,
  detail: (id: string) => ['investment-plans', id] as const,
  allocations: (planId: string) => ['investment-plans', planId, 'allocations'] as const,
  allAllocations: ['investment-allocations', 'all'] as const,
};

export function useInvestmentPlans(status?: PlanStatus) {
  return useQuery({
    queryKey: investmentPlanKeys.list(status),
    queryFn: () => apiClient.investmentPlans.list(status),
  });
}

export function useInvestmentPlan(id: string) {
  return useQuery({
    queryKey: investmentPlanKeys.detail(id),
    queryFn: () => apiClient.investmentPlans.get(id),
    enabled: !!id,
  });
}

export function useCreateInvestmentPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateInvestmentPlanRequest) => apiClient.investmentPlans.create(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['investment-plans'] });
      toast.success('Investment plan created');
    },
    onError: (error) => toast.error(`Failed to create plan: ${getErrorMessage(error)}`),
  });
}

export function useUpdateInvestmentPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateInvestmentPlanRequest }) =>
      apiClient.investmentPlans.update(id, body),
    onSuccess: (plan: InvestmentPlan) => {
      queryClient.invalidateQueries({ queryKey: ['investment-plans'] });
      queryClient.setQueryData(investmentPlanKeys.detail(plan.id), plan);
      toast.success('Investment plan updated');
    },
    onError: (error) => toast.error(`Failed to update plan: ${getErrorMessage(error)}`),
  });
}

export function usePauseInvestmentPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.investmentPlans.pause(id),
    onSuccess: (plan: InvestmentPlan) => {
      queryClient.invalidateQueries({ queryKey: ['investment-plans'] });
      queryClient.setQueryData(investmentPlanKeys.detail(plan.id), plan);
    },
  });
}

export function useResumeInvestmentPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.investmentPlans.resume(id),
    onSuccess: (plan: InvestmentPlan) => {
      queryClient.invalidateQueries({ queryKey: ['investment-plans'] });
      queryClient.setQueryData(investmentPlanKeys.detail(plan.id), plan);
    },
  });
}

export function useTerminateInvestmentPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.investmentPlans.terminate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['investment-plans'] });
      toast.success('Investment plan terminated');
    },
    onError: (error) => toast.error(`Failed to terminate plan: ${getErrorMessage(error)}`),
  });
}

export function useExecuteInvestmentPlanNow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.investmentPlans.executeNow(id),
    onSuccess: (allocation: InvestmentAllocation) => {
      queryClient.invalidateQueries({ queryKey: ['investment-plans'] });
      queryClient.invalidateQueries({ queryKey: investmentPlanKeys.allocations(allocation.planId) });
      queryClient.invalidateQueries({ queryKey: investmentPlanKeys.allAllocations });
    },
    onError: (error) => toast.error(`Execute failed: ${getErrorMessage(error)}`),
  });
}

export function useInvestmentPlanAllocations(planId: string, limit = 50) {
  return useQuery({
    queryKey: investmentPlanKeys.allocations(planId),
    queryFn: () => apiClient.investmentPlans.listAllocations(planId, limit),
    enabled: !!planId,
  });
}

export function useAllInvestmentAllocations(limit = 100) {
  return useQuery({
    queryKey: investmentPlanKeys.allAllocations,
    queryFn: () => apiClient.investmentAllocations.list(limit),
  });
}
