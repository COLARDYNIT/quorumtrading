export const typography = {
  /**
   * Font Families
   *
   * Post v2: sans is the default for body/chrome/prose. Mono is reserved for
   * data, tickers, prices, IDs, timestamps, code — anything tabular or
   * copy-paste-worthy. Use `fontFamily.sans` (alias of `display`) for headings.
   */
  fontFamily: {
    sans: "'IBM Plex Sans', system-ui, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif",
    mono: "'IBM Plex Mono', 'SF Mono', 'Courier New', Courier, monospace",
    display: "'IBM Plex Sans', system-ui, -apple-system, sans-serif",
  },

  /**
   * Font Sizes — v2 scale, bumped one notch (May 2026). Base sits at 15px, the
   * sweet spot between dense-tool convention (Bloomberg/Linear at 14px) and
   * general-consumer (Material/iOS at 16–17px). The bump was the response to
   * users zooming to 125% — the system sat too tight at 14px.
   *
   * Use `xs` only for micro-eyebrows; the floor for chrome labels is `sm`.
   * Mono numeric ledger cells stay tight at hardcoded 12.5–13.5px on call sites —
   * the token scale is for chrome (labels, body, headings).
   */
  fontSize: {
    xs: '12px',   // micro-labels, eyebrows
    sm: '13px',   // buttons, chips, form labels
    base: '15px', // body, inputs, p, h5
    lg: '17px',   // h4
    xl: '19px',   // h3, page titles
    '2xl': '24px',// h2
    '3xl': '30px',// h1
    '4xl': '44px',// hero

    /**
     * Label sizes — DS v0.4.0. Separate from the chrome scale above because labels are
     * mono uppercase and read a size smaller than sans at the same px. The census found
     * eight sizes between 9px and 12px doing this one job; these two replace them all.
     */
    label: '11px',   // pills, tags, badges
    labelSm: '10px', // compact ledger variant, row actions
  },

  // Line Heights
  lineHeight: {
    tight: '1.2',
    normal: '1.4',
    relaxed: '1.6',
  },

  // Letter Spacing (important for terminal aesthetic)
  letterSpacing: {
    tight: '-0.5px',
    normal: '0.5px',
    wide: '1px',
  },

  // Font Weights
  fontWeight: {
    normal: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },
} as const;

export type ThemeTypography = typeof typography;
