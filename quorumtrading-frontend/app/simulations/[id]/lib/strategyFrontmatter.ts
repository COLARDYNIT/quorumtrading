/**
 * Pure helpers for reading strategy frontmatter and framing portfolio headline
 * numbers. Extracted from SimulationDataContext/ChartPanel so the parsing and the
 * return arithmetic can be unit-tested without mounting the simulation page.
 */

/**
 * Read one ticker list out of strategy frontmatter.
 *
 * YAML allows two spellings for a sequence and the backend parser accepts both,
 * so this must too. Handling only the block form silently produced an empty
 * ticker set for every strategy written inline — which is not a cosmetic loss:
 * `configuredTickers` gates the holdings table, the per-ticker chart series and
 * the benchmark-overlay chips, so a real BTC position rendered as
 * "No open positions. Holding 100% cash."
 *
 *   crypto_tickers: [BTC, ETH]     <- flow sequence
 *   crypto_tickers:                <- block sequence
 *     - BTC
 *     - ETH
 */
export function parseTickerList(yamlContent: string, key: string): string[] {
  const clean = (raw: string) => raw.trim().replace(/^["']|["']$/g, '').trim();

  const flow = yamlContent.match(new RegExp(`^[ \\t]*${key}:[ \\t]*\\[([^\\]]*)\\]`, 'm'));
  if (flow) {
    return flow[1].split(',').map(clean).filter(t => t.length > 0);
  }

  // Block items may sit at the key's own indentation or deeper, so allow zero.
  const block = yamlContent.match(
    new RegExp(`^[ \\t]*${key}:[ \\t]*\\n((?:[ \\t]*-[ \\t]*\\S+[ \\t]*(?:\\n|$))*)`, 'm')
  );
  if (block) {
    return block[1]
      .split('\n')
      .map(line => clean(line.trim().replace(/^-[ \t]*/, '')))
      .filter(t => t.length > 0);
  }

  return [];
}

/**
 * Extract the crypto and stock ticker lists from a strategy's YAML frontmatter.
 * Returns empty lists when there is no frontmatter or it cannot be read.
 */
export function parseConfiguredTickers(markdownContent: string): { crypto: string[]; stock: string[] } {
  try {
    const yamlMatch = markdownContent.match(/^---\n([\s\S]*?)\n---/);
    if (!yamlMatch) {
      return { crypto: [], stock: [] };
    }

    const yamlContent = yamlMatch[1];

    return {
      crypto: parseTickerList(yamlContent, 'crypto_tickers'),
      stock: parseTickerList(yamlContent, 'stock_tickers'),
    };
  } catch (error) {
    console.error('Failed to parse configured tickers:', error);
    return { crypto: [], stock: [] };
  }
}

/**
 * Parse a strategy schedule duration into hours.
 *
 * Mirrors the backend's `DurationParser` — `^(\d+)(s|min|m|h|d|w)$`, where `m`
 * and `min` both mean minutes and `w` is seven days. Returns null for anything
 * it does not recognise rather than guessing a default.
 */
export function durationToHours(raw: string | null | undefined): number | null {
  if (!raw) return null;
  const m = raw.trim().toLowerCase().match(/^(\d+)(s|min|m|h|d|w)$/);
  if (!m) return null;
  const value = parseInt(m[1], 10);
  if (!Number.isFinite(value) || value <= 0) return null;
  switch (m[2]) {
    case 's': return value / 3600;
    case 'm':
    case 'min': return value / 60;
    case 'h': return value;
    case 'd': return value * 24;
    case 'w': return value * 168;
    default: return null;
  }
}

/**
 * Evaluation cadence in hours for an interval-scheduled (crypto) strategy.
 *
 * Reads the modern `live_schedule.interval` the backend writes today, falling
 * back to the legacy `evaluation_interval_seconds`. Handling only the legacy
 * key left `evaluationIntervalHours` null for every strategy written in the
 * current format, which in turn left the next-evaluation estimate unavailable.
 */
export function parseIntervalHours(yamlContent: string): number | null {
  const scheduleBlock = yamlContent.match(/^[ \t]*live_schedule:[ \t]*\n((?:[ \t]+\S.*\n?)*)/m);
  if (scheduleBlock) {
    const interval = scheduleBlock[1].match(/^[ \t]*interval:[ \t]*["']?([^"'\s#]+)["']?/m);
    if (interval) {
      // A bare number is seconds, matching the backend's numeric shortcut.
      const bare = interval[1].match(/^\d+$/);
      if (bare) return parseInt(interval[1], 10) / 3600;
      const hours = durationToHours(interval[1]);
      if (hours != null) return hours;
    }
    const seconds = scheduleBlock[1].match(/^[ \t]*interval_seconds:[ \t]*(\d+)/m);
    if (seconds) return parseInt(seconds[1], 10) / 3600;
  }
  const legacy = yamlContent.match(/^[ \t]*evaluation_interval_seconds:[ \t]*(\d+)/m);
  if (legacy) return parseInt(legacy[1], 10) / 3600;
  return null;
}

/**
 * Headline return for the portfolio chart.
 *
 * The basis is TOTAL INVESTED (seed + Σ contributions), not the seed. A DCA top-up
 * is added capital, not profit — measuring against the seed alone reported the
 * injected cash as gains, so a $500 seed topped up to $12,000 and now worth
 * $11,552 read "+2210.45%" directly beside the header KPI's correct "−3.73%".
 */
export function portfolioReturnPct(invested: number, current: number): number {
  return invested > 0 ? ((current - invested) / invested) * 100 : 0;
}
