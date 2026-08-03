/**
 * Utility functions for calculating performer rankings
 *
 * CRITICAL: This uses dataPoints as the source of truth, not cached currentValue.
 * This ensures the benchmark rankings match what's displayed in the chart.
 */

import { BenchmarkSimulation, BenchmarkEntry } from '@/app/lib/types/benchmark';

/**
 * Calculates all performers sorted by portfolio value (descending)
 *
 * DERIVES values from dataPoints (source of truth), NOT cached properties.
 * This ensures the sidebar always matches the chart display.
 *
 * @param simulations - Array of benchmark simulations
 * @returns Array of leader entries sorted by portfolio value (highest first)
 */
export function calculateLeaders(simulations: BenchmarkSimulation[]): BenchmarkEntry[] {
  if (simulations.length === 0) {
    return [];
  }

  // Derive current values directly from dataPoints (source of truth)
  // Never use cached currentValue/returnPercent properties
  const simulationsWithDerivedValues = simulations.map(sim => {
    if (sim.dataPoints.length === 0) {
      // Fallback for simulations with no data yet
      return {
        ...sim,
        derivedValue: sim.currentValue,
        derivedReturnPercent: sim.returnPercent,
      };
    }

    // Get the LAST data point (most recent timestamp)
    const lastDataPoint = sim.dataPoints[sim.dataPoints.length - 1];
    const derivedValue = lastDataPoint.portfolioValue;
    // startValue is the invested basis here: benchmark members are not DCA-funded
    // (contributions come from investment plans targeting standalone LIVE sims, never
    // benchmark entries), so startValue == total invested. The authoritative
    // contribution-aware return for funded sims lives on the backend (BenchmarkResource).
    const derivedReturnPercent = ((derivedValue - sim.startValue) / sim.startValue) * 100;

    return {
      ...sim,
      derivedValue,
      derivedReturnPercent,
    };
  });

  // Sort by derived value (descending) for true ranking
  const sorted = [...simulationsWithDerivedValues].sort(
    (a, b) => b.derivedValue - a.derivedValue
  );

  // Return all performers as a sorted array
  return sorted.map(sim => ({
    id: sim.id,
    name: sim.name,
    strategyName: sim.strategyName,
    color: sim.color,
    value: sim.derivedValue,
    returnPercent: sim.derivedReturnPercent,
    status: sim.status,
  }));
}
