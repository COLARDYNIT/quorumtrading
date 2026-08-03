'use client';

import { useMemo } from 'react';
import { useParams } from 'next/navigation';
import styled from 'styled-components';
import { Panel, AgentCard } from '@/app/components/shared/v2';
import type { AgentKind } from '@/app/components/shared/v2';
import { useConsensusWithAgents } from '@/app/lib/api/hooks';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';
import type { ConsensusWithAgentsResponse, LLMDecision } from '@/app/lib/types/api';

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
`;

const EmptyMsg = styled.div`
  padding: 20px 12px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 13px;
  grid-column: 1 / -1;
`;

interface AgentRow {
  kind: AgentKind;
  name: string;
  agentType: string;
  calls: number;
  /** Per-decision boolean: did this agent's vote match the final consensus action? */
  matches: boolean[];
  /** Number of times the orchestrator overrode the base majority. */
  overrides?: number;
}

const AGENT_DEFS: Array<{ kind: AgentKind; name: string; agentType: string }> = [
  { kind: 'tech', name: 'Technical', agentType: 'TECHNICAL' },
  { kind: 'fund', name: 'Fundamental', agentType: 'FUNDAMENTAL' },
  { kind: 'sent', name: 'Sentiment', agentType: 'SENTIMENT' },
  { kind: 'orch', name: 'Orchestrator', agentType: 'ORCHESTRATOR' },
];

function deriveAgents(history: ConsensusWithAgentsResponse[]): AgentRow[] {
  const matchesFinal = (a: LLMDecision | undefined, finalLabel: string) =>
    !!a && finalLabel !== '' && parseActionLabel(a.suggestedAction) === finalLabel;

  return AGENT_DEFS.map(def => {
    const matches: boolean[] = [];
    let calls = 0;
    let overrides = 0;
    for (const c of history) {
      const agents = c.agentDecisions ?? [];
      const a = agents.find(x => x.agentType?.toUpperCase() === def.agentType);
      if (!a) continue;
      calls++;
      const finalLabel = parseActionLabel(c.consensusDecision.finalAction);
      const matched = matchesFinal(a, finalLabel);
      matches.push(matched);
      if (def.agentType === 'ORCHESTRATOR') {
        const baseAgents = ['TECHNICAL', 'FUNDAMENTAL', 'SENTIMENT'];
        const baseMatching = baseAgents
          .map(t => agents.find(x => x.agentType?.toUpperCase() === t))
          .filter(x => matchesFinal(x, finalLabel))
          .length;
        if (matched && baseMatching < 2) overrides++;
      }
    }
    return {
      kind: def.kind,
      name: def.name,
      agentType: def.agentType,
      calls,
      matches,
      overrides: def.agentType === 'ORCHESTRATOR' ? overrides : undefined,
    };
  });
}

export function AgentsPanel() {
  const params = useParams();
  const simulationId = (params?.id as string | undefined) ?? '';
  // Same wrapped-feed reasoning as DecisionsPanel: per-agent stats need agent rows.
  // Endpoint caps `limit` at 100.
  const { data: consensusWithAgents } = useConsensusWithAgents(simulationId, undefined, 100);

  const rows = useMemo(() => {
    const history = (consensusWithAgents ?? []) as ConsensusWithAgentsResponse[];
    return deriveAgents(history);
  }, [consensusWithAgents]);

  const totalSessions = consensusWithAgents?.length ?? 0;
  const hasData = rows.some(r => r.calls > 0);

  return (
    <Panel
      title="Agent council"
      count={totalSessions === 0 ? 'awaiting first round' : `last ${Math.min(totalSessions, 12)} of ${totalSessions} session${totalSessions === 1 ? '' : 's'}`}
    >
      <Grid>
        {!hasData ? (
          <EmptyMsg>Per-agent accuracy appears after the first consensus round.</EmptyMsg>
        ) : (
          rows.map(r => {
            const recentMatches = r.matches.slice(0, 12).reverse();
            const accuracy = r.calls > 0
              ? Math.round((r.matches.filter(Boolean).length / r.calls) * 100)
              : undefined;
            return (
              <AgentCard
                key={r.agentType}
                kind={r.kind}
                name={r.name}
                calls={r.calls}
                accuracyPct={accuracy}
                contribution={accuracy != null ? `${accuracy >= 50 ? '+' : ''}${accuracy - 50}%` : undefined}
                votes={recentMatches}
                overrides={r.overrides}
              />
            );
          })
        )}
      </Grid>
    </Panel>
  );
}
