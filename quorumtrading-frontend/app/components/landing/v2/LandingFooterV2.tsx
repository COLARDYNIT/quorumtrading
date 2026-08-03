'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { Wordmark } from '@/app/components/shared/Wordmark';

const Wrap = styled.div`
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: #0c1118;
`;

// Top row — design's minimal anchor + version line
const TopRow = styled.div`
  padding: 28px 0 18px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Container = styled.div`
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 32px;
  display: flex; flex-direction: column; gap: 16px;

  @media (max-width: 768px) {
    padding: 0 20px;
  }
`;

const TopFlex = styled.div`
  display: flex; justify-content: space-between; align-items: center;
  gap: 16px; flex-wrap: wrap;

  & > .links { display: flex; gap: 18px; flex-wrap: wrap; }
  & > .links a {
    color: ${p => p.theme.colors.fgMuted};
    text-decoration: none;
    font-size: 13px;
  }
  & > .links a:hover { color: ${p => p.theme.colors.foreground}; }
  & > .ver {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgSubtle};
  }
`;

// Legal block — preserved from previous LandingLayout so we don't lose
// Terms / Privacy / company info that's required to be on a marketing site.
const Legal = styled.div`
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  padding: 24px 0 32px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 24px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};

  & h4 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 11.5px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgMuted};
    margin: 0 0 8px;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }
  & a, & span {
    display: block;
    color: ${p => p.theme.colors.fgDimmed};
    text-decoration: none;
    font-size: 13px;
    line-height: 1.7;
  }
  & a:hover { color: ${p => p.theme.colors.foreground}; }
`;

const Copyright = styled.div`
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  padding: 16px 0 24px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  color: ${p => p.theme.colors.fgSubtle};
  text-align: center;
  line-height: 1.6;
`;

export function LandingFooterV2() {
  return (
    <Wrap>
      <TopRow>
        <Container>
          <TopFlex>
            <Wordmark href="/" markSize={18} fontSize={13} gap={8} trailing="· colardyn-it.be" />
            <span className="links">
              <a href="#problem">Why</a>
              <a href="#council">Council</a>
              <Link href="/strategies/public">Strategies</Link>
              <a href="#evidence">Backtests</a>
              <a href="#trust">Go live</a>
              <Link href="/auth/signin">Start free</Link>
            </span>
            <span className="ver">v3.7.2 · build {new Date().toISOString().slice(0, 10)}</span>
          </TopFlex>
        </Container>
      </TopRow>

      <Container>
        <Legal>
          <div>
            <h4>Resources</h4>
            <Link href="/faq">FAQ</Link>
            <Link href="/blog">Changelog</Link>
            <a href="https://www.reddit.com/r/quorumtrading/" target="_blank" rel="noopener noreferrer">Community</a>
          </div>
          <div>
            <h4>Legal</h4>
            <Link href="/terms">Terms of Service</Link>
            <Link href="/privacy">Privacy Policy</Link>
          </div>
          <div>
            <h4>Company</h4>
            <a href="https://colardyn-it.be/" target="_blank" rel="noopener noreferrer">Colardyn IT CommV</a>
            <span>BTW BE 0892.696.641</span>
            <span>Frederik de Merodestraat 50, 2800 Mechelen</span>
            <a href="mailto:frederikcolardyn@colardyn-it.be">frederikcolardyn@colardyn-it.be</a>
          </div>
        </Legal>

        <Copyright>
          QuorumTrading is experimental. AI decisions do not constitute financial advice. Trade at your own risk.
          <br />
          © {new Date().getFullYear()} QuorumTrading. All rights reserved.
        </Copyright>
      </Container>
    </Wrap>
  );
}
