import type { ThemeColors } from './colors';

export const lightColors: ThemeColors = {
  // Core Colors — adapted for light backgrounds
  background: '#f4f4f0',
  foreground: '#065a1f',

  // Terminal Color Palette — deeper tones for contrast on light
  terminalGreen: '#065a1f',
  terminalBlue: '#0077aa',
  terminalAmber: '#b37800',
  terminalPink: '#cc1144',
  terminalMint: '#046b46',

  // Semantic Colors
  success: '#046b46',
  warning: '#b37800',
  error: '#cc1144',
  info: '#0077aa',

  // UI Element Colors
  primary: '#065a1f',
  secondary: '#b8e6c8',
  tertiary: '#d4f0dc',

  // Data Category Colors (for agent types)
  technical: '#065a1f',
  fundamental: '#b37800',
  sentiment: '#0077aa',

  // Chart Colors — saturated enough to read on white
  chart: {
    line1: '#065a1f',
    line2: '#0077aa',
    line3: '#b37800',
    line4: '#cc1144',
    line5: '#046b46',
    line6: '#d4467a',
    line7: '#b8860b',
    line8: '#6a1bb2',
    grid: '#e0e0dc',
    axis: '#999999',
    tooltip: {
      background: 'rgba(255, 255, 255, 0.98)',
      border: '#0077aa',
      text: '#1a1a1a',
      label: '#0077aa',
    },
  },

  // Medal/Rank Colors — same across modes (always on card surfaces)
  medals: {
    gold: { primary: '#d4a800', secondary: '#c08a00', text: '#000000' },
    silver: { primary: '#a0a0a0', secondary: '#707070', text: '#000000' },
    bronze: { primary: '#b06a28', secondary: '#7a4510', text: '#ffffff' },
  },

  // Coverage/Progress Colors
  coverage: {
    full: '#065a1f',
    high: '#d4a800',
    medium: '#cc7000',
    low: '#cc1144',
    none: '#e8e8e4',
  },

  // Score Colors
  score: {
    high: { text: '#065a1f', background: 'rgba(6, 90, 31, 0.1)', border: '#065a1f' },
    medium: { text: '#b37800', background: 'rgba(179, 120, 0, 0.1)', border: '#b37800' },
    low: { text: '#cc1144', background: 'rgba(204, 17, 68, 0.1)', border: '#cc1144' },
  },

  // Stock/Crypto Category Colors
  crypto: '#0a8a30',
  stocks: '#2a9d8f',

  // Additional UI Colors
  border: '#d4d4d0',
  borderHighlight: '#0077aa',
  borderSubtle: '#e0e0dc',
  hover: 'rgba(6, 90, 31, 0.08)',
  active: 'rgba(6, 90, 31, 0.15)',
  disabled: '#c8ccc9',

  // v2 brand + chrome additions
  brand: '#065a1f',
  brandHover: '#0a7a2a',
  brandPressed: '#044415',
  brandTint: 'rgba(6, 90, 31, 0.08)',
  fgStrong: '#0a1410',
  fgMuted: '#6b7c72',
  fgDimmed: '#8a98a4',
  fgSubtle: '#aaaaaa',
  fgInverse: '#ffffff',
  disabledBg: '#eaeae6',
  disabledFg: '#aaaaaa',
  focusRing: '#0077aa',

  // Text variations
  text: '#1a2e1f',
  textPrimary: '#1a2e1f',
  textSecondary: '#0077aa',
  textMuted: '#6b7c72',
  textDimmed: '#888888',
  textSubtle: '#aaaaaa',
  textInverse: '#ffffff',
  textWhite: '#1a1a1a',
  textLight: '#555555',

  // Surface colors
  surface: '#ffffff',
  surfaceElevated: '#f9f9f6',
  surfaceDark: '#eaeae6',

  // Trading Action Colors
  action: {
    buy: '#065a1f',
    sell: '#cc1144',
    hold: '#b37800',
    neutral: '#888888',
  },

  // Research Agent Type Colors
  agents: {
    newsSentiment: '#0077aa',
    strategyDesigner: '#065a1f',
    viabilityAssessor: '#b8860b',
    technicalAnalyst: '#065a1f',
    fundamentalAnalyst: '#b37800',
    sentimentAnalyst: '#0077aa',
  },

  // Semantic color aliases
  danger: '#cc1144',
  positive: '#046b46',
  negative: '#cc1144',
};
