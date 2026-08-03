import type { ThemeColors } from './colors';

export const darkColors: ThemeColors = {
  // Core Terminal Colors (80s Terminal Green Palette)
  background: '#0a0e14',
  foreground: '#e6e6e6',

  // 80s Terminal Color Palette
  terminalGreen: '#00ff41',
  terminalBlue: '#00d9ff',
  terminalAmber: '#ffb000',
  terminalPink: '#ff3366',
  terminalMint: '#06ffa5',

  // Semantic Colors
  success: '#06ffa5',
  warning: '#ffb000',
  error: '#ff3366',
  info: '#00d9ff',

  // UI Element Colors
  primary: '#00ff41',
  secondary: '#007a1f',
  tertiary: '#003d0f',

  // Data Category Colors (for agent types)
  technical: '#00ff41',
  fundamental: '#ffb000',
  sentiment: '#00d9ff',

  // Chart Colors
  chart: {
    line1: '#00ff41',
    line2: '#00d9ff',
    line3: '#ffb000',
    line4: '#ff3366',
    line5: '#06ffa5',
    line6: '#ff6b9d',
    line7: '#ffd700',
    line8: '#8a2be2',
    grid: '#1a2332',
    axis: '#4a5568',
    tooltip: {
      background: 'rgba(10, 14, 20, 0.98)',
      border: '#00d9ff',
      text: '#ffffff',
      label: '#00d9ff',
    },
  },

  // Medal/Rank Colors
  medals: {
    gold: { primary: '#ffd700', secondary: '#ffa500', text: '#000000' },
    silver: { primary: '#c0c0c0', secondary: '#808080', text: '#000000' },
    bronze: { primary: '#cd7f32', secondary: '#8b4513', text: '#ffffff' },
  },

  // Coverage/Progress Colors
  coverage: {
    full: '#00ff41',
    high: '#ffd700',
    medium: '#ff8c00',
    low: '#ff006e',
    none: '#1a1a1a',
  },

  // Score Colors
  score: {
    high: { text: '#00ff00', background: 'rgba(0, 255, 0, 0.2)', border: '#00ff00' },
    medium: { text: '#ffaa00', background: 'rgba(255, 170, 0, 0.2)', border: '#ffaa00' },
    low: { text: '#ff4444', background: 'rgba(255, 68, 68, 0.2)', border: '#ff4444' },
  },

  // Stock/Crypto Category Colors
  crypto: '#00cc33',
  stocks: '#4ecdc4',

  // Additional UI Colors (v2 — surfaces re-stacked, borders visible)
  border: '#2a3344',
  borderHighlight: '#00d9ff',
  borderSubtle: '#1a2030',
  hover: 'rgba(0, 255, 65, 0.10)',
  active: 'rgba(0, 255, 65, 0.20)',
  disabled: '#4a5562',

  // v2 brand + chrome additions
  brand: '#00ff41',
  brandHover: '#33ff66',
  brandPressed: '#00cc34',
  brandTint: 'rgba(0, 255, 65, 0.08)',
  fgStrong: '#ffffff',
  fgMuted: '#8a98a4',
  fgDimmed: '#5a6a76',
  fgSubtle: '#4a5562',
  fgInverse: '#0a0e14',
  disabledBg: '#1a2030',
  disabledFg: '#4a5562',
  focusRing: '#00d9ff',

  // Text variations (neutralized — chrome is greys, not green)
  text: '#e6e6e6',
  textPrimary: '#e6e6e6',
  textSecondary: '#cccccc',
  textMuted: '#8a98a4',
  textDimmed: '#5a6a76',
  textSubtle: '#4a5562',
  textInverse: '#0a0e14',
  textWhite: '#ffffff',
  textLight: '#cccccc',

  // Surface colors (re-stacked for visible elevation)
  surface: '#141a23',
  surfaceElevated: '#1e2632',
  surfaceDark: '#1a1a1a',

  // Trading Action Colors — buy is mint to separate from --brand
  action: {
    buy: '#06ffa5',
    sell: '#ff3366',
    hold: '#ffb000',
    neutral: '#8a98a4',
  },

  // Research Agent Type Colors
  agents: {
    newsSentiment: '#00d4ff',
    strategyDesigner: '#00ff41',
    viabilityAssessor: '#ffd700',
    technicalAnalyst: '#00ff41',
    fundamentalAnalyst: '#ffb000',
    sentimentAnalyst: '#00d9ff',
  },

  // Semantic color aliases
  danger: '#ff3366',
  positive: '#06ffa5',
  negative: '#ff3366',
};
