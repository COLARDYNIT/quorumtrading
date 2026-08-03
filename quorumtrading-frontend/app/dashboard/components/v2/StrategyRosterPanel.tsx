'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { useQueries } from '@tanstack/react-query';
import { Panel } from '@/app/components/shared/v2';
import { apiClient } from '@/app/lib/api/client';
import { queryKeys } from '@/app/lib/api/hooks';
import type { StrategyResponse, SimulationResponse, StrategyPerformanceResponse } from '@/app/lib/api/client';

interface StrategyRosterPanelProps {
  strategies: StrategyResponse[];
  simulations: SimulationResponse[];
  /** How many top strategies to show. Default 5. */
  limit?: number;
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
  }
  thead th.r { text-align: right; }
  td { padding: 9px 12px; border-bottom: 1px solid ${p => p.theme.colors.borderSubtle}; font-size: 13px; vertical-align: middle; }
  tr:last-child td { border-bottom: none; }
  tr:hover td { background: rgba(255, 255, 255, 0.015); cursor: pointer; }

  .name { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .name .main { color: ${p => p.theme.colors.foreground}; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
  .name .meta { color: ${p => p.theme.colors.fgDimmed}; font-family: ${p => p.theme.typography.fontFamily.mono}; font-size: 11px; }

  .num { font-family: ${p => p.theme.typography.fontFamily.mono}; text-align: right; }
  .num.pos { color: ${p => p.theme.colors.positive}; }
  .num.neg { color: ${p => p.theme.colors.negative}; }
  .num.warn { color: ${p => p.theme.colors.terminalAmber}; }
`;

const Empty = styled.div`
  padding: 28px 16px; text-align: center;
  color: ${p => p.theme.colors.fgMuted}; font-size: 13px;

  a { color: ${p => p.theme.colors.brand}; text-decoration: none; }
  a:hover { text-decoration: underline; }
`;

export function StrategyRosterPanel({ strategies, simulations, limit = 5 }: StrategyRosterPanelProps) {
  const simsByStrategy = useMemo(() => {
    const m = new Map<string, number>();
    simulations.forEach(s => m.set(s.strategyId, (m.get(s.strategyId) ?? 0) + 1));
    return m;
  }, [simulations]);

  // Top N strategies by sim count (proxy for "most active") — fetch their perf.
  const topStrategies = useMemo(() => {
    return [...strategies]
      .sort((a, b) => (simsByStrategy.get(b.id) ?? 0) - (simsByStrategy.get(a.id) ?? 0))
      .slice(0, limit);
  }, [strategies, simsByStrategy, limit]);

  const perfQueries = useQueries({
    queries: topStrategies.map(s => ({
      queryKey: queryKeys.strategyPerformance(s.id),
      queryFn: () => apiClient.strategies.getPerformance(s.id),
      enabled: !!s.id,
      staleTime: 5 * 60 * 1000,
    })),
  });

  if (strategies.length === 0) {
    return (
      <Panel
        title="Strategy roster"
        count="0 strategies"
        headerActions={<Link href="/strategies/new" style={{ fontSize: 10, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>+ New</Link>}
      >
        <Empty>
          No strategies authored yet. <Link href="/strategies/new">Author one →</Link>
        </Empty>
      </Panel>
    );
  }

  return (
    <Panel
      title="Strategy roster"
      count={`${strategies.length} strateg${strategies.length === 1 ? 'y' : 'ies'} · ${topStrategies.length} shown`}
      noBodyPadding
      headerActions={<Link href="/strategies/new" style={{ fontSize: 10, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>+ New</Link>}
    >
      <Table>
        <thead>
          <tr>
            <th>Strategy</th>
            <th className="r">Sims</th>
            <th className="r">Avg ret</th>
            <th className="r">Sharpe</th>
          </tr>
        </thead>
        <tbody>
          {topStrategies.map((s, idx) => {
            const perf = perfQueries[idx].data as StrategyPerformanceResponse | undefined;
            const sims = simsByStrategy.get(s.id) ?? 0;
            const ret = perf?.avgReturn ?? null;
            const sharpe = perf?.avgSharpeRatio ?? null;
            return (
              <tr key={s.id} onClick={() => { window.location.href = `/strategies/${s.id}`; }}>
                <td>
                  <div className="name">
                    <span className="main">{s.name}</span>
                    <span className="meta">v{s.version} · {sims} sim{sims === 1 ? '' : 's'}</span>
                  </div>
                </td>
                <td className="num">{sims}</td>
                <td className={`num ${ret == null ? '' : ret >= 0 ? 'pos' : 'neg'}`}>
                  {ret == null ? '—' : `${ret >= 0 ? '+' : '−'}${Math.abs(ret).toFixed(2)}%`}
                </td>
                <td className={`num ${sharpe == null ? '' : sharpe > 0.5 ? '' : 'warn'}`}>
                  {sharpe == null ? '—' : sharpe.toFixed(2)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </Table>
    </Panel>
  );
}
