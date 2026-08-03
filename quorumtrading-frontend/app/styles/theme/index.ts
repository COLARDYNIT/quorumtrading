import { colors, type ThemeColors } from './colors';
import { darkColors } from './colors-dark';
import { lightColors } from './colors-light';
import { typography } from './typography';
import type { ThemeTypography } from './typography';
import * as effects from './effects';
import type { ThemeMode } from '../../contexts/ThemeModeContext';

const shared = {
  typography,
  effects,

  // Spacing scale (8px base)
  spacing: {
    '2xs': '2px',
    xs: '4px',
    sm: '8px',
    md: '16px',
    lg: '24px',
    xl: '32px',
    '2xl': '48px',
    '3xl': '64px',
  },

  // Border radius
  borderRadius: {
    none: '0',
    sm: '2px',
    md: '4px',
    lg: '8px',
    /**
     * Labels (pill / tag / badge / row action) — DS v0.4.0 decision A2.
     *
     * Every small label sits on this one radius; the round-pill exception is retired
     * ("bracket-y, not bubble-y" now applies to labels too). The one round survivor is
     * the switch track, which is a control, not a label, and hardcodes its own radius.
     */
    label: '2px',
  },

  /**
   * Label tint stops — DS v0.4.0.
   *
   * `color` is the ONLY per-kind property on a label: border and fill both derive from
   * `currentColor` via `color-mix`. That replaces the six hand-rolled rgba alphas the
   * Aug 2026 census found, and it means the light theme retints by swapping hue tokens
   * alone — no per-kind fill literals to chase. Consume via `labelBorder`/`labelFill`
   * in `app/styles/labels.ts`, not by re-deriving the strings.
   */
  labelMix: {
    border: '42%',
    bg: '7%',
  },

  // Z-index layers
  zIndex: {
    dropdown: 1000,
    modal: 2000,
    tooltip: 3000,
    notification: 4000,
  },

  // Transitions
  transition: {
    fast: '150ms ease',
    normal: '250ms ease',
    slow: '350ms ease',
  },
} as const;

/** Default dark theme (backwards-compatible) */
export const terminalTheme = { ...shared, colors } as const;

/** Build a theme for the given mode */
export function buildTheme(mode: ThemeMode) {
  return { ...shared, colors: mode === 'light' ? lightColors : darkColors };
}

/** Theme type uses widened ThemeColors so both dark/light palettes satisfy it */
export interface TerminalTheme {
  colors: ThemeColors;
  typography: ThemeTypography;
  effects: typeof effects;
  spacing: typeof shared.spacing;
  borderRadius: typeof shared.borderRadius;
  labelMix: typeof shared.labelMix;
  zIndex: typeof shared.zIndex;
  transition: typeof shared.transition;
}

// Re-export for convenience
export { colors } from './colors';
export { darkColors } from './colors-dark';
export { lightColors } from './colors-light';
export { typography } from './typography';
export * from './effects';
