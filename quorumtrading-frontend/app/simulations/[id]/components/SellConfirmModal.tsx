'use client';

import styled from 'styled-components';
import { TerminalModal, TerminalButton, TerminalText } from '@/app/components/terminal';
import { useSellPosition } from '@/app/lib/api/hooks';

export interface SellConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  simulationId: string;
  symbol: string;
  quantity: number;
  value: number;
}

const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const PositionDetail = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
  padding: ${({ theme }) => theme.spacing.sm};
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.sm};
`;

const DetailRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: ${({ theme }) => theme.typography.fontSize.sm};
`;

const LabelText = styled.span`
  color: ${({ theme }) => theme.colors.textMuted};
`;

const ValueText = styled.span`
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  justify-content: flex-end;
`;

export function SellConfirmModal({
  isOpen,
  onClose,
  simulationId,
  symbol,
  quantity,
  value,
}: SellConfirmModalProps) {
  const sellMutation = useSellPosition();

  const handleConfirm = async () => {
    try {
      await sellMutation.mutateAsync({ simulationId, symbol });
      onClose();
    } catch {
      // Error handled by mutation's onError callback
    }
  };

  const handleClose = () => {
    if (!sellMutation.isPending) {
      onClose();
    }
  };

  const formatCurrency = (v: number) =>
    `$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  const formatQuantity = (v: number) => {
    if (v < 1) return v.toFixed(4);
    if (v < 100) return v.toFixed(2);
    return v.toLocaleString('en-US', { maximumFractionDigits: 0 });
  };

  return (
    <TerminalModal isOpen={isOpen} onClose={handleClose} title={`Sell ${symbol}`} width="sm">
      <Content>
        <TerminalText variant="muted">
          This will sell your entire position at market price.
        </TerminalText>

        <PositionDetail>
          <DetailRow>
            <LabelText>Symbol</LabelText>
            <ValueText>{symbol}</ValueText>
          </DetailRow>
          <DetailRow>
            <LabelText>Quantity</LabelText>
            <ValueText>{formatQuantity(quantity)}</ValueText>
          </DetailRow>
          <DetailRow>
            <LabelText>Est. Value</LabelText>
            <ValueText>{formatCurrency(value)}</ValueText>
          </DetailRow>
        </PositionDetail>

        <ButtonGroup>
          <TerminalButton
            variant="ghost"
            onClick={handleClose}
            disabled={sellMutation.isPending}
          >
            Cancel
          </TerminalButton>
          <TerminalButton
            variant="danger"
            onClick={handleConfirm}
            disabled={sellMutation.isPending}
          >
            {sellMutation.isPending ? 'Selling...' : 'Sell at Market Price'}
          </TerminalButton>
        </ButtonGroup>
      </Content>
    </TerminalModal>
  );
}
