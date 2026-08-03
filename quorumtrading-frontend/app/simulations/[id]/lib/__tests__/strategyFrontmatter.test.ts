import {
  parseConfiguredTickers,
  parseTickerList,
  portfolioReturnPct,
  durationToHours,
  parseIntervalHours,
} from '../strategyFrontmatter';

const wrap = (yaml: string) => `---\n${yaml}\n---\n\n# Strategy\n\nProse.\n`;

describe('parseConfiguredTickers', () => {
  it('reads a flow sequence written inline', () => {
    // The regression: every strategy the backend writes uses this spelling, and
    // the old block-only regex returned [] for all of them.
    const md = wrap('crypto_tickers: [BTC]\nconsensus_method: MAJORITY');
    expect(parseConfiguredTickers(md)).toEqual({ crypto: ['BTC'], stock: [] });
  });

  it('reads a multi-entry flow sequence', () => {
    const md = wrap('crypto_tickers: [BTC, ETH, SOL]');
    expect(parseConfiguredTickers(md).crypto).toEqual(['BTC', 'ETH', 'SOL']);
  });

  it('reads a block sequence', () => {
    const md = wrap('crypto_tickers:\n  - BTC\n  - ETH');
    expect(parseConfiguredTickers(md).crypto).toEqual(['BTC', 'ETH']);
  });

  it('reads a block sequence at the key indentation', () => {
    const md = wrap('stock_tickers:\n- AAPL\n- MSFT');
    expect(parseConfiguredTickers(md).stock).toEqual(['AAPL', 'MSFT']);
  });

  it('reads both asset classes from one document', () => {
    const md = wrap('stock_tickers: [AAPL]\ncrypto_tickers: [BTC]');
    expect(parseConfiguredTickers(md)).toEqual({ crypto: ['BTC'], stock: ['AAPL'] });
  });

  it('strips quotes around tickers', () => {
    const md = wrap(`crypto_tickers: ["BTC", 'ETH']`);
    expect(parseConfiguredTickers(md).crypto).toEqual(['BTC', 'ETH']);
  });

  it('treats an empty flow sequence as no tickers', () => {
    const md = wrap('crypto_tickers: []\nstock_tickers: [QQQ]');
    expect(parseConfiguredTickers(md)).toEqual({ crypto: [], stock: ['QQQ'] });
  });

  it('does not read the real strategy list as empty', () => {
    // Verbatim frontmatter from the AUDIT-D BTC accumulation strategy.
    const md = [
      '---',
      'crypto_tickers: [BTC]',
      'live_schedule:',
      '  interval: 1w',
      'consensus_method: MAJORITY',
      'position_limits:',
      '  BTC: 0.9',
      'agents:',
      '  - TECHNICAL',
      '---',
      '',
      '# Long-Horizon BTC Accumulation',
    ].join('\n');
    expect(parseConfiguredTickers(md).crypto).toEqual(['BTC']);
  });

  it('does not mistake a nested mapping for a ticker list', () => {
    const md = wrap('position_limits:\n  BTC: 0.9\ncrypto_tickers: [ETH]');
    expect(parseConfiguredTickers(md).crypto).toEqual(['ETH']);
  });

  it('returns empty lists when there is no frontmatter', () => {
    expect(parseConfiguredTickers('# Just prose')).toEqual({ crypto: [], stock: [] });
  });

  it('returns an empty list for an absent key', () => {
    expect(parseTickerList('consensus_method: MAJORITY', 'crypto_tickers')).toEqual([]);
  });
});

describe('durationToHours', () => {
  it.each([
    ['1h', 1],
    ['4h', 4],
    ['1d', 24],
    ['1w', 168],
    ['2w', 336],
    ['30m', 0.5],
    ['30min', 0.5],
    ['1800s', 0.5],
  ])('parses %s', (input, expected) => {
    expect(durationToHours(input)).toBeCloseTo(expected, 6);
  });

  it('rejects malformed or non-positive durations', () => {
    for (const bad of ['', 'weekly', '1y', '0h', '-1h', 'h1', null, undefined]) {
      expect(durationToHours(bad)).toBeNull();
    }
  });
});

describe('parseIntervalHours', () => {
  it('reads the modern live_schedule.interval', () => {
    // The format the backend writes today — previously unreadable, which left
    // the evaluation cadence and next-run estimate permanently unavailable.
    const yaml = 'crypto_tickers: [BTC]\nlive_schedule:\n  interval: 1w\nbacktest_schedule:\n  interval: 1w';
    expect(parseIntervalHours(yaml)).toBe(168);
  });

  it('reads a quoted interval', () => {
    expect(parseIntervalHours('live_schedule:\n  interval: "4h"')).toBe(4);
  });

  it('treats a bare number as seconds, matching the backend', () => {
    expect(parseIntervalHours('live_schedule:\n  interval: 3600')).toBe(1);
  });

  it('reads interval_seconds nested under live_schedule', () => {
    expect(parseIntervalHours('live_schedule:\n  interval_seconds: 7200')).toBe(2);
  });

  it('falls back to the legacy top-level key', () => {
    expect(parseIntervalHours('evaluation_interval_seconds: 900')).toBe(0.25);
  });

  it('prefers live_schedule over the legacy key when both are present', () => {
    const yaml = 'evaluation_interval_seconds: 900\nlive_schedule:\n  interval: 1d';
    expect(parseIntervalHours(yaml)).toBe(24);
  });

  it('returns null when no schedule is stated', () => {
    expect(parseIntervalHours('crypto_tickers: [BTC]\nconsensus_method: MAJORITY')).toBeNull();
  });

  it('does not read a stock schedule block as an interval', () => {
    const yaml = 'live_schedule:\n  evaluation_times: ["10:00"]\n  timezone: America/New_York';
    expect(parseIntervalHours(yaml)).toBeNull();
  });
});

describe('portfolioReturnPct', () => {
  it('measures against total invested, not the seed', () => {
    // $500 seed + $11,500 of DCA contributions, now worth $11,552.23.
    // Against the seed this read +2210.45%; the true result is a small loss.
    expect(portfolioReturnPct(12000, 11552.23)).toBeCloseTo(-3.73, 2);
  });

  it('reports a gain above the invested basis', () => {
    expect(portfolioReturnPct(1000, 1250)).toBeCloseTo(25, 6);
  });

  it('is zero at break-even', () => {
    expect(portfolioReturnPct(1000, 1000)).toBe(0);
  });

  it('avoids dividing by a zero basis', () => {
    expect(portfolioReturnPct(0, 500)).toBe(0);
  });
});
