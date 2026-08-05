'use client';

import styled from 'styled-components';
import { LandingNavV2 } from './LandingNavV2';
import { HeroBV2 } from './HeroBV2';
import { Act1ProblemPanel } from './Act1ProblemPanel';
import { Act2CouncilPanel } from './Act2CouncilPanel';
import { Act3StrategyPanel } from './Act3StrategyPanel';
import { Act4BacktestPanel } from './Act4BacktestPanel';
import { Act5GoLiveSection } from './Act5GoLiveSection';
import { Act6OwnItPanel } from './Act6OwnItPanel';
import { FooterCTAV2 } from './FooterCTAV2';
import { LandingFooterV2 } from './LandingFooterV2';

const Page = styled.div`
  width: 100%;
  min-height: 100%;
  background: ${p => p.theme.colors.background};
  color: ${p => p.theme.colors.foreground};
  font-family: ${p => p.theme.typography.fontFamily.sans};
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  scroll-behavior: smooth;
`;

export function LandingV2() {
  return (
    <Page>
      <LandingNavV2 />
      <HeroBV2 />
      <Act1ProblemPanel />
      <Act2CouncilPanel />
      <Act3StrategyPanel />
      <Act4BacktestPanel />
      <Act5GoLiveSection />
      <Act6OwnItPanel />
      <FooterCTAV2 />
      <LandingFooterV2 />
    </Page>
  );
}
