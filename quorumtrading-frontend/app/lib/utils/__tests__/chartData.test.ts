import { transformToChartData } from '../chartData';
import { BenchmarkSimulation, ChartDataPoint } from '@/app/lib/types/benchmark';

/**
 * Helper to create mock BenchmarkSimulation
 */
function createMockSimulation(overrides: Partial<BenchmarkSimulation> = {}): BenchmarkSimulation {
  return {
    id: 'sim-1',
    name: 'Test Simulation',
    strategyName: 'Test Strategy',
    strategyId: 'strategy-1',
    color: '#00ff41',
    currentValue: 10000,
    startValue: 10000,
    returnPercent: 0,
    dataPoints: [],
    startDate: new Date('2024-01-01'),
    endDate: new Date('2024-12-31'),
    status: 'COMPLETED',
    ...overrides,
  };
}

describe('transformToChartData', () => {
  describe('empty input', () => {
    it('should return empty array for empty simulations', () => {
      const result = transformToChartData([]);

      expect(result).toEqual([]);
    });
  });

  describe('single simulation', () => {
    it('should transform single simulation with one dataPoint', () => {
      const timestamp = new Date('2024-01-01T12:00:00Z');
      const simulation = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp, portfolioValue: 10000 },
        ],
      });

      const result = transformToChartData([simulation]);

      expect(result).toHaveLength(1);
      expect(result[0]).toEqual({
        timestamp: timestamp.getTime(),
        'sim-1': 10000,
      });
    });

    it('should transform single simulation with multiple dataPoints', () => {
      const ts1 = new Date('2024-01-01T12:00:00Z');
      const ts2 = new Date('2024-01-02T12:00:00Z');
      const ts3 = new Date('2024-01-03T12:00:00Z');

      const simulation = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp: ts1, portfolioValue: 10000 },
          { timestamp: ts2, portfolioValue: 10500 },
          { timestamp: ts3, portfolioValue: 11000 },
        ],
      });

      const result = transformToChartData([simulation]);

      expect(result).toHaveLength(3);
      expect(result[0]).toEqual({ timestamp: ts1.getTime(), 'sim-1': 10000 });
      expect(result[1]).toEqual({ timestamp: ts2.getTime(), 'sim-1': 10500 });
      expect(result[2]).toEqual({ timestamp: ts3.getTime(), 'sim-1': 11000 });
    });
  });

  describe('multiple simulations', () => {
    it('should merge simulations with same timestamps', () => {
      const timestamp = new Date('2024-01-01T12:00:00Z');

      const sim1 = createMockSimulation({
        id: 'sim-1',
        dataPoints: [{ timestamp, portfolioValue: 10000 }],
      });
      const sim2 = createMockSimulation({
        id: 'sim-2',
        dataPoints: [{ timestamp, portfolioValue: 12000 }],
      });

      const result = transformToChartData([sim1, sim2]);

      expect(result).toHaveLength(1);
      expect(result[0]).toEqual({
        timestamp: timestamp.getTime(),
        'sim-1': 10000,
        'sim-2': 12000,
      });
    });

    it('should sort all timestamps chronologically', () => {
      const ts1 = new Date('2024-01-01T12:00:00Z');
      const ts2 = new Date('2024-01-02T12:00:00Z');
      const ts3 = new Date('2024-01-03T12:00:00Z');

      const sim1 = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp: ts3, portfolioValue: 11000 }, // Later timestamp
          { timestamp: ts1, portfolioValue: 10000 }, // Earlier timestamp
        ],
      });
      const sim2 = createMockSimulation({
        id: 'sim-2',
        dataPoints: [
          { timestamp: ts2, portfolioValue: 10500 },
        ],
      });

      const result = transformToChartData([sim1, sim2]);

      expect(result).toHaveLength(3);
      expect(result[0].timestamp).toBe(ts1.getTime());
      expect(result[1].timestamp).toBe(ts2.getTime());
      expect(result[2].timestamp).toBe(ts3.getTime());
    });
  });

  describe('forward-fill interpolation', () => {
    it('should forward-fill values between actual dataPoints', () => {
      const ts1 = new Date('2024-01-01T12:00:00Z');
      const ts2 = new Date('2024-01-02T12:00:00Z');
      const ts3 = new Date('2024-01-03T12:00:00Z');

      // sim-1 has data at ts1 and ts3
      // sim-2 has data at ts1, ts2, and ts3
      const sim1 = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp: ts1, portfolioValue: 10000 },
          { timestamp: ts3, portfolioValue: 11000 },
        ],
      });
      const sim2 = createMockSimulation({
        id: 'sim-2',
        dataPoints: [
          { timestamp: ts1, portfolioValue: 12000 },
          { timestamp: ts2, portfolioValue: 12500 },
          { timestamp: ts3, portfolioValue: 13000 },
        ],
      });

      const result = transformToChartData([sim1, sim2]);

      expect(result).toHaveLength(3);

      // At ts1: both have actual data
      expect(result[0]['sim-1']).toBe(10000);
      expect(result[0]['sim-2']).toBe(12000);

      // At ts2: sim-1 should forward-fill from ts1 value
      expect(result[1]['sim-1']).toBe(10000); // Forward-filled
      expect(result[1]['sim-2']).toBe(12500);

      // At ts3: both have actual data
      expect(result[2]['sim-1']).toBe(11000);
      expect(result[2]['sim-2']).toBe(13000);
    });
  });

  describe('no extrapolation beyond last data', () => {
    it('should NOT forward-fill beyond last actual dataPoint', () => {
      const ts1 = new Date('2024-01-01T12:00:00Z');
      const ts2 = new Date('2024-01-02T12:00:00Z');
      const ts3 = new Date('2024-01-03T12:00:00Z');

      // sim-1 has data only at ts1 (ends early)
      // sim-2 has data at all timestamps
      const sim1 = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp: ts1, portfolioValue: 10000 },
        ],
      });
      const sim2 = createMockSimulation({
        id: 'sim-2',
        dataPoints: [
          { timestamp: ts1, portfolioValue: 12000 },
          { timestamp: ts2, portfolioValue: 12500 },
          { timestamp: ts3, portfolioValue: 13000 },
        ],
      });

      const result = transformToChartData([sim1, sim2]);

      expect(result).toHaveLength(3);

      // At ts1: sim-1 has data
      expect(result[0]['sim-1']).toBe(10000);

      // At ts2 and ts3: sim-1 should be null (beyond its last dataPoint)
      expect(result[1]['sim-1']).toBeNull();
      expect(result[2]['sim-1']).toBeNull();

      // sim-2 should have data at all points
      expect(result[0]['sim-2']).toBe(12000);
      expect(result[1]['sim-2']).toBe(12500);
      expect(result[2]['sim-2']).toBe(13000);
    });
  });

  describe('null values before first data', () => {
    it('should return null before first actual dataPoint', () => {
      const ts1 = new Date('2024-01-01T12:00:00Z');
      const ts2 = new Date('2024-01-02T12:00:00Z');
      const ts3 = new Date('2024-01-03T12:00:00Z');

      // sim-1 starts at ts1
      // sim-2 starts at ts2 (later)
      const sim1 = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp: ts1, portfolioValue: 10000 },
          { timestamp: ts2, portfolioValue: 10500 },
          { timestamp: ts3, portfolioValue: 11000 },
        ],
      });
      const sim2 = createMockSimulation({
        id: 'sim-2',
        dataPoints: [
          { timestamp: ts2, portfolioValue: 12000 },
          { timestamp: ts3, portfolioValue: 13000 },
        ],
      });

      const result = transformToChartData([sim1, sim2]);

      expect(result).toHaveLength(3);

      // At ts1: sim-2 should be null (before its first dataPoint)
      expect(result[0]['sim-1']).toBe(10000);
      expect(result[0]['sim-2']).toBeNull();

      // At ts2 and ts3: both have data
      expect(result[1]['sim-1']).toBe(10500);
      expect(result[1]['sim-2']).toBe(12000);
      expect(result[2]['sim-1']).toBe(11000);
      expect(result[2]['sim-2']).toBe(13000);
    });
  });

  describe('edge cases', () => {
    it('should handle simulation with no dataPoints', () => {
      const sim1 = createMockSimulation({
        id: 'sim-1',
        dataPoints: [],
      });
      const sim2 = createMockSimulation({
        id: 'sim-2',
        dataPoints: [
          { timestamp: new Date('2024-01-01T12:00:00Z'), portfolioValue: 10000 },
        ],
      });

      const result = transformToChartData([sim1, sim2]);

      // Only sim2's timestamp should be in the result
      expect(result).toHaveLength(1);
      expect(result[0]['sim-2']).toBe(10000);
      // sim-1 has no data, so it should be null
      expect(result[0]['sim-1']).toBeNull();
    });

    it('should handle all simulations with no dataPoints', () => {
      const simulations = [
        createMockSimulation({ id: 'sim-1', dataPoints: [] }),
        createMockSimulation({ id: 'sim-2', dataPoints: [] }),
      ];

      const result = transformToChartData(simulations);

      expect(result).toEqual([]);
    });

    it('should handle duplicate timestamps within same simulation', () => {
      const timestamp = new Date('2024-01-01T12:00:00Z');

      const simulation = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp, portfolioValue: 10000 },
          { timestamp, portfolioValue: 10500 }, // Duplicate timestamp, different value
        ],
      });

      const result = transformToChartData([simulation]);

      // Should deduplicate - behavior depends on find() returning first match
      expect(result).toHaveLength(1);
      expect(result[0]['sim-1']).toBe(10000); // First value wins
    });

    it('should preserve timestamp precision', () => {
      const preciseTimestamp = new Date('2024-01-01T12:34:56.789Z');

      const simulation = createMockSimulation({
        id: 'sim-1',
        dataPoints: [
          { timestamp: preciseTimestamp, portfolioValue: 10000 },
        ],
      });

      const result = transformToChartData([simulation]);

      expect(result[0].timestamp).toBe(preciseTimestamp.getTime());
    });
  });
});
