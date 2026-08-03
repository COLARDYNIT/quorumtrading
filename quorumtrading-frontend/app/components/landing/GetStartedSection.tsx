'use client';

import styled from 'styled-components';
import Link from 'next/link';
import { fadeInUp, shadows } from '../../styles/animations';
import { SectionTitle } from '../shared';

const STEPS = [
  {
    number: 1,
    title: 'Write a Strategy',
    description: 'Write or browse proven strategies from our curated library',
    href: '/strategies/public',
  },
  {
    number: 2,
    title: 'Connect Your Broker',
    description: 'Link your Alpaca account in minutes',
    href: '/settings/broker-accounts',
  },
  {
    number: 3,
    title: 'AI Invests for You',
    description: 'Monitor performance while AI handles the trading',
    href: '/dashboard',
  },
];

const Section = styled.section`
  padding: ${p => p.theme.spacing['3xl']} ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.background};
  border-bottom: none;

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing['2xl']} ${p => p.theme.spacing.md};
  }
`;

const Container = styled.div`
  max-width: 1100px;
  margin: 0 auto;
`;

const StepsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: ${p => p.theme.spacing.lg};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    max-width: 400px;
    margin-left: auto;
    margin-right: auto;
  }
`;

const StepCard = styled(Link)<{ $delay: number }>`
  display: block;
  text-decoration: none;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.lg};
  padding: ${p => p.theme.spacing.xl} ${p => p.theme.spacing.lg};
  text-align: center;
  box-shadow: ${shadows.cardPremium};
  animation: ${fadeInUp} 0.5s ease-out both;
  animation-delay: ${p => p.$delay}ms;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: ${shadows.cardHover};
  }
`;

const StepNumber = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 48px;
  font-weight: 700;
  color: ${p => p.theme.colors.terminalGreen};
  margin-bottom: ${p => p.theme.spacing.md};
  line-height: 1;
  text-shadow: var(--qt-text-shadow-primary);
`;

const StepTitle = styled.h3`
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.textPrimary};
  margin-bottom: ${p => p.theme.spacing.sm};
  font-weight: 600;
`;

const StepDescription = styled.p`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
`;

export function GetStartedSection() {
  return (
    <Section>
      <Container>
        <SectionTitle $color="cyan" $centered $size="xl">Start Investing in Three Steps</SectionTitle>

        <StepsGrid>
          {STEPS.map((step, index) => (
            <StepCard key={step.number} href={step.href} $delay={index * 100}>
              <StepNumber>{step.number}</StepNumber>
              <StepTitle>{step.title}</StepTitle>
              <StepDescription>{step.description}</StepDescription>
            </StepCard>
          ))}
        </StepsGrid>
      </Container>
    </Section>
  );
}
