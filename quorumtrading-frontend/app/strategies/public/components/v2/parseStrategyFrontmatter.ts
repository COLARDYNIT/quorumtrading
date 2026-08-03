/**
 * Extract YAML frontmatter from a strategy's markdownContent.
 *
 * Strategies in this codebase follow `qwen-moving-average-crossover-strategy.md`
 * shape — a YAML frontmatter block at the top, then plain markdown. We need a
 * few of those fields (tickers, consensus_method, threshold, evaluation_times)
 * surfaced as card metadata in the public library. They are not separate
 * columns on `StrategyResponse`, so we parse them client-side once per render.
 */
import * as yaml from 'yaml';

export interface StrategyFrontmatter {
  consensusMethod?: string;
  consensusThreshold?: number;
  evaluationTimes?: string[];
  evaluationIntervalSeconds?: number;
  stockTickers: string[];
  cryptoTickers: string[];
  riskProfile?: string;
}

const EMPTY: StrategyFrontmatter = { stockTickers: [], cryptoTickers: [] };

export function parseStrategyFrontmatter(markdownContent: string | undefined | null): StrategyFrontmatter {
  if (!markdownContent) return EMPTY;
  const lines = markdownContent.split('\n');
  if (!lines[0]?.trim().startsWith('---')) return EMPTY;
  let endIdx = -1;
  for (let i = 1; i < lines.length; i++) {
    if (lines[i]?.trim() === '---') { endIdx = i; break; }
  }
  if (endIdx === -1) return EMPTY;
  const body = lines.slice(1, endIdx).join('\n');
  let parsed: Record<string, unknown>;
  try {
    parsed = (yaml.parse(body) as Record<string, unknown>) ?? {};
  } catch {
    return EMPTY;
  }
  const toStringArr = (v: unknown): string[] =>
    Array.isArray(v) ? v.map(x => String(x).trim()).filter(Boolean) : [];

  return {
    consensusMethod: typeof parsed.consensus_method === 'string' ? parsed.consensus_method : undefined,
    consensusThreshold: typeof parsed.consensus_threshold === 'number' ? parsed.consensus_threshold : undefined,
    evaluationTimes: toStringArr((parsed.market_hours as Record<string, unknown> | undefined)?.evaluation_times),
    evaluationIntervalSeconds: typeof parsed.evaluation_interval_seconds === 'number' ? parsed.evaluation_interval_seconds : undefined,
    stockTickers: toStringArr(parsed.stock_tickers),
    cryptoTickers: toStringArr(parsed.crypto_tickers),
    riskProfile: typeof parsed.risk_profile === 'string' ? parsed.risk_profile : undefined,
  };
}

/** Combine stock + crypto tickers for display in a single ticker row. */
export function allTickers(fm: StrategyFrontmatter): string[] {
  return [...fm.stockTickers, ...fm.cryptoTickers];
}

/** Human-readable cadence string from frontmatter, falling back to "—". */
export function formatCadence(fm: StrategyFrontmatter): string {
  if (fm.evaluationTimes && fm.evaluationTimes.length > 0) {
    return fm.evaluationTimes.join(' · ') + ' ET';
  }
  if (fm.evaluationIntervalSeconds) {
    const secs = fm.evaluationIntervalSeconds;
    if (secs % 3600 === 0) return `Every ${secs / 3600}h`;
    if (secs % 60 === 0) return `Every ${secs / 60}m`;
    return `Every ${secs}s`;
  }
  return '—';
}
