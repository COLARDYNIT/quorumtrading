'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { ActionPill, VoteChip, type ActionKind } from '@/app/components/shared/v2';
import { dotBase, labelCompact, pillBase, tagBase } from '@/app/styles/labels';
import { LP_ANCHORS } from './anchors';

// ───────────────────────────────────────────────────────────────────────────
// Layout primitives shared with the other Acts
// ───────────────────────────────────────────────────────────────────────────

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

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 56px 0;
  }
`;

const Step = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.brand};
  margin-bottom: 14px;

  & > .num {
    color: ${p => p.theme.colors.fgDimmed};
    margin-right: 8px;
  }
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
  display: flex; gap: 12px; align-items: center; margin-top: 24px; flex-wrap: wrap;
`;

const CtaGhost = styled.a`
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

// ───────────────────────────────────────────────────────────────────────────
// Panel chrome
// ───────────────────────────────────────────────────────────────────────────

const Panel = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  overflow: hidden;
`;

const PanelHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  gap: 10px;

  & > .ttl {
    display: flex; align-items: baseline; gap: 10px; min-width: 0;
  }
  & > .ttl > h3 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    margin: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  & > .ttl > .count {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgDimmed};
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
`;

const LivePill = styled.span`
  ${pillBase}
  ${dotBase}
  color: ${p => p.theme.colors.brand};

  & > .dot {
    box-shadow: 0 0 6px currentColor;
  }
`;

// ───────────────────────────────────────────────────────────────────────────
// Mini KPI strip — 3 cells
// ───────────────────────────────────────────────────────────────────────────

const Kpis = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const Kpi = styled.div`
  background: ${p => p.theme.colors.surface};
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;

  & > .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 9.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & > .val {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 17px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  & > .val.pos { color: ${p => p.theme.colors.positive}; }
  & > .val.neg { color: ${p => p.theme.colors.negative}; }
  & > .delta {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

// ───────────────────────────────────────────────────────────────────────────
// Proof chart — 18 ticks with BUY/HOLD/SELL dots
// ───────────────────────────────────────────────────────────────────────────

const ChartWrap = styled.div`
  position: relative;
  width: 100%;
  height: 160px;
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  & > svg { display: block; width: 100%; height: 100%; }
`;

const ChartLegend = styled.div`
  position: absolute;
  top: 8px; left: 12px;
  display: flex; gap: 12px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgMuted};

  & > span > .sw {
    display: inline-block;
    width: 10px; height: 2px;
    margin-right: 5px;
    vertical-align: middle;
  }
  & > span > .dot {
    display: inline-block;
    width: 6px; height: 6px; border-radius: 50%;
    margin-right: 5px;
    vertical-align: middle;
  }
`;

function ProofChart() {
  const W = 720, H = 160;
  const data = [100, 99, 102, 104, 103, 105, 109, 112, 108, 111, 114, 116, 113, 115, 118, 121, 119, 123];
  const min = 96, max = 126;
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * W;
    const y = H - ((v - min) / (max - min)) * (H - 20) - 10;
    return { x, y };
  });
  const line = pts.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
  const buyP = pts[4], holdP = pts[11], sellP = pts[14];
  return (
    <ChartWrap>
      <ChartLegend>
        <span><span className="sw" style={{ background: 'var(--qt-brand)' }} />portfolio</span>
        <span><span className="dot" style={{ background: 'var(--qt-action-buy)' }} />BUY</span>
        <span><span className="dot" style={{ background: 'var(--qt-action-hold)' }} />HOLD</span>
        <span><span className="dot" style={{ background: 'var(--qt-action-sell)' }} />SELL</span>
      </ChartLegend>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none">
        {[0.25, 0.5, 0.75].map(p => (
          <line key={p} x1={0} x2={W} y1={H * p} y2={H * p} stroke="var(--qt-border-subtle)" strokeWidth={0.5} />
        ))}
        <polyline points={line} fill="none" stroke="var(--qt-brand)" strokeWidth={1.6} />
        <circle cx={buyP.x} cy={buyP.y} r={4} fill="var(--qt-action-buy)" stroke="var(--qt-bg)" strokeWidth={1.5} />
        <circle cx={holdP.x} cy={holdP.y} r={4} fill="var(--qt-action-hold)" stroke="var(--qt-bg)" strokeWidth={1.5} />
        <circle cx={sellP.x} cy={sellP.y} r={4} fill="var(--qt-action-sell)" stroke="var(--qt-bg)" strokeWidth={1.5} />
      </svg>
    </ChartWrap>
  );
}

// ───────────────────────────────────────────────────────────────────────────
// Mini decisions table
// ───────────────────────────────────────────────────────────────────────────

const DecisionTable = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-family: ${p => p.theme.typography.fontFamily.sans};

  thead th {
    text-align: left;
    font-size: 9px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    padding: 6px 12px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  }
  thead th.r { text-align: right; }
  td {
    padding: 7px 12px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    font-size: 12px;
    vertical-align: middle;
  }
  tr:last-child td { border-bottom: none; }
  td.ts { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.fgMuted}; white-space: nowrap; }
  td.px { font-family: ${p => p.theme.typography.fontFamily.mono}; color: ${p => p.theme.colors.foreground}; text-align: right; }
`;

const VotesInline = styled.span`
  display: inline-flex; gap: 4px;
`;

type AgentKey = 'tech' | 'fund' | 'sent';

/** Agent identity hue — the one per-agent property, per DS v0.4.0. */
const agentColorKey = { tech: 'technical', fund: 'fundamental', sent: 'sentiment' } as const;

/**
 * The agent's own recommendation, tinted by agent.
 *
 * Deliberately NOT the shared `VoteChip` atom: that one is the boolean ✓/✕ endorsement chip
 * (used in the decisions table above), while this carries the action word and no glyph.
 */
const AgentVote = styled.span<{ $agent: AgentKey }>`
  ${tagBase}
  ${labelCompact}
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors[agentColorKey[p.$agent]]};
`;

function MiniDecisionsTable() {
  type Row = { ts: string; action: ActionKind; sym: string; px: number; votes: { t: boolean; f: boolean; s: boolean } };
  const rows: Row[] = [
    { ts: '13:54 ET', action: 'hold', sym: 'QQQ', px: 451.80, votes: { t: true,  f: false, s: true } },
    { ts: '10:00 ET', action: 'buy',  sym: 'NVDA', px: 942.10, votes: { t: true,  f: true,  s: true } },
    { ts: '07 May',  action: 'buy',  sym: 'QQQ', px: 445.30, votes: { t: true,  f: true,  s: true } },
  ];
  return (
    <DecisionTable>
      <thead>
        <tr>
          <th>When</th>
          <th>Action</th>
          <th>Votes (T · F · S)</th>
          <th className="r">Price</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            <td className="ts">{r.ts}</td>
            <td><ActionPill action={r.action} /></td>
            <td>
              <VotesInline>
                <VoteChip agent="tech" ok={r.votes.t} />
                <VoteChip agent="fund" ok={r.votes.f} />
                <VoteChip agent="sent" ok={r.votes.s} />
              </VotesInline>
            </td>
            <td className="px">${r.px.toFixed(2)}</td>
          </tr>
        ))}
      </tbody>
    </DecisionTable>
  );
}

// ───────────────────────────────────────────────────────────────────────────
// Agent strip — TECH / FUND / SENT rows with vote + reason
// ───────────────────────────────────────────────────────────────────────────

const Strip = styled.div`
  display: flex; flex-direction: column;
`;

const StripRow = styled.div<{ $agent: AgentKey }>`
  display: grid;
  grid-template-columns: 110px 60px 1fr;
  gap: 12px;
  align-items: center;
  padding: 9px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 13px;

  &:last-child { border-bottom: none; }

  & > .who {
    display: flex; align-items: center; gap: 6px;
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    text-transform: uppercase;
    letter-spacing: 0.05em;
    font-size: 11.5px;
    color: ${p => p.$agent === 'tech' ? p.theme.colors.technical
              : p.$agent === 'fund' ? p.theme.colors.fundamental
              :                       p.theme.colors.sentiment};
  }
  & > .who > .dot {
    width: 6px; height: 6px; border-radius: 50%;
    background: currentColor;
  }
  & > .vote {
    width: 50px;
    justify-content: center;
  }
  & > .reason {
    color: ${p => p.theme.colors.fgMuted};
    font-size: 12.5px;
    line-height: 1.45;
  }
  & > .reason .accent { color: ${p => p.theme.colors.info}; }
`;

function AgentStrip() {
  return (
    <Strip>
      <StripRow $agent="tech">
        <span className="who"><span className="dot" />Technical</span>
        <AgentVote className="vote" $agent="tech">BUY</AgentVote>
        <span className="reason">50d crossed 200d 3 sessions ago; RSI 62 — room to run.</span>
      </StripRow>
      <StripRow $agent="fund">
        <span className="who"><span className="dot" />Fundamental</span>
        <AgentVote className="vote" $agent="fund">BUY</AgentVote>
        <span className="reason">QQQ P/E in band; sector earnings beats running 67%; ETF flows positive.</span>
      </StripRow>
      <StripRow $agent="sent">
        <span className="who"><span className="dot" />Sentiment</span>
        <AgentVote className="vote" $agent="sent">BUY</AgentVote>
        <span className="reason">News +0.42 post earnings beat. <span className="accent">Tech-sector sentiment</span> rebounded from −0.18.</span>
      </StripRow>
    </Strip>
  );
}

// ───────────────────────────────────────────────────────────────────────────
// Section
// ───────────────────────────────────────────────────────────────────────────

export function Act1ProblemPanel() {
  const { sim } = LP_ANCHORS;
  return (
    <Container id="problem">
      <Act>
        <div>
          <Step><span className="num">Act 1</span>The problem</Step>
          <H2>Most bots are <span style={{ color: 'var(--qt-pink)' }}>opaque</span>.<br />Yours doesn&apos;t have to be.</H2>
          <Sub>
            Pick a typical trading bot. It bought QQQ at 14:00 ET on 07 May. Why?
            &quot;Signal triggered.&quot; That&apos;s not a trade you can defend — to yourself, your spouse, or the IRS.
          </Sub>
          <Sub>
            QuorumTrading writes a paragraph for every decision and stamps it with three independent votes.
            Open any council tick and you get the chart, the trail, the reasoning — the same panels
            you&apos;d see if it were running your money.
          </Sub>
          <CtaRow>
            <CtaGhost href="#evidence">See a real backtest<span className="gly">→</span></CtaGhost>
          </CtaRow>
        </div>

        <Panel>
          <PanelHead>
            <div className="ttl">
              <h3>Tech ETF — Moving Average Crossover</h3>
              <span className="count">sim {sim.id} · QQQ · alpaca paper · DCA on</span>
            </div>
            <LivePill><span className="dot" />Live</LivePill>
          </PanelHead>

          <Kpis>
            <Kpi>
              <div className="lbl">Portfolio value</div>
              <div className="val">${sim.portfolioValue.toLocaleString('en-US', { minimumFractionDigits: 2 })}</div>
              <div className="delta">vs $20,000 start</div>
            </Kpi>
            <Kpi>
              <div className="lbl">Total return</div>
              <div className="val pos">+{sim.pctTotal.toFixed(2)}%</div>
              <div className="delta">today +${sim.plDay.toFixed(2)}</div>
            </Kpi>
            <Kpi>
              <div className="lbl">vs QQQ b&amp;h</div>
              <div className="val neg">{sim.vsBench.toFixed(2)}%</div>
              <div className="delta">QQQ +9.52% same window</div>
            </Kpi>
          </Kpis>

          <ProofChart />

          <PanelHead style={{ borderTop: '1px solid var(--qt-border-subtle)' }}>
            <div className="ttl">
              <h3>Decision history</h3>
              <span className="count">3 of last 38</span>
            </div>
            <Link href="#evidence" style={{
              fontFamily: 'var(--qt-font-sans)',
              fontSize: 10,
              color: 'var(--qt-fg-muted)',
              textDecoration: 'none',
              padding: '3px 8px',
              border: '1px solid var(--qt-border)',
              borderRadius: 2,
            }}>Open full trail →</Link>
          </PanelHead>

          <MiniDecisionsTable />

          <PanelHead style={{ borderTop: '1px solid var(--qt-border-subtle)' }}>
            <div className="ttl">
              <h3>Council · this tick</h3>
              <span className="count">quorum unanimous · MAJORITY threshold 0.65</span>
            </div>
          </PanelHead>

          <AgentStrip />
        </Panel>
      </Act>
    </Container>
  );
}
