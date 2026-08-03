/**
 * Invested-capital and deployment maths for the simulation detail page.
 *
 * Kept pure and separate from the components so the arithmetic behind the
 * headline figures can be unit-tested. Every function here derives from data
 * the backend already returns — `initialCash`, the contribution list, and the
 * positions on a portfolio snapshot. Nothing is estimated.
 */

export interface ContributionLike {
  amount: number;
  /** Moment the cash landed. Preferred over `contributionDate`, which is the
   *  funding *day* normalised to 00:00 UTC and used as an idempotency key. */
  createdAt?: string | null;
  contributionDate: string;
}

export interface InvestedStep {
  /** Epoch-ms at which invested capital reached `total`. */
  ms: number;
  /** Cumulative invested capital from this moment on. */
  total: number;
  /** Cash added at this step. Zero for the opening seed. */
  added: number;
}

/**
 * Replay the seed plus each contribution into a stepped invested-capital series.
 *
 * The first step is the seed at the simulation's start; each later step is one
 * contribution at the moment it landed. Steps are sorted and coalesced so two
 * contributions with the same timestamp produce one riser.
 */
export function buildInvestedSteps(
  initialCash: number,
  contributions: ContributionLike[],
  startMs: number
): InvestedStep[] {
  const events = contributions
    .map(c => {
      const preferred = c.createdAt ? new Date(c.createdAt).getTime() : NaN;
      const fallback = new Date(c.contributionDate).getTime();
      const ms = Number.isNaN(preferred) ? fallback : preferred;
      return { ms, amount: c.amount };
    })
    .filter(e => !Number.isNaN(e.ms) && Number.isFinite(e.amount))
    .sort((a, b) => a.ms - b.ms);

  const steps: InvestedStep[] = [{ ms: startMs, total: initialCash, added: 0 }];
  let running = initialCash;
  for (const e of events) {
    running += e.amount;
    const last = steps[steps.length - 1];
    // A contribution at (or before) the seed instant lifts the opening step
    // rather than drawing a zero-width riser.
    if (e.ms <= last.ms) {
      last.total = running;
      last.added += e.amount;
    } else {
      steps.push({ ms: e.ms, total: running, added: e.amount });
    }
  }
  return steps;
}

/** Invested capital in force at `ms` — the step function's value, not interpolated. */
export function investedAt(steps: InvestedStep[], ms: number): number {
  if (steps.length === 0) return 0;
  let value = steps[0].total;
  for (const s of steps) {
    if (ms >= s.ms) value = s.total;
    else break;
  }
  return value;
}

export interface HoldingLike {
  amount: number;
  /** Average entry price — the cost basis per unit. */
  buyPrice: number;
  currentPrice: number;
}

export interface Deployment {
  /** Cost basis of every open position: Σ qty × average entry price. */
  deployedCost: number;
  /** Current market value of every open position. */
  marketValue: number;
  /** Share of invested capital put to work, 0–100. */
  workPct: number;
  /** Share of invested capital sitting in cash, 0–100. */
  idlePct: number;
}

/**
 * Split invested capital into what the strategy actually deployed and what is
 * still idle.
 *
 * Deployment is measured at **cost**, not market value: the question is how much
 * of the money the user put in was committed, and marking it to market would let
 * a price move masquerade as a deployment decision. `idlePct` is the remainder,
 * clamped so an unrealised loss can never report negative idle cash.
 */
export function deploymentSplit(holdings: HoldingLike[], invested: number): Deployment {
  let deployedCost = 0;
  let marketValue = 0;
  for (const h of holdings) {
    if (!Number.isFinite(h.amount) || h.amount <= 0) continue;
    deployedCost += h.amount * (h.buyPrice || 0);
    marketValue += h.amount * (h.currentPrice || 0);
  }
  const workPct = invested > 0 ? Math.min(100, Math.max(0, (deployedCost / invested) * 100)) : 0;
  return { deployedCost, marketValue, workPct, idlePct: 100 - workPct };
}

/**
 * Human cadence for the evaluation schedule, or null when the strategy does not
 * state one. Never guesses: a strategy with no parsable schedule gets no label
 * rather than a plausible-sounding default.
 */
export function cadenceLabel(
  evaluationIntervalHours: number | null | undefined,
  evaluationTimes?: string[] | null
): string | null {
  if (evaluationTimes && evaluationTimes.length > 0) {
    return evaluationTimes.length === 1 ? 'daily' : `${evaluationTimes.length}× daily`;
  }
  if (!evaluationIntervalHours || evaluationIntervalHours <= 0) return null;
  const h = evaluationIntervalHours;
  if (h === 1) return 'hourly';
  if (h === 24) return 'daily';
  if (h === 168) return 'weekly';
  if (h < 24) return `every ${h}h`;
  if (h % 168 === 0) return `every ${h / 168} weeks`;
  if (h % 24 === 0) return `every ${h / 24} days`;
  return `every ${h}h`;
}
