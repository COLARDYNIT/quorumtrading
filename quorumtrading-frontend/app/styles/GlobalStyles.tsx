'use client';

import { createGlobalStyle } from 'styled-components';

function hexToRgb(hex: string): string {
  const h = hex.replace('#', '');
  return `${parseInt(h.slice(0, 2), 16)}, ${parseInt(h.slice(2, 4), 16)}, ${parseInt(h.slice(4, 6), 16)}`;
}

export const GlobalStyles = createGlobalStyle`
  /* Theme-driven CSS custom properties for animations/shadows */
  :root {
    /* Primary glow (for glowPulse keyframe) */
    --qt-glow-10: rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.1);
    --qt-glow-20: rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.2);
    --qt-glow-30: rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.3);
    --qt-glow-50: rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.5);

    /* Box shadows */
    --qt-shadow-card: 0 4px 12px rgba(0, 0, 0, ${p => p.theme.colors.background === '#0a0e14' ? '0.2' : '0.08'});
    --qt-shadow-card-premium: 0 4px 24px rgba(0, 0, 0, ${p => p.theme.colors.background === '#0a0e14' ? '0.4' : '0.12'}), 0 0 0 1px rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.1);
    --qt-shadow-card-hover: 0 8px 32px rgba(0, 0, 0, ${p => p.theme.colors.background === '#0a0e14' ? '0.4' : '0.12'}), 0 0 0 1px rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.2);

    /* Text shadows */
    --qt-text-shadow-primary: 0 0 2px rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.4);
    --qt-text-shadow-cyan: 0 0 2px rgba(${p => hexToRgb(p.theme.colors.terminalBlue)}, 0.4);
    --qt-text-shadow-amber: 0 0 2px rgba(${p => hexToRgb(p.theme.colors.terminalAmber)}, 0.4);

    /* Effects */
    --qt-text-glow: 0 0 1px rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.3);
    --qt-heading-glow: 0 0 2px rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.4), 0 0 4px rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.1);
    --qt-border-glow-shadow: 0 0 0 1px rgba(${p => hexToRgb(p.theme.colors.primary)}, 0.2);

    /* Agent colors */
    --qt-agent-technical: ${p => p.theme.colors.technical};
    --qt-agent-fundamental: ${p => p.theme.colors.fundamental};
    --qt-agent-sentiment: ${p => p.theme.colors.sentiment};
    --qt-agent-orchestrator: ${p => p.theme.colors.terminalMint};

    /* Action colors */
    --qt-action-buy: ${p => p.theme.colors.action.buy};
    --qt-action-sell: ${p => p.theme.colors.action.sell};
    --qt-action-hold: ${p => p.theme.colors.action.hold};
    --qt-action-neutral: ${p => p.theme.colors.action.neutral};

    /* Semantic vars (also set by inline script, kept in sync here) */
    --qt-bg: ${p => p.theme.colors.background};
    --qt-fg: ${p => p.theme.colors.foreground};
    --qt-info: ${p => p.theme.colors.info};
    --qt-err: ${p => p.theme.colors.error};
    --qt-muted: ${p => p.theme.colors.textMuted};
    --qt-surface: ${p => p.theme.colors.surface};

    /* v2 design system tokens — chrome / brand / focus */
    --qt-brand: ${p => p.theme.colors.brand};
    --qt-brand-hover: ${p => p.theme.colors.brandHover};
    --qt-brand-pressed: ${p => p.theme.colors.brandPressed};
    --qt-brand-tint: ${p => p.theme.colors.brandTint};
    --qt-fg-strong: ${p => p.theme.colors.fgStrong};
    --qt-fg-muted: ${p => p.theme.colors.fgMuted};
    --qt-fg-dimmed: ${p => p.theme.colors.fgDimmed};
    --qt-fg-subtle: ${p => p.theme.colors.fgSubtle};
    --qt-fg-inverse: ${p => p.theme.colors.fgInverse};
    --qt-border: ${p => p.theme.colors.border};
    --qt-border-subtle: ${p => p.theme.colors.borderSubtle};
    --qt-surface-elevated: ${p => p.theme.colors.surfaceElevated};
    --qt-focus-ring: ${p => p.theme.colors.focusRing};
    --qt-disabled-bg: ${p => p.theme.colors.disabledBg};
    --qt-disabled-fg: ${p => p.theme.colors.disabledFg};
    --qt-action-buy: ${p => p.theme.colors.action.buy};
    --qt-action-sell: ${p => p.theme.colors.action.sell};
    --qt-action-hold: ${p => p.theme.colors.action.hold};
    --qt-action-neutral: ${p => p.theme.colors.action.neutral};
    --qt-positive: ${p => p.theme.colors.positive};
    --qt-negative: ${p => p.theme.colors.negative};
    --qt-amber: ${p => p.theme.colors.terminalAmber};
    --qt-pink: ${p => p.theme.colors.terminalPink};
    --qt-mint: ${p => p.theme.colors.terminalMint};
    --qt-font-sans: ${p => p.theme.typography.fontFamily.sans};
    --qt-font-mono: ${p => p.theme.typography.fontFamily.mono};

    /* v0.4.0 label tint stops. The color property is the only per-kind property on a
       label; border and fill mix out of currentColor at these two ratios.
       See app/styles/labels.ts. */
    --qt-label-border-mix: ${p => p.theme.labelMix.border};
    --qt-label-bg-mix: ${p => p.theme.labelMix.bg};
  }

  /* CSS Reset & Base Styles */
  * {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
  }

  html {
    /* 100% = 16px browser default. Lets user zoom + a11y prefs scale rem-based
       values naturally; body sizing comes from the theme.fontSize.* tokens. */
    font-size: 100%;
  }

  body {
    /* v2: body uses sans; mono reserved for data via .mono / code / pre.
       Explicit font-size pegs body to --fs-base regardless of html (which is
       100% so rem-based values track user-zoom). */
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: ${p => p.theme.typography.fontSize.base};
    background-color: ${p => p.theme.colors.background};
    color: ${p => p.theme.colors.foreground};
    line-height: ${p => p.theme.typography.lineHeight.normal};
    letter-spacing: 0;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    overflow-x: hidden;
    transition: background-color 0.2s ease, color 0.2s ease;
  }

  /* Numerics / IDs / timestamps / code — explicit opt-in to mono. */
  .mono, .tabular {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
  }

  /* Headings — neutral by default. Brand color is NOT applied to chrome. */
  h1, h2, h3, h4, h5, h6 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    letter-spacing: -0.005em;
    color: ${p => p.theme.colors.fgStrong};
    text-transform: none;
  }

  /* Focus — globally visible. Cyan ring so it doesn't compete with brand. */
  :focus { outline: none; }
  :focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: 2px;
    border-radius: 2px;
  }

  /* Links - subtle hover effect */
  a {
    color: ${p => p.theme.colors.info};
    text-decoration: none;
    transition: ${p => p.theme.transition.fast};

    &:hover {
      color: ${p => p.theme.colors.foreground};
    }
  }

  /* Code blocks */
  code, pre {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    background-color: ${p => p.theme.colors.hover};
    border: 1px solid ${p => p.theme.colors.border};
    border-radius: ${p => p.theme.borderRadius.sm};
  }

  /* Scrollbar styling */
  ::-webkit-scrollbar {
    width: 12px;
    height: 12px;
  }

  ::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.border};
  }

  ::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.secondary};
    border: 1px solid ${p => p.theme.colors.primary};

    &:hover {
      background: ${p => p.theme.colors.primary};
    }
  }

  /* Selection */
  ::selection {
    background-color: ${p => p.theme.colors.active};
    color: ${p => p.theme.colors.foreground};
  }
`;
