'use client';

import styled from 'styled-components';

/**
 * The one switch — the DS v0.4.0 `switch` species.
 *
 * The round track is the single surviving round shape in the label overhaul, and it is
 * deliberate: a switch is a control, not a label, and the roundness is what makes it read
 * as throwable rather than as a status to be scanned.
 *
 * Rendered as a real `button[role=switch]` so it is keyboard-operable and announces its
 * state; the census found two hand-rolled `<span>` switches that were neither.
 */
const Track = styled.button<{ $lg: boolean }>`
  --w: ${p => (p.$lg ? '36px' : '30px')};
  --h: ${p => (p.$lg ? '20px' : '16px')};
  position: relative;
  display: inline-block;
  width: var(--w);
  height: var(--h);
  padding: 0;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: var(--h);
  cursor: pointer;
  vertical-align: middle;
  transition: ${p => p.theme.transition.fast};
  flex-shrink: 0;

  &::after {
    content: '';
    position: absolute;
    top: 1px;
    left: 1px;
    width: calc(var(--h) - 4px);
    height: calc(var(--h) - 4px);
    background: ${p => p.theme.colors.fgMuted};
    border-radius: 50%;
    transition: ${p => p.theme.transition.fast};
  }

  &[aria-checked='true'] {
    background: ${p => p.theme.colors.brandTint};
    border-color: ${p => p.theme.colors.brand};
  }

  &[aria-checked='true']::after {
    left: calc(var(--w) - var(--h) + 1px);
    background: ${p => p.theme.colors.brand};
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
`;

interface SwitchProps {
  checked: boolean;
  onChange: (next: boolean) => void;
  /** Accessible name. Required — a bare track tells a screen reader nothing. */
  label: string;
  /** 36×20 variant. */
  lg?: boolean;
  disabled?: boolean;
  className?: string;
}

export function Switch({ checked, onChange, label, lg = false, disabled = false, className }: SwitchProps) {
  return (
    <Track
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      $lg={lg}
      className={className}
      onClick={() => onChange(!checked)}
    />
  );
}
