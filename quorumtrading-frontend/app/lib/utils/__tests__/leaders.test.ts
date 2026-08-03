import { calculateLeaders } from '../leaders';
import { BenchmarkSimulation, BenchmarkEntry } from '@/app/lib/types/benchmark';

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

describe('calculateLeaders', () => {
  describe('empty input', () => {
    it('should return empty array for empty simulations', () => {
      const result = calculateLeaders([]);

      expect(result).toEqual([]);
    });
  });

  describe('sorting by portfolio value', () => {
    it('should sort simulations by derived value descending (highest first)', () => {
      const simulations: BenchmarkSimulation[] = [
        createMockSimulation({
          id: 'sim-1',
          name: 'Third Place',
          dataPoints: [
            { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
            { timestamp: new Date('2024-06-01'), portfolioValue: 11000 },
          ],
          startValue: 10000,
        }),
        createMockSimulation({
          id: 'sim-2',
          name: 'First Place',
          dataPoints: [
            { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
            { timestamp: new Date('2024-06-01'), portfolioValue: 15000 },
          ],
          startValue: 10000,
        }),
        createMockSimulation({
          id: 'sim-3',
          name: 'Second Place',
          dataPoints: [
            { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
            { timestamp: new Date('2024-06-01'), portfolioValue: 12000 },
          ],
          startValue: 10000,
        }),
      ];

      const result = calculateLeaders(simulations);

      expect(result).toHaveLength(3);
      expect(result[0].name).toBe('First Place');
      expect(result[0].value).toBe(15000);
      expect(result[1].name).toBe('Second Place');
      expect(result[1].value).toBe(12000);
      expect(result[2].name).toBe('Third Place');
      expect(result[2].value).toBe(11000);
    });
  });

  describe('deriving values from dataPoints', () => {
    it('should use last dataPoint value instead of cached currentValue', () => {
      const simulation = createMockSimulation({
        currentValue: 9000, // Cached value (should be ignored)
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
          { timestamp: new Date('2024-06-01'), portfolioValue: 12000 }, // Latest value
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].value).toBe(12000); // Uses dataPoint, not cached value
    });

    it('should calculate return percent from dataPoints', () => {
      const simulation = createMockSimulation({
        returnPercent: 0, // Cached value (should be ignored)
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
          { timestamp: new Date('2024-06-01'), portfolioValue: 12000 }, // 20% return
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].returnPercent).toBe(20); // (12000 - 10000) / 10000 * 100
    });

    it('should fallback to cached values when dataPoints is empty', () => {
      const simulation = createMockSimulation({
        currentValue: 9500,
        returnPercent: -5,
        dataPoints: [], // Empty
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].value).toBe(9500);
      expect(result[0].returnPercent).toBe(-5);
    });
  });

  describe('return percent calculation', () => {
    it('should calculate positive return correctly', () => {
      const simulation = createMockSimulation({
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
          { timestamp: new Date('2024-06-01'), portfolioValue: 15000 },
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].returnPercent).toBe(50); // 50% gain
    });

    it('should calculate negative return correctly', () => {
      const simulation = createMockSimulation({
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
          { timestamp: new Date('2024-06-01'), portfolioValue: 8000 },
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].returnPercent).toBe(-20); // 20% loss
    });

    it('should calculate zero return correctly', () => {
      const simulation = createMockSimulation({
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
          { timestamp: new Date('2024-06-01'), portfolioValue: 10000 },
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].returnPercent).toBe(0);
    });
  });

  describe('BenchmarkEntry properties', () => {
    it('should include all required properties in returned entries', () => {
      const simulation = createMockSimulation({
        id: 'test-id-123',
        name: 'Test Name',
        strategyName: 'Strategy ABC',
        color: '#ff0000',
        status: 'SIMULATING',
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0]).toEqual({
        id: 'test-id-123',
        name: 'Test Name',
        strategyName: 'Strategy ABC',
        color: '#ff0000',
        value: 10000,
        returnPercent: 0,
        status: 'SIMULATING',
      });
    });
  });

  describe('edge cases', () => {
    it('should handle simulations with single dataPoint', () => {
      const simulation = createMockSimulation({
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].value).toBe(10000);
      expect(result[0].returnPercent).toBe(0);
    });

    it('should handle simulations with many dataPoints', () => {
      const simulation = createMockSimulation({
        dataPoints: [
          { timestamp: new Date('2024-01-01'), portfolioValue: 10000 },
          { timestamp: new Date('2024-02-01'), portfolioValue: 10500 },
          { timestamp: new Date('2024-03-01'), portfolioValue: 11000 },
          { timestamp: new Date('2024-04-01'), portfolioValue: 10800 },
          { timestamp: new Date('2024-05-01'), portfolioValue: 11500 },
          { timestamp: new Date('2024-06-01'), portfolioValue: 12500 }, // Last value
        ],
        startValue: 10000,
      });

      const result = calculateLeaders([simulation]);

      expect(result[0].value).toBe(12500);
      expect(result[0].returnPercent).toBe(25);
    });

    it('should maintain stable sort order for equal values', () => {
      const simulations: BenchmarkSimulation[] = [
        createMockSimulation({
          id: 'sim-a',
          name: 'Simulation A',
          dataPoints: [{ timestamp: new Date('2024-01-01'), portfolioValue: 10000 }],
          startValue: 10000,
        }),
        createMockSimulation({
          id: 'sim-b',
          name: 'Simulation B',
          dataPoints: [{ timestamp: new Date('2024-01-01'), portfolioValue: 10000 }],
          startValue: 10000,
        }),
      ];

      const result = calculateLeaders(simulations);

      // Both have equal values, order should be stable (original order preserved)
      expect(result).toHaveLength(2);
      expect(result[0].value).toBe(10000);
      expect(result[1].value).toBe(10000);
    });
  });
});
