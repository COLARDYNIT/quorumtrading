'use client';

import { useState, useEffect } from 'react';
import styled, { keyframes } from 'styled-components';
import Link from 'next/link';
import { fadeInUp, pulse, shadows } from '../../styles/animations';
import { SectionTitle } from '../shared';
import { AGENT_COLORS, ACTION_COLORS } from '@/app/lib/utils/themeColors';

const slideInLine = keyframes`
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

type ScanEntry = {
  time: string;
  agent: string;
  agentColor: string;
  ticker: string;
  signal: string;
  signalColor: string;
};

const SCAN_FEED: ScanEntry[] = [
  { time: '09:31', agent: 'TECHNICAL', agentColor: AGENT_COLORS.TECHNICAL, ticker: 'NVDA', signal: 'RSI breakout above 70 — momentum confirmed', signalColor: AGENT_COLORS.TECHNICAL },
  { time: '09:31', agent: 'SENTIMENT', agentColor: AGENT_COLORS.SENTIMENT, ticker: 'NVDA', signal: 'Social volume +340% — bullish shift', signalColor: AGENT_COLORS.SENTIMENT },
  { time: '09:32', agent: 'FUNDAMENTAL', agentColor: AGENT_COLORS.FUNDAMENTAL, ticker: 'NVDA', signal: 'Revenue beat +23% — strong guidance', signalColor: AGENT_COLORS.FUNDAMENTAL },
  { time: '09:33', agent: 'CONSENSUS', agentColor: ACTION_COLORS.BUY, ticker: 'NVDA', signal: 'BUY — 3/3 agents agree (87%)', signalColor: ACTION_COLORS.BUY },
  { time: '10:15', agent: 'TECHNICAL', agentColor: AGENT_COLORS.TECHNICAL, ticker: 'BTC', signal: 'Price testing $68k support level', signalColor: AGENT_COLORS.TECHNICAL },
  { time: '10:15', agent: 'SENTIMENT', agentColor: AGENT_COLORS.SENTIMENT, ticker: 'BTC', signal: 'Fear & Greed index at 23 — extreme fear', signalColor: AGENT_COLORS.SENTIMENT },
  { time: '10:16', agent: 'FUNDAMENTAL', agentColor: AGENT_COLORS.FUNDAMENTAL, ticker: 'BTC', signal: 'ETF inflows steady — institutional demand', signalColor: AGENT_COLORS.FUNDAMENTAL },
  { time: '10:17', agent: 'CONSENSUS', agentColor: ACTION_COLORS.BUY, ticker: 'BTC', signal: 'BUY — 3/3 agents agree (76%)', signalColor: ACTION_COLORS.BUY },
  { time: '11:02', agent: 'TECHNICAL', agentColor: AGENT_COLORS.TECHNICAL, ticker: 'AAPL', signal: 'MACD crossover — trend reversal signal', signalColor: AGENT_COLORS.TECHNICAL },
  { time: '11:02', agent: 'SENTIMENT', agentColor: AGENT_COLORS.SENTIMENT, ticker: 'AAPL', signal: 'News neutral — no catalyst detected', signalColor: AGENT_COLORS.SENTIMENT },
  { time: '11:03', agent: 'CONSENSUS', agentColor: ACTION_COLORS.HOLD, ticker: 'AAPL', signal: 'HOLD — insufficient consensus (52%)', signalColor: ACTION_COLORS.HOLD },
];

const VISIBLE_LINES = 5;

const Section = styled.section`
  padding: ${p => p.theme.spacing['3xl']} ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing['2xl']} ${p => p.theme.spacing.md};
  }
`;

const Container = styled.div`
  max-width: 900px;
  margin: 0 auto;
  animation: ${fadeInUp} 0.6s ease-out both;
`;

const SectionSubtitle = styled.p`
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.textMuted};
  text-align: center;
  max-width: 580px;
  margin: 0 auto ${p => p.theme.spacing['2xl']};
  line-height: 1.6;

  @media (max-width: 768px) {
    font-size: ${p => p.theme.typography.fontSize.sm};
  }
`;

const Highlight = styled.span`
  color: ${p => p.theme.colors.terminalGreen};
  font-weight: 600;
`;

const ScannerWindow = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.lg};
  overflow: hidden;
  box-shadow: ${shadows.cardPremium};
`;

const ScannerTitleBar = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const LiveDot = styled.span`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${p => p.theme.colors.terminalGreen};
  animation: ${pulse} 1.5s ease-in-out infinite;
  box-shadow: 0 0 4px ${p => p.theme.colors.terminalGreen}40;
`;

const ScannerBody = styled.div`
  padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.lg};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  min-height: 180px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  @media (max-width: 768px) {
    font-size: ${p => p.theme.typography.fontSize.xs};
    padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
    min-height: 160px;
  }
`;

const ScanLine = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  align-items: baseline;
  animation: ${slideInLine} 0.3s ease-out both;
  line-height: 1.5;

  @media (max-width: 768px) {
    flex-wrap: wrap;
  }
`;

const TimeStamp = styled.span`
  color: ${p => p.theme.colors.textMuted};
  opacity: 0.5;
  flex-shrink: 0;
  min-width: 42px;
`;

const AgentTag = styled.span<{ $color: string }>`
  color: ${p => p.$color};
  font-weight: 600;
  flex-shrink: 0;
  min-width: 100px;

  @media (max-width: 768px) {
    min-width: 80px;
  }
`;

const TickerTag = styled.span`
  color: ${p => p.theme.colors.textPrimary};
  font-weight: 600;
  flex-shrink: 0;
`;

const SignalText = styled.span<{ $color: string }>`
  color: ${p => p.$color};
  opacity: 0.8;
`;

const CtaRow = styled.div`
  text-align: center;
  margin-top: ${p => p.theme.spacing.xl};
`;

const CtaLink = styled(Link)`
  display: inline-block;
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalGreen};
  text-decoration: none;
  border: 1px solid ${p => p.theme.colors.terminalGreen}40;
  border-radius: ${p => p.theme.borderRadius.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  transition: background 0.2s ease, border-color 0.2s ease;

  &:hover {
    background: ${p => p.theme.colors.terminalGreen}15;
    border-color: ${p => p.theme.colors.terminalGreen};
  }
`;

export function MarketOpportunitiesSection() {
  const [feedStart, setFeedStart] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setFeedStart(prev => {
        const next = prev + 1;
        return next + VISIBLE_LINES > SCAN_FEED.length ? 0 : next;
      });
    }, 2500);
    return () => clearInterval(interval);
  }, []);

  const visibleEntries = SCAN_FEED.slice(feedStart, feedStart + VISIBLE_LINES);

  return (
    <Section>
      <Container>
        <SectionTitle $color="cyan" $centered $size="xl">AI That Finds Opportunities For You</SectionTitle>
        <SectionSubtitle>
          <Highlight>Four specialized AI agents</Highlight> continuously scan stocks and
          crypto — analyzing price action, fundamentals, sentiment, and risk so you never
          miss a move.
        </SectionSubtitle>

        <ScannerWindow>
          <ScannerTitleBar>
            <LiveDot />
            <span>market-scanner — live feed</span>
          </ScannerTitleBar>
          <ScannerBody>
            {visibleEntries.map((entry, i) => (
              <ScanLine key={`${feedStart}-${i}`}>
                <TimeStamp>{entry.time}</TimeStamp>
                <AgentTag $color={entry.agentColor}>{entry.agent}</AgentTag>
                <TickerTag>{entry.ticker}</TickerTag>
                <SignalText $color={entry.signalColor}>{entry.signal}</SignalText>
              </ScanLine>
            ))}
          </ScannerBody>
        </ScannerWindow>

        <CtaRow>
          <CtaLink href="/research">Run your own AI market research &rarr;</CtaLink>
        </CtaRow>
      </Container>
    </Section>
  );
}
