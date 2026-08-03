'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { fadeInUp } from '@/app/styles/animations';
import { AGENT_COLORS } from '@/app/lib/utils/themeColors';

const Section = styled.section`
  padding: 64px 24px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const Container = styled.div`
  max-width: 1120px;
  margin: 0 auto;
`;

const Header = styled.div`
  text-align: center;
  margin-bottom: 32px;
  animation: ${fadeInUp} 0.5s ease-out;
`;

const Title = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.info};
  letter-spacing: -0.012em;
  margin: 0 0 8px;
`;

const Subtitle = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  max-width: 900px;
  margin: 0 auto;

  @media (max-width: 768px) { grid-template-columns: 1fr; }
`;

const ExploreCard = styled(Link)<{ $delay: number }>`
  display: block;
  text-align: center;
  padding: 20px 18px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  text-decoration: none;
  transition: ${p => p.theme.transition.fast};
  animation: ${fadeInUp} 0.5s ease-out backwards;
  animation-delay: ${p => p.$delay}ms;

  &:hover {
    text-decoration: none;
    border-color: ${p => p.theme.colors.fgMuted};
    background: ${p => p.theme.colors.surfaceElevated};
  }
`;

const CardIcon = styled.div<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 24px;
  color: ${p => p.$color};
  margin-bottom: 10px;
`;

const CardTitle = styled.h3`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgStrong};
  margin: 0 0 6px;
`;

const CardDescription = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.5;
  margin: 0 0 10px;
`;

const LearnMore = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.info};
`;

const cards = [
  {
    icon: '⬡',
    color: AGENT_COLORS.SENTIMENT,
    title: 'AI Consensus',
    description: 'Three specialists analyze independently, then vote. No single point of failure.',
    href: '/faq/consensus',
  },
  {
    icon: '~',
    color: AGENT_COLORS.FUNDAMENTAL,
    title: 'Market Research',
    description: 'Describe an idea or thesis — AI finds tickers and suggests strategies.',
    href: '/faq/market-research',
  },
  {
    icon: '~',
    color: AGENT_COLORS.SENTIMENT,
    title: 'Replay History',
    description: 'Test strategies against years of market data. See every decision the AI made.',
    href: '/faq/backtesting',
  },
  {
    icon: '>_',
    color: AGENT_COLORS.TECHNICAL,
    title: 'Terminal Integration',
    description: 'Trade from Claude Code, Cursor, or any MCP-compatible AI agent.',
    href: '/faq/mcp-integration',
  },
];

export function ExploreMoreSection() {
  return (
    <Section>
      <Container>
        <Header>
          <Title>Explore More</Title>
          <Subtitle>Dive deeper into what QuorumTrading can do</Subtitle>
        </Header>
        <Grid>
          {cards.map((card, index) => (
            <ExploreCard key={card.title} href={card.href} $delay={index * 100}>
              <CardIcon $color={card.color}>{card.icon}</CardIcon>
              <CardTitle>{card.title}</CardTitle>
              <CardDescription>{card.description}</CardDescription>
              <LearnMore>Learn more &rarr;</LearnMore>
            </ExploreCard>
          ))}
        </Grid>
      </Container>
    </Section>
  );
}
