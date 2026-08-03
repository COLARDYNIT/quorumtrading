/**
 * Default colors (dark theme). Import darkColors/lightColors for mode-specific palettes.
 *
 * Token semantics (post v2 design system refresh):
 *   - `brand` is reserved for identity (logo, primary CTA, active sidebar item) — 3 uses per screen, max.
 *   - Chrome (headings, panel titles, body) uses neutral `fg*` greys; never the brand.
 *   - `action.buy` is now mint (was bright green) so it visually separates from `brand`.
 *   - Hue tokens (terminalBlue/Amber/Pink/Mint) are data-only — chart series, status pills, agent identity.
 *   - Surfaces are re-stacked with ~6% lightness gaps for visible elevation.
 *   - Old aliases (terminalGreen, primary, text, textPrimary, surface, etc.) are retained for back-compat.
 */
export const colors = {
  // Core Terminal Colors (80s Terminal Green Palette)
  background: '#0a0e14',        // Deep black with blue tint
  foreground: '#e6e6e6',        // Primary body text (neutral — was matrix green)

  // 80s Terminal Color Palette
  terminalGreen: '#00ff41',     // Primary terminal green
  terminalBlue: '#00d9ff',      // Terminal cyan/blue (for headers, titles)
  terminalAmber: '#ffb000',     // Amber/orange (for warnings, accents)
  terminalPink: '#ff3366',      // Hot pink (for errors, negative values)
  terminalMint: '#06ffa5',      // Mint green (for positive values, success)

  // Semantic Colors
  success: '#06ffa5',           // Mint green for positive
  warning: '#ffb000',           // Amber/orange
  error: '#ff3366',             // Hot pink
  info: '#00d9ff',              // Cyan blue

  // UI Element Colors
  primary: '#00ff41',           // Matrix green
  secondary: '#007a1f',         // Darker green
  tertiary: '#003d0f',          // Darkest green

  // Data Category Colors (for agent types)
  technical: '#00ff41',         // Green - Technical Analysis
  fundamental: '#ffb000',       // Amber - Fundamental Analysis
  sentiment: '#00d9ff',         // Cyan - Sentiment Analysis

  // Chart Colors (for multiple simulations - diverse 80s palette)
  chart: {
    line1: '#00ff41',           // Matrix green
    line2: '#00d9ff',           // Cyan blue
    line3: '#ffb000',           // Amber
    line4: '#ff3366',           // Hot pink
    line5: '#06ffa5',           // Mint green
    line6: '#ff6b9d',           // Coral pink
    line7: '#ffd700',           // Gold
    line8: '#8a2be2',           // Purple
    grid: '#1a2332',            // Dark blue-grey for grid lines
    axis: '#4a5568',            // Medium grey for axes
    tooltip: {
      background: 'rgba(10, 14, 20, 0.98)',
      border: '#00d9ff',
      text: '#ffffff',
      label: '#00d9ff',
    },
  },

  // Medal/Rank Colors (for benchmarks)
  medals: {
    gold: {
      primary: '#ffd700',
      secondary: '#ffa500',
      text: '#000000',
    },
    silver: {
      primary: '#c0c0c0',
      secondary: '#808080',
      text: '#000000',
    },
    bronze: {
      primary: '#cd7f32',
      secondary: '#8b4513',
      text: '#ffffff',
    },
  },

  // Coverage/Progress Colors (for data availability calendar)
  coverage: {
    full: '#00ff41',            // 100% coverage - Matrix green
    high: '#ffd700',            // 75-99% - Gold
    medium: '#ff8c00',          // 50-74% - Dark orange
    low: '#ff006e',             // 0-49% - Hot pink
    none: '#1a1a1a',            // No data - Dark grey
  },

  // Score Colors (for viability assessments, etc.)
  score: {
    high: {
      text: '#00ff00',
      background: 'rgba(0, 255, 0, 0.2)',
      border: '#00ff00',
    },
    medium: {
      text: '#ffaa00',
      background: 'rgba(255, 170, 0, 0.2)',
      border: '#ffaa00',
    },
    low: {
      text: '#ff4444',
      background: 'rgba(255, 68, 68, 0.2)',
      border: '#ff4444',
    },
  },

  // Stock/Crypto Category Colors
  crypto: '#00cc33',            // Bright green for crypto
  stocks: '#4ecdc4',            // Teal for stocks

  // Additional UI Colors (re-stacked surfaces — ~6% lightness gaps)
  border: '#2a3344',            // Visible card border (was #1a2332)
  borderHighlight: '#00d9ff',   // Cyan border for highlights
  borderSubtle: '#1a2030',      // Hairline dividers (was #333333)
  hover: 'rgba(0, 255, 65, 0.10)',
  active: 'rgba(0, 255, 65, 0.20)',
  disabled: '#4a5562',

  // === v2 design system additions ===
  // BRAND — identity + primary actions only (3 uses per screen max).
  brand: '#00ff41',
  brandHover: '#33ff66',
  brandPressed: '#00cc34',
  brandTint: 'rgba(0, 255, 65, 0.08)',

  // Foreground stack (neutral chrome)
  fgStrong: '#ffffff',          // hero values, page titles
  fgMuted: '#8a98a4',           // secondary text, metadata
  fgDimmed: '#5a6a76',          // timestamps, captions
  fgSubtle: '#4a5562',          // least important
  fgInverse: '#0a0e14',         // text on brand fills

  // Disabled component states
  disabledBg: '#1a2030',
  disabledFg: '#4a5562',

  // Focus ring — cyan, not brand, so focus is unambiguous
  focusRing: '#00d9ff',

  // Text variations (all neutralized — chrome is greys, not green)
  text: '#e6e6e6',              // Alias for textPrimary (used in styled components)
  textPrimary: '#e6e6e6',       // Primary body text
  textSecondary: '#cccccc',     // Secondary body (was cyan)
  textMuted: '#8a98a4',         // Metadata
  textDimmed: '#5a6a76',        // Timestamps
  textSubtle: '#4a5562',        // Least important
  textInverse: '#0a0e14',       // Dark background
  textWhite: '#ffffff',         // Pure white for readability
  textLight: '#cccccc',         // Light grey for details

  // Surface colors — re-stacked for ~6% lightness gaps (visible elevation)
  surface: '#141a23',           // Card surface (was #0f1419 — +6% L)
  surfaceElevated: '#1e2632',   // Modal / hover card (was #14191f — +6% L)
  surfaceDark: '#1a1a1a',       // Dark surface for error displays, modals

  // Trading Action Colors — buy desaturated to mint so it separates from --brand
  action: {
    buy: '#06ffa5',             // Mint for buy (was bright #00ff41 — collided with brand)
    sell: '#ff3366',            // Hot pink for sell actions
    hold: '#ffb000',            // Amber for hold actions
    neutral: '#8a98a4',         // Grey for unknown/neutral
  },

  // Research Agent Type Colors
  agents: {
    newsSentiment: '#00d4ff',   // Cyan for news sentiment analyst
    strategyDesigner: '#00ff41', // Green for strategy designer
    viabilityAssessor: '#ffd700', // Gold for viability assessor
    technicalAnalyst: '#00ff41', // Green for technical
    fundamentalAnalyst: '#ffb000', // Amber for fundamental
    sentimentAnalyst: '#00d9ff', // Cyan for sentiment
  },

  // Semantic color aliases
  danger: '#ff3366',            // Alias for error
  positive: '#06ffa5',          // Mint green for gains
  negative: '#ff3366',          // Hot pink for losses
} as const;

// Widen literal types so dark/light palettes can use different values
type DeepWritable<T> = {
  -readonly [K in keyof T]: T[K] extends string
    ? string
    : T[K] extends object
      ? DeepWritable<T[K]>
      : T[K];
};

export type ThemeColors = DeepWritable<typeof colors>;

export { darkColors } from './colors-dark';
export { lightColors } from './colors-light';
