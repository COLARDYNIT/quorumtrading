'use client';

import styled, { css } from 'styled-components';
import { dotBase, labelColor, pillBase, type LabelKind } from '@/app/styles/labels';

const Container = styled.div`
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 32px;

  @media (max-width: 768px) {
    padding: 0 20px;
  }
`;

const Wrap = styled.div`
  padding: 80px 0;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const Header = styled.div`
  max-width: 640px;
  margin-bottom: 36px;
`;

const Step = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.brand};
  margin-bottom: 14px;
  & > .num { color: ${p => p.theme.colors.fgDimmed}; margin-right: 8px; }
`;

const H2 = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 36px;
  line-height: 1.12;
  letter-spacing: -0.018em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  max-width: 22ch;
  margin: 0;

  @media (max-width: 768px) {
    font-size: 30px;
  }
`;

const Sub = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  line-height: 1.6;
  color: ${p => p.theme.colors.fgMuted};
  max-width: 60ch;
  margin: 16px 0 0;
`;

const Ladder = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  overflow: hidden;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const TrustStep = styled.div<{ $live?: boolean }>`
  background: ${p => p.theme.colors.surface};
  padding: 22px 24px;
  display: flex; flex-direction: column; gap: 8px;
  position: relative;

  ${p => p.$live && css`
    background: linear-gradient(180deg, rgba(0, 255, 65, 0.05), ${p.theme.colors.surface} 60%);
  `}

  & > .head {
    display: flex; align-items: center; gap: 10px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & > .head > .num {
    width: 22px; height: 22px;
    border-radius: 50%;
    border: 1px solid ${p => p.theme.colors.border};
    display: inline-flex; align-items: center; justify-content: center;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgMuted};
    ${p => p.$live && css`color: ${p.theme.colors.brand}; border-color: ${p.theme.colors.brand};`}
  }
  & > h4 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 19px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    letter-spacing: -0.005em;
    margin: 0;
  }
  & > p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    color: ${p => p.theme.colors.fgMuted};
    line-height: 1.55;
    margin: 0;
  }
`;

type PillKind = 'mock' | 'paper' | 'live';

/**
 * The trust ladder's three rungs, expressed as DS label kinds so the marketing pills and the
 * in-app StatusPill read from one hue table. `mock` is a neutral, not a status of its own.
 */
const LADDER_KIND: Record<PillKind, LabelKind> = { mock: 'neutral', paper: 'paper', live: 'live' };

const Pill = styled.span<{ $kind: PillKind }>`
  ${pillBase}
  ${dotBase}
  align-self: flex-start;
  margin-top: 4px;
  color: ${p => labelColor[LADDER_KIND[p.$kind]](p.theme.colors)};
`;

export function Act5GoLiveSection() {
  return (
    <Container id="trust">
      <Wrap>
        <Header>
          <Step><span className="num">Act 5</span>Go live</Step>
          <H2>Mock. Paper. Real money.<br />You decide when.</H2>
          <Sub>
            Three states, one toggle each. The council doesn&apos;t know the difference; only the broker does.
            Cash floor, max position, max drawdown enforced in code at every state.
          </Sub>
        </Header>
        <Ladder>
          <TrustStep>
            <div className="head"><span className="num">M</span><span>Mock</span></div>
            <h4>Fake fills · zero capital</h4>
            <p>Same data feeds, same agents. Orders never leave the platform. Debug a strategy quickly without
              spinning up a broker account.</p>
            <Pill $kind="mock"><span className="dot" />mock</Pill>
          </TrustStep>
          <TrustStep>
            <div className="head"><span className="num">P</span><span>Paper</span></div>
            <h4>Alpaca paper · fake money</h4>
            <p>Real fills against real market data with paper capital. Most users live here 4–12 weeks before
              promoting.</p>
            <Pill $kind="paper"><span className="dot" />paper</Pill>
          </TrustStep>
          <TrustStep $live>
            <div className="head"><span className="num">L</span><span>Live</span></div>
            <h4>Alpaca live · real money</h4>
            <p>One toggle, one confirmation. DCA top-ups optional. Pause from the dashboard any time — the trail
              keeps writing.</p>
            <Pill $kind="live"><span className="dot" />live</Pill>
          </TrustStep>
        </Ladder>
      </Wrap>
    </Container>
  );
}
