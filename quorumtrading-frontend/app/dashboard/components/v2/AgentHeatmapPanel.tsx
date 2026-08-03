'use client';

import { useMemo, Fragment } from 'react';
import styled from 'styled-components';
import { useQueries } from '@tanstack/react-query';
import { Panel } from '@/app/components/shared/v2';
import { apiClient } from '@/app/lib/api/client';
import { queryKeys } from '@/app/lib/api/hooks';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';
import type { SimulationResponse, ConsensusWithAgentsResponse, LLMDecision } from '@/app/lib/types/api';

interface AgentHeatmapPanelProps {
  liveSims: SimulationResponse[];
  /** Number of trailing days. Default 14. */
  days?: number;
}

type AgentKey = 'TECHNICAL' | 'FUNDAMENTAL' | 'SENTIMENT';
const AGENTS: AgentKey[] = ['TECHNICAL', 'FUNDAMENTAL', 'SENTIMENT'];

const Grid = styled.div<{ $cols: number }>`
  display: grid;
  grid-template-columns: 96px repeat(${p => p.$cols}, minmax(0, 1fr));
  gap: 2px;
  padding: 12px 14px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
`;

const RowLabel = styled.span`
  color: ${p => p.theme.colors.fgMuted};
  align-self: center;
  font-size: 11.5px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
`;

const HCell = styled.div<{ $agent: AgentKey; $intensity: number }>`
  height: 18px;
  border-radius: 1px;
  background: ${p => {
    switch (p.$agent) {
      case 'TECHNICAL': return p.theme.colors.agents.technicalAnalyst;
      case 'FUNDAMENTAL': return p.theme.colors.agents.fundamentalAnalyst;
      case 'SENTIMENT': return p.theme.colors.agents.sentimentAnalyst;
    }
  }};
  opacity: ${p => {
    // intensity is 0..1 over actively agreeing votes
    if (p.$intensity <= 0) return 0.1;
    if (p.$intensity < 0.25) return 0.25;
    if (p.$intensity < 0.5) return 0.45;
    if (p.$intensity < 0.75) return 0.7;
    return 0.95;
  }};
`;

const OffCell = styled.div`
  height: 18px;
  border-radius: 1px;
  background: ${p => p.theme.colors.background};
  opacity: 0.6;
`;

const DayLabel = styled.span`
  text-align: center;
  color: ${p => p.theme.colors.fgDimmed};
  font-size: 9px;
`;

const Legend = styled.div`
  display: flex; gap: 16px; padding: 4px 14px 12px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgMuted};
  flex-wrap: wrap;

  .sw { display: inline-block; width: 8px; height: 8px; margin-right: 5px; border-radius: 1px; vertical-align: middle; }
`;

const Empty = styled.div`
  padding: 28px 16px; text-align: center;
  color: ${p => p.theme.colors.fgMuted}; font-size: 13px;
`;

function isoDay(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function AgentHeatmapPanel({ liveSims, days = 14 }: AgentHeatmapPanelProps) {
  const queries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.consensusWithAgents(s.id, undefined, 100, undefined),
      queryFn: () => apiClient.decisions.consensusWithAgents.list(s.id, undefined, 100, undefined),
      enabled: !!s.id,
      staleTime: 60_000,
    })),
  });

  const { dayLabels, dayKeys, matrix, totals } = useMemo(() => {
    const now = new Date();
    const dayKeys: string[] = [];
    const dayLabels: string[] = [];
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(now);
      d.setUTCDate(now.getUTCDate() - i);
      d.setUTCHours(0, 0, 0, 0);
      dayKeys.push(isoDay(d));
      dayLabels.push(String(d.getUTCDate()).padStart(2, '0'));
    }

    // For each agent, count "active" days: how many of that agent's votes in that day matched the final consensus action.
    const buckets: Record<AgentKey, Map<string, { matched: number; total: number }>> = {
      TECHNICAL: new Map(),
      FUNDAMENTAL: new Map(),
      SENTIMENT: new Map(),
    };

    queries.forEach(q => {
      const rows = (q.data as ConsensusWithAgentsResponse[] | undefined) ?? [];
      rows.forEach(c => {
        const ts = c.consensusDecision?.decisionTimestamp;
        if (!ts) return;
        const dayKey = isoDay(new Date(ts));
        if (!dayKeys.includes(dayKey)) return;
        const finalLabel = parseActionLabel(c.consensusDecision.finalAction);
        const agents = c.agentDecisions ?? [];
        AGENTS.forEach(key => {
          const a: LLMDecision | undefined = agents.find(x => x.agentType?.toUpperCase() === key);
          if (!a) return;
          const matched = parseActionLabel(a.suggestedAction) === finalLabel && finalLabel !== '';
          const bucket = buckets[key].get(dayKey) ?? { matched: 0, total: 0 };
          bucket.total += 1;
          if (matched) bucket.matched += 1;
          buckets[key].set(dayKey, bucket);
        });
      });
    });

    const matrix: Record<AgentKey, { intensity: number; total: number }[]> = {
      TECHNICAL: [],
      FUNDAMENTAL: [],
      SENTIMENT: [],
    };
    AGENTS.forEach(key => {
      matrix[key] = dayKeys.map(d => {
        const b = buckets[key].get(d);
        if (!b || b.total === 0) return { intensity: 0, total: 0 };
        return { intensity: b.matched / b.total, total: b.total };
      });
    });

    const totals: Record<AgentKey, { matched: number; total: number }> = {
      TECHNICAL: { matched: 0, total: 0 },
      FUNDAMENTAL: { matched: 0, total: 0 },
      SENTIMENT: { matched: 0, total: 0 },
    };
    AGENTS.forEach(key => {
      buckets[key].forEach(b => {
        totals[key].matched += b.matched;
        totals[key].total += b.total;
      });
    });

    return { dayKeys, dayLabels, matrix, totals };
  }, [queries, days]);

  const anyData = Object.values(totals).some(t => t.total > 0);

  return (
    <Panel
      title="Agent vote heatmap"
      count={`last ${days} days`}
    >
      {!anyData ? (
        <Empty>No consensus decisions in the last {days} days yet.</Empty>
      ) : (
        <>
          <Grid $cols={dayKeys.length}>
            {AGENTS.map(key => (
              <Fragment key={key}>
                <RowLabel>{key === 'TECHNICAL' ? 'Technical' : key === 'FUNDAMENTAL' ? 'Fundamental' : 'Sentiment'}</RowLabel>
                {matrix[key].map((cell, i) => cell.total === 0
                  ? <OffCell key={`${key}-${i}`} title={`${dayKeys[i]} · no votes`} />
                  : <HCell key={`${key}-${i}`} $agent={key} $intensity={cell.intensity}
                      title={`${dayKeys[i]} · ${Math.round(cell.intensity * 100)}% agreement (${cell.total} vote${cell.total === 1 ? '' : 's'})`} />)}
              </Fragment>
            ))}
            <span />
            {dayLabels.map((d, i) => <DayLabel key={`day-${i}`}>{d}</DayLabel>)}
          </Grid>
          <Legend>
            {AGENTS.map(key => {
              const t = totals[key];
              const pct = t.total > 0 ? Math.round((t.matched / t.total) * 100) : 0;
              const swatchColor = key === 'TECHNICAL'
                ? 'var(--qt-agent-technical, #00ff41)'
                : key === 'FUNDAMENTAL'
                ? 'var(--qt-agent-fundamental, #ffb000)'
                : 'var(--qt-agent-sentiment, #00d9ff)';
              return (
                <span key={key}>
                  <span className="sw" style={{ background: swatchColor }} />
                  {key === 'TECHNICAL' ? 'Technical' : key === 'FUNDAMENTAL' ? 'Fundamental' : 'Sentiment'} · {pct}% w/ consensus
                </span>
              );
            })}
          </Legend>
        </>
      )}
    </Panel>
  );
}
