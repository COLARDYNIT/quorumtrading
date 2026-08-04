/**
 * Tests for the keyless-install derivation: a symbol whose asset class has no
 * usable (enabled + credentialed) provider must surface as `nokey` — "needs a
 * market-data API key" — and never as a backfill failure or, worst, as
 * "before provider coverage — permanently unavailable" (issue #7).
 */
import {
  buildDays,
  buildVerdict,
  cellWord,
  isCrypto,
  keylessAssets,
  priceCellState,
  symbolNeedsKey,
  type AssetClassUsability,
  type DayCell,
} from '../coverageDerive';
import type { DailyCoverage, DataGap, GapProgressDTO } from '@/app/lib/types/dataAvailability';
import { GapStatus } from '@/app/lib/types/dataAvailability';

const KEYLESS_INSTALL: AssetClassUsability = { stock: false, crypto: true };
const FULLY_KEYED: AssetClassUsability = { stock: true, crypto: true };

function pastDay(iso: string): DayCell {
  const day = Number(iso.slice(8, 10));
  const date = new Date(iso + 'T00:00:00');
  const dow = date.getDay();
  return { day, iso, weekend: dow === 0 || dow === 6, future: false };
}

const NO_DATA = {
  daily: new Map<string, DailyCoverage>(),
  gaps: [] as DataGap[],
  failedGaps: [] as GapProgressDTO[],
};

describe('symbolNeedsKey', () => {
  it('is false when status has not loaded — nothing is claimed without the real signal', () => {
    expect(symbolNeedsKey('GME', undefined)).toBe(false);
  });

  it('flags equities on a keyless install but not crypto', () => {
    expect(symbolNeedsKey('GME', KEYLESS_INSTALL)).toBe(true);
    expect(symbolNeedsKey('BTC', KEYLESS_INSTALL)).toBe(false);
  });

  it('flags nothing when both classes are usable', () => {
    expect(symbolNeedsKey('GME', FULLY_KEYED)).toBe(false);
    expect(symbolNeedsKey('BTC', FULLY_KEYED)).toBe(false);
  });

  it('follows the crypto classifier for pair-suffixed symbols', () => {
    expect(isCrypto('SOL-USD')).toBe(true);
    expect(symbolNeedsKey('SOL-USD', { stock: true, crypto: false })).toBe(true);
  });
});

describe('priceCellState nokey override (issue #7 finding 4)', () => {
  // GameStop squeeze week, Tuesday (market open, no data present).
  const d = pastDay('2021-01-26');

  it('turns a poisoned pre-horizon cell into nokey when the class has no usable provider', () => {
    // A failed keyless backfill raised the cached horizon to after the range —
    // the exact bug: "GME · 2021-01-XX · before provider coverage — permanently
    // unavailable" on an install where a free key would fetch it.
    const horizon = { GME: '2021-02-05' };
    expect(
      priceCellState('GME', d, { ...NO_DATA, horizon, usable: KEYLESS_INSTALL })
    ).toBe('nokey');
    // With a usable stock provider the same horizon is a real measurement.
    expect(
      priceCellState('GME', d, { ...NO_DATA, horizon, usable: FULLY_KEYED })
    ).toBe('horizon');
  });

  it('turns failed cells into nokey — the failure is a config problem, not the range', () => {
    const failedGaps: GapProgressDTO[] = [
      {
        symbol: 'GME',
        dataType: 'PRICE',
        from: '2021-01-25',
        to: '2021-01-29',
        status: GapStatus.FAILED,
        provider: null,
        error: 'no credentials',
        eventsFetched: null,
      },
    ];
    expect(
      priceCellState('GME', d, { ...NO_DATA, failedGaps, usable: KEYLESS_INSTALL })
    ).toBe('nokey');
  });

  it('turns actionable gap cells into nokey — a backfill click cannot fix a missing key', () => {
    const gaps: DataGap[] = [
      { symbol: 'GME', dataType: 'PRICE', from: '2021-01-25', to: '2021-01-29' },
    ];
    const daily = new Map<string, DailyCoverage>([
      ['2021-01-26', { date: '2021-01-26', percentage: 0, perType: {}, perSymbol: { GME: 0 } }],
    ]);
    expect(
      priceCellState('GME', d, { daily, gaps, failedGaps: [], usable: KEYLESS_INSTALL })
    ).toBe('nokey');
  });

  it('keeps honest states honest: present, closed and future are never overridden', () => {
    const daily = new Map<string, DailyCoverage>([
      ['2021-01-26', { date: '2021-01-26', percentage: 100, perType: {}, perSymbol: { GME: 100 } }],
    ]);
    expect(
      priceCellState('GME', d, { daily, gaps: [], failedGaps: [], usable: KEYLESS_INSTALL })
    ).toBe('present');

    const saturday = pastDay('2021-01-30');
    expect(
      priceCellState('GME', saturday, { ...NO_DATA, usable: KEYLESS_INSTALL })
    ).toBe('closed');

    const future: DayCell = { day: 1, iso: '2099-01-01', weekend: false, future: true };
    expect(
      priceCellState('GME', future, { ...NO_DATA, usable: KEYLESS_INSTALL })
    ).toBe('future');
  });

  it('never fires without the status signal (undefined usable)', () => {
    const horizon = { GME: '2021-02-05' };
    expect(priceCellState('GME', d, { ...NO_DATA, horizon })).toBe('horizon');
  });

  it('leaves keyless-served crypto untouched', () => {
    const gaps: DataGap[] = [
      { symbol: 'BTC', dataType: 'PRICE', from: '2021-01-25', to: '2021-01-29' },
    ];
    expect(
      priceCellState('BTC', d, { daily: new Map(), gaps, failedGaps: [], usable: KEYLESS_INSTALL })
    ).toBe('action');
  });
});

describe('cellWord for nokey', () => {
  it('says the fix, not a data verdict', () => {
    expect(cellWord('nokey')).toContain('needs a market-data API key');
    expect(cellWord('nokey')).not.toContain('permanently unavailable');
  });
});

describe('buildVerdict with a keyless class', () => {
  it('sets nokey slots aside instead of counting them as backfillable-missing', () => {
    const days = buildDays(2021, 0); // Jan 2021 — entirely in the past
    // The backend emits a daily row (with 0% for GME) on market-open days.
    const daily = new Map<string, DailyCoverage>(
      days
        .filter(d => !d.weekend)
        .map(d => [d.iso, { date: d.iso, percentage: 0, perType: {}, perSymbol: { GME: 0 } }])
    );
    const gaps: DataGap[] = [
      { symbol: 'GME', dataType: 'PRICE', from: '2021-01-01', to: '2021-01-31' },
    ];
    const withKey = buildVerdict({
      symbols: ['GME'],
      dataTypes: ['PRICE'],
      days,
      daily,
      gaps,
      failedGaps: [],
      usable: FULLY_KEYED,
    });
    const keyless = buildVerdict({
      symbols: ['GME'],
      dataTypes: ['PRICE'],
      days,
      daily,
      gaps,
      failedGaps: [],
      usable: KEYLESS_INSTALL,
    });
    // With a key the weekdays are expected-and-missing; keyless they are set aside.
    expect(withKey.expected).toBeGreaterThan(0);
    expect(keyless.expected).toBe(0);
    expect(keyless.setAsideCount).toBeGreaterThanOrEqual(withKey.expected);
  });
});

describe('keylessAssets (historic-event consequence)', () => {
  it('lists exactly the symbols a keyless backfill would skip', () => {
    expect(keylessAssets(['GME', 'AMC', 'BTC'], KEYLESS_INSTALL)).toEqual(['GME', 'AMC']);
    expect(keylessAssets(['GME', 'AMC', 'BTC'], FULLY_KEYED)).toEqual([]);
    expect(keylessAssets(['GME'], undefined)).toEqual([]);
  });
});
