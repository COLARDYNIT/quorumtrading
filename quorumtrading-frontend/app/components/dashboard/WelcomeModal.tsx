'use client';

import styled from 'styled-components';
import { TerminalModal } from '../terminal/TerminalModal';
import { TerminalButton } from '../terminal/TerminalButton';
import { TerminalText } from '../terminal/TerminalText';
import { useInstanceInfo } from '@/app/lib/api/hooks';

interface WelcomeModalProps {
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

const CreditBox = styled.div`
  border: 1px solid ${({ theme }) => theme.colors.terminalGreen}60;
  padding: ${({ theme }) => theme.spacing.md};
  text-align: center;
`;

const CreditAmount = styled.div`
  font-size: ${({ theme }) => theme.typography.fontSize['3xl']};
  color: ${({ theme }) => theme.colors.terminalGreen};
  font-weight: bold;
  text-shadow: 0 0 10px ${({ theme }) => theme.colors.terminalGreen}80;
`;

const CreditLabel = styled(TerminalText).attrs({ size: 'sm' })`
  color: ${({ theme }) => theme.colors.textMuted};
  margin-top: ${({ theme }) => theme.spacing.xs};
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

export function WelcomeModal({ isOpen, onClose, userName }: WelcomeModalProps) {
  const greeting = userName ? `Welcome, ${userName}` : 'Welcome to QuorumTrading';
  const { data: instance } = useInstanceInfo();

  // The hosted copy promises a $2 AI credit and points at market research and the assistant chat.
  // None of that is true on a self-hosted instance: there is no credit because the user supplies
  // their own engine, and both of those features are hosted-edition only. Greeting someone with
  // three things their install cannot do is a poor first impression, so this edition gets its own.
  if (instance?.edition === 'standalone') {
    return (
      <TerminalModal
        isOpen={isOpen}
        onClose={onClose}
        title="// WELCOME"
        width="md"
        footer={<TerminalButton onClick={onClose}>Get Started</TerminalButton>}
      >
        <Content>
          <Heading>{greeting}</Heading>

          <TerminalText size="sm" variant="muted">
            This instance is yours. Nothing is metered and nothing phones home — the AI consensus
            runs on whichever <Highlight>engine you point it at</Highlight>, and your data stays in
            your own PostgreSQL.
          </TerminalText>

          <CreditBox>
            <CreditAmount>$0.00</CreditAmount>
            <CreditLabel>No credits, no subscription — you bring the AI engine</CreditLabel>
          </CreditBox>

          <Heading>What you can do</Heading>
          <List>
            <ListItem>Backfill crypto with no API keys at all — BTC and ETH work out of the box</ListItem>
            <ListItem>Backtest strategies with multi-agent consensus and read every agent&apos;s reasoning</ListItem>
            <ListItem>Drive the whole platform from Claude Code over MCP, without touching this UI</ListItem>
            <ListItem>Trade paper or live through your own broker account when you&apos;re ready</ListItem>
          </List>

          <TerminalText size="sm" variant="muted">
            Stocks need your own market-data key. Market research and the assistant chat are
            hosted-edition features at quorumtrading.com and are switched off here.
          </TerminalText>
        </Content>
      </TerminalModal>
    );
  }

  return (
    <TerminalModal
      isOpen={isOpen}
      onClose={onClose}
      title="// WELCOME"
      width="md"
      footer={
        <TerminalButton onClick={onClose}>
          Get Started
        </TerminalButton>
      }
    >
      <Content>
        <Heading>{greeting}</Heading>

        <TerminalText size="sm" variant="muted">
          Your account is ready. We&apos;ve added a free <Highlight>AI credit</Highlight> so
          you can start exploring immediately.
        </TerminalText>

        <CreditBox>
          <CreditAmount>$2.00</CreditAmount>
          <CreditLabel>Free AI Credit</CreditLabel>
        </CreditBox>

        <Heading>What you can do</Heading>
        <List>
          <ListItem>Run AI-powered market research on any stock or crypto</ListItem>
          <ListItem>Backtest trading strategies with multi-agent consensus</ListItem>
          <ListItem>Chat with the AI assistant about markets and strategies</ListItem>
        </List>

        <TerminalText size="sm" variant="muted">
          Your credit covers AI model usage. You can always add more from the budget page.
        </TerminalText>
      </Content>
    </TerminalModal>
  );
}
