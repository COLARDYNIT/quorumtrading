import {
  buildInvestedSteps,
  investedAt,
  deploymentSplit,
  cadenceLabel,
} from '../investedCapital';

const START = Date.UTC(2026, 6, 29, 12, 20, 35);
const c = (amount: number, createdAt: string, contributionDate = createdAt) => ({
  amount, createdAt, contributionDate,
});

describe('buildInvestedSteps', () => {
  it('starts at the seed with no contributions', () => {
    expect(buildInvestedSteps(500, [], START)).toEqual([
      { ms: START, total: 500, added: 0 },
    ]);
  });

  it('accumulates contributions in chronological order', () => {
    const steps = buildInvestedSteps(500, [
      c(11000, '2026-07-29T12:22:47Z'),
      c(500, '2026-07-29T12:21:03Z'),
    ], START);

    expect(steps.map(s => s.total)).toEqual([500, 1000, 12000]);
    expect(steps.map(s => s.added)).toEqual([0, 500, 11000]);
  });

  it('folds a contribution at or before the seed instant into the opening step', () => {
    // contributionDate is midnight UTC — before the simulation's own start.
    const steps = buildInvestedSteps(500, [
      { amount: 500, createdAt: null, contributionDate: '2026-07-29T00:00:00Z' },
    ], START);

    expect(steps).toHaveLength(1);
    expect(steps[0]).toEqual({ ms: START, total: 1000, added: 500 });
  });

  it('prefers createdAt over the date-only contributionDate', () => {
    const steps = buildInvestedSteps(500, [
      { amount: 500, createdAt: '2026-07-29T18:00:00Z', contributionDate: '2026-07-29T00:00:00Z' },
    ], START);

    expect(steps).toHaveLength(2);
    expect(steps[1].ms).toBe(Date.UTC(2026, 6, 29, 18, 0, 0));
  });

  it('ignores unparseable timestamps rather than producing NaN steps', () => {
    const steps = buildInvestedSteps(500, [
      { amount: 100, createdAt: 'not-a-date', contributionDate: 'not-a-date either' },
    ], START);

    expect(steps).toEqual([{ ms: START, total: 500, added: 0 }]);
  });
});

describe('investedAt', () => {
  const steps = buildInvestedSteps(500, [
    c(500, '2026-07-29T12:21:03Z'),
    c(11000, '2026-07-29T12:22:47Z'),
  ], START);

  it('holds the level between steps rather than interpolating', () => {
    const between = Date.UTC(2026, 6, 29, 12, 22, 0);
    expect(investedAt(steps, between)).toBe(1000);
  });

  it('returns the seed before the first contribution', () => {
    expect(investedAt(steps, START)).toBe(500);
  });

  it('returns the full total after the last contribution', () => {
    expect(investedAt(steps, Date.UTC(2026, 6, 30, 9, 0, 0))).toBe(12000);
  });

  it('clamps to the opening level before the series starts', () => {
    expect(investedAt(steps, START - 60_000)).toBe(500);
  });

  it('is zero for an empty series', () => {
    expect(investedAt([], START)).toBe(0);
  });
});

describe('deploymentSplit', () => {
  it('measures deployment at cost, not market value', () => {
    // The real case: one BTC buy of ~$448 against $12,000 invested.
    const d = deploymentSplit(
      [{ amount: 0.00696985, buyPrice: 64272.92083124, currentPrice: 63700 }],
      12000
    );

    expect(d.deployedCost).toBeCloseTo(447.9726, 4);
    expect(d.workPct).toBeCloseTo(3.7331, 4);
    expect(d.idlePct).toBeCloseTo(96.2669, 4);
  });

  it('does not let an unrealised loss report negative idle capital', () => {
    const d = deploymentSplit([{ amount: 1, buyPrice: 100, currentPrice: 1 }], 50);

    expect(d.workPct).toBe(100);
    expect(d.idlePct).toBe(0);
  });

  it('skips zero-quantity holdings', () => {
    const d = deploymentSplit(
      [{ amount: 0, buyPrice: 100, currentPrice: 100 }, { amount: 2, buyPrice: 50, currentPrice: 60 }],
      1000
    );

    expect(d.deployedCost).toBe(100);
    expect(d.marketValue).toBe(120);
  });

  it('reports nothing deployed when there is no invested capital', () => {
    expect(deploymentSplit([], 0)).toEqual({
      deployedCost: 0, marketValue: 0, workPct: 0, idlePct: 100,
    });
  });
});

describe('cadenceLabel', () => {
  it.each([
    [1, 'hourly'],
    [4, 'every 4h'],
    [24, 'daily'],
    [168, 'weekly'],
    [336, 'every 2 weeks'],
    [72, 'every 3 days'],
  ])('describes a %ih interval as "%s"', (hours, expected) => {
    expect(cadenceLabel(hours)).toBe(expected);
  });

  it('prefers scheduled evaluation times when present', () => {
    expect(cadenceLabel(null, ['10:00'])).toBe('daily');
    expect(cadenceLabel(null, ['10:00', '14:00'])).toBe('2× daily');
  });

  it('returns null rather than guessing when the strategy states no schedule', () => {
    expect(cadenceLabel(null)).toBeNull();
    expect(cadenceLabel(undefined)).toBeNull();
    expect(cadenceLabel(0)).toBeNull();
  });
});
