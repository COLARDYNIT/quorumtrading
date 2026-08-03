/**
 * Color palette for benchmark chart lines
 * Colors cycle if more than 7 simulations
 */
export const CHART_COLORS = [
  '#4AA3FF',  // Blue
  '#9D4EDD',  // Purple
  '#06FFA5',  // Green
  '#FF6B35',  // Orange
  '#FF006E',  // Red
  '#00F5FF',  // Cyan
  '#FFD60A',  // Yellow
] as const;

/**
 * Assigns a unique color to a simulation based on its index
 * @param index - Zero-based index of the simulation
 * @returns Hex color string
 */
export function getSimulationColor(index: number): string {
  return CHART_COLORS[index % CHART_COLORS.length];
}

/**
 * Overlay colors for buy-and-hold reference lines
 */
export const OVERLAY_COLORS: Record<string, string> = {
  SPY: '#8B8BFF',    // Muted lavender
  QQQ: '#FF8B8B',    // Muted salmon
  VTI: '#8BFFB5',    // Muted mint
  BTC: '#F7931A',    // Bitcoin orange
  ETH: '#627EEA',    // Ethereum blue
};

/**
 * Rotating palette for custom/unknown overlay tickers
 */
const CUSTOM_OVERLAY_COLORS = [
  '#FFD93D',  // Gold
  '#FF6B9D',  // Pink
  '#45B7D1',  // Teal
  '#98D8C8',  // Sage
  '#DDA0DD',  // Plum
  '#F4A460',  // Sandy brown
] as const;

export function getOverlayColor(symbol: string): string {
  if (OVERLAY_COLORS[symbol]) return OVERLAY_COLORS[symbol];
  // Deterministic color from rotating palette based on symbol hash
  let hash = 0;
  for (let i = 0; i < symbol.length; i++) {
    hash = symbol.charCodeAt(i) + ((hash << 5) - hash);
  }
  return CUSTOM_OVERLAY_COLORS[Math.abs(hash) % CUSTOM_OVERLAY_COLORS.length];
}
