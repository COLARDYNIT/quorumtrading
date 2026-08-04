'use client';

import styled, { css } from 'styled-components';

const REPO_URL = 'https://github.com/COLARDYNIT/quorumtrading';

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

  @media (max-width: 768px) {
    padding: 56px 0;
  }
`;

const Header = styled.div`
  max-width: 720px;
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

  & > .accent { color: ${p => p.theme.colors.brand}; }

  @media (max-width: 768px) {
    font-size: 30px;
  }
`;

const Sub = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  line-height: 1.6;
  color: ${p => p.theme.colors.fgMuted};
  max-width: 62ch;
  margin: 16px 0 0;

  & > a { color: ${p => p.theme.colors.brand}; text-decoration: none; }
  & > a:hover { color: ${p => p.theme.colors.brandHover}; }
`;

const Term = styled.div`
  margin-top: 28px;
  max-width: 820px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  background: ${p => p.theme.colors.surface};
  overflow: hidden;
`;

const TermHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const TermBody = styled.pre`
  margin: 0;
  padding: 16px 18px;
  background: ${p => p.theme.colors.background};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  line-height: 2;
  color: ${p => p.theme.colors.fgMuted};
  overflow-x: auto;

  & .ln { display: flex; gap: 10px; white-space: pre; }
  & .prompt { color: ${p => p.theme.colors.fgDimmed}; }
  & .cmd { color: ${p => p.theme.colors.brand}; }

  @media (max-width: 768px) {
    font-size: 11.5px;
    line-height: 1.7;
  }
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  overflow: hidden;
  margin-top: 40px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const Card = styled.div<{ $brand?: boolean }>`
  background: ${p => p.theme.colors.surface};
  padding: 22px 24px;
  display: flex; flex-direction: column; gap: 10px;

  & > .head {
    display: flex; align-items: center; gap: 10px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & > .head > .num {
    width: 22px; height: 22px;
    border-radius: 50%;
    border: 1px solid ${p => p.theme.colors.border};
    display: inline-flex; align-items: center; justify-content: center;
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
    ${p => p.$brand && css`color: ${p.theme.colors.brand}; border-color: ${p.theme.colors.brand};`}
  }
  & > h4 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 18px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    letter-spacing: -0.005em;
    margin: 0;
  }
  & > h4 > .sep { color: ${p => p.theme.colors.fgDimmed}; font-weight: 400; }
  & > p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    color: ${p => p.theme.colors.fgMuted};
    line-height: 1.55;
    margin: 0;
  }
  & > p code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.foreground};
  }
`;

const Chips = styled.div`
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
  margin-top: auto;
  padding-top: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  letter-spacing: 0.1em;
  text-transform: uppercase;

  & > .chip {
    border: 1px solid ${p => p.theme.colors.border};
    border-radius: 3px;
    padding: 2px 7px;
    color: ${p => p.theme.colors.fgMuted};
  }
  & > .chip.brand {
    border-color: rgba(0, 255, 65, 0.35);
    color: ${p => p.theme.colors.brand};
    background: rgba(0, 255, 65, 0.06);
  }
`;

const CtaRow = styled.div`
  display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
  margin-top: 32px;
`;

const Cta = styled.a<{ $solid?: boolean }>`
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px;
  border-radius: 4px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  text-decoration: none;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.foreground};

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }

  ${p => p.$solid && css`
    background: ${p.theme.colors.brand};
    border-color: ${p.theme.colors.brand};
    color: ${p.theme.colors.fgInverse};
    &:hover {
      background: ${p.theme.colors.brandHover};
      border-color: ${p.theme.colors.brandHover};
      color: ${p.theme.colors.fgInverse};
    }
  `}

  & > .gly { font-family: ${p => p.theme.typography.fontFamily.mono}; }
`;

const CtaNote = styled.div`
  display: flex; gap: 14px; flex-wrap: wrap;
  margin-top: 14px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};

  & .gly { color: ${p => p.theme.colors.brand}; margin-right: 4px; }
`;

export function Act6OwnItPanel() {
  return (
    <Container id="own">
      <Wrap>
        <Header>
          <Step><span className="num">Act 6</span>Own it</Step>
          <H2>The code is <span className="accent">public</span>.<br />Run it on your own machine.</H2>
          <Sub>
            The whole platform is open source at{' '}
            <a href={REPO_URL} target="_blank" rel="noopener noreferrer">github.com/COLARDYNIT/quorumtrading</a>
            {' '}— AGPL-3.0, dual-licensed. Clone it, build it, and the keys and the data stay on your
            side of the wire.
          </Sub>
          <Term>
            <TermHead><span>Self-host · two commands</span><span>docker compose</span></TermHead>
            <TermBody>
              <span className="ln"><span className="prompt">$</span><span className="cmd">git clone {REPO_URL}.git</span></span>
              <span className="ln"><span className="prompt">$</span><span className="cmd">docker compose -f docker-compose.local.yml up --build</span></span>
            </TermBody>
          </Term>
        </Header>
        <Grid>
          <Card>
            <div className="head"><span className="num">1</span><span>Your machine</span></div>
            <h4>Single-user <span className="sep">·</span> no sign-up</h4>
            <p>Self-hosted QuorumTrading is single-user by design. There is no account to create and no
              tenant to share. Your simulations, decisions, and trails are written to your own PostgreSQL.</p>
            <Chips>
              <span className="chip">single-user</span>
              <span className="chip">no sign-up</span>
              <span className="chip">own postgres</span>
            </Chips>
          </Card>
          <Card $brand>
            <div className="head"><span className="num">2</span><span>Your keys</span></div>
            <h4>Crypto backtests <span className="sep">·</span> zero API keys</h4>
            <p>Crypto backtesting runs with no API keys at all. Stocks need one free market-data key.
              Bring your own LLM — Ollama, LM Studio, or a <code>claude</code> CLI — and the AI consensus
              costs $0 in API spend.</p>
            <Chips>
              <span className="chip brand">$0 api spend</span>
              <span className="chip">ollama</span>
              <span className="chip">lm studio</span>
              <span className="chip">claude cli</span>
            </Chips>
          </Card>
          <Card>
            <div className="head"><span className="num">3</span><span>Your license</span></div>
            <h4>AGPL-3.0 <span className="sep">·</span> dual-licensed</h4>
            <p>Copyleft by default, with a commercial license available if AGPL doesn&apos;t fit. It is the
              same codebase that runs the hosted service, not a trimmed community edition.</p>
            <Chips>
              <span className="chip">agpl-3.0</span>
              <span className="chip">commercial license</span>
              <span className="chip">same codebase</span>
            </Chips>
          </Card>
        </Grid>
        <CtaRow>
          <Cta $solid href={REPO_URL} target="_blank" rel="noopener noreferrer">
            View on GitHub<span className="gly">→</span>
          </Cta>
          <Cta href={`${REPO_URL}/blob/main/LOCAL_SETUP.md`} target="_blank" rel="noopener noreferrer">
            Self-host guide
          </Cta>
        </CtaRow>
        <CtaNote>
          <span><span className="gly">▸</span>Docker + Docker Compose</span>
          <span><span className="gly">▸</span>PostgreSQL runs in the stack</span>
          <span><span className="gly">▸</span>Market research stays hosted-only</span>
        </CtaNote>
      </Wrap>
    </Container>
  );
}
