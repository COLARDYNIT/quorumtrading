'use client';

import styled from 'styled-components';
import { TerminalModal, TerminalButton, TerminalText } from '@/app/components/terminal';
import { useLiquidateAll } from '@/app/lib/api/hooks';

export interface LiquidateConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  simulationId: string;
  holdings: Array<{ symbol: string; amount: number; value: number }>;
}

const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const WarningBox = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  border: 1px solid ${({ theme }) => theme.colors.error};
  background: ${({ theme }) => theme.colors.error}10;
`;

const PositionList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
  padding: ${({ theme }) => theme.spacing.sm};
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.sm};
`;

const PositionRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: ${({ theme }) => theme.typography.fontSize.sm};
`;

const SymbolText = styled.span`
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const DetailText = styled.span`
  color: ${({ theme }) => theme.colors.textMuted};
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  justify-content: flex-end;
`;

export function LiquidateConfirmModal({
  isOpen,
  onClose,
  simulationId,
  holdings,
}: LiquidateConfirmModalProps) {
  const liquidateMutation = useLiquidateAll();

  const handleConfirm = async () => {
    try {
      await liquidateMutation.mutateAsync(simulationId);
      onClose();
    } catch {
      // Error handled by mutation's onError callback (toast)
    }
  };

  const handleClose = () => {
    if (!liquidateMutation.isPending) {
      onClose();
    }
  };

  const activePositions = holdings.filter(h => h.amount > 0);
  const totalValue = activePositions.reduce((sum, h) => sum + h.value, 0);

  const formatCurrency = (value: number) =>
    `$${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  const formatQuantity = (value: number) => {
    if (value < 1) return value.toFixed(4);
    if (value < 100) return value.toFixed(2);
    return value.toLocaleString('en-US', { maximumFractionDigits: 0 });
  };

  return (
    <TerminalModal isOpen={isOpen} onClose={handleClose} title="Liquidate All Positions" width="sm">
      <Content>
        <WarningBox>
          <TerminalText variant="error" weight="bold">
            This will sell ALL positions at market price.
          </TerminalText>
          <TerminalText variant="error" style={{ marginTop: '4px' }}>
            This action cannot be undone.
          </TerminalText>
        </WarningBox>

        {activePositions.length > 0 && (
          <PositionList>
            {activePositions.map(h => (
              <PositionRow key={h.symbol}>
                <SymbolText>{h.symbol}</SymbolText>
                <DetailText>{formatQuantity(h.amount)} &middot; {formatCurrency(h.value)}</DetailText>
              </PositionRow>
            ))}
            <PositionRow style={{ borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '6px', marginTop: '4px' }}>
              <SymbolText>Total</SymbolText>
              <SymbolText>{formatCurrency(totalValue)}</SymbolText>
            </PositionRow>
          </PositionList>
        )}

        <ButtonGroup>
          <TerminalButton
            variant="ghost"
            onClick={handleClose}
            disabled={liquidateMutation.isPending}
          >
            Cancel
          </TerminalButton>
          <TerminalButton
            variant="danger"
            onClick={handleConfirm}
            disabled={liquidateMutation.isPending}
          >
            {liquidateMutation.isPending ? 'Liquidating...' : 'Sell All at Market Price'}
          </TerminalButton>
        </ButtonGroup>
      </Content>
    </TerminalModal>
  );
}
