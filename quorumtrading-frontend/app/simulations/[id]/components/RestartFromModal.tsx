'use client';

import styled, { keyframes } from 'styled-components';
import { TerminalText, TerminalButton } from '@/app/components/terminal';
import { useRestartFromPreview, useRestartFrom } from '@/app/lib/api/hooks';

const fadeInUp = keyframes`
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
`;

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContent = styled.div`
  background: ${p => p.theme.colors.background};
  border: 2px solid ${p => p.theme.colors.terminalAmber};
  border-radius: ${p => p.theme.borderRadius.md};
  max-width: 500px;
  width: calc(100% - 32px);
  max-height: 80vh;
  overflow-y: auto;
  animation: ${fadeInUp} 0.3s ease-out;
`;

const Header = styled.div`
  padding: ${p => p.theme.spacing.lg};
  border-bottom: 1px solid ${p => p.theme.colors.terminalAmber}44;
  background: ${p => p.theme.colors.terminalAmber}11;
`;

const Title = styled.h2`
  margin: 0;
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.terminalAmber};
  font-weight: bold;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const Body = styled.div`
  padding: ${p => p.theme.spacing.lg};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const InfoRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid ${p => p.theme.colors.primary}22;
`;

const Footer = styled.div`
  padding: ${p => p.theme.spacing.lg};
  border-top: 1px solid ${p => p.theme.colors.terminalAmber}44;
  display: flex;
  justify-content: flex-end;
  gap: ${p => p.theme.spacing.md};
`;

const Note = styled.div`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.terminalAmber}22;
  border-left: 3px solid ${p => p.theme.colors.terminalAmber};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

interface Props {
  simulationId: string;
  consensusDecisionId: number;
  decisionTimestamp: string;
  onClose: () => void;
}

export function RestartFromModal({ simulationId, consensusDecisionId, decisionTimestamp, onClose }: Props) {
  const { data: preview, isLoading } = useRestartFromPreview(simulationId, consensusDecisionId);
  const restartMutation = useRestartFrom();

  const handleRestart = () => {
    restartMutation.mutate(
      { simulationId, consensusDecisionId },
      { onSuccess: () => onClose() }
    );
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'America/New_York',
    }) + ' ET';
  };

  const formatMoney = (amount: number) => {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount);
  };

  return (
    <Overlay onClick={onClose}>
      <ModalContent onClick={e => e.stopPropagation()}>
        <Header>
          <Title>Restart From Decision</Title>
        </Header>

        <Body>
          <TerminalText size="sm">
            Restart simulation from <strong>{formatDate(decisionTimestamp)}</strong>.
            All data at and after this point will be removed.
          </TerminalText>

          {isLoading ? (
            <TerminalText size="sm" variant="muted">Loading preview...</TerminalText>
          ) : preview ? (
            <>
              <div>
                <InfoRow>
                  <TerminalText size="xs" variant="muted">Consensus decisions to remove</TerminalText>
                  <TerminalText size="xs" weight="bold">{preview.consensusDecisionsToDelete}</TerminalText>
                </InfoRow>
                <InfoRow>
                  <TerminalText size="xs" variant="muted">LLM decisions to remove</TerminalText>
                  <TerminalText size="xs" weight="bold">{preview.llmDecisionsToDelete}</TerminalText>
                </InfoRow>
                <InfoRow>
                  <TerminalText size="xs" variant="muted">Portfolio snapshots to remove</TerminalText>
                  <TerminalText size="xs" weight="bold">{preview.portfolioSnapshotsToDelete}</TerminalText>
                </InfoRow>
                <InfoRow>
                  <TerminalText size="xs" variant="muted">LLM cost preserved</TerminalText>
                  <TerminalText size="xs" weight="bold">{formatMoney(preview.archivedCostUsd)}</TerminalText>
                </InfoRow>
                <InfoRow>
                  <TerminalText size="xs" variant="muted">Portfolio at restart</TerminalText>
                  <TerminalText size="xs" weight="bold">
                    {formatMoney(preview.portfolioValueAtRestart)} ({formatMoney(preview.portfolioCashAtRestart)} cash)
                  </TerminalText>
                </InfoRow>
              </div>

              <Note>
                Simulation will be paused after restart. Resume when ready.
                LLM costs from removed decisions are preserved in simulation history.
              </Note>
            </>
          ) : null}
        </Body>

        <Footer>
          <TerminalButton
            size="sm"
            variant="ghost"
            onClick={onClose}
            disabled={restartMutation.isPending}
          >
            Cancel
          </TerminalButton>
          <TerminalButton
            size="sm"
            variant="primary"
            onClick={handleRestart}
            disabled={isLoading || restartMutation.isPending}
            style={{
              background: 'rgba(255, 191, 0, 0.15)',
              borderColor: 'rgba(255, 191, 0, 0.6)',
              color: '#ffbf00',
            }}
          >
            {restartMutation.isPending ? 'Restarting...' : 'Restart'}
          </TerminalButton>
        </Footer>
      </ModalContent>
    </Overlay>
  );
}
