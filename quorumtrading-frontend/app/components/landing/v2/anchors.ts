/**
 * Anchor numbers — pinned across all five acts so a careful reader can trace
 * the same strategy through hero → problem → council → strategy → backtest.
 * Editing these in one place updates every panel.
 */
export const LP_ANCHORS = {
  strategy: {
    name: 'Tech ETF — Moving Average Crossover',
    file: 'strategy.md',
    version: 'v3.2',
    ticker: 'QQQ',
    consensus: 'MAJORITY · threshold 0.65',
  },
  sim: {
    id: '#025C205B',
    portfolioValue: 21580.84,
    starting: 20000,
    pctTotal: 7.90,
    plDay: 87.40,
    vsBench: -1.62,
  },
  backtest: {
    range: '2021-06 → 2026-02',
    totalReturn: 111.64,
    sharpe: 0.86,
    maxDd: -19.21,
    benchPct: 72.0,
    secondStratPct: 84.2,
  },
  latestDecision: {
    action: 'BUY' as const,
    sym: 'QQQ',
    px: 445.30,
    ts: '07 May 14:00 ET',
  },
  liveCapital: 41631,
  sims: 3,
};
