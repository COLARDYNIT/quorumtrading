'use client';

import { css, keyframes } from 'styled-components';
import type { ThemeColors } from './theme/colors';

/**
 * LABELS — unified small-label family (DS v0.4.0 · Aug 2026 overhaul).
 *
 * The repo-side mirror of the design system's `LABELS` section. The DS ships plain CSS
 * classes (`.pill` / `.tag` / `.badge` / `.switch`); this app is styled-components, so the
 * same semantics are expressed as shared `css` fragments the v2 atoms compose. The rules
 * below are the contract — keep them true, not the class names.
 *
 * One species per job:
 *   pill   — state of a process. Leading dot; the dot animates ONLY while work is
 *            happening (live / simulating / waiting / syncing).
 *   tag    — categorical metadata. No dot. Hue via `kind`; `id` renders identifiers
 *            verbatim (never case-transformed).
 *   badge  — counts.
 *   switch — the one switch (round track: it is a control, not a label).
 *
 * One voice: uppercase mono, `fontSize.label` 11px / `labelSm` 10px.
 * One radius: `borderRadius.label` (2px) — A2, the round-pill exception is retired.
 * Two tint stops: `color` is the ONLY per-kind property; border and fill derive from
 * `currentColor` via color-mix. Solid fill is reserved for a live pill carrying real money.
 */

/**
 * Border and fill, both derived from `currentColor`.
 *
 * This is the whole point of the v0.4.0 tint model: a kind sets `color` and nothing else,
 * so there is no per-kind rgba literal to fall out of sync with the light theme. The
 * percentages come from `theme.labelMix`, published as custom properties by GlobalStyles.
 */
export const labelBorder = 'color-mix(in srgb, currentColor var(--qt-label-border-mix), transparent)';
export const labelFill = 'color-mix(in srgb, currentColor var(--qt-label-bg-mix), transparent)';

/** The one label animation. Opacity only — a moving dot must not reflow a dense row. */
export const pulse = keyframes`
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
`;

/**
 * Every label kind in the system. `color` is the only property a kind may set.
 *
 * `waiting` / `syncing` exist so a stalled-looking run reads as work-in-progress rather
 * than as a finished one; see the motion axis below.
 */
export type LabelKind =
  | 'live'
  | 'paper'
  | 'restricted'
  | 'done'
  | 'completed'
  | 'neutral'
  | 'error'
  | 'failed'
  | 'simulating'
  | 'waiting'
  | 'syncing'
  | 'dca'
  | 'pending'
  | 'paused'
  | 'pausedExt';

/**
 * Kind → hue. Reading a colour out of the theme (never a literal) is what lets the light
 * palette retint the whole family by swapping tokens.
 */
export const labelColor: Record<LabelKind, (c: ThemeColors) => string> = {
  live: c => c.brand,
  paper: c => c.terminalAmber,
  /** Partial audience — "on for N people". Amber so a partly-on flag cannot read as fully on. */
  restricted: c => c.terminalAmber,
  done: c => c.fgMuted,
  completed: c => c.fgMuted,
  neutral: c => c.fgMuted,
  error: c => c.terminalPink,
  failed: c => c.terminalPink,
  simulating: c => c.info,
  waiting: c => c.info,
  syncing: c => c.info,
  dca: c => c.info,
  pending: c => c.fgMuted,
  paused: c => c.fgMuted,
  pausedExt: c => c.terminalAmber,
};

/**
 * Tag hues. The DS ships these as `.hue-*` utilities; here they are a prop.
 *
 * A tag is categorical, so its hue carries no status meaning — it groups. `dim` is the
 * default and the right answer whenever the grouping is not worth a colour.
 */
export type LabelHue = 'dim' | 'brand' | 'amber' | 'pink' | 'cyan' | 'mint';

export const hueColor: Record<LabelHue, (c: ThemeColors) => string> = {
  dim: c => c.fgMuted,
  brand: c => c.brand,
  amber: c => c.terminalAmber,
  pink: c => c.terminalPink,
  cyan: c => c.info,
  mint: c => c.terminalMint,
};

/**
 * Motion axis — the dot animates only while the system is actually doing something.
 *
 * A pulsing dot is a claim that work is in flight. Completed, paused and failed states
 * are still by definition, so they opt out; `still` on a call site forces the same for a
 * live-kinded pill that is not currently progressing.
 */
const ANIMATED_KINDS: ReadonlySet<LabelKind> = new Set<LabelKind>([
  'live',
  'simulating',
  'waiting',
  'syncing',
]);

export function isAnimatedKind(kind: LabelKind): boolean {
  return ANIMATED_KINDS.has(kind);
}

/** Shared base for pill and tag — the single source of the label voice. */
export const labelBase = css`
  display: inline-flex;
  align-items: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  line-height: 1;
  white-space: nowrap;
  border-radius: ${p => p.theme.borderRadius.label};
  color: ${p => p.theme.colors.fgMuted};
  border: 1px solid ${labelBorder};
  background: ${labelFill};
`;

/** Process state. Carries a dot. */
export const pillBase = css`
  ${labelBase}
  gap: 6px;
  padding: 3px 10px;
  font-size: ${p => p.theme.typography.fontSize.label};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  letter-spacing: 0.08em;
`;

/** Categorical metadata. No dot. */
export const tagBase = css`
  ${labelBase}
  gap: 5px;
  padding: 2px 8px;
  font-size: ${p => p.theme.typography.fontSize.label};
  font-weight: ${p => p.theme.typography.fontWeight.normal};
  letter-spacing: 0.06em;
  line-height: 1.3;
`;

/**
 * Identifier content — emails, symbols, key prefixes.
 *
 * Never case-transformed: uppercasing an identifier makes it wrong to copy, and a user
 * cannot tell whether the casing they see is the casing that is stored.
 */
export const tagIdentifier = css`
  text-transform: none;
  letter-spacing: 0.04em;
`;

/** Compact — dense ledgers only. */
export const labelCompact = css`
  font-size: ${p => p.theme.typography.fontSize.labelSm};
  padding: 2px 7px;
  gap: 4px;
`;

/** The leading dot, plus the kind-specific dot treatments. */
export const dotBase = css`
  & > .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    flex-shrink: 0;
  }

  /*
   * Compat opt-out, carried over from the DS's plain-CSS form: a call site that puts the
   * still class on the dot itself gets the same result as the still prop. Kept so markup
   * written against the CSS classes keeps working; prefer the prop in new code.
   */
  & > .dot.still {
    animation: none;
    box-shadow: none;
  }
`;

/** Pending — hollow ring: queued, nothing happening yet. */
export const dotPending = css`
  & > .dot {
    background: transparent;
    box-shadow: inset 0 0 0 1px currentColor;
  }
`;

/**
 * Paused — a pause-bar glyph rather than a disc.
 *
 * "Paused" is the dominant signal: a paused row never shows a Live pill, so the dot has to
 * carry the difference at 6px.
 */
export const dotPaused = css`
  & > .dot {
    position: relative;
    width: 7px;
    height: 7px;
    border-radius: 1px;
    background: transparent;
    box-shadow:
      inset 1.5px 0 0 currentColor,
      inset -1.5px 0 0 currentColor;
  }
`;
