'use client';

import styled, { css } from 'styled-components';
import { labelCompact, tagBase } from '@/app/styles/labels';

/**
 * Agent vote — Tech / Fund / Sent, ✓ or ✕.
 *
 * A DS `tag` in the v0.4.0 family. The census flagged `.vote-chip` as the worst offender:
 * one class name carrying two different designs. There is one design now, and the only
 * per-agent property is the hue.
 */
export type VoteAgent = 'tech' | 'fund' | 'sent';

interface VoteChipProps {
  agent: VoteAgent;
  ok: boolean;
  className?: string;
  label?: string;
}

const agentLabels: Record<VoteAgent, string> = {
  tech: 'Tech',
  fund: 'Fund',
  sent: 'Sent',
};

/** Agent identity hues — `color` only; the tag base derives border and fill. */
const okStyles = {
  tech: css`
    color: ${p => p.theme.colors.agents.technicalAnalyst};
  `,
  fund: css`
    color: ${p => p.theme.colors.agents.fundamentalAnalyst};
  `,
  sent: css`
    color: ${p => p.theme.colors.agents.sentimentAnalyst};
  `,
} as const;

const Chip = styled.span<{ $agent: VoteAgent; $ok: boolean }>`
  ${tagBase}
  ${labelCompact}
  gap: 3px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};

  ${p =>
    p.$ok
      ? okStyles[p.$agent]
      : css`
          /* A vote against is absence of endorsement, not an error — it stays neutral so a
             row of dissent does not read as a row of failures. */
          color: ${p.theme.colors.fgSubtle};
        `}

  & > .glyph {
    line-height: 1;
  }
`;

const VoteChipRow = styled.span`
  display: inline-flex;
  gap: 4px;
`;

export function VoteChip({ agent, ok, label, className }: VoteChipProps) {
  return (
    <Chip $agent={agent} $ok={ok} className={className}>
      <span className="glyph">{ok ? '✓' : '✕'}</span>
      {label ?? agentLabels[agent]}
    </Chip>
  );
}

export { VoteChipRow };
