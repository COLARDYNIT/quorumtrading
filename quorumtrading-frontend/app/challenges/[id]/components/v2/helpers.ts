import type { ChallengeResponse, ChallengeEntryResponse, ChallengeChartData } from '@/app/lib/types/challenge';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

export function fmtDate(iso: string | null | undefined, withYear = true): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const day = String(d.getUTCDate()).padStart(2, '0');
  const mo = MONTHS[d.getUTCMonth()];
  return withYear ? `${day} ${mo} '${String(d.getUTCFullYear()).slice(2)}` : `${day} ${mo}`;
}

export function fmtRange(ch: ChallengeResponse): string {
  return `${fmtDate(ch.simulationStartDate)} → ${fmtDate(ch.simulationEndDate)}`;
}

export function fmtMoney(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n)) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(n);
}

export function fmtPct(s: string | number | null | undefined): string | null {
  if (s == null) return null;
  const n = typeof s === 'string' ? parseFloat(s) : s;
  if (!Number.isFinite(n)) return null;
  return `${n >= 0 ? '+' : '−'}${Math.abs(n).toFixed(2)}%`;
}

export interface Countdown {
  ms: number;
  label: string;
  urgent: boolean; // < 1h
  soon: boolean;   // < 24h
  expired: boolean;
}

export function countdownTo(iso: string | null | undefined): Countdown {
  if (!iso) return { ms: 0, label: '—', urgent: false, soon: false, expired: true };
  const ms = new Date(iso).getTime() - Date.now();
  if (ms <= 0) return { ms: 0, label: 'closed', urgent: false, soon: false, expired: true };
  const days = Math.floor(ms / 86_400_000);
  const hours = Math.floor((ms % 86_400_000) / 3_600_000);
  const mins = Math.floor((ms % 3_600_000) / 60_000);
  let label: string;
  if (days > 0) label = `${days}d ${hours}h`;
  else if (hours > 0) label = `${hours}h ${mins}m`;
  else label = `${mins}m`;
  return { ms, label, urgent: ms < 3_600_000, soon: ms < 86_400_000, expired: false };
}

/** Days between two ISO dates. Floors. */
export function daysBetween(startIso: string, endIso: string): number {
  const a = new Date(startIso).getTime();
  const b = new Date(endIso).getTime();
  if (!Number.isFinite(a) || !Number.isFinite(b)) return 0;
  return Math.max(0, Math.floor((b - a) / 86_400_000));
}

/** Top-of-leaderboard + the user's own entry (when outside top 5). */
export interface LeaderboardSplit {
  top: ChallengeEntryResponse[];
  you: ChallengeEntryResponse | null;
  /** True when there's a real gap between top.last.rank and you.rank. */
  gap: boolean;
}

export function leaderboardSplit(
  entries: ChallengeEntryResponse[],
  userSimIds: Set<string>,
): LeaderboardSplit {
  const you = entries.find((e) => userSimIds.has(e.simulationId)) ?? null;
  const top = entries.filter((e) => !you || e.simulationId !== you.simulationId);
  const gap = !!you && top.length > 0 && you.rank > top[top.length - 1].rank + 1;
  return { top, you, gap };
}

/** Count of entries beating the buy-and-hold baseline (real data, no fabrication). */
export function entriesBeatingBaseline(
  entries: ChallengeEntryResponse[],
  baseline: ChallengeChartData['buyAndHold'],
): number | null {
  if (!baseline) return null;
  return entries.filter((e) => {
    if (e.returnPercent == null) return false;
    const n = parseFloat(e.returnPercent);
    return Number.isFinite(n) && n > baseline.returnPercent;
  }).length;
}

/** Format executionTimeSeconds as Nm Ns / Nh Nm. */
export function fmtExecutionTime(s: number | null | undefined): string | null {
  if (s == null) return null;
  if (s < 60) return `${s}s`;
  if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`;
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return `${h}h ${m}m`;
}
