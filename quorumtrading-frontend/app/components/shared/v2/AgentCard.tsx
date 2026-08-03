'use client';

import styled, { css } from 'styled-components';

export type AgentKind = 'tech' | 'fund' | 'sent' | 'orch';

interface AgentCardProps {
  kind: AgentKind;
  name: string;
  calls: number;
  accuracyPct?: number;
  contribution?: string;
  votes?: boolean[];
  overrides?: number;
  className?: string;
}

const colorByKind = {
  tech: css`
    & .who { color: ${p => p.theme.colors.agents.technicalAnalyst}; }
    & .who .dot { background: ${p => p.theme.colors.agents.technicalAnalyst}; }
    & .bars .b.y { background: ${p => p.theme.colors.agents.technicalAnalyst}; }
  `,
  fund: css`
    & .who { color: ${p => p.theme.colors.agents.fundamentalAnalyst}; }
    & .who .dot { background: ${p => p.theme.colors.agents.fundamentalAnalyst}; }
    & .bars .b.y { background: ${p => p.theme.colors.agents.fundamentalAnalyst}; }
  `,
  sent: css`
    & .who { color: ${p => p.theme.colors.agents.sentimentAnalyst}; }
    & .who .dot { background: ${p => p.theme.colors.agents.sentimentAnalyst}; }
    & .bars .b.y { background: ${p => p.theme.colors.agents.sentimentAnalyst}; }
  `,
  orch: css`
    & .who { color: ${p => p.theme.colors.fgStrong}; }
    & .who .dot { background: ${p => p.theme.colors.fgMuted}; }
    & .bars .b.y { background: ${p => p.theme.colors.fgMuted}; }
  `,
} as const;

const Card = styled.div<{ $kind: AgentKind }>`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 9px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  ${p => colorByKind[p.$kind]}

  & .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  & .who {
    display: flex;
    align-items: center;
    gap: 6px;
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }
  & .who .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }
  & .calls {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & .stats {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px 12px;
  }
  & .stat .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & .stat .v {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 15px;
    color: ${p => p.theme.colors.fgStrong};
  }
  & .stat .v.pos { color: ${p => p.theme.colors.positive}; }
  & .bars {
    display: flex;
    gap: 2px;
    align-items: end;
    height: 24px;
    padding-top: 2px;
  }
  & .bars .b {
    flex: 1;
    background: ${p => p.theme.colors.border};
    border-radius: 1px;
    opacity: 0.8;
  }
  & .bars .b.n { background: rgba(255, 51, 102, 0.5); }
  & .meta {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

export function AgentCard({ kind, name, calls, accuracyPct, contribution, votes, overrides, className }: AgentCardProps) {
  return (
    <Card $kind={kind} className={className}>
      <div className="head">
        <div className="who">
          <span className="dot" />
          {name}
        </div>
        <span className="calls">{calls} calls</span>
      </div>
      <div className="stats">
        <div className="stat">
          <div className="lbl">Accuracy</div>
          <div className="v">{accuracyPct != null ? `${accuracyPct}%` : '—'}</div>
        </div>
        <div className="stat">
          <div className="lbl">Contribution</div>
          <div className={`v${contribution?.trim().startsWith('+') ? ' pos' : ''}`}>{contribution ?? '—'}</div>
        </div>
      </div>
      {votes && votes.length > 0 && (
        <div className="bars" aria-hidden="true">
          {votes.map((v, i) => (
            <div key={i} className={`b ${v ? 'y' : 'n'}`} style={{ height: `${8 + (v ? 14 : 0)}px` }} />
          ))}
        </div>
      )}
      {overrides != null && <div className="meta">{overrides} override{overrides === 1 ? '' : 's'}</div>}
    </Card>
  );
}
