'use client';

import styled, { css } from 'styled-components';
import {
  dotBase,
  dotPaused,
  dotPending,
  isAnimatedKind,
  labelColor,
  labelCompact,
  pillBase,
  pulse,
  type LabelKind,
} from '@/app/styles/labels';

/**
 * Process state. One of the DS v0.4.0 label species.
 *
 * Kinds are `color`-only — border and fill mix out of `currentColor`, so adding a kind
 * means adding a hue, never a new set of rgba literals.
 */
export type StatusPillKind = LabelKind;

interface StatusPillProps {
  kind: StatusPillKind;
  children: React.ReactNode;
  className?: string;
  /**
   * Force the dot still on a kind that would otherwise animate.
   *
   * The dot is a claim that work is in flight. A live simulation that is between bars, or
   * a row rendered into a static report, should not keep making that claim.
   */
  still?: boolean;
  /**
   * Solid fill — the ONLY solid label in the system, and only for real money.
   *
   * Reserved deliberately: if solid meant anything else, a paper run and a funded live run
   * would look alike at a glance, which is the one mistake this pill exists to prevent.
   */
  money?: boolean;
  /** Paper-toned live — live shape, muted dot, so paper never borrows the real-money look. */
  paperToned?: boolean;
  /** 10px variant for dense ledgers. */
  compact?: boolean;
}

const Pill = styled.span<{
  $kind: StatusPillKind;
  $still: boolean;
  $money: boolean;
  $paperToned: boolean;
  $compact: boolean;
}>`
  ${pillBase}
  ${dotBase}
  color: ${p => labelColor[p.$kind](p.theme.colors)};

  ${p =>
    p.$compact &&
    css`
      ${labelCompact}
      & > .dot {
        width: 5px;
        height: 5px;
      }
    `}

  ${p => p.$kind === 'pending' && dotPending}
  ${p => (p.$kind === 'paused' || p.$kind === 'pausedExt') && dotPaused}

  ${p =>
    isAnimatedKind(p.$kind) &&
    !p.$still &&
    css`
      & > .dot {
        animation: ${pulse} 1.4s ease-in-out infinite;
      }
    `}

  ${p =>
    p.$kind === 'live' &&
    !p.$paperToned &&
    !p.$money &&
    css`
      & > .dot {
        box-shadow: 0 0 6px currentColor;
      }
    `}

  ${p =>
    p.$paperToned &&
    css`
      & > .dot {
        animation: none;
        box-shadow: none;
        opacity: 0.85;
      }
    `}

  ${p =>
    p.$money &&
    css`
      color: ${p.theme.colors.fgInverse};
      border-color: ${p.theme.colors.brand};
      background: ${p.theme.colors.brand};
      & > .dot {
        background: ${p.theme.colors.fgInverse};
        box-shadow: none;
      }
    `}
`;

export function StatusPill({
  kind,
  children,
  className,
  still = false,
  money = false,
  paperToned = false,
  compact = false,
}: StatusPillProps) {
  return (
    <Pill
      $kind={kind}
      $still={still}
      $money={money}
      $paperToned={paperToned}
      $compact={compact}
      className={className}
    >
      <span className="dot" />
      {children}
    </Pill>
  );
}
