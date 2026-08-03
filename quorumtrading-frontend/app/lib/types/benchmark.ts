/**
 * Benchmark data types for real-time simulation comparison
 */

import { CostPreset } from './brokerAccount';

export interface BenchmarkDataPoint {
  timestamp: Date;
  portfolioValue: number;
}

export interface BenchmarkSimulation {
  id: string;
  name: string;
  strategyName: string;
  strategyId: string;
  color: string;
  currentValue: number;
  startValue: number;
  returnPercent: number;
  dataPoints: BenchmarkDataPoint[];
  startDate: Date;
  endDate: Date;
  status: 'PENDING' | 'WAITING_FOR_DATA' | 'SIMULATING' | 'LIVE' | 'COMPLETED' | 'FAILED';
}

export interface BenchmarkData {
  simulations: BenchmarkSimulation[];
  timelineBounds: {
    start: Date;
    end: Date;
  };
}

export interface BenchmarkState {
  data: BenchmarkData;
  leaders: {
    top: BenchmarkEntry[];
    bottom: BenchmarkEntry[];
  };
  loading: boolean;
  wsConnected: boolean;
  error: string | null;
}

export interface BenchmarkEntry {
  id: string;
  name: string;
  strategyName: string;
  color: string;
  value: number;
  returnPercent: number;
  status: 'PENDING' | 'WAITING_FOR_DATA' | 'SIMULATING' | 'LIVE' | 'COMPLETED' | 'FAILED';
}

/**
 * Chart data point format for Recharts
 * Maps simulation IDs to portfolio values at each timestamp
 */
export interface ChartDataPoint {
  timestamp: number; // Unix timestamp for X-axis
  [simulationId: string]: number | null; // Portfolio value or null if inactive
}

// ============================================================================
// Wizard API Types (POST /api/v1/benchmarks)
// ============================================================================

/**
 * Mock broker configuration for creating simulated broker accounts.
 * Used when creating a benchmark without an existing broker account.
 */
export interface BenchmarkMockConfig {
  /** Starting capital amount in USD */
  initialCapital: number;
  /** Cost simulation preset (determines commission/slippage) */
  preset: CostPreset;
}

/**
 * Request to create a new benchmark with multiple strategies
 * POST /api/v1/benchmarks
 *
 * **Broker Configuration Options (mutually exclusive):**
 * - `mockConfig`: Creates new mock broker accounts (one per strategy) with the specified configuration
 * - `brokerAccountId` + `capitalPerStrategy`: Uses an existing paper/real broker account
 *
 * If neither is provided, the backend will create mock accounts with default settings.
 */
export interface CreateBenchmarkRequest {
  /** Array of strategy UUIDs to include in the benchmark */
  strategyIds: string[];

  /** Start date/time in ISO-8601 format (omit for live mode - backend uses now) */
  startDate?: string;

  /** End date/time in ISO-8601 format (omit for live mode) */
  endDate?: string;

  /** Optional custom name (auto-generated if omitted) */
  name?: string;

  // ============================================================================
  // Broker Configuration (mutually exclusive options)
  // ============================================================================

  /**
   * Mock broker configuration for backtests.
   * When provided, creates a new mock broker account per strategy with the specified settings.
   * Mutually exclusive with `brokerAccountId`.
   */
  mockConfig?: BenchmarkMockConfig;

  /**
   * Existing broker account ID to use for live/paper trading.
   * Must be a valid UUID of an active broker account.
   * Requires `capitalPerStrategy` to also be set.
   * Mutually exclusive with `mockConfig`.
   */
  brokerAccountId?: string;

  /**
   * Capital allocation per strategy when using an existing broker account.
   * Required when `brokerAccountId` is provided.
   * Amount in USD that will be reserved from the broker account for each strategy.
   */
  capitalPerStrategy?: number;
}

/**
 * Response from creating a benchmark
 * HTTP 202 ACCEPTED - Simulations start asynchronously
 */
export interface CreateBenchmarkResponse {
  /** UUID of the created benchmark. Null when a single strategy was submitted (standalone simulation). */
  benchmarkId: string | null;

  /** Array of simulation UUIDs created */
  simulationIds: string[];

  /** Final benchmark/simulation name (custom or auto-generated) */
  benchmarkName: string;
}

/**
 * Benchmark details from API
 * GET /api/v1/benchmarks/{id}
 * GET /api/v1/benchmarks (array)
 */
export interface BenchmarkApiResponse {
  /** Unique benchmark identifier */
  id: string;

  /** Benchmark name (custom or auto-generated) */
  name: string;

  /** Start date/time in ISO-8601 format */
  startDate: string;

  /** End date/time in ISO-8601 format (null for live mode) */
  endDate: string | null;

  /** Array of simulation UUIDs in this benchmark */
  simulationIds: string[];

  /** Creation timestamp in ISO-8601 format */
  createdAt: string;

  /** True if live mode (no end date), false for backtests */
  isLive: boolean;
}

// ============================================================================
// Overlay Types (GET /api/v1/benchmarks/{id}/overlays)
// ============================================================================

export interface OverlayDataPoint {
  timestamp: string;
  portfolioValue: number;
}

export interface OverlayResponse {
  symbol: string;
  assetType: 'STOCK' | 'CRYPTO';
  status: 'READY' | 'NO_DATA';
  dataPoints: OverlayDataPoint[];
  returnPercent: number | null;
  errorMessage: string | null;
}

// ============================================================================
// Compact Chart Data Types (GET /api/v1/benchmarks/{id}/chart-data)
// ============================================================================

export interface CompactSimulationInfo {
  id: string;
  name: string;
  strategyName: string;
  initialCash: number;
}

/**
 * Compact columnar chart data from the backend.
 * simulations[i] maps to points[][i+1] (index 0 is always epoch-ms timestamp).
 */
export interface CompactChartData {
  simulations: CompactSimulationInfo[];
  points: (number | null)[][];
}

export const POPULAR_OVERLAYS = [
  { symbol: 'SPY', label: 'S&P 500' },
  { symbol: 'QQQ', label: 'Nasdaq' },
  { symbol: 'VTI', label: 'Total Market' },
  { symbol: 'BTC', label: 'Bitcoin' },
  { symbol: 'ETH', label: 'Ethereum' },
] as const;

/**
 * Benchmark mode type
 */
export type BenchmarkMode = 'backtest' | 'live';

/**
 * Helper to determine benchmark mode
 */
export function getBenchmarkMode(benchmark: BenchmarkApiResponse): BenchmarkMode {
  return benchmark.isLive ? 'live' : 'backtest';
}

/**
 * Helper to format benchmark date range
 */
export function formatBenchmarkDateRange(benchmark: BenchmarkApiResponse): string {
  const start = new Date(benchmark.startDate).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  });

  if (benchmark.endDate) {
    const end = new Date(benchmark.endDate).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
    return `${start} - ${end}`;
  }

  return `Started ${start}`;
}
