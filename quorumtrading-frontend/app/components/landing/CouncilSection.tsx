'use client';

import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { fadeInUp, pulse, glowPulse } from '../../styles/animations';
import { AGENT_COLORS } from '@/app/lib/utils/themeColors';
import { SectionTitle, SectionSubtitle } from '../shared';
import { QuorumExplainer } from './QuorumExplainer';

// Agent data
const AGENTS = [
  {
    id: 'technical',
    name: 'TECHNICAL',
    icon: '◇',
    color: AGENT_COLORS.TECHNICAL,
    metrics: [
      { label: 'RSI', value: '72.4' },
      { label: 'MACD', value: '↑' },
      { label: 'Support', value: '✓' },
    ],
    stance: 'LONG',
    confidence: 84,
  },
  {
    id: 'fundamental',
    name: 'FUNDAMENTAL',
    icon: '◆',
    color: AGENT_COLORS.FUNDAMENTAL,
    metrics: [
      { label: 'P/E', value: '18.3' },
      { label: 'Debt', value: 'Low' },
      { label: 'Growth', value: '✓' },
    ],
    stance: 'LONG',
    confidence: 71,
  },
  {
    id: 'sentiment',
    name: 'SENTIMENT',
    icon: '○',
    color: AGENT_COLORS.SENTIMENT,
    metrics: [
      { label: 'Fear Idx', value: '23' },
      { label: 'Social', value: '↑' },
      { label: 'News', value: 'Mixed' },
    ],
    stance: 'NEUTRAL',
    confidence: 52,
  },
];

// Styled Components
const Section = styled.section`
  padding: ${p => p.theme.spacing['3xl']} ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.border};

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing['2xl']} ${p => p.theme.spacing.md};
  }
`;

const Container = styled.div`
  max-width: 1100px;
  margin: 0 auto;
`;

const AgentsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: ${p => p.theme.spacing.lg};
  margin-bottom: ${p => p.theme.spacing.xl};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: ${p => p.theme.spacing.md};
  }
`;

const AgentCard = styled.div<{ $color: string; $delay: number }>`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
  animation: ${fadeInUp} 0.5s ease-out both;
  animation-delay: ${p => p.$delay}ms;
  transition: ${p => p.theme.transition.normal};

  &:hover {
    border-color: ${p => p.$color};
    box-shadow: 0 0 20px ${p => p.$color}33;
    transform: translateY(-4px);
  }
`;

const AgentHeader = styled.div<{ $color: string }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.$color};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: 600;
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const AgentIcon = styled.span`
  font-size: 18px;
`;

const MetricsList = styled.div`
  padding: ${p => p.theme.spacing.md};
`;

const MetricRow = styled.div<{ $delay: number }>`
  display: flex;
  justify-content: space-between;
  padding: ${p => p.theme.spacing.xs} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  animation: ${pulse} 3s ease-in-out infinite;
  animation-delay: ${p => p.$delay}ms;
`;

const MetricLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
`;

const MetricValue = styled.span`
  color: ${p => p.theme.colors.textPrimary};
`;

const StanceSection = styled.div<{ $color: string }>`
  padding: ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
  text-align: center;
  background: ${p => p.$color}0a;
`;

const Stance = styled.div<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: 700;
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.$color};
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const Confidence = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const ConnectorWrapper = styled.div<{ $visible: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: ${p => p.theme.spacing.lg};
  opacity: ${p => p.$visible ? 1 : 0};
  transition: opacity 0.5s ease;

  @media (max-width: 968px) {
    display: none;
  }
`;

const ConnectorLines = styled.div`
  display: flex;
  justify-content: space-between;
  width: 100%;
  max-width: 1000px;
  padding: 0 60px;
`;

const VerticalLine = styled.div<{ $delay: number }>`
  width: 2px;
  height: 30px;
  background: ${p => p.theme.colors.terminalGreen};
  opacity: 0;
  animation: ${fadeInUp} 0.4s ease-out forwards;
  animation-delay: ${p => p.$delay}ms;
`;

const HorizontalLine = styled.div`
  width: calc(100% - 120px);
  max-width: 880px;
  height: 2px;
  background: ${p => p.theme.colors.terminalGreen};
  position: relative;
`;

const CenterDropLine = styled.div`
  position: absolute;
  left: 50%;
  top: 0;
  transform: translateX(-50%);
  width: 2px;
  height: 30px;
  background: ${p => p.theme.colors.terminalGreen};
`;

const ConsensusBox = styled.div<{ $visible: boolean }>`
  max-width: 280px;
  margin: 0 auto;
  padding: ${p => p.theme.spacing.lg};
  background: ${p => p.theme.colors.surface};
  border: 2px solid ${p => p.theme.colors.terminalGreen};
  border-radius: ${p => p.theme.borderRadius.md};
  text-align: center;
  opacity: ${p => p.$visible ? 1 : 0};
  transform: translateY(${p => p.$visible ? 0 : 20}px);
  transition: all 0.5s ease;
  animation: ${p => p.$visible ? glowPulse : 'none'} 3s ease-in-out infinite;
`;

const ConsensusLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const ConsensusVerdict = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize['2xl']};
  font-weight: 700;
  color: ${p => p.theme.colors.terminalGreen};
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const ConsensusConfidence = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.textMuted};
`;

interface CouncilSectionProps {
  /** When true, renders just the agent cards + consensus demo without section wrapper and title */
  embedded?: boolean;
}

export function CouncilSection({ embedded = false }: CouncilSectionProps) {
  const [showConnectors, setShowConnectors] = useState(false);
  const [showConsensus, setShowConsensus] = useState(false);

  useEffect(() => {
    const connectorTimer = setTimeout(() => setShowConnectors(true), 800);
    const consensusTimer = setTimeout(() => setShowConsensus(true), 1500);

    return () => {
      clearTimeout(connectorTimer);
      clearTimeout(consensusTimer);
    };
  }, []);

  const councilDemo = (
    <Container>
      <AgentsGrid>
        {AGENTS.map((agent, index) => (
          <AgentCard key={agent.id} $color={agent.color} $delay={index * 100}>
            <AgentHeader $color={agent.color}>
              <AgentIcon>{agent.icon}</AgentIcon>
              {agent.name}
            </AgentHeader>
            <MetricsList>
              {agent.metrics.map((metric, mIndex) => (
                <MetricRow key={metric.label} $delay={mIndex * 500}>
                  <MetricLabel>{metric.label}:</MetricLabel>
                  <MetricValue>{metric.value}</MetricValue>
                </MetricRow>
              ))}
            </MetricsList>
            <StanceSection $color={agent.color}>
              <Stance $color={agent.color}>→ {agent.stance}</Stance>
              <Confidence>conf: {agent.confidence}%</Confidence>
            </StanceSection>
          </AgentCard>
        ))}
      </AgentsGrid>

      <ConnectorWrapper $visible={showConnectors}>
        <ConnectorLines>
          <VerticalLine $delay={0} />
          <VerticalLine $delay={100} />
          <VerticalLine $delay={200} />
        </ConnectorLines>
        <HorizontalLine>
          <CenterDropLine />
        </HorizontalLine>
      </ConnectorWrapper>

      <ConsensusBox $visible={showConsensus}>
        <ConsensusLabel>Consensus</ConsensusLabel>
        <ConsensusVerdict>BUY</ConsensusVerdict>
        <ConsensusConfidence>Confidence: 73%</ConsensusConfidence>
      </ConsensusBox>
    </Container>
  );

  if (embedded) {
    return councilDemo;
  }

  return (
    <Section>
      <Container>
        <SectionTitle $color="cyan" $centered $size="xl">The Council Decides</SectionTitle>
      </Container>

      {councilDemo}

      <Container>
        <SectionSubtitle $centered style={{ marginTop: '32px' }}>
          Three specialists. No ego. Complete reasoning transparency.
        </SectionSubtitle>

        <QuorumExplainer />
      </Container>
    </Section>
  );
}
