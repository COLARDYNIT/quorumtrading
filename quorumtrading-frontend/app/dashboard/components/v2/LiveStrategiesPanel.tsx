'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { useQueries } from '@tanstack/react-query';
import { Panel, ActionPill, type ActionKind } from '@/app/components/shared/v2';
import { apiClient } from '@/app/lib/api/client';
import { queryKeys } from '@/app/lib/api/hooks';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';
import { SparkLine } from './SparkLine';
import type {
  SimulationResponse,
  PortfolioSnapshot,
  ChartDataPoint,
  ConsensusDecisionsResponse,
  ContributionResponse,
} from '@/app/lib/types/api';
import type { StrategyResponse } from '@/app/lib/api/client';

interface LiveStrategiesPanelProps {
  liveSims: SimulationResponse[];
  strategies: StrategyResponse[];
}

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-family: ${p => p.theme.typography.fontFamily.sans};

  thead th {
    text-align: left;
    font-size: 9.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    padding: 8px 12px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    background: ${p => p.theme.colors.background};
  }
  thead th.r { text-align: right; }

  tbody tr {
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    cursor: pointer;
    transition: background 120ms ease;
  }
  tbody tr:last-child { border-bottom: none; }
  tbody tr:hover td { background: rgba(255, 255, 255, 0.02); }

  td {
    padding: 9px 12px;
    vertical-align: middle;
    font-size: 13px;
  }
  td.r { text-align: right; }

  .name { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .name .row1 { display: flex; align-items: center; gap: 8px; }
  .name .strat { color: ${p => p.theme.colors.fgStrong}; font-size: 13.5px; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
  .name .id { font-family: ${p => p.theme.typography.fontFamily.mono}; font-size: 11px; color: ${p => p.theme.colors.fgDimmed}; }
  .name .meta { font-family: ${p => p.theme.typography.fontFamily.mono}; font-size: 11.5px; color: ${p => p.theme.colors.fgDimmed}; }

  .val { font-family: ${p => p.theme.typography.fontFamily.mono}; font-variant-numeric: tabular-nums; font-size: 13.5px; color: ${p => p.theme.colors.fgStrong}; }
  .val.pos { color: ${p => p.theme.colors.positive}; }
  .val.neg { color: ${p => p.theme.colors.negative}; }
  .ts { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.fgDimmed}; font-size: 11px; display: block; margin-top: 2px; }
`;

const EmptyMsg = styled.div`
  padding: 28px 16px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 13px;

  a { color: ${p => p.theme.colors.brand}; text-decoration: none; }
  a:hover { text-decoration: underline; }
`;

function brokerLabel(t: SimulationResponse['brokerType']): string {
  if (t === 'ALPACA_LIVE') return 'Alpaca live';
  if (t === 'ALPACA_PAPER') return 'Alpaca paper';
  if (t === 'MOCK') return 'Mock';
  return '—';
}

function fmtMoney(n: number, dp = 0): string {
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: dp, maximumFractionDigits: dp });
}

function actionToKind(action: string): ActionKind {
  const a = action.toUpperCase();
  if (a === 'BUY') return 'buy';
  if (a === 'SELL') return 'sell';
  return 'hold';
}

function shortTime(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleString('en-US', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
  } catch { return iso; }
}

export function LiveStrategiesPanel({ liveSims, strategies }: LiveStrategiesPanelProps) {
  const stratById = useMemo(() => {
    const m = new Map<string, StrategyResponse>();
    strategies.forEach(s => m.set(s.id, s));
    return m;
  }, [strategies]);

  const portfolioQueries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.portfolioLatest(s.id),
      queryFn: () => apiClient.portfolio.latest(s.id),
      enabled: !!s.id,
      staleTime: 10_000,
    })),
  });

  const chartQueries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.simulationChartData(s.id, 24),
      queryFn: () => apiClient.simulations.chartData(s.id, 24),
      enabled: !!s.id,
      staleTime: 30_000,
    })),
  });

  const decisionQueries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.consensusDecisions(s.id),
      queryFn: () => apiClient.decisions.consensus.list(s.id, undefined, undefined, 1),
      enabled: !!s.id,
      staleTime: 10_000,
    })),
  });

  // DCA contributions per live sim — needed so the Return column measures against
  // total invested (seed + Σ contributions), not the seed alone. Lightweight rows.
  const contributionQueries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.simulationContributions(s.id),
      queryFn: () => apiClient.simulations.contributions(s.id),
      enabled: !!s.id,
      staleTime: 30_000,
    })),
  });

  if (liveSims.length === 0) {
    return (
      <Panel title="Live strategies" count="0 active">
        <EmptyMsg>
          No live simulations yet. <Link href="/simulations">Promote a backtest to live →</Link>
        </EmptyMsg>
      </Panel>
    );
  }

  return (
    <Panel
      title="Live strategies"
      count={`${liveSims.length} active`}
      noBodyPadding
      headerActions={<Link href="/simulations?type=live" style={{ fontSize: 10, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>Open all →</Link>}
    >
      <Table>
        <thead>
          <tr>
            <th>Sim</th>
            <th className="r">Value</th>
            <th className="r">Return</th>
            <th>Trend</th>
            <th className="r">Last</th>
          </tr>
        </thead>
        <tbody>
          {liveSims.map((sim, idx) => {
            const portfolio = portfolioQueries[idx].data as PortfolioSnapshot | undefined;
            const series = (chartQueries[idx].data as ChartDataPoint[] | undefined) ?? [];
            const decisions = (decisionQueries[idx].data as ConsensusDecisionsResponse | undefined)?.decisions ?? [];
            const lastDecision = decisions[0];

            const totalValue = portfolio?.totalValue;
            const contributions = (contributionQueries[idx].data as ContributionResponse[] | undefined) ?? [];
            const contributedTotal = contributions.reduce((sum, c) => sum + (Number(c.amount) || 0), 0);
            // Basis = seed + Σ contributions, so a DCA top-up isn't counted as profit.
            const investedBase = (sim.initialCash ?? series[0]?.portfolioValue ?? 0) + contributedTotal;
            const returnPct = investedBase > 0 && totalValue != null ? ((totalValue - investedBase) / investedBase) * 100 : null;
            const trendData = series.length >= 2 ? series.map(p => p.portfolioValue) : [];
            const positive = returnPct == null ? true : returnPct >= 0;
            const stratName = stratById.get(sim.strategyId)?.name ?? sim.name ?? sim.strategyId;

            return (
              <tr
                key={sim.id}
                onClick={() => { window.location.href = `/simulations/${sim.id}`; }}
                title={`Open ${sim.name ?? sim.id}`}
              >
                <td>
                  <div className="name">
                    <div className="row1">
                      <span className="strat">{stratName}</span>
                    </div>
                    <span className="meta">{brokerLabel(sim.brokerType)} · #{sim.id.slice(0, 8).toUpperCase()}</span>
                  </div>
                </td>
                <td className="r">
                  <span className="val">{totalValue != null ? fmtMoney(totalValue, 0) : '—'}</span>
                </td>
                <td className="r">
                  <span className={`val ${returnPct == null ? '' : returnPct >= 0 ? 'pos' : 'neg'}`}>
                    {returnPct == null ? '—' : `${returnPct >= 0 ? '+' : '−'}${Math.abs(returnPct).toFixed(2)}%`}
                  </span>
                </td>
                <td>
                  {trendData.length >= 2
                    ? <SparkLine data={trendData} positive={positive} />
                    : <span style={{ color: 'var(--qt-fg-dimmed)', fontSize: 10 }}>n/a</span>}
                </td>
                <td className="r">
                  {lastDecision ? (
                    <>
                      <ActionPill action={actionToKind(parseActionLabel(lastDecision.finalAction))} />
                      <span className="ts">{shortTime(lastDecision.decisionTimestamp)}</span>
                    </>
                  ) : (
                    <span style={{ color: 'var(--qt-fg-dimmed)', fontSize: 10 }}>—</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </Table>
    </Panel>
  );
}
