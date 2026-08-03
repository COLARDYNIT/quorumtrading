'use client';

import styled from 'styled-components';
import { AGENT_COLORS, ACTION_COLORS } from '@/app/lib/utils/themeColors';

interface Vote {
  agent: string;
  action: string;
  confidence: number;
}

interface VoteSummaryProps {
  votes?: unknown;
  [key: string]: unknown;
}

// Normalize a raw vote entry to {agent, action, confidence}.
// Handles two formats LLMs produce:
//   1. String:  "TECHNICAL: BUY (0.85)"
//   2. Object:  { agent: "TECHNICAL", action: "BUY", confidence: 0.85 }
//              (with various field-name aliases)
function normalizeVote(raw: any): Vote {
  // Format 1: plain string like "AGENT: ACTION (CONFIDENCE)"
  if (typeof raw === 'string') {
    const match = raw.match(/^([A-Z_]+)\s*:\s*([A-Z]+)\s*\((\d+(?:\.\d+)?)\)$/i);
    if (match) {
      return {
        agent: match[1].toUpperCase(),
        action: match[2].toUpperCase(),
        confidence: parseFloat(match[3]),
      };
    }
    return { agent: '', action: '', confidence: NaN };
  }

  // Format 2: object with varying field names
  const agent = raw.agent || raw.agentType || raw.name || raw.type || raw.role || '';
  const action = raw.action || raw.decision || raw.suggestedAction || raw.recommendation || raw.vote || '';
  const conf = raw.confidence ?? raw.score ?? raw.confidenceScore ?? raw.conf ?? null;
  const confidence = typeof conf === 'number' ? conf : typeof conf === 'string' ? parseFloat(conf) : NaN;
  return { agent: String(agent).toUpperCase(), action: String(action).toUpperCase(), confidence };
}

export function VoteSummary({ votes }: VoteSummaryProps) {
  let raw: any[] = [];

  if (typeof votes === 'string') {
    try { raw = JSON.parse(votes); } catch { return null; }
  } else if (Array.isArray(votes)) {
    raw = votes;
  } else {
    return null;
  }

  if (!Array.isArray(raw) || raw.length === 0) return null;

  const data: Vote[] = raw.map(normalizeVote);

  // Separate orchestrator from other agents
  const agentVotes = data.filter(v => v.agent !== 'ORCHESTRATOR');
  const orchestratorVote = data.find(v => v.agent === 'ORCHESTRATOR');

  // Count actions for the consensus summary
  const actionCounts = agentVotes.reduce<Record<string, number>>((acc, v) => {
    acc[v.action] = (acc[v.action] || 0) + 1;
    return acc;
  }, {});

  return (
    <Container>
      {/* Consensus summary bar */}
      {Object.keys(actionCounts).length > 0 && (
        <ConsensusSummary>
          {Object.entries(actionCounts)
            .sort(([, a], [, b]) => b - a)
            .map(([action, count]) => (
              <ConsensusChip key={action} $color={ACTION_COLORS[action] || ACTION_COLORS.SKIP}>
                {count} {action}
              </ConsensusChip>
            ))}
          {orchestratorVote && (
            <>
              <ConsensusDivider />
              <ConsensusChip $color={ACTION_COLORS[orchestratorVote.action] || ACTION_COLORS.SKIP} $isOrchestrator>
                FINAL: {orchestratorVote.action}
              </ConsensusChip>
            </>
          )}
        </ConsensusSummary>
      )}

      {/* Individual agent votes */}
      <VoteGrid>
        {agentVotes.map((vote, i) => (
          <VoteCard key={i} $agentColor={AGENT_COLORS[vote.agent] || ACTION_COLORS.SKIP}>
            <VoteCardHeader>
              <AgentDot $color={AGENT_COLORS[vote.agent] || ACTION_COLORS.SKIP} />
              <AgentName>{formatAgentName(vote.agent)}</AgentName>
            </VoteCardHeader>
            <VoteCardBody>
              <VoteAction $color={ACTION_COLORS[vote.action] || ACTION_COLORS.SKIP}>{vote.action}</VoteAction>
              <VoteConfidence>{(vote.confidence * 100).toFixed(0)}%</VoteConfidence>
            </VoteCardBody>
          </VoteCard>
        ))}
      </VoteGrid>
    </Container>
  );
}

function formatAgentName(agent: string): string {
  if (!agent) return 'Unknown';
  return agent.replace(/_/g, ' ').toLowerCase()
    .split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const ConsensusSummary = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  flex-wrap: wrap;
`;

const ConsensusChip = styled.span<{ $color: string; $isOrchestrator?: boolean }>`
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  background: ${p => p.$color}15;
  color: ${p => p.$color};
  border: 1px solid ${p => p.$color}40;
  border-radius: 4px;
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.$isOrchestrator
    ? p.theme.typography.fontWeight.bold
    : p.theme.typography.fontWeight.semibold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  letter-spacing: 0.3px;
`;

const ConsensusDivider = styled.div`
  width: 1px;
  height: 16px;
  background: ${p => p.theme.colors.border};
`;

const VoteGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: ${p => p.theme.spacing.sm};
`;

const VoteCard = styled.div<{ $agentColor: string }>`
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-left: 3px solid ${p => p.$agentColor};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const VoteCardHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
`;

const AgentDot = styled.span<{ $color: string }>`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${p => p.$color};
  flex-shrink: 0;
`;

const AgentName = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  letter-spacing: 0.2px;
`;

const VoteCardBody = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const VoteAction = styled.span<{ $color: string }>`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$color};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const VoteConfidence = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;
