'use client';

import { useEffect, useState } from 'react';
import styled, { keyframes, css } from 'styled-components';
import { AGENT_COLORS, ACTION_COLORS } from '@/app/lib/utils/themeColors';
import { textShadows } from '@/app/styles/animations';

// Animation keyframes
const fadeIn = keyframes`
  from { opacity: 0; }
  to { opacity: 1; }
`;

const fadeInUp = keyframes`
  from {
    opacity: 0;
    transform: translateY(15px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

const pulseGlow = keyframes`
  0%, 100% {
    box-shadow: 0 0 15px var(--qt-glow-30);
  }
  50% {
    box-shadow: 0 0 25px var(--qt-glow-50);
  }
`;

const fillBar = keyframes`
  from { width: 0%; }
  to { width: 75%; }
`;

// Styled Components
const Section = styled.section`
  padding: ${p => p.theme.spacing['3xl']} ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing['2xl']} ${p => p.theme.spacing.md};
  }
`;

const SectionInner = styled.div`
  max-width: 900px;
  margin: 0 auto;
`;

const Container = styled.div`
  margin-top: ${p => p.theme.spacing['3xl']};
  padding-top: ${p => p.theme.spacing['2xl']};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

const Header = styled.div`
  text-align: center;
  margin-bottom: ${p => p.theme.spacing['2xl']};
`;

const Title = styled.h3`
  font-size: ${p => p.theme.typography.fontSize['2xl']};
  color: ${p => p.theme.colors.terminalGreen};
  margin-bottom: ${p => p.theme.spacing.md};
  text-shadow: ${textShadows.primary};

  @media (max-width: 768px) {
    font-size: ${p => p.theme.typography.fontSize.xl};
  }
`;

const Etymology = styled.p`
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.textMuted};
  max-width: 700px;
  margin: 0 auto;
  line-height: 1.6;

  @media (max-width: 768px) {
    font-size: ${p => p.theme.typography.fontSize.base};
    padding: 0 ${p => p.theme.spacing.md};
  }
`;

const Highlight = styled.span`
  color: ${p => p.theme.colors.terminalGreen};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const VisualContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${p => p.theme.spacing.xl};
  margin-top: ${p => p.theme.spacing.xl};
`;

const VotingSimulation = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 500px;
  padding: ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
`;

const VoteIndicators = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.lg};
  margin-bottom: ${p => p.theme.spacing.lg};

  @media (max-width: 480px) {
    gap: ${p => p.theme.spacing.md};
  }
`;

const VoteIndicator = styled.div<{ $color: string; $voted: boolean; $delay: number }>`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  opacity: ${p => p.$voted ? 1 : 0.3};
  transition: all 0.4s ease;
  transition-delay: ${p => p.$delay}ms;
`;

const VoteCircle = styled.div<{ $color: string; $voted: boolean; $yes?: boolean }>`
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 2px solid ${p => p.$voted ? p.$color : p.theme.colors.border};
  background: ${p => p.$voted ? `${p.$color}20` : 'transparent'};
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: ${p => p.$voted ? (p.$yes ? 'var(--qt-action-buy)' : 'var(--qt-action-sell)') : 'inherit'};
  transition: all 0.4s ease;

  ${p => p.$voted && css`
    box-shadow: 0 0 15px ${p.$color}40;
  `}

  @media (max-width: 480px) {
    width: 40px;
    height: 40px;
    font-size: 16px;
  }
`;

const VoteLabel = styled.span<{ $color: string; $voted: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9px;
  color: ${p => p.$voted ? p.$color : p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.5px;
  transition: color 0.4s ease;
`;

const ThresholdSection = styled.div`
  width: 100%;
  margin-bottom: ${p => p.theme.spacing.lg};
`;

const ThresholdLabel = styled.div`
  display: flex;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const ThresholdBar = styled.div`
  position: relative;
  width: 100%;
  height: 8px;
  background: ${p => p.theme.colors.background};
  border-radius: 4px;
  overflow: hidden;
`;

const ThresholdFill = styled.div<{ $percentage: number; $animate: boolean }>`
  height: 100%;
  background: ${p => p.$percentage >= 66.7
    ? p.theme.colors.terminalGreen
    : p.theme.colors.terminalAmber};
  border-radius: 4px;
  width: ${p => p.$percentage}%;
  transition: all 0.5s ease;

  ${p => p.$percentage >= 66.7 && css`
    box-shadow: 0 0 10px ${p.theme.colors.terminalGreen};
  `}
`;

const ThresholdMarker = styled.div`
  position: absolute;
  left: 66.7%;
  top: -4px;
  bottom: -4px;
  width: 2px;
  background: ${p => p.theme.colors.terminalGreen};

  &::after {
    content: 'QUORUM';
    position: absolute;
    top: -18px;
    left: 50%;
    transform: translateX(-50%);
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 8px;
    color: ${p => p.theme.colors.terminalGreen};
    white-space: nowrap;
  }
`;

const StatusBox = styled.div<{ $reached: boolean }>`
  padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.xl};
  background: ${p => p.$reached
    ? `${p.theme.colors.terminalGreen}15`
    : `${p.theme.colors.terminalAmber}15`};
  border: 1px solid ${p => p.$reached
    ? p.theme.colors.terminalGreen
    : p.theme.colors.terminalAmber};
  border-radius: ${p => p.theme.borderRadius.md};
  text-align: center;
  transition: all 0.5s ease;

  ${p => p.$reached && css`
    animation: ${pulseGlow} 2s ease-in-out infinite;
  `}
`;

const StatusText = styled.div<{ $reached: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$reached
    ? p.theme.colors.terminalGreen
    : p.theme.colors.terminalAmber};
  letter-spacing: 0.1em;
`;

const StatusSubtext = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  margin-top: ${p => p.theme.spacing.xs};
`;

const BenefitsList = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: ${p => p.theme.spacing.lg};
  margin-top: ${p => p.theme.spacing.xl};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: ${p => p.theme.spacing.md};
  }
`;

const BenefitCard = styled.div<{ $delay: number }>`
  text-align: center;
  padding: ${p => p.theme.spacing.lg};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  animation: ${fadeInUp} 0.5s ease-out both;
  animation-delay: ${p => p.$delay}ms;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${p => p.theme.colors.terminalGreen};
    transform: translateY(-2px);
  }
`;

const BenefitTitle = styled.h4`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalGreen};
  margin-bottom: ${p => p.theme.spacing.xs};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const BenefitDescription = styled.p`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
`;

// Agent vote data
const VOTES = [
  { id: 'technical', icon: '◇', label: 'Tech', color: AGENT_COLORS.TECHNICAL, vote: true },
  { id: 'fundamental', icon: '◆', label: 'Fund', color: AGENT_COLORS.FUNDAMENTAL, vote: true },
  { id: 'sentiment', icon: '○', label: 'Sent', color: AGENT_COLORS.SENTIMENT, vote: false },
];

const BENEFITS = [
  {
    title: 'No Rogue AI',
    description: 'A single misfiring agent can\'t trigger trades. Collective agreement required.',
  },
  {
    title: 'Collective Wisdom',
    description: 'Combines technical, fundamental, and sentiment perspectives.',
  },
  {
    title: 'Full Transparency',
    description: 'See exactly why each agent voted and how consensus was reached.',
  },
];

interface QuorumExplainerProps {
  /** When true, renders as inline content without section wrapper (for FAQ pages) */
  embedded?: boolean;
}

export function QuorumExplainer({ embedded = false }: QuorumExplainerProps) {
  const [step, setStep] = useState(0);
  const [cycleCount, setCycleCount] = useState(0);

  // Animation cycle: show votes accumulating
  useEffect(() => {
    const timers: NodeJS.Timeout[] = [];

    const runCycle = () => {
      setStep(0);
      timers.push(setTimeout(() => setStep(1), 500));
      timers.push(setTimeout(() => setStep(2), 1000));
      timers.push(setTimeout(() => setStep(3), 1500)); // All votes in
      timers.push(setTimeout(() => {
        setCycleCount(c => c + 1);
      }, 5000)); // Reset after showing result
    };

    runCycle();

    return () => timers.forEach(t => clearTimeout(t));
  }, [cycleCount]);

  // Calculate current votes based on step
  const votesShown = Math.min(step, 3);
  const yesVotes = VOTES.slice(0, votesShown).filter(v => v.vote).length;
  const totalAgents = VOTES.length;
  const percentage = (yesVotes / totalAgents) * 100;
  const quorumReached = yesVotes >= 2;

  const content = (
    <>
      <Header>
        <Title>Why &quot;Quorum&quot;?</Title>
        <Etymology>
          In governance, a <Highlight>quorum</Highlight> is the minimum number of members
          required to make a decision valid. Here, a <Highlight>majority of AI agents</Highlight> must
          agree before any trade executes—ensuring no single point of failure.
        </Etymology>
      </Header>

      <VisualContainer>
        <VotingSimulation>
          <VoteIndicators>
            {VOTES.map((vote, index) => {
              const isShown = index < votesShown;
              return (
                <VoteIndicator
                  key={vote.id}
                  $color={vote.color}
                  $voted={isShown}
                  $delay={0}
                >
                  <VoteCircle $color={vote.color} $voted={isShown} $yes={vote.vote}>
                    {isShown ? (vote.vote ? '✓' : '✗') : vote.icon}
                  </VoteCircle>
                  <VoteLabel $color={vote.color} $voted={isShown}>
                    {vote.label}
                  </VoteLabel>
                </VoteIndicator>
              );
            })}
          </VoteIndicators>

          <ThresholdSection>
            <ThresholdLabel>
              <span>VOTES: {yesVotes}/{totalAgents}</span>
              <span>{percentage.toFixed(0)}%</span>
            </ThresholdLabel>
            <ThresholdBar>
              <ThresholdFill $percentage={percentage} $animate={true} />
              <ThresholdMarker />
            </ThresholdBar>
          </ThresholdSection>

          <StatusBox $reached={quorumReached}>
            <StatusText $reached={quorumReached}>
              {quorumReached ? '✓ QUORUM REACHED' : '⏳ AWAITING QUORUM'}
            </StatusText>
            <StatusSubtext>
              {quorumReached
                ? 'Trade execution authorized'
                : `Need ${2 - yesVotes} more agreeing vote${2 - yesVotes !== 1 ? 's' : ''}`}
            </StatusSubtext>
          </StatusBox>
        </VotingSimulation>

        <BenefitsList>
          {BENEFITS.map((benefit, index) => (
            <BenefitCard key={benefit.title} $delay={index * 150}>
              <BenefitTitle>{benefit.title}</BenefitTitle>
              <BenefitDescription>{benefit.description}</BenefitDescription>
            </BenefitCard>
          ))}
        </BenefitsList>
      </VisualContainer>
    </>
  );

  if (embedded) {
    return <Container>{content}</Container>;
  }

  return (
    <Section>
      <SectionInner>{content}</SectionInner>
    </Section>
  );
}
