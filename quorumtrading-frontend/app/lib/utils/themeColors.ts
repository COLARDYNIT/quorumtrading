/**
 * Semantic color mappings that reference CSS custom properties.
 * These adapt automatically to dark/light mode without needing theme context.
 *
 * Usage: AGENT_COLORS['TECHNICAL'] → 'var(--qt-agent-technical)'
 * Works in: styled-components, inline styles, SVG fill, etc.
 */

export const AGENT_COLORS: Record<string, string> = {
  TECHNICAL: 'var(--qt-agent-technical)',
  FUNDAMENTAL: 'var(--qt-agent-fundamental)',
  SENTIMENT: 'var(--qt-agent-sentiment)',
  ORCHESTRATOR: 'var(--qt-agent-orchestrator)',
};

export const ACTION_COLORS: Record<string, string> = {
  BUY: 'var(--qt-action-buy)',
  SELL: 'var(--qt-action-sell)',
  HOLD: 'var(--qt-action-hold)',
  SKIP: 'var(--qt-action-neutral)',
};

export function agentColor(agent: string): string {
  return AGENT_COLORS[agent.toUpperCase()] ?? 'var(--qt-agent-technical)';
}

export function actionColor(action: string): string {
  return ACTION_COLORS[action.toUpperCase()] ?? 'var(--qt-action-neutral)';
}
