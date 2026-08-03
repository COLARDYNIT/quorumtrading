'use client';

import styled, { css } from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import type { AgentConfig, AgentType } from '../../lib/parseStrategyYaml';

interface Props {
  consensusMethod: string | null;
  consensusQuorum: string | null;
  agents: Record<AgentType, AgentConfig>;
}

interface AgentRow {
  type: AgentType;
  kind: 'tech' | 'fund' | 'sent';
  name: string;
}

const AGENT_ROWS: AgentRow[] = [
  { type: 'technical',   kind: 'tech', name: 'Technical' },
  { type: 'fundamental', kind: 'fund', name: 'Fundamental' },
  { type: 'sentiment',   kind: 'sent', name: 'Sentiment' },
];

const colorByKind = {
  tech: css`
    & .who { color: ${p => p.theme.colors.agents.technicalAnalyst}; }
    & .who .dot { background: ${p => p.theme.colors.agents.technicalAnalyst}; }
  `,
  fund: css`
    & .who { color: ${p => p.theme.colors.agents.fundamentalAnalyst}; }
    & .who .dot { background: ${p => p.theme.colors.agents.fundamentalAnalyst}; }
  `,
  sent: css`
    & .who { color: ${p => p.theme.colors.agents.sentimentAnalyst}; }
    & .who .dot { background: ${p => p.theme.colors.agents.sentimentAnalyst}; }
  `,
} as const;

const WeightsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const WeightRow = styled.div<{ $kind: AgentRow['kind'] }>`
  display: grid;
  grid-template-columns: 130px 60px 1fr;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  padding: 8px 10px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  ${p => colorByKind[p.$kind]}

  & .who {
    display: flex;
    align-items: center;
    gap: 6px;
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    text-transform: uppercase;
    letter-spacing: 0.05em;
    font-size: 12px;
  }
  & .who .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }
  & .w {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.fgStrong};
    font-size: 13px;
  }
  & .src {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgDimmed};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  & .src.empty {
    color: ${p => p.theme.colors.fgSubtle};
    font-style: italic;
  }
`;

/**
 * Renders the three AI agents that make up the consensus council. Weight and
 * sources come from the strategy YAML's `agents.<type>` block — when the
 * strategy doesn't declare a value we surface "—" / "default sources" rather
 * than inventing them, so the panel is always an accurate read of what's in
 * the strategy file.
 */
export function AgentCouncilPanel({ consensusMethod, consensusQuorum, agents }: Props) {
  const quorumLabel = consensusMethod
    ? `${consensusMethod.toLowerCase()} quorum${consensusQuorum ? ` · ${consensusQuorum}` : ''}`
    : 'consensus method unset';

  return (
    <Panel
      title="Agent council"
      count={`${AGENT_ROWS.length} agents · ${quorumLabel}`}
    >
      <WeightsList>
        {AGENT_ROWS.map(row => {
          const cfg = agents[row.type];
          const weight = cfg.weight != null ? cfg.weight.toFixed(1) : '—';
          const sourceText = cfg.sources.length > 0 ? cfg.sources.join(' · ') : 'default sources';
          return (
            <WeightRow key={row.type} $kind={row.kind}>
              <span className="who">
                <span className="dot" />
                {row.name}
              </span>
              <span className="w">w={weight}</span>
              <span className={`src${cfg.sources.length === 0 ? ' empty' : ''}`} title={sourceText}>
                {sourceText}
              </span>
            </WeightRow>
          );
        })}
      </WeightsList>
    </Panel>
  );
}
