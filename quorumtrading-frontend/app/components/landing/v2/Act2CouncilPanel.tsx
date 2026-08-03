'use client';

import Link from 'next/link';
import styled, { css } from 'styled-components';
import { LP_ANCHORS } from './anchors';

const Container = styled.div`
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 32px;

  @media (max-width: 768px) {
    padding: 0 20px;
  }
`;

const Act = styled.section`
  padding: 80px 0;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 56px;
  align-items: center;

  & > .copy { order: 2; }
  & > .anchor { order: 1; }

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 56px 0;
    & > .copy { order: 0; }
    & > .anchor { order: 1; }
  }
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
  margin: 20px 0 0;
  & + & { margin-top: 14px; }
`;

const CtaRow = styled.div`
  display: flex; gap: 12px; align-items: center; margin-top: 28px; flex-wrap: wrap;
`;

const CtaGhost = styled.a`
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
  transition: border-color 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
  & > .gly { font-family: ${p => p.theme.typography.fontFamily.mono}; }
`;

const Anchor = styled.div`
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  background: ${p => p.theme.colors.surface};
  overflow: hidden;
`;

const PanelHead = styled.div`
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  gap: 10px;

  & > .ttl { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
  & > .ttl > h3 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    margin: 0;
  }
  & > .ttl > .count {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

// Agent grid

const Grid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 18px;

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
`;

type AgentKind = 'tech' | 'fund' | 'sent' | 'orch';

const agentColor = (k: AgentKind) => css`
  color: ${p => k === 'tech' ? p.theme.colors.technical
            : k === 'fund' ? p.theme.colors.fundamental
            : k === 'sent' ? p.theme.colors.sentiment
            :                p.theme.colors.terminalMint};
`;

const Card = styled.div<{ $kind: AgentKind }>`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 3px;
  padding: 14px 16px;
  display: flex; flex-direction: column; gap: 10px;

  .head {
    display: flex; align-items: center; justify-content: space-between;
  }
  .head .who {
    display: flex; align-items: center; gap: 6px;
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12.5px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    text-transform: uppercase;
    letter-spacing: 0.05em;
    ${p => agentColor(p.$kind)}
  }
  .head .who .dot {
    width: 6px; height: 6px; border-radius: 50%; background: currentColor;
  }
  .head .meta {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
  }

  .stats { display: flex; gap: 16px; }
  .stat { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .stat .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 9px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  .stat .v {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 19px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  .stat .v.warn { color: ${p => p.theme.colors.terminalAmber}; }

  .bars-lbl {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
    text-transform: uppercase;
    letter-spacing: 0.08em;
    display: flex; justify-content: space-between; align-items: baseline;
  }
  .bars-lbl .right { color: ${p => p.theme.colors.fgMuted}; letter-spacing: 0; }

  .bars {
    display: grid;
    grid-template-columns: repeat(12, 1fr);
    gap: 3px;
    align-items: end;
    height: 26px;
  }
  .bars .b {
    border-radius: 1px;
    ${p => agentColor(p.$kind)}
    background: currentColor;
  }
  .bars .b.n { background: ${p => p.theme.colors.borderSubtle}; }

  .note {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
    line-height: 1.4;
  }
`;

type CardData = {
  kind: AgentKind;
  name: string;
  withConsensus: number | null;
  overrideRate?: number;
  sessions: number;
  votes: number[];
  note: string;
};

const CARDS: CardData[] = [
  { kind: 'tech', name: 'Technical',   withConsensus: 71, sessions: 12, votes: [1,1,0,1,1,1,1,0,1,1,0,1], note: 'voted with the final consensus 8 of 12 sessions' },
  { kind: 'fund', name: 'Fundamental', withConsensus: 64, sessions: 12, votes: [1,0,0,1,1,1,1,1,0,0,1,1], note: 'voted with the final consensus 8 of 12 sessions' },
  { kind: 'sent', name: 'Sentiment',   withConsensus: 58, sessions: 12, votes: [1,1,1,0,0,1,1,0,1,1,0,1], note: 'voted with the final consensus 7 of 12 sessions' },
  { kind: 'orch', name: 'Orchestrator', withConsensus: null, overrideRate: 8, sessions: 12, votes: [1,1,1,1,0,1,1,1,1,0,1,1], note: 'overrode the majority on 1 of 12 sessions · earnings eve' },
];

export function Act2CouncilPanel() {
  const { sim } = LP_ANCHORS;
  return (
    <Container id="council">
      <Act>
        <div className="copy">
          <Step><span className="num">Act 2</span>The council</Step>
          <H2>Three specialists.<br />One quorum.</H2>
          <Sub>
            Technical reads price action and momentum. Fundamental reads filings and ratios. Sentiment reads news,
            filings, and social. Each runs on its own model with its own context window — no agent sees another&apos;s
            reasoning until after the orchestrator counts the vote.
          </Sub>
          <Sub>
            The cards on the right show what we actually measure: how often each agent voted with the final
            consensus, and how often the orchestrator chose to override the majority. We don&apos;t track
            &quot;accuracy&quot; — there&apos;s no labeled ground truth for an agent&apos;s vote being right.
          </Sub>
          <CtaRow>
            <CtaGhost href="#strategies">See how a strategy configures the council<span className="gly">→</span></CtaGhost>
          </CtaRow>
        </div>

        <Anchor className="anchor">
          <PanelHead>
            <div className="ttl">
              <h3>Agent council · last 12 sessions</h3>
              <span className="count">Tech ETF · sim {sim.id}</span>
            </div>
          </PanelHead>
          <Grid>
            {CARDS.map(a => {
              const isOrch = a.kind === 'orch';
              return (
                <Card key={a.name} $kind={a.kind}>
                  <div className="head">
                    <span className="who"><span className="dot" />{a.name}</span>
                    <span className="meta">{a.sessions} sessions</span>
                  </div>
                  <div className="stats">
                    {isOrch ? (
                      <>
                        <div className="stat">
                          <div className="lbl">Override rate</div>
                          <div className="v warn">{a.overrideRate}%</div>
                        </div>
                        <div className="stat">
                          <div className="lbl">Sessions</div>
                          <div className="v">{a.sessions}</div>
                        </div>
                      </>
                    ) : (
                      <>
                        <div className="stat">
                          <div className="lbl">With consensus</div>
                          <div className="v">{a.withConsensus}%</div>
                        </div>
                        <div className="stat">
                          <div className="lbl">Sessions</div>
                          <div className="v">{a.sessions}</div>
                        </div>
                      </>
                    )}
                  </div>
                  <div className="bars-lbl">
                    <span>{isOrch ? 'Override · 12 sessions' : 'Voted yes · 12 sessions'}</span>
                    <span className="right">→ recent</span>
                  </div>
                  <div className="bars" aria-hidden="true">
                    {a.votes.map((v, i) => (
                      <div key={i} className={'b ' + (v ? 'y' : 'n')} style={{ height: `${8 + v * 14}px` }} />
                    ))}
                  </div>
                  <div className="note">{a.note}</div>
                </Card>
              );
            })}
          </Grid>
        </Anchor>
      </Act>
    </Container>
  );
}
