/**
 * Transforms benchmark data into Recharts-compatible format
 */

import { BenchmarkSimulation, ChartDataPoint, OverlayResponse, CompactChartData } from '@/app/lib/types/benchmark';
import { getSimulationColor } from '@/app/lib/utils/colors';

/**
 * Transforms simulations data into unified timeline for Recharts
 * Creates an array of data points where each point contains all simulation values at that timestamp
 * Uses forward-fill interpolation to create continuous lines between actual data points
 *
 * @param simulations - Array of benchmark simulations
 * @returns Array of chart data points with unified timeline
 */
export function transformToChartData(simulations: BenchmarkSimulation[]): ChartDataPoint[] {
  if (simulations.length === 0) {
    return [];
  }

  // Step 1: Collect all unique timestamps across all simulations
  const timestampSet = new Set<number>();
  simulations.forEach(sim => {
    sim.dataPoints.forEach(point => {
      timestampSet.add(point.timestamp.getTime());
    });
  });

  // Step 2: Sort timestamps chronologically
  const sortedTimestamps = Array.from(timestampSet).sort((a, b) => a - b);

  // Step 3: Build chart data points with forward-fill between data points, but no extrapolation beyond last data
  const lastKnownValues = new Map<string, number>(); // Track last value for each simulation
  const lastDataTimestamp = new Map<string, number>(); // Track last actual data timestamp for each simulation

  // Calculate the last actual data timestamp for each simulation
  simulations.forEach(sim => {
    if (sim.dataPoints.length > 0) {
      const lastPoint = sim.dataPoints[sim.dataPoints.length - 1];
      lastDataTimestamp.set(sim.id, lastPoint.timestamp.getTime());
    }
  });

  const chartData: ChartDataPoint[] = sortedTimestamps.map(timestamp => {
    const dataPoint: ChartDataPoint = { timestamp };

    // For each simulation, find the value at this timestamp or forward-fill from last known value
    simulations.forEach(sim => {
      const matchingPoint = sim.dataPoints.find(
        point => point.timestamp.getTime() === timestamp
      );

      if (matchingPoint) {
        // Update last known value and use it
        lastKnownValues.set(sim.id, matchingPoint.portfolioValue);
        dataPoint[sim.id] = matchingPoint.portfolioValue;
      } else {
        // Use last known value only if we haven't passed the last actual data point
        const lastValue = lastKnownValues.get(sim.id);
        const lastTimestamp = lastDataTimestamp.get(sim.id);

        if (lastValue !== undefined && lastTimestamp !== undefined && timestamp <= lastTimestamp) {
          // Forward-fill: draw line between actual data points
          dataPoint[sim.id] = lastValue;
        } else {
          // Beyond last data point or before first data point: use null
          dataPoint[sim.id] = null;
        }
      }
    });

    return dataPoint;
  });

  return chartData;
}

/**
 * Merges overlay data points into existing chart data using a unified timeline.
 * Overlay keys are prefixed with "overlay_" to distinguish them from simulation IDs.
 * Uses forward-fill interpolation between overlay data points.
 */
export function mergeOverlaysIntoChartData(
  chartData: ChartDataPoint[],
  overlays: OverlayResponse[]
): ChartDataPoint[] {
  const readyOverlays = overlays.filter(o => o.status === 'READY' && o.dataPoints.length > 0);
  if (readyOverlays.length === 0) return chartData;

  // Collect all timestamps (existing + overlay)
  const timestampSet = new Set<number>();
  chartData.forEach(p => timestampSet.add(p.timestamp));
  readyOverlays.forEach(overlay => {
    overlay.dataPoints.forEach(dp => {
      timestampSet.add(new Date(dp.timestamp).getTime());
    });
  });

  const sortedTimestamps = Array.from(timestampSet).sort((a, b) => a - b);

  // Build lookup maps for existing chart data
  const existingDataMap = new Map<number, ChartDataPoint>();
  chartData.forEach(p => existingDataMap.set(p.timestamp, p));

  // Build overlay data maps (timestamp -> value) for each overlay
  const overlayMaps = readyOverlays.map(overlay => {
    const map = new Map<number, number>();
    overlay.dataPoints.forEach(dp => {
      map.set(new Date(dp.timestamp).getTime(), dp.portfolioValue);
    });
    return { symbol: overlay.symbol, map, dataPoints: overlay.dataPoints };
  });

  // Merge into unified timeline
  const lastKnown: Record<string, number | undefined> = {};
  const lastTimestamp: Record<string, number> = {};

  // Pre-calculate last data timestamp for each overlay
  overlayMaps.forEach(({ symbol, dataPoints }) => {
    if (dataPoints.length > 0) {
      lastTimestamp[symbol] = new Date(dataPoints[dataPoints.length - 1].timestamp).getTime();
    }
  });

  return sortedTimestamps.map(ts => {
    const existing = existingDataMap.get(ts);
    const point: ChartDataPoint = existing ? { ...existing } : { timestamp: ts };

    overlayMaps.forEach(({ symbol, map }) => {
      const key = `overlay_${symbol}`;
      const value = map.get(ts);

      if (value !== undefined) {
        lastKnown[symbol] = value;
        point[key] = value;
      } else {
        const last = lastKnown[symbol];
        const lastTs = lastTimestamp[symbol];
        if (last !== undefined && lastTs !== undefined && ts <= lastTs) {
          point[key] = last;
        } else {
          point[key] = null;
        }
      }
    });

    return point;
  });
}

/**
 * Merges overlay data into simulation portfolio history (index-based X-axis).
 *
 * The simulation chart uses sequential indices with `fullTimestamp: Date` on each point.
 * For each overlay, we binary-search the overlay's sorted epoch-ms array to find the
 * closest preceding timestamp for each portfolio data point, then forward-fill.
 *
 * Returns the same data array with `overlay_${symbol}` keys added — no new points inserted.
 */
export function mergeOverlaysIntoPortfolioHistory<T extends { fullTimestamp?: Date }>(
  data: T[],
  overlays: OverlayResponse[]
): T[] {
  const readyOverlays = overlays.filter(o => o.status === 'READY' && o.dataPoints.length > 0);
  if (readyOverlays.length === 0 || data.length === 0) return data;

  // Pre-build sorted arrays per overlay
  const overlayArrays = readyOverlays.map(overlay => {
    const sorted = overlay.dataPoints
      .map(dp => ({ epochMs: new Date(dp.timestamp).getTime(), value: dp.portfolioValue }))
      .sort((a, b) => a.epochMs - b.epochMs);
    return { symbol: overlay.symbol, sorted };
  });

  return data.map(point => {
    if (!point.fullTimestamp) return point;
    const pointMs = point.fullTimestamp.getTime();
    const extended: Record<string, unknown> = { ...point };

    for (const { symbol, sorted } of overlayArrays) {
      // Binary search: find largest epochMs <= pointMs
      let lo = 0, hi = sorted.length - 1, best = -1;
      while (lo <= hi) {
        const mid = (lo + hi) >>> 1;
        if (sorted[mid].epochMs <= pointMs) {
          best = mid;
          lo = mid + 1;
        } else {
          hi = mid - 1;
        }
      }
      extended[`overlay_${symbol}`] = best >= 0 ? sorted[best].value : null;
    }

    return extended as T;
  });
}

/**
 * Transforms compact columnar chart data from the backend into
 * BenchmarkSimulation[] and ChartDataPoint[] for the existing chart components.
 *
 * @param data - Compact chart data from GET /api/v1/benchmarks/{id}/chart-data
 * @returns simulations (with colors, return %, data points) and chartData for Recharts
 */
export function transformCompactChartData(
  data: CompactChartData
): { simulations: BenchmarkSimulation[]; chartData: ChartDataPoint[] } {
  const { simulations: simInfos, points } = data;

  if (simInfos.length === 0) {
    return { simulations: [], chartData: [] };
  }

  // When simulations exist but no portfolio snapshots yet (benchmark just started),
  // return simulation metadata so the UI can show a "preparing" state
  if (points.length === 0) {
    const simulations: BenchmarkSimulation[] = simInfos.map((info, index) => ({
      id: info.id,
      name: info.name || `Simulation ${info.id.slice(0, 8)}`,
      strategyName: info.strategyName,
      strategyId: '',
      color: getSimulationColor(index),
      currentValue: info.initialCash || 100000,
      startValue: info.initialCash || 100000,
      returnPercent: 0,
      dataPoints: [],
      startDate: new Date(),
      endDate: new Date(),
      status: 'PENDING' as const,
    }));
    return { simulations, chartData: [] };
  }

  // Build per-simulation data points and track first/last values
  const simDataPoints: Map<string, { timestamp: Date; portfolioValue: number }[]> = new Map();
  const firstValue: Map<string, number> = new Map();
  const lastValue: Map<string, number> = new Map();

  for (const row of points) {
    const timestamp = row[0] as number;

    for (let i = 0; i < simInfos.length; i++) {
      const val = row[i + 1];
      if (val == null) continue;

      const simId = simInfos[i].id;
      const numVal = val as number;

      if (!simDataPoints.has(simId)) {
        simDataPoints.set(simId, []);
      }
      simDataPoints.get(simId)!.push({
        timestamp: new Date(timestamp),
        portfolioValue: numVal,
      });

      if (!firstValue.has(simId)) {
        firstValue.set(simId, numVal);
      }
      lastValue.set(simId, numVal);
    }
  }

  // Build BenchmarkSimulation array
  const simulations: BenchmarkSimulation[] = simInfos.map((info, index) => {
    const dataPoints = simDataPoints.get(info.id) || [];
    const startValue = info.initialCash || firstValue.get(info.id) || 100000;
    const currentValue = lastValue.get(info.id) || startValue;
    const returnPercent = ((currentValue - startValue) / startValue) * 100;

    const startDate = dataPoints.length > 0 ? dataPoints[0].timestamp : new Date();
    const endDate = dataPoints.length > 0 ? dataPoints[dataPoints.length - 1].timestamp : new Date();

    return {
      id: info.id,
      name: info.name || `Simulation ${info.id.slice(0, 8)}`,
      strategyName: info.strategyName,
      strategyId: '', // not available in compact response, not needed for chart
      color: getSimulationColor(index),
      currentValue,
      startValue,
      returnPercent,
      dataPoints,
      startDate,
      endDate,
      status: 'COMPLETED' as const, // chart-data doesn't include status; callers can override
    };
  });

  // Build ChartDataPoint[] with forward-fill (reuse existing logic via transformToChartData)
  const chartData = transformToChartData(simulations);

  return { simulations, chartData };
}
