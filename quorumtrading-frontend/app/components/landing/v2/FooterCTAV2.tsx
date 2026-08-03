'use client';

import Link from 'next/link';
import styled from 'styled-components';

const Wrap = styled.div`
  padding: 100px 0 88px;
  text-align: center;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: radial-gradient(ellipse 800px 220px at 50% 100%, rgba(0, 255, 65, 0.05), transparent 70%);

  @media (max-width: 768px) {
    padding: 64px 0;
  }
`;

const Container = styled.div`
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 32px;
`;

const H2 = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 44px;
  letter-spacing: -0.02em;
  line-height: 1.05;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0 auto;
  max-width: 18ch;

  @media (max-width: 768px) {
    font-size: 32px;
  }
`;

const Sub = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  color: ${p => p.theme.colors.fgMuted};
  margin: 16px auto 28px;
  font-size: 17px;
  max-width: 50ch;
`;

const CtaRow = styled.div`
  display: flex; gap: 12px; align-items: center; justify-content: center; flex-wrap: wrap;
`;

const CtaSolid = styled(Link)`
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  text-decoration: none;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  & > .gly { font-family: ${p => p.theme.typography.fontFamily.mono}; }
`;

const CtaGhost = styled(Link)`
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  text-decoration: none;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
`;

const Trust = styled.div`
  display: flex; gap: 16px; margin-top: 14px; justify-content: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  flex-wrap: wrap;

  & > span > .gly { color: ${p => p.theme.colors.brand}; margin-right: 4px; }
`;

export function FooterCTAV2() {
  return (
    <Wrap>
      <Container>
        <H2>Open the council on your next trade.</H2>
        <Sub>Five minutes from sign-up to a council voting on your first backtest. No subscription, no
          contract — you only pay for the AI tokens you actually burn.</Sub>
        <CtaRow>
          <CtaSolid href="/auth/signin">Start free<span className="gly">→</span></CtaSolid>
          <CtaGhost href="/strategies/public">Browse public strategies</CtaGhost>
        </CtaRow>
        <Trust>
          <span><span className="gly">●</span>platform is free</span>
          <span><span className="gly">●</span>pay only for AI you consume</span>
          <span><span className="gly">●</span>mock, paper, and live Alpaca — no platform fee</span>
        </Trust>
      </Container>
    </Wrap>
  );
}
