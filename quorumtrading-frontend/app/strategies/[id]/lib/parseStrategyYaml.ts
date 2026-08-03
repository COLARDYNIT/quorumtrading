/**
 * Lightweight regex-based extractor for the YAML frontmatter / body of a
 * strategy markdown document. We don't pull in a YAML parser — the surface we
 * care about (rules panel + chips) is small and stable.
 */
export type AgentType = 'technical' | 'fundamental' | 'sentiment';

export interface AgentConfig {
  weight: number | null;
  sources: string[];
}

export interface ParsedStrategyConfig {
  tickers: string[];
  consensusMethod: string | null;
  consensusQuorum: string | null;
  evaluationTimes: string[];
  marketHours: { open: string; close: string } | null;
  marketOnly: boolean | null;
  timezone: string | null;
  maxPositionPct: number | null;
  minCashFloorPct: number | null;
  maxDrawdownPct: number | null;
  orchestratorOverride: boolean | null;
  vetoWindow: string[];
  agents: Record<AgentType, AgentConfig>;
}

const EMPTY_AGENT: AgentConfig = { weight: null, sources: [] };

const EMPTY: ParsedStrategyConfig = {
  tickers: [],
  consensusMethod: null,
  consensusQuorum: null,
  evaluationTimes: [],
  marketHours: null,
  marketOnly: null,
  timezone: null,
  maxPositionPct: null,
  minCashFloorPct: null,
  maxDrawdownPct: null,
  orchestratorOverride: null,
  vetoWindow: [],
  agents: {
    technical: { ...EMPTY_AGENT },
    fundamental: { ...EMPTY_AGENT },
    sentiment: { ...EMPTY_AGENT },
  },
};

/**
 * Extract one named agent block from inside the `agents:` section. Handles
 * both inline (`sources: [a, b]`) and block (`sources:\n    - a\n    - b`)
 * source lists. Returns nulls when the block is missing.
 */
function parseAgent(md: string, name: AgentType): AgentConfig {
  // Match the agent block: `agents:` then nested by 2-space indent.
  // Capture everything until we hit either another top-level key or the next
  // sibling agent at the same 2-space indent.
  const blockMatch = md.match(
    new RegExp(`^\\s{2}${name}:\\s*\\n((?:\\s{4,}.*\\n?)+)`, 'm')
  );
  if (!blockMatch) return { ...EMPTY_AGENT };

  const block = blockMatch[1];
  const weightMatch = block.match(/weight:\s*(-?\d+(?:\.\d+)?)/);
  const weight = weightMatch ? Number(weightMatch[1]) : null;

  let sources: string[] = [];
  const inlineSources = block.match(/sources:\s*\[([^\]]+)\]/);
  if (inlineSources) {
    sources = inlineSources[1].split(',').map(s => s.trim().replace(/["']/g, '')).filter(Boolean);
  } else {
    const blockSources = block.match(/sources:\s*\n((?:\s+-\s*[\w.-]+\n?)+)/);
    if (blockSources) {
      sources = (blockSources[1].match(/[\w][\w.-]*/g) || []);
    }
  }

  return { weight: Number.isFinite(weight as number) ? weight : null, sources };
}

function num(match: RegExpMatchArray | null): number | null {
  if (!match) return null;
  const n = Number(match[1]);
  return Number.isFinite(n) ? n : null;
}

export function parseStrategyYaml(markdown: string | null | undefined): ParsedStrategyConfig {
  if (!markdown) return EMPTY;
  const md = markdown;

  const tickerMatch = md.match(/stock_tickers:\s*\n((?:\s*-\s*[\w.-]+\n?)+)/);
  const cryptoTickerMatch = md.match(/crypto_tickers:\s*\n((?:\s*-\s*[\w.-]+\n?)+)/);
  const tickers: string[] = [];
  if (tickerMatch) tickers.push(...(tickerMatch[1].match(/[A-Z][\w.-]*/g) || []));
  if (cryptoTickerMatch) tickers.push(...(cryptoTickerMatch[1].match(/[A-Z][\w.-]*/g) || []));

  const consensusMatch = md.match(/consensus_method:\s*(\w+)/);
  const consensusMethod = consensusMatch ? consensusMatch[1] : null;

  // Try to surface "(X of Y)" hint when method is MAJORITY/QUORUM
  let consensusQuorum: string | null = null;
  const quorumMatch = md.match(/quorum_required:\s*(\d+)/);
  if (quorumMatch) {
    consensusQuorum = `${quorumMatch[1]} of 3`;
  } else if (consensusMethod === 'MAJORITY') {
    consensusQuorum = '2 of 3';
  } else if (consensusMethod === 'UNANIMOUS') {
    consensusQuorum = '3 of 3';
  }

  const evalMatch = md.match(/evaluation_times:\s*\[([^\]]+)\]/);
  const evaluationTimes = evalMatch
    ? (evalMatch[1].match(/\d{2}:\d{2}/g) || [])
    : [];

  const openMatch = md.match(/market_open:\s*["']?(\d{2}:\d{2})["']?/);
  const closeMatch = md.match(/market_close:\s*["']?(\d{2}:\d{2})["']?/);
  const marketHours = openMatch && closeMatch ? { open: openMatch[1], close: closeMatch[1] } : null;

  const marketOnlyMatch = md.match(/market_only:\s*(true|false)/);
  const marketOnly = marketOnlyMatch ? marketOnlyMatch[1] === 'true' : null;

  const tzMatch = md.match(/timezone:\s*["']?([\w/_-]+)["']?/);
  const timezone = tzMatch ? tzMatch[1] : null;

  const maxPosition = num(md.match(/max_position_pct:\s*(-?\d+(?:\.\d+)?)/));
  const minCashFloor = num(md.match(/min_cash_floor_pct:\s*(-?\d+(?:\.\d+)?)/));
  const maxDrawdown = num(md.match(/max_drawdown_pct:\s*(-?\d+(?:\.\d+)?)/));

  const overrideMatch = md.match(/allow_override:\s*(true|false)/);
  const orchestratorOverride = overrideMatch ? overrideMatch[1] === 'true' : null;

  const vetoMatch = md.match(/veto_window:\s*\[([^\]]+)\]/);
  const vetoWindow = vetoMatch
    ? vetoMatch[1].split(',').map(s => s.trim().replace(/["']/g, '')).filter(Boolean)
    : [];

  const agents: Record<AgentType, AgentConfig> = {
    technical: parseAgent(md, 'technical'),
    fundamental: parseAgent(md, 'fundamental'),
    sentiment: parseAgent(md, 'sentiment'),
  };

  return {
    tickers,
    consensusMethod,
    consensusQuorum,
    evaluationTimes,
    marketHours,
    marketOnly,
    timezone,
    maxPositionPct: maxPosition,
    minCashFloorPct: minCashFloor,
    maxDrawdownPct: maxDrawdown,
    orchestratorOverride,
    vetoWindow,
    agents,
  };
}
