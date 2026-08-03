/**
 * coverageDerive.ts — client-side derivation for the Market Data Availability
 * "Operator dashboard" (Alternative A) redesign.
 *
 * This mirrors the SAMPLE derivation logic in the design bundle (da-data.jsx:
 * priceCellState / presenceState / buildWorklist / buildCoverage) but is fed
 * EXCLUSIVELY from the real API response (DataAvailabilityResponse) — no
 * fabricated numbers, dates, counts, or coverage values.
 *
 * Hard rule from the redesign brief: every value here comes from a real API
 * field. Anything the backend cannot supply today (per-gap provider/est records,
 * data horizons, per-symbol trading calendar / month gap-dots) is NOT computed
 * here — it is hedged at the render site.
 */

import { isWeekend, parseISO, getDaysInMonth, isAfter, startOfDay } from 'date-fns';
import type {
  DataAvailabilityResponse,
  DailyCoverage,
  DataGap,
  GapProgressDTO,
} from '@/app/lib/types/dataAvailability';
import { GapStatus } from '@/app/lib/types/dataAvailability';

// ── cell + presence states (authoritative six, per da-app CellStateRef) ──
export type PriceCellState =
  | 'present'
  | 'partial'
  | 'action'
  | 'closed'
  | 'future'
  | 'failed'
  | 'horizon';

export type PresenceCellState = 'present' | 'action' | 'nosrc' | 'future';

// human-readable labels (ported verbatim from da-atoms cellWord / presWord)
export function cellWord(st: PriceCellState): string {
  return (
    {
      present: 'present',
      partial: 'partial',
      action: 'missing — fixable',
      closed: 'market closed — nothing expected',
      future: 'not yet occurred',
      failed: 'backfill failed',
      horizon: 'before provider coverage — permanently unavailable',
    } as Record<PriceCellState, string>
  )[st] ?? st;
}

export function presWord(st: PresenceCellState): string {
  return (
    {
      present: 'present',
      action: 'absent — fixable',
      nosrc: 'absent — no provider',
      future: 'not yet occurred',
    } as Record<PresenceCellState, string>
  )[st] ?? st;
}

// ── crypto classification ────────────────────────────────────────────────
// We cannot reliably classify every ticker via the API today. We infer crypto
// from a known set + a `-USD`/`-USDT` pair suffix. Anything not matched is
// treated as an equity (weekends → market closed). This only affects the
// closed-vs-missing rendering; it never fabricates coverage numbers.
const KNOWN_CRYPTO = new Set([
  'BTC',
  'ETH',
  'SOL',
  'XRP',
  'ADA',
  'DOGE',
  'AVAX',
  'DOT',
  'MATIC',
  'LINK',
  'LTC',
  'BCH',
  'UNI',
  'ATOM',
  'XLM',
]);

export function isCrypto(symbol: string): boolean {
  const base = symbol.replace(/[-/](USD|USDT|USDC)$/i, '').toUpperCase();
  return KNOWN_CRYPTO.has(base) || /[-/](USD|USDT|USDC)$/i.test(symbol);
}

// ── day model for the selected month ─────────────────────────────────────
export interface DayCell {
  day: number; // 1-based day of month
  iso: string; // yyyy-MM-dd
  weekend: boolean;
  future: boolean;
  /** The matching DailyCoverage row, if the backend emitted one for this day. */
  daily?: DailyCoverage;
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/**
 * Build the per-day model for a month. Absence of a DailyCoverage entry is the
 * load-bearing signal: the backend only emits market-open days, so a calendar
 * day with NO daily entry === market was closed (for equities) / a real gap
 * (for crypto, which expects every day).
 */
export function buildDays(year: number, monthIndex0: number): DayCell[] {
  const reference = new Date(year, monthIndex0, 1);
  const total = getDaysInMonth(reference);
  const today = startOfDay(new Date());
  return Array.from({ length: total }, (_, i) => {
    const day = i + 1;
    const date = new Date(year, monthIndex0, day);
    const iso = `${year}-${pad(monthIndex0 + 1)}-${pad(day)}`;
    return {
      day,
      iso,
      weekend: isWeekend(date),
      future: isAfter(startOfDay(date), today),
    };
  });
}

/** Index the daily coverage rows by their ISO date for O(1) lookup. */
export function indexDaily(
  dailyCoverage: DailyCoverage[] | undefined
): Map<string, DailyCoverage> {
  const map = new Map<string, DailyCoverage>();
  (dailyCoverage ?? []).forEach((d) => {
    // backend date may carry a time component — normalise to the date prefix
    const key = d.date.slice(0, 10);
    map.set(key, d);
  });
  return map;
}

// ── gap lookups (real DataGap[]) ─────────────────────────────────────────
function gapCoversDay(gap: DataGap, iso: string): boolean {
  const from = gap.from.slice(0, 10);
  const to = gap.to.slice(0, 10);
  return iso >= from && iso <= to;
}

/** Live failed cells come from the active/most-recent job's gapDetails. */
function failedCoversDay(
  failedGaps: GapProgressDTO[],
  symbol: string,
  dataType: string,
  iso: string
): boolean {
  return failedGaps.some(
    (g) =>
      g.status === GapStatus.FAILED &&
      g.symbol.toUpperCase() === symbol.toUpperCase() &&
      g.dataType === dataType &&
      iso >= g.from.slice(0, 10) &&
      iso <= g.to.slice(0, 10)
  );
}

export interface PriceCellInput {
  daily: Map<string, DailyCoverage>;
  gaps: DataGap[];
  failedGaps: GapProgressDTO[];
  /** `/horizon` signal (symbol → earliest ISO date). Drives pre-coverage cells. */
  horizon?: Record<string, string>;
}

/**
 * Classify one (symbol, day) price cell from real data.
 *
 *  - future  → day is after today.
 *  - horizon → only if a real horizon date exists for the symbol (gated; today
 *              `horizon` is always undefined so this never fires — no fabricated
 *              crypto pre-2024 floor).
 *  - failed  → a live job FAILED on this symbol/day.
 *  - closed  → equity with NO daily entry for the day (backend omits closed days).
 *  - action  → a DataGap for symbol+PRICE covers this day, OR a daily entry
 *              exists with perSymbol < 100.
 *  - present → perSymbol >= 100 (or a daily entry with no gap & no deficit).
 */
export function priceCellState(
  symbol: string,
  d: DayCell,
  input: PriceCellInput
): PriceCellState {
  const crypto = isCrypto(symbol);

  // (forthcoming) before-provider-coverage horizon — gated behind real data
  if (input.horizon) {
    const start = input.horizon[symbol];
    if (start && d.iso < start.slice(0, 10)) return 'horizon';
  }

  if (d.future) return 'future';

  if (failedCoversDay(input.failedGaps, symbol, 'PRICE', d.iso)) return 'failed';

  const daily = input.daily.get(d.iso);

  if (!daily) {
    // No market-open day emitted. Equity → market closed (nothing expected).
    // Crypto expects every day → treat the absence as a missing/actionable cell
    // only if a real gap covers it; otherwise we cannot assert presence, so we
    // render it as missing (action) since crypto expects data every day.
    if (!crypto) return 'closed';
    const cryptoGap = input.gaps.some(
      (g) =>
        g.symbol.toUpperCase() === symbol.toUpperCase() &&
        g.dataType === 'PRICE' &&
        gapCoversDay(g, d.iso)
    );
    return cryptoGap ? 'action' : 'action';
  }

  const value = daily.perSymbol?.[symbol];
  const gapMissing = input.gaps.some(
    (g) =>
      g.symbol.toUpperCase() === symbol.toUpperCase() &&
      g.dataType === 'PRICE' &&
      gapCoversDay(g, d.iso)
  );

  if (gapMissing) return 'action';
  if (value === undefined) {
    // daily entry exists (market open) but no per-symbol figure → present unless
    // a gap says otherwise (handled above).
    return 'present';
  }
  if (value >= 100) return 'present';
  if (value > 0) return 'partial';
  return 'action';
}

export interface PresenceInput {
  daily: Map<string, DailyCoverage>;
  gaps: DataGap[];
}

/**
 * Market-wide NEWS/SOCIAL presence for a day.
 *
 *  - future  → day after today.
 *  - present → perType[kind] >= 100.
 *  - action  → a market-wide DataGap of that type covers a PAST day, or
 *              perType[kind] < 100 on an emitted day.
 *
 * NOTE on `nosrc`: the design renders SOCIAL with no provider as `nosrc`. We
 * CANNOT detect "no provider configured" from the API today, so SOCIAL gaps are
 * rendered as `action` (see brief). No `nosrc` is fabricated.
 */
export function presenceState(
  kind: 'NEWS' | 'SOCIAL',
  d: DayCell,
  input: PresenceInput
): PresenceCellState {
  if (d.future) return 'future';

  const marketGap = input.gaps.some(
    (g) =>
      (g.symbol === 'MARKET' || !g.symbol) &&
      g.dataType === kind &&
      gapCoversDay(g, d.iso)
  );
  if (marketGap) return 'action';

  const daily = input.daily.get(d.iso);
  if (!daily) {
    // No emitted day. For market-wide news/social, absence isn't a closed-market
    // signal (news flows 7 days a week), but we have no positive presence proof.
    // Without a gap we cannot claim missing either — render present (the daily
    // roll-up would have flagged a gap if one existed).
    return 'present';
  }
  const value = daily.perType?.[kind];
  if (value === undefined) return 'present';
  return value >= 100 ? 'present' : 'action';
}

// ── verdict roll-up ("completeness of the possible") ─────────────────────
export interface CoverageVerdict {
  /** Backfillable-present %, excluding closed/future/horizon slots. */
  pct: number;
  present: number;
  expected: number;
  /** Count of gaps[] in the past (actionable). */
  actionableCount: number;
  /** Future/closed set-aside count derivable from the day model. */
  setAsideCount: number;
}

export interface VerdictInput {
  symbols: string[];
  dataTypes: string[];
  days: DayCell[];
  daily: Map<string, DailyCoverage>;
  gaps: DataGap[];
  failedGaps: GapProgressDTO[];
  horizon?: Record<string, string>;
}

export function buildVerdict(input: VerdictInput): CoverageVerdict {
  const { symbols, dataTypes, days } = input;
  let expected = 0;
  let present = 0;
  let setAside = 0;

  const hasPrice = dataTypes.includes('PRICE');
  const hasNews = dataTypes.includes('NEWS');

  if (hasPrice) {
    symbols.forEach((sym) => {
      days.forEach((d) => {
        const st = priceCellState(sym, d, {
          daily: input.daily,
          gaps: input.gaps,
          failedGaps: input.failedGaps,
          horizon: input.horizon,
        });
        if (st === 'closed' || st === 'future' || st === 'horizon') {
          setAside++;
          return; // not expected / not backfillable
        }
        expected++;
        if (st === 'present' || st === 'partial') present++;
      });
    });
  }

  if (hasNews) {
    days.forEach((d) => {
      const st = presenceState('NEWS', d, { daily: input.daily, gaps: input.gaps });
      if (st === 'future') {
        setAside++;
        return;
      }
      expected++;
      if (st === 'present') present++;
    });
  }

  const pct = expected ? Math.round((present / expected) * 100) : 100;

  // actionable = gaps the backend classified ACTIONABLE; fall back to the
  // local "range starts in the past" heuristic when classification is absent.
  const today = startOfDay(new Date());
  const actionableCount = input.gaps.filter((g) => {
    if (g.classification) return g.classification === 'ACTIONABLE';
    const from = parseISO(g.from.slice(0, 10));
    return !isAfter(startOfDay(from), today);
  }).length;

  return { pct, present, expected, actionableCount, setAsideCount: setAside };
}

// ── live job derivation (real GapProgressDTO[]) ──────────────────────────
export interface JobSummary {
  jobId: string;
  status: string;
  message: string;
  updatedAt: string;
  progressPercentage: number | null;
  rows: GapProgressDTO[];
  processed: number;
  success: number;
  skipped: number;
  failed: number;
  noData: number;
  events: number;
}

export function summariseJob(job: {
  jobId: string;
  status: string;
  message: string;
  updatedAt: string;
  progressPercentage: number | null;
  gapDetails: GapProgressDTO[];
}): JobSummary {
  const rows = job.gapDetails;
  const success = rows.filter((r) => r.status === GapStatus.SUCCESS).length;
  const skipped = rows.filter((r) => r.status === GapStatus.SKIPPED).length;
  const failed = rows.filter((r) => r.status === GapStatus.FAILED).length;
  const noData = rows.filter((r) => r.status === GapStatus.NO_DATA).length;
  const events = rows.reduce((n, r) => n + (r.eventsFetched ?? 0), 0);
  return {
    jobId: job.jobId,
    status: job.status,
    message: job.message,
    updatedAt: job.updatedAt,
    progressPercentage: job.progressPercentage,
    rows,
    processed: rows.length,
    success,
    skipped,
    failed,
    noData,
    events,
  };
}

// ── map a backend historic-event category to a design cat class ──────────
export function eventCatClass(category: string): string {
  switch ((category || '').toUpperCase()) {
    case 'CRISIS':
      return 'crisis';
    case 'BULL_RUN':
      return 'bull';
    case 'VOLATILITY':
      return 'vol';
    case 'CATALYST':
      return 'cat';
    case 'BEAR_MARKET':
      return 'bear';
    default:
      return 'bear';
  }
}

export function eventCatLabel(category: string): string {
  switch ((category || '').toUpperCase()) {
    case 'CRISIS':
      return 'Crisis';
    case 'BULL_RUN':
      return 'Bull Run';
    case 'VOLATILITY':
      return 'Volatility';
    case 'CATALYST':
      return 'Catalyst';
    case 'BEAR_MARKET':
      return 'Bear Market';
    default:
      return category || '—';
  }
}
