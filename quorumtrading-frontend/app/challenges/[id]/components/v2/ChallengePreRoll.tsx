'use client';

import Link from 'next/link';
import styled, { keyframes } from 'styled-components';
import type { ChallengeResponse } from '@/app/lib/types/challenge';
import { buildStrategyLink } from '@/app/lib/types/challenge';
import { countdownTo, fmtDate } from './helpers';

interface Props {
  ch: ChallengeResponse;
}

export function ChallengePreRoll({ ch }: Props) {
  const cd = countdownTo(ch.participationOpen);
  return (
    <Root>
      <Eye><Dot />PARTICIPATION OPENS</Eye>
      <Big>{cd.label}</Big>
      <Target>{fmtDate(ch.participationOpen)}</Target>
      <Body>
        The equity-curve chart appears once the first entries register their sims. Until
        then, prepare a strategy that trades <Brand>{ch.ticker}</Brand> and queue it to
        join when doors open.
      </Body>
      <Acts>
        <PrimaryLink href={buildStrategyLink(ch.ticker, ch.tickerType, ch.id)}>
          <span>↗</span>
          Prepare strategy
        </PrimaryLink>
        <GhostLink href="/strategies/public">
          Browse public library
        </GhostLink>
      </Acts>
    </Root>
  );
}

const Root = styled.div`
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  background:
    radial-gradient(ellipse 600px 180px at 0% 0%, rgba(0, 217, 255, 0.04), transparent 70%),
    ${p => p.theme.colors.surface};
  border-radius: 4px;
  padding: 32px 28px;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const pulse = keyframes`
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
`;

const Eye = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.info};
  margin-bottom: 4px;
`;

const Dot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${p => p.theme.colors.info};
  box-shadow: 0 0 6px rgba(0, 217, 255, 0.5);
  animation: ${pulse} 1.6s ease-in-out infinite;
`;

const Big = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 44px;
  letter-spacing: -0.02em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  font-variant-numeric: tabular-nums;
`;

const Target = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgDimmed};
  letter-spacing: 0.04em;
  margin-bottom: 10px;
`;

const Body = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14.5px;
  line-height: 1.6;
  color: ${p => p.theme.colors.fgMuted};
  max-width: 70ch;
  margin: 0 0 8px;
`;

const Brand = styled.b`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.brand};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const Acts = styled.div`
  display: flex;
  gap: 10px;
  margin-top: 4px;
  flex-wrap: wrap;
`;

const PrimaryLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  text-decoration: none;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  text-decoration: none;

  &:hover { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;
