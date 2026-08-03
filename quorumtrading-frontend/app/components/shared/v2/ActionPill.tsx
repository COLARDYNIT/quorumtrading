'use client';

import styled, { css } from 'styled-components';
import { labelCompact, tagBase } from '@/app/styles/labels';

/**
 * Trading decision label — BUY / SELL / HOLD / DCA-BUY / SKIP.
 *
 * Despite the name this is a DS `tag` (categorical: *what the decision was*), not the DS
 * `action-pill` button species. The row-level action button with the `›` verb marker is
 * `RowAction` — see the note there. The name is kept because every decision table in the
 * app imports it; renaming it would be a large rename for no user-visible gain.
 *
 * NOTE (DS v0.4.0 §9): the DS kit's own ActionPill carried an `icon` prop, made redundant
 * once the `›` marker shipped CSS-side. This repo's copy never had one, so there is nothing
 * to deprecate here — the affordance lives on `RowAction`, which is the species that needs it.
 */
export type ActionKind = 'buy' | 'sell' | 'hold' | 'dca-buy' | 'skip';

interface ActionPillProps {
  action: ActionKind;
  override?: boolean;
  className?: string;
  children?: React.ReactNode;
}

/**
 * Kinds set `color` only — border and fill mix out of `currentColor` via the tag base.
 *
 * `skip` is the one shape exception in the family: it keeps a DASHED border because SKIP is
 * the engine failing to decide, not a decision. Colour alone was not enough — these rows used
 * to render as amber HOLD pills, so a run that decided nothing looked like a run that chose
 * to hold. The dash is what makes that unmistakable, and a test guards it.
 */
const kindStyles = {
  buy: css`
    color: ${p => p.theme.colors.action.buy};
  `,
  sell: css`
    color: ${p => p.theme.colors.action.sell};
  `,
  hold: css`
    color: ${p => p.theme.colors.action.hold};
  `,
  'dca-buy': css`
    color: ${p => p.theme.colors.info};
  `,
  skip: css`
    color: ${p => p.theme.colors.textMuted};
    border-style: dashed;
  `,
} as const;

const Tag = styled.span<{ $action: ActionKind }>`
  ${tagBase}
  ${labelCompact}
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  ${p => kindStyles[p.$action]}
`;

const OverrideBadge = styled.span`
  ${tagBase}
  ${labelCompact}
  margin-left: 6px;
  color: ${p => p.theme.colors.terminalAmber};
`;

export function ActionPill({ action, override, children, className }: ActionPillProps) {
  return (
    <span className={className}>
      <Tag $action={action}>{children ?? action.replace('-', ' ')}</Tag>
      {override && <OverrideBadge>override</OverrideBadge>}
    </span>
  );
}
