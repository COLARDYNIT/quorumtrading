'use client';

import styled from 'styled-components';
import type { UserAction } from '@/app/lib/a2ui/types';

interface TradeButtonProps {
  label?: unknown;
  actionType?: unknown;
  payload?: unknown;
  variant?: unknown;
  onAction?: (action: UserAction) => void;
  [key: string]: unknown;
}

export function TradeButton({ label, actionType, payload, variant, onAction }: TradeButtonProps) {
  const handleClick = () => {
    if (!onAction) return;
    onAction({
      componentId: 'trade-button',
      actionType: String(actionType || 'trade'),
      payload: typeof payload === 'object' && payload !== null ? payload as Record<string, unknown> : undefined,
    });
  };

  const variantStr = String(variant || 'primary');

  return (
    <Button $variant={variantStr} onClick={handleClick} type="button">
      {String(label || 'Execute')}
    </Button>
  );
}

const Button = styled.button<{ $variant: string }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 16px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  letter-spacing: 0.5px;
  border-radius: ${p => p.theme.borderRadius.sm};
  cursor: pointer;
  transition: all 0.15s ease;
  text-transform: uppercase;

  background: ${p => {
    switch (p.$variant) {
      case 'buy': return `${p.theme.colors.action.buy}20`;
      case 'sell': return `${p.theme.colors.action.sell}20`;
      default: return `${p.theme.colors.terminalBlue}20`;
    }
  }};
  color: ${p => {
    switch (p.$variant) {
      case 'buy': return p.theme.colors.action.buy;
      case 'sell': return p.theme.colors.action.sell;
      default: return p.theme.colors.terminalBlue;
    }
  }};
  border: 1px solid ${p => {
    switch (p.$variant) {
      case 'buy': return `${p.theme.colors.action.buy}40`;
      case 'sell': return `${p.theme.colors.action.sell}40`;
      default: return `${p.theme.colors.terminalBlue}40`;
    }
  }};

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  &:active {
    transform: translateY(0);
  }
`;
