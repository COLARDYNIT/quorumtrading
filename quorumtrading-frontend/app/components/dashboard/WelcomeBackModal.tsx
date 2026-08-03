'use client';

import styled from 'styled-components';
import { TerminalModal } from '../terminal/TerminalModal';
import { TerminalButton } from '../terminal/TerminalButton';
import { TerminalText } from '../terminal/TerminalText';

interface WelcomeBackModalProps {
  isOpen: boolean;
  onClose: () => void;
  userName?: string;
}

const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const Heading = styled(TerminalText).attrs({ size: 'base', weight: 'bold' })`
  color: ${({ theme }) => theme.colors.terminalBlue};
`;

const Highlight = styled.span`
  color: ${({ theme }) => theme.colors.terminalGreen};
  font-weight: bold;
`;

const List = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const ListItem = styled.li`
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.typography.fontSize.sm};
  &::before {
    content: '> ';
    color: ${({ theme }) => theme.colors.terminalGreen};
  }
`;

export function WelcomeBackModal({ isOpen, onClose, userName }: WelcomeBackModalProps) {
  const greeting = userName ? `Welcome back, ${userName}` : 'Welcome back';

  return (
    <TerminalModal
      isOpen={isOpen}
      onClose={onClose}
      title="// WELCOME BACK"
      width="md"
      footer={
        <TerminalButton onClick={onClose}>
          Continue
        </TerminalButton>
      }
    >
      <Content>
        <Heading>{greeting}</Heading>

        <TerminalText size="sm" variant="muted">
          Your account has been set up again. To start using AI features, you&apos;ll
          need to <Highlight>top up your budget</Highlight> from the settings page.
        </TerminalText>

        <Heading>Pick up where you left off</Heading>
        <List>
          <ListItem>Create or import trading strategies</ListItem>
          <ListItem>Run AI-powered market research</ListItem>
          <ListItem>Backtest strategies with multi-agent consensus</ListItem>
        </List>

        <TerminalText size="sm" variant="muted">
          Head to Settings &gt; Budget to add AI credits and get started.
        </TerminalText>
      </Content>
    </TerminalModal>
  );
}
