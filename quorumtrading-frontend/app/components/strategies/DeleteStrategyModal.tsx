'use client';

import styled from 'styled-components';
import { TerminalModal, TerminalButton, TerminalText } from '../terminal';
import { useDeleteStrategy } from '@/app/lib/api/hooks';

export interface DeleteStrategyModalProps {
  isOpen: boolean;
  onClose: () => void;
  onDeleted?: () => void;
  strategyId: string;
  strategyName: string;
  simulationCount: number;
  hasLiveSims: boolean;
}

const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  justify-content: flex-end;
`;

const WarningBox = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  border: 1px solid ${({ theme }) => theme.colors.error};
  background: ${({ theme }) => theme.colors.error}10;
`;

const BlockedBox = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  border: 2px solid ${({ theme }) => theme.colors.warning};
  background: ${({ theme }) => theme.colors.warning}10;
`;

export function DeleteStrategyModal({
  isOpen,
  onClose,
  onDeleted,
  strategyId,
  strategyName,
  simulationCount,
  hasLiveSims,
}: DeleteStrategyModalProps) {
  const deleteMutation = useDeleteStrategy();

  const handleDelete = async () => {
    try {
      await deleteMutation.mutateAsync(strategyId);
      onDeleted ? onDeleted() : onClose();
    } catch {
      // Error handled by mutation's onError callback
    }
  };

  const handleClose = () => {
    if (!deleteMutation.isPending) {
      onClose();
    }
  };

  if (hasLiveSims) {
    return (
      <TerminalModal isOpen={isOpen} onClose={onClose} title="Cannot Delete Strategy" width="sm">
        <Content>
          <BlockedBox>
            <TerminalText variant="warning" weight="bold">
              Active LIVE Simulations
            </TerminalText>
            <TerminalText variant="warning" style={{ marginTop: '8px' }}>
              <strong>{strategyName}</strong> has running live or paper simulations.
              Stop and liquidate them before deleting.
            </TerminalText>
          </BlockedBox>
          <ButtonGroup>
            <TerminalButton variant="ghost" onClick={onClose}>
              Close
            </TerminalButton>
          </ButtonGroup>
        </Content>
      </TerminalModal>
    );
  }

  return (
    <TerminalModal isOpen={isOpen} onClose={handleClose} title="Delete Strategy" width="sm">
      <Content>
        <WarningBox>
          <TerminalText variant="error" weight="bold">
            Warning: This action cannot be undone!
          </TerminalText>
        </WarningBox>

        <TerminalText>
          Delete <strong>{strategyName}</strong> and its {simulationCount} simulation{simulationCount !== 1 ? 's' : ''}?
        </TerminalText>

        <ButtonGroup>
          <TerminalButton
            variant="ghost"
            onClick={handleClose}
            disabled={deleteMutation.isPending}
          >
            Cancel
          </TerminalButton>
          <TerminalButton
            variant="danger"
            onClick={handleDelete}
            disabled={deleteMutation.isPending}
          >
            {deleteMutation.isPending ? 'Deleting...' : 'Delete Strategy'}
          </TerminalButton>
        </ButtonGroup>
      </Content>
    </TerminalModal>
  );
}
