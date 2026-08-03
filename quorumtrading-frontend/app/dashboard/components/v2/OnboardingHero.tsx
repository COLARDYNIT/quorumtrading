'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';

interface OnboardingHeroProps {
  hasResearch: boolean;
  hasStrategies: boolean;
  hasSimulations: boolean;
  hasBrokerAccount: boolean;
}

const Welcome = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

const Hero = styled.div`
  padding: 28px 32px 30px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  background:
    radial-gradient(ellipse 600px 240px at 0% 0%, rgba(0,255,65,0.06), transparent 70%),
    ${p => p.theme.colors.surface};
  display: flex; flex-direction: column; gap: 14px;
`;

const H2 = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  color: ${p => p.theme.colors.fgStrong};
  letter-spacing: -0.005em;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const Lede = styled.p`
  color: ${p => p.theme.colors.fgMuted};
  font-size: 14px;
  line-height: 1.55;
  max-width: 56ch;
  margin: 0;
`;

const Steps = styled.div`
  display: flex; flex-direction: column; gap: 0;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 3px;
  margin-top: 6px;
`;

const Step = styled(Link)<{ $done: boolean; $active: boolean }>`
  display: grid;
  grid-template-columns: 28px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 13px;
  text-decoration: none;
  color: inherit;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255, 255, 255, 0.02); }

  .n {
    width: 24px; height: 24px;
    border-radius: 50%;
    display: inline-flex; align-items: center; justify-content: center;
    border: 1px solid ${p => p.$done ? p.theme.colors.brand : p.$active ? p.theme.colors.fgStrong : p.theme.colors.border};
    color: ${p => p.$done ? p.theme.colors.brand : p.$active ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
    background: ${p => p.$done ? 'rgba(0, 255, 65, 0.06)' : 'transparent'};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
  }
  .ttl { color: ${p => p.theme.colors.foreground}; }
  .ttl .meta { display: block; color: ${p => p.theme.colors.fgDimmed}; font-size: 12px; margin-top: 2px; }
  .arrow {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12px;
    padding: 4px 10px;
    border: 1px solid ${p => p.$active ? p.theme.colors.brand : p.theme.colors.border};
    background: ${p => p.$active ? p.theme.colors.brandTint : 'transparent'};
    color: ${p => p.$active ? p.theme.colors.brand : p.theme.colors.fgMuted};
    border-radius: 2px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const PreviewList = styled.ul`
  padding-left: 18px;
  line-height: 1.75;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 12.5px;
  margin: 0;

  li { margin-bottom: 2px; }
  li b { color: ${p => p.theme.colors.foreground}; font-family: ${p => p.theme.typography.fontFamily.mono}; font-weight: ${p => p.theme.typography.fontWeight.medium}; }
`;

const PreviewText = styled.p`
  font-size: 12.5px; color: ${p => p.theme.colors.fgMuted}; line-height: 1.55;
  margin: 0 0 10px;
`;

export function OnboardingHero({ hasResearch, hasStrategies, hasSimulations, hasBrokerAccount }: OnboardingHeroProps) {
  const stepNum =
    !hasBrokerAccount ? 1 :
    !hasStrategies ? 2 :
    !hasSimulations ? 3 :
    4;

  return (
    <Welcome>
      <Hero>
        <H2>Spin up your first strategy in four steps</H2>
        <Lede>
          QuorumTrading runs your rules through a three-agent council on every evaluation tick. Author a strategy,
          backtest it against history, then promote it live with a Mock, Alpaca paper, or Alpaca live broker — DCA optional.
        </Lede>
        <Steps>
          <Step href="/settings/broker-accounts" $done={hasBrokerAccount} $active={stepNum === 1}>
            <span className="n">{hasBrokerAccount ? '✓' : '1'}</span>
            <span className="ttl">Connect a broker
              <span className="meta">Mock for safe play, Alpaca paper for realistic fills, Alpaca live to deploy capital</span>
            </span>
            <span className="arrow">{hasBrokerAccount ? 'Manage' : 'Connect →'}</span>
          </Step>
          <Step href={hasResearch ? '/strategies/new' : '/research/new'} $done={hasStrategies} $active={stepNum === 2}>
            <span className="n">{hasStrategies ? '✓' : '2'}</span>
            <span className="ttl">Author a strategy
              <span className="meta">Markdown + YAML rules · entries, exits, risk caps, consensus method — or fork from research</span>
            </span>
            <span className="arrow">{hasStrategies ? 'Browse' : 'Start →'}</span>
          </Step>
          <Step href="/simulations" $done={hasSimulations} $active={stepNum === 3}>
            <span className="n">{hasSimulations ? '✓' : '3'}</span>
            <span className="ttl">Backtest against history
              <span className="meta">1–10 years · review every council trail before going live</span>
            </span>
            <span className="arrow">{hasSimulations ? 'Open' : 'After step 2'}</span>
          </Step>
          <Step href="/simulations" $done={false} $active={stepNum === 4}>
            <span className="n">4</span>
            <span className="ttl">Promote to live (Mock first)
              <span className="meta">Mock → Alpaca paper → Alpaca live · attach a DCA plan optionally</span>
            </span>
            <span className="arrow">{stepNum === 4 ? 'Promote →' : 'After step 3'}</span>
          </Step>
        </Steps>
      </Hero>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Panel title="What you'll see here next" count="preview · once live">
          <PreviewText>Once a backtest is promoted to live, this control room fills with eight panels:</PreviewText>
          <PreviewList>
            <li><b>Live strategies</b> — value, today&apos;s P&amp;L, 14-session trend, last vote</li>
            <li><b>Recent council decisions</b> — quorum trail, newest first</li>
            <li><b>Agent vote heatmap</b> — 14-day intensity by agent</li>
            <li><b>Upcoming</b> — DCA, scheduled evaluations</li>
            <li><b>Strategy roster · Notifications · Research · Challenges</b></li>
          </PreviewList>
        </Panel>

        <Panel
          title="Quick links"
          count="get oriented"
          noBodyPadding
        >
          <Steps style={{ border: 'none', borderRadius: 0 }}>
            <Step href="/research" $done={false} $active={false}>
              <span className="n">→</span>
              <span className="ttl">Discover strategies
                <span className="meta">Let AI scan the market and surface candidate strategies</span>
              </span>
              <span className="arrow">Open</span>
            </Step>
            <Step href="/strategies/public" $done={false} $active={false}>
              <span className="n">→</span>
              <span className="ttl">Browse public strategies
                <span className="meta">Fork a community-published strategy and tune it</span>
              </span>
              <span className="arrow">Open</span>
            </Step>
            <Step href="/challenges" $done={false} $active={false}>
              <span className="n">→</span>
              <span className="ttl">Join a challenge
                <span className="meta">Competitive single-ticker sims — paper + historical events</span>
              </span>
              <span className="arrow">Open</span>
            </Step>
          </Steps>
        </Panel>
      </div>
    </Welcome>
  );
}
