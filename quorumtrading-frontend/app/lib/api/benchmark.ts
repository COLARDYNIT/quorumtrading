/**
 * API client for benchmark data fetching
 */

import { BenchmarkSimulation, BenchmarkData } from '@/app/lib/types/benchmark';
import { getSimulationColor } from '@/app/lib/utils/colors';
import { SimulationStatus } from '@/app/lib/types/api';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL?.replace('/api/v1', '') || 'http://localhost:8085';

interface SimulationResponse {
  id: string;
  name: string;
  strategyId: string;
  startDate: string;
  endDate: string;
  status: SimulationStatus;
  paused: boolean;
  progress: number;
}

interface PortfolioSnapshotResponse {
  id: string;
  simulationId: string;
  timestamp: string;
  cashBalance: number;
  positions: Record<string, unknown>;
  totalValue: number;
}

interface PortfolioSnapshotsResponse {
  snapshots: PortfolioSnapshotResponse[];
  total: number;
  simulationId: string;
  limit: number;
}

interface StrategyResponse {
  id: string;
  name: string;
  version: number;
  markdownContent: string;
  createdAt: string;
}

/**
 * Fetches simulations with optional filtering
 * @param since - Optional ISO-8601 timestamp to filter by startedAt >= since
 * @param includeEnded - Whether to include paused/ended simulations (default: false for backwards compatibility)
 */
export async function fetchActiveSimulations(
  since?: string,
  includeEnded: boolean = false
): Promise<SimulationResponse[]> {
  const url = new URL(`${API_BASE_URL}/api/v1/simulations`);
  if (since) {
    url.searchParams.append('since', since);
  }

  const response = await fetch(url.toString(), { credentials: 'include' });

  if (!response.ok) {
    throw new Error(`Failed to fetch simulations: ${response.statusText}`);
  }

  const allSimulations: SimulationResponse[] = await response.json();

  // Filter to non-paused simulations unless includeEnded is true
  if (includeEnded) {
    return allSimulations;
  }
  return allSimulations.filter(sim => !sim.paused);
}

/**
 * Fetches portfolio snapshots for a specific simulation
 *
 * CRITICAL: Snapshots are returned in reverse chronological order from the backend.
 * This function sorts them chronologically (oldest → newest) to ensure:
 * - dataPoints[0] = initial portfolio state
 * - dataPoints[-1] = current portfolio state
 *
 * This is the single source of truth for all benchmark calculations.
 */
export async function fetchPortfolioSnapshots(
  simulationId: string,
  options?: { resolution?: string; excludePositions?: boolean }
): Promise<PortfolioSnapshotResponse[]> {
  const params = new URLSearchParams({ simulationId });
  if (options?.resolution) {
    params.set('resolution', options.resolution);
  }
  if (options?.excludePositions) {
    params.set('excludePositions', 'true');
  }
  const url = `${API_BASE_URL}/api/v1/portfolios/snapshots?${params}`;
  const response = await fetch(url, { credentials: 'include' });

  if (!response.ok) {
    throw new Error(
      `Failed to fetch portfolio snapshots for ${simulationId}: ${response.statusText}`
    );
  }

  const data: PortfolioSnapshotsResponse = await response.json();

  // Backend returns snapshots in reverse chronological order (newest first)
  // Sort them chronologically (oldest first) to ensure consistent indexing:
  // - First element = oldest snapshot (initial state)
  // - Last element = newest snapshot (current state)
  return data.snapshots.sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  );
}

/**
 * Fetches strategy details by ID
 */
export async function fetchStrategy(strategyId: string): Promise<StrategyResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/strategies/${strategyId}`, { credentials: 'include' });

  if (!response.ok) {
    throw new Error(`Failed to fetch strategy ${strategyId}: ${response.statusText}`);
  }

  return response.json();
}

/**
 * Fetches complete benchmark data (all simulations + snapshots)
 * @param since - Optional ISO-8601 timestamp to filter by startedAt >= since
 */
export async function fetchBenchmarkData(since?: string): Promise<BenchmarkData> {
  // Step 1: Fetch simulations (include ended simulations when since is provided)
  const includeEnded = !!since;
  const simulations = await fetchActiveSimulations(since, includeEnded);

  if (simulations.length === 0) {
    return {
      simulations: [],
      timelineBounds: {
        start: new Date(),
        end: new Date(),
      },
    };
  }

  // Step 2: Fetch portfolio snapshots and strategy names in parallel
  // Use excludePositions + daily resolution for benchmarks (only need totalValue for chart)
  const simulationDataPromises = simulations.map(async (sim, index) => {
    const [snapshots, strategy] = await Promise.all([
      fetchPortfolioSnapshots(sim.id, { resolution: 'daily', excludePositions: true }),
      fetchStrategy(sim.strategyId),
    ]);

    const dataPoints = snapshots.map(snapshot => ({
      timestamp: new Date(snapshot.timestamp),
      portfolioValue: snapshot.totalValue,
    }));

    const currentValue = dataPoints.length > 0
      ? dataPoints[dataPoints.length - 1].portfolioValue
      : 100000;

    // Use first snapshot as fallback; callers with access to initialCash should prefer that
    const startValue = dataPoints.length > 0
      ? dataPoints[0].portfolioValue
      : 100000;

    const returnPercent = ((currentValue - startValue) / startValue) * 100;

    const benchmarkSim: BenchmarkSimulation = {
      id: sim.id,
      name: sim.name || `Simulation ${sim.id.slice(0, 8)}`,
      strategyName: strategy.name,
      strategyId: sim.strategyId,
      color: getSimulationColor(index),
      currentValue,
      startValue,
      returnPercent,
      dataPoints,
      startDate: new Date(sim.startDate),
      endDate: new Date(sim.endDate),
      status: sim.status,
    };

    return benchmarkSim;
  });

  const benchmarkSimulations = await Promise.all(simulationDataPromises);

  // Step 3: Calculate timeline bounds
  const allStartDates = benchmarkSimulations.map(s => s.startDate);
  const allEndDates = benchmarkSimulations.map(s => s.endDate);

  const timelineStart = new Date(Math.min(...allStartDates.map(d => d.getTime())));
  const timelineEnd = new Date(Math.max(...allEndDates.map(d => d.getTime())));

  return {
    simulations: benchmarkSimulations,
    timelineBounds: {
      start: timelineStart,
      end: timelineEnd,
    },
  };
}
