'use client';

import Link from 'next/link';
import styled from 'styled-components';

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
  padding: 96px 0 80px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 40px;
  align-items: end;

  @media (max-width: 768px) {
    padding: 56px 0 48px;
  }
`;

const Eyebrow = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;

  & > .gly {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: ${p => p.theme.colors.brand};
    box-shadow: 0 0 6px rgba(0, 255, 65, 0.45);
  }
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 64px;
  line-height: 1.02;
  letter-spacing: -0.022em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  max-width: 16ch;
  margin: 0;

  & > .accent { color: ${p => p.theme.colors.brand}; }

  @media (max-width: 768px) {
    font-size: 42px;
    max-width: 100%;
  }
`;

const Lead = styled.p`
  font-size: 17px;
  line-height: 1.55;
  color: ${p => p.theme.colors.foreground};
  max-width: 56ch;
  margin: 16px 0 0;

  @media (max-width: 768px) {
    font-size: 15px;
  }
`;

const CtaRow = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 28px;
  flex-wrap: wrap;
`;

const CtaSolid = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  text-decoration: none;
  transition: background 120ms ease;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  & > .gly { font-family: ${p => p.theme.typography.fontFamily.mono}; }
`;

const CtaGhost = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  text-decoration: none;
  transition: border-color 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
  & > .gly { font-family: ${p => p.theme.typography.fontFamily.mono}; }
`;

const MetaLine = styled.div`
  display: flex;
  gap: 24px;
  align-items: baseline;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgDimmed};
  padding-top: 24px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  margin-top: 36px;
  flex-wrap: wrap;
`;

const Stat = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;

  & > .v {
    color: ${p => p.theme.colors.fgStrong};
    font-size: 17px;
  }
  & > .l {
    color: ${p => p.theme.colors.fgDimmed};
    font-size: 11px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }
`;

export function HeroBV2() {
  return (
    <Container>
      <Wrap>
        <div>
          <Eyebrow><span className="gly" />QuorumTrading · v3.7</Eyebrow>
          <H1>
            Trading bots,<br />
            but you can <span className="accent">read</span><br />
            the receipts.
          </H1>
          <Lead>
            A council of three AI agents votes on every buy, sell, and hold. The reasoning is in the trail.
            Backtest it, paper-trade it, take it live — at your pace.
          </Lead>
          <CtaRow>
            <CtaSolid href="/auth/signin">Start free<span className="gly">→</span></CtaSolid>
            <CtaGhost href="/strategies/public">Browse public strategies<span className="gly">→</span></CtaGhost>
          </CtaRow>
        </div>
        <MetaLine>
          <Stat><span className="l">Council shape</span><span className="v">3 agents · 1 orchestrator</span></Stat>
          <Stat><span className="l">Trust ladder</span><span className="v">Mock → paper → live</span></Stat>
          <Stat><span className="l">5-yr backtest · QQQ MA</span><span className="v">0.86 Sharpe</span></Stat>
          <Stat><span className="l">Backtester</span><span className="v">5yr / &lt; 30s</span></Stat>
          <Stat><span className="l">Restart point</span><span className="v">any council tick</span></Stat>
        </MetaLine>
      </Wrap>
    </Container>
  );
}
