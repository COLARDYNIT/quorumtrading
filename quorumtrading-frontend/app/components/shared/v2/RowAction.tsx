'use client';

import styled, { css } from 'styled-components';
import { labelBorder } from '@/app/styles/labels';

/**
 * Row-level action button — the DS v0.4.0 `action-pill` species (decision B2).
 *
 * Named `RowAction`, not `ActionPill`, because this repo already ships an `ActionPill`
 * that is a *trading decision tag* (BUY / SELL / HOLD). Two different things wearing one
 * name is exactly the collision the Aug 2026 label census was called to end, so the button
 * takes the unambiguous name and the tag keeps its meaning.
 *
 * The leading `›` is a verb marker: it makes clickability legible at rest rather than only
 * on hover, which is what separates this from a Tag that happens to be inside a row.
 */
export type RowActionKind = 'neutral' | 'primary' | 'danger' | 'info';

const kindColor = {
  neutral: null,
  primary: css`
    color: ${p => p.theme.colors.brand};
  `,
  danger: css`
    color: ${p => p.theme.colors.terminalPink};
  `,
  info: css`
    color: ${p => p.theme.colors.info};
  `,
} as const;

const Btn = styled.button<{ $kind: RowActionKind }>`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.labelSm};
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 4px 10px;
  border-radius: ${p => p.theme.borderRadius.label};
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  transition: ${p => p.theme.transition.fast};
  white-space: nowrap;
  line-height: 1;

  &::before {
    content: '›';
    font-size: 11px;
    line-height: 1;
    color: ${p => p.theme.colors.fgDimmed};
    transition: ${p => p.theme.transition.fast};
  }

  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: ${p => p.theme.colors.hover};
  }

  &:hover:not(:disabled)::before {
    color: ${p => p.theme.colors.brand};
    transform: translateX(1px);
  }

  &:disabled {
    cursor: not-allowed;
    color: ${p => p.theme.colors.disabledFg};
    border-color: ${p => p.theme.colors.disabledBg};
  }

  ${p => kindColor[p.$kind]}

  ${p =>
    p.$kind !== 'neutral' &&
    css`
      border-color: ${labelBorder};

      &::before {
        color: inherit;
        opacity: 0.65;
      }

      &:hover:not(:disabled) {
        color: inherit;
        background: color-mix(in srgb, currentColor 8%, transparent);
        border-color: currentColor;
      }

      &:hover:not(:disabled)::before {
        color: inherit;
        opacity: 1;
        transform: translateX(1px);
      }
    `}
`;

interface RowActionProps {
  children: React.ReactNode;
  onClick?: () => void;
  kind?: RowActionKind;
  disabled?: boolean;
  className?: string;
  title?: string;
}

export function RowAction({
  children,
  onClick,
  kind = 'neutral',
  disabled = false,
  className,
  title,
}: RowActionProps) {
  return (
    <Btn type="button" $kind={kind} onClick={onClick} disabled={disabled} className={className} title={title}>
      {children}
    </Btn>
  );
}

/**
 * Boxless tertiary action (decision B4).
 *
 * For inline actions that would make a row too busy if boxed — "clear · everyone" next to a
 * filter, that sort of thing. The dotted underline is the affordance; it upgrades to solid
 * on hover so the control still confirms it is live.
 */
const TextBtn = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.labelSm};
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 4px 2px;
  border: none;
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  line-height: 1;
  text-decoration: underline dotted;
  text-decoration-color: ${p => p.theme.colors.fgSubtle};
  text-underline-offset: 4px;
  transition: ${p => p.theme.transition.fast};

  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.brand};
    text-decoration-style: solid;
    text-decoration-color: ${p => p.theme.colors.brand};
  }

  &:disabled {
    cursor: not-allowed;
    color: ${p => p.theme.colors.disabledFg};
  }
`;

interface TextActionProps {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
}

export function TextAction({ children, onClick, disabled = false, className }: TextActionProps) {
  return (
    <TextBtn type="button" onClick={onClick} disabled={disabled} className={className}>
      {children}
    </TextBtn>
  );
}
