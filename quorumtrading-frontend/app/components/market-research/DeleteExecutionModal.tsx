'use client';

import styled from 'styled-components';
import { TerminalModal, TerminalButton, TerminalText } from '../terminal';

export interface DeleteExecutionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  isDeleting: boolean;
  executionDate?: string;
  hasLinkedStrategy?: boolean;
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

const InfoBox = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.borderRadius.sm};
`;

export function DeleteExecutionModal({
  isOpen,
  onClose,
  onConfirm,
  isDeleting,
  executionDate,
  hasLinkedStrategy,
}: DeleteExecutionModalProps) {
  const handleClose = () => {
    if (!isDeleting) {
      onClose();
    }
  };

  return (
    <TerminalModal
      isOpen={isOpen}
      onClose={handleClose}
      title="Delete Execution"
      width="sm"
    >
      <Content>
        <TerminalText>
          Delete execution{executionDate && <> from <strong>{executionDate}</strong></>}?
        </TerminalText>

        {hasLinkedStrategy && (
          <InfoBox>
            <TerminalText variant="muted" size="sm">
              Note: The strategy created from this execution will not be deleted.
            </TerminalText>
          </InfoBox>
        )}

        <ButtonGroup>
          <TerminalButton
            variant="ghost"
            onClick={handleClose}
            disabled={isDeleting}
          >
            Cancel
          </TerminalButton>
          <TerminalButton
            variant="danger"
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? 'Deleting...' : 'Delete'}
          </TerminalButton>
        </ButtonGroup>
      </Content>
    </TerminalModal>
  );
}
