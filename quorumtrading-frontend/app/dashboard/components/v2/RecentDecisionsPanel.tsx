'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { useQueries } from '@tanstack/react-query';
import { Panel, ActionPill, type ActionKind } from '@/app/components/shared/v2';
import { apiClient } from '@/app/lib/api/client';
import { queryKeys } from '@/app/lib/api/hooks';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';
import type { SimulationResponse, ConsensusDecisionsResponse, ConsensusDecision } from '@/app/lib/types/api';
import type { StrategyResponse } from '@/app/lib/api/client';

interface RecentDecisionsPanelProps {
  liveSims: SimulationResponse[];
  strategies: StrategyResponse[];
  /** Max rows to render. */
  limit?: number;
}

const List = styled.div`
  display: flex; flex-direction: column;
`;

const Row = styled(Link)`
  display: grid;
  grid-template-columns: 96px 64px 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 9px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 12.5px;
  text-decoration: none;
  color: inherit;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255, 255, 255, 0.02); }

  .when { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.fgMuted}; font-size: 11.5px; }
  .what { color: ${p => p.theme.colors.foreground}; line-height: 1.4; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
  .what .sym { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.fgStrong}; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
  .what .strat { color: ${p => p.theme.colors.fgMuted}; font-size: 11.5px; }
  .px { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.fgMuted}; text-align: right; font-size: 12px; }
`;

const Empty = styled.div`
  padding: 28px 16px; text-align: center;
  color: ${p => p.theme.colors.fgMuted}; font-size: 13px;
`;

type Row = ConsensusDecision & { strategyName: string; simName: string };

function actionToKind(action: string): ActionKind {
  const a = action.toUpperCase();
  if (a === 'BUY') return 'buy';
  if (a === 'SELL') return 'sell';
  return 'hold';
}

function shortDate(iso: string): string {
  try {
    const d = new Date(iso);
    const today = new Date();
    const sameDay = d.toDateString() === today.toDateString();
    if (sameDay) return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
    return d.toLocaleString('en-US', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
  } catch { return iso; }
}

export function RecentDecisionsPanel({ liveSims, strategies, limit = 7 }: RecentDecisionsPanelProps) {
  const stratById = useMemo(() => {
    const m = new Map<string, StrategyResponse>();
    strategies.forEach(s => m.set(s.id, s));
    return m;
  }, [strategies]);

  const queries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.consensusDecisions(s.id),
      queryFn: () => apiClient.decisions.consensus.list(s.id, undefined, undefined, limit),
      enabled: !!s.id,
      staleTime: 10_000,
    })),
  });

  const merged: Row[] = useMemo(() => {
    const all: Row[] = [];
    queries.forEach((q, idx) => {
      const list = (q.data as ConsensusDecisionsResponse | undefined)?.decisions ?? [];
      const sim = liveSims[idx];
      const strategyName = stratById.get(sim.strategyId)?.name ?? sim.name ?? sim.strategyId.slice(0, 8);
      list.forEach(d => all.push({ ...d, strategyName, simName: sim.name ?? sim.id }));
    });
    all.sort((a, b) => new Date(b.decisionTimestamp).getTime() - new Date(a.decisionTimestamp).getTime());
    return all.slice(0, limit);
  }, [queries, liveSims, stratById, limit]);

  return (
    <Panel
      title="Recent council decisions"
      count={`${merged.length} shown`}
      noBodyPadding
      bodyMaxHeight={420}
      headerActions={<Link href="/simulations?type=live" style={{ fontSize: 10, color: 'var(--qt-fg-muted, #8a98a4)', textDecoration: 'none' }}>Open trail →</Link>}
    >
      {merged.length === 0 ? (
        <Empty>No decisions yet. The agent council convenes once a live simulation is running.</Empty>
      ) : (
        <List>
          {merged.map(r => (
            <Row key={r.id} href={`/simulations/${r.simulationId}`}>
              <span className="when">{shortDate(r.decisionTimestamp)}</span>
              <ActionPill action={actionToKind(parseActionLabel(r.finalAction))} />
              <span className="what">
                <span className="sym">{r.symbol ?? '—'}</span>{' '}
                <span className="strat">· {r.strategyName}</span>
              </span>
              <span className="px">{r.executionPrice != null ? `@ $${r.executionPrice.toFixed(2)}` : '—'}</span>
            </Row>
          ))}
        </List>
      )}
    </Panel>
  );
}
