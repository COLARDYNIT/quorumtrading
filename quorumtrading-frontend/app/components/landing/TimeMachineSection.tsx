'use client';

import { useEffect, useState, useCallback } from 'react';
import styled from 'styled-components';
import { SectionTitle, SectionSubtitle } from '../shared';

// Historical decisions data
const HISTORICAL_DECISIONS = [
  {
    date: 'MAR 12, 2020',
    time: '14:32:07 UTC',
    event: 'COVID Crash',
    decision: 'SELL',
    asset: 'BTC',
    confidence: 94,
    reasoning: 'Technical detected unprecedented volatility. All agents aligned on capital preservation.',
    position: 10, // percentage along timeline
  },
  {
    date: 'NOV 10, 2021',
    time: '09:15:22 UTC',
    event: 'All-Time High',
    decision: 'HOLD',
    asset: 'BTC',
    confidence: 67,
    reasoning: 'Technical showed overbought. Sentiment extremely bullish. Council split on timing.',
    position: 38,
  },
  {
    date: 'NOV 08, 2022',
    time: '18:45:11 UTC',
    event: 'FTX Collapse',
    decision: 'SELL',
    asset: 'ETH',
    confidence: 89,
    reasoning: 'Fundamental flagged contagion risk. All agents triggered emergency exit protocol.',
    position: 58,
  },
  {
    date: 'JAN 10, 2024',
    time: '16:00:03 UTC',
    event: 'ETF Approval',
    decision: 'BUY',
    asset: 'BTC',
    confidence: 82,
    reasoning: 'Fundamental detected institutional catalyst. Technical confirmed breakout pattern.',
    position: 82,
  },
];

// Styled Components
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
`;

const TimelineContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${p => p.theme.spacing.xl};
  margin-bottom: ${p => p.theme.spacing.xl};
`;

const DecisionCard = styled.div<{ $visible: boolean; $decision: string }>`
  width: 320px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p =>
    p.$decision === 'BUY' ? p.theme.colors.terminalGreen :
    p.$decision === 'SELL' ? p.theme.colors.terminalPink :
    p.theme.colors.terminalAmber
  };
  border-radius: ${p => p.theme.borderRadius.md};
  padding: ${p => p.theme.spacing.lg};
  box-shadow: var(--qt-shadow-card-premium);
  opacity: ${p => p.$visible ? 1 : 0};
  transform: translateY(${p => p.$visible ? 0 : -10}px);
  transition: opacity 0.3s ease, transform 0.3s ease;

  @media (max-width: 768px) {
    width: 280px;
    padding: ${p => p.theme.spacing.md};
  }
`;

const TimelineWrapper = styled.div`
  width: 100%;
`;

const CardDate = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.terminalBlue};
  font-weight: 600;
  margin-bottom: ${p => p.theme.spacing['2xs']};
`;

const CardTime = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  margin-bottom: ${p => p.theme.spacing.md};
`;

const CardEvent = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const CardDecision = styled.div<{ $decision: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xl};
  font-weight: 700;
  color: ${p =>
    p.$decision === 'BUY' ? p.theme.colors.terminalGreen :
    p.$decision === 'SELL' ? p.theme.colors.terminalPink :
    p.theme.colors.terminalAmber
  };
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const CardAsset = styled.span`
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.textMuted};
  margin-left: ${p => p.theme.spacing.sm};
`;

const CardConfidence = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  margin-bottom: ${p => p.theme.spacing.md};
`;

const CardReasoning = styled.div`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textLight};
  line-height: 1.5;
  font-style: italic;
  border-top: 1px solid ${p => p.theme.colors.border};
  padding-top: ${p => p.theme.spacing.md};
`;


const Timeline = styled.div`
  position: relative;
  height: 4px;
  background: ${p => p.theme.colors.border};
  border-radius: 2px;
  margin: 0 ${p => p.theme.spacing.xl};
`;

const TimelineProgress = styled.div<{ $progress: number }>`
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  width: ${p => p.$progress}%;
  background: ${p => p.theme.colors.terminalGreen};
  border-radius: 2px;
  transition: width 0.3s ease;
`;

const Playhead = styled.div<{ $position: number }>`
  position: absolute;
  left: ${p => p.$position}%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 16px;
  height: 16px;
  background: ${p => p.theme.colors.terminalGreen};
  border: 2px solid ${p => p.theme.colors.background};
  border-radius: 50%;
  cursor: pointer;
  transition: left 0.3s ease;
  box-shadow: 0 0 10px ${p => p.theme.colors.terminalGreen}88;
  z-index: 5;

  &:hover {
    transform: translate(-50%, -50%) scale(1.2);
  }
`;

const TimelineLabels = styled.div`
  display: flex;
  justify-content: space-between;
  margin-top: ${p => p.theme.spacing.md};
  padding: 0 ${p => p.theme.spacing.xl};
`;

const TimelineLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
`;

const EventMarkers = styled.div`
  position: absolute;
  top: -12px;
  left: 0;
  right: 0;
  height: 24px;
`;

const EventMarker = styled.button<{ $position: number; $active: boolean }>`
  position: absolute;
  left: ${p => p.$position}%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: ${p => p.$active ? 16 : 12}px;
  height: ${p => p.$active ? 16 : 12}px;
  background: ${p => p.$active ? p.theme.colors.terminalGreen : p.theme.colors.surface};
  border: 2px solid ${p => p.$active ? p.theme.colors.terminalGreen : p.theme.colors.textMuted};
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
  box-shadow: ${p => p.$active ? `0 0 12px ${p.theme.colors.terminalGreen}` : 'none'};

  &:hover {
    transform: translate(-50%, -50%) scale(1.3);
    background: ${p => p.theme.colors.terminalGreen};
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 12px ${p => p.theme.colors.terminalGreen};
  }

  &:focus {
    outline: none;
    box-shadow: 0 0 0 3px ${p => p.theme.colors.terminalGreen}44;
  }
`;

const EventLabels = styled.div`
  display: flex;
  justify-content: space-between;
  margin-top: ${p => p.theme.spacing.sm};
  padding: 0 ${p => p.theme.spacing.md};
`;

const EventLabel = styled.button<{ $active: boolean }>`
  background: none;
  border: none;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$active ? p.theme.colors.terminalGreen : p.theme.colors.textMuted};
  cursor: pointer;
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  border-radius: ${p => p.theme.borderRadius.sm};
  transition: all 0.2s ease;
  white-space: nowrap;

  &:hover {
    color: ${p => p.theme.colors.terminalGreen};
    background: ${p => p.theme.colors.terminalGreen}11;
  }
`;

const ControlHint = styled.p`
  text-align: center;
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  margin-top: ${p => p.theme.spacing.xl};
  font-style: italic;
`;

interface TimeMachineSectionProps {
  /** When true, renders just the timeline demo without section wrapper and title */
  embedded?: boolean;
}

export function TimeMachineSection({ embedded = false }: TimeMachineSectionProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(true);
  const [cardVisible, setCardVisible] = useState(true);

  const currentDecision = HISTORICAL_DECISIONS[currentIndex];

  const goToDecision = useCallback((index: number) => {
    if (index === currentIndex) return;
    setCardVisible(false);
    setTimeout(() => {
      setCurrentIndex(index);
      setCardVisible(true);
    }, 200);
  }, [currentIndex]);

  // Auto-advance through decisions
  useEffect(() => {
    if (!isPlaying) return;

    const timer = setInterval(() => {
      setCardVisible(false);
      setTimeout(() => {
        setCurrentIndex(prev => (prev + 1) % HISTORICAL_DECISIONS.length);
        setCardVisible(true);
      }, 200);
    }, 4000);

    return () => clearInterval(timer);
  }, [isPlaying]);

  const timelineDemo = (
    <Container>
      <TimelineContainer
        onMouseEnter={() => setIsPlaying(false)}
        onMouseLeave={() => setIsPlaying(true)}
      >
        <DecisionCard $visible={cardVisible} $decision={currentDecision.decision}>
          <CardDate>{currentDecision.date}</CardDate>
          <CardTime>{currentDecision.time}</CardTime>
          <CardEvent>{currentDecision.event}</CardEvent>
          <CardDecision $decision={currentDecision.decision}>
            {currentDecision.decision}
            <CardAsset>{currentDecision.asset}</CardAsset>
          </CardDecision>
          <CardConfidence>Confidence: {currentDecision.confidence}%</CardConfidence>
          <CardReasoning>&ldquo;{currentDecision.reasoning}&rdquo;</CardReasoning>
        </DecisionCard>

        <TimelineWrapper>
          <Timeline>
            <TimelineProgress $progress={currentDecision.position} />
            <EventMarkers>
              {HISTORICAL_DECISIONS.map((decision, index) => (
                <EventMarker
                  key={decision.date}
                  $position={decision.position}
                  $active={index === currentIndex}
                  onClick={() => goToDecision(index)}
                  title={decision.event}
                />
              ))}
            </EventMarkers>
            <Playhead $position={currentDecision.position} />
          </Timeline>

          <EventLabels>
            {HISTORICAL_DECISIONS.map((decision, index) => (
              <EventLabel
                key={decision.date}
                $active={index === currentIndex}
                onClick={() => goToDecision(index)}
              >
                {decision.event}
              </EventLabel>
            ))}
          </EventLabels>

          <TimelineLabels>
            <TimelineLabel>2020</TimelineLabel>
            <TimelineLabel>2021</TimelineLabel>
            <TimelineLabel>2022</TimelineLabel>
            <TimelineLabel>2023</TimelineLabel>
            <TimelineLabel>2024</TimelineLabel>
            <TimelineLabel>2025</TimelineLabel>
          </TimelineLabels>
        </TimelineWrapper>
      </TimelineContainer>

      <ControlHint>
        Hover to pause. Click markers to jump to key moments.
      </ControlHint>
    </Container>
  );

  if (embedded) {
    return timelineDemo;
  }

  return (
    <Section>
      <SectionTitle $color="cyan" $centered $size="xl">Replay History. Refine Strategy.</SectionTitle>
      <SectionSubtitle $centered style={{ marginBottom: '48px' }}>
        Scrub through years of market data in minutes. See every decision. Understand every outcome.
      </SectionSubtitle>
      {timelineDemo}
    </Section>
  );
}
