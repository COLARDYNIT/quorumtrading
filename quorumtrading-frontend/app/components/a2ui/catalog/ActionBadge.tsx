'use client';

import styled, { useTheme } from 'styled-components';

interface ActionBadgeProps {
  action?: unknown;
  [key: string]: unknown;
}

export function ActionBadge({ action }: ActionBadgeProps) {
  const theme = useTheme();
  const actionStr = String(action || 'UNKNOWN');
  const { buy, sell, hold, neutral } = theme.colors.action as { buy: string; sell: string; hold: string; neutral: string };
  const colorMap: Record<string, string> = { BUY: buy, SELL: sell, HOLD: hold };
  const color = colorMap[actionStr.toUpperCase()] || neutral;

  return (
    <Badge $color={color}>
      {actionStr.toUpperCase()}
    </Badge>
  );
}

const Badge = styled.span<{ $color: string }>`
  display: inline-block;
  padding: 4px 12px;
  background: ${p => p.$color}15;
  color: ${p => p.$color};
  border: 1px solid ${p => p.$color}40;
  border-radius: ${p => p.theme.borderRadius.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  letter-spacing: 0.5px;
`;
