'use client';

import styled from 'styled-components';
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

const Anchor = styled.div`
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  background: ${p => p.theme.colors.surface};
  overflow: hidden;
`;

const Head = styled.div`
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
  /* Decorative range chips — pinned demo data, so these are not interactive. */
  & > .tabs { display: flex; gap: 4px; pointer-events: none; }
  & > .tabs > span {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    padding: 3px 8px;
    border: 1px solid ${p => p.theme.colors.border};
    background: transparent;
    color: ${p => p.theme.colors.fgMuted};
    border-radius: 2px;
  }
  & > .tabs > span.on {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: rgba(255, 255, 255, 0.04);
  }
`;

const ChartFrame = styled.div`
  background: ${p => p.theme.colors.background};
  padding: 14px;
  position: relative;

  & > svg { width: 100%; height: 240px; display: block; }
`;

/**
 * Chart legend rendered as HTML, not SVG text. The SVG uses
 * preserveAspectRatio="none" so the polylines stretch to fill the container;
 * if we put the labels inside the SVG they get stretched horizontally too.
 * Absolute-positioned spans stay at their natural rendered size.
 */
const ChartLegend = styled.div`
  position: absolute;
  top: 26px;
  left: 26px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 14px;
  background: rgba(10, 14, 20, 0.85);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 3px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  line-height: 1.4;
  pointer-events: none;

  & > span {
    display: flex;
    align-items: center;
    gap: 8px;
    white-space: nowrap;
  }
  & > span > .sw {
    display: inline-block;
    width: 14px;
    height: 2px;
    flex-shrink: 0;
  }
  & > span > .pct {
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.fgStrong};
    min-width: 60px;
  }
  & > span > .label {
    color: ${p => p.theme.colors.fgMuted};
  }
  & > span.strat > .pct { color: ${p => p.theme.colors.brand}; }
  & > span.rot   > .pct { color: ${p => p.theme.colors.terminalAmber}; }
  & > span.qqq   > .pct { color: ${p => p.theme.colors.info}; }
  & > span.strat > .sw  { background: ${p => p.theme.colors.brand}; }
  & > span.rot   > .sw  { background: ${p => p.theme.colors.terminalAmber}; opacity: 0.75; }
  & > span.qqq   > .sw  { background: ${p => p.theme.colors.info}; opacity: 0.75; }
`;

const CashRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};

  & > .c {
    background: ${p => p.theme.colors.surface};
    padding: 12px 14px;
    display: flex; flex-direction: column; gap: 3px;
  }
  & > .c > .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 9.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & > .c > .v {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    font-size: 19px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
  & > .c > .v.pos { color: ${p => p.theme.colors.positive}; }
  & > .c > .v.neg { color: ${p => p.theme.colors.negative}; }
`;

function BacktestChart() {
  const W = 720, H = 240;
  const strat = [100,103,108,112,115,118,123,128,135,141,148,154,162,170,177,182,187,196,205,212,209,200,194,202,210,212];
  const qqq   = [100,102,105,108,110,113,116,119,124,128,131,135,140,144,148,152,157,162,166,170,168,164,162,168,172,172];
  const rot   = [100,101,106,110,112,114,118,121,126,130,134,140,146,151,156,158,162,168,173,177,176,172,168,174,180,184];
  const mk = (arr: number[]) => arr.map((v, i) => {
    const x = (i / (arr.length - 1)) * W;
    const y = H - ((v - 95) / (220 - 95)) * (H - 20) - 10;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none">
      {[0.25, 0.5, 0.75].map(p => (
        <line key={p} x1={0} x2={W} y1={H * p} y2={H * p} stroke="var(--qt-border-subtle)" strokeWidth={0.5} />
      ))}
      <polyline points={mk(qqq)}   fill="none" stroke="var(--qt-info, #00d9ff)"  strokeWidth={1.2} strokeDasharray="3 3" opacity={0.7} />
      <polyline points={mk(rot)}   fill="none" stroke="var(--qt-amber)"          strokeWidth={1.2} strokeDasharray="2 4" opacity={0.6} />
      <polyline points={mk(strat)} fill="none" stroke="var(--qt-brand)"          strokeWidth={1.8} />
    </svg>
  );
}

export function Act4BacktestPanel() {
  const { backtest, strategy } = LP_ANCHORS;
  return (
    <Container id="evidence">
      <Act>
        <div className="copy">
          <Step><span className="num">Act 4</span>Backtest</Step>
          <H2>Years of receipts<br />before a dollar of yours.</H2>
          <Sub>
            Replay 1–10 years of historical bar data. The council votes on each tick as if it were live.
            You get the equity curve, the decision trail, every hold and every override.
          </Sub>
          <Sub>
            Every backtest renders against benchmarks. Below: Tech ETF — MA vs. QQQ buy &amp; hold and the
            public Megacaps Rotor strategy on the same 5-year window. Click any tick in-app to restart the council
            from that point with a tweaked rule.
          </Sub>
        </div>

        <Anchor className="anchor">
          <Head>
            <div className="ttl">
              <h3>5-year backtest · Tech ETF — MA</h3>
              <span className="count">{backtest.range} · {strategy.version}</span>
            </div>
            <div className="tabs" aria-hidden="true">
              <span className="on">All</span>
              <span>3Y</span>
              <span>1Y</span>
            </div>
          </Head>
          <ChartFrame>
            <BacktestChart />
            <ChartLegend>
              <span className="strat">
                <span className="sw" />
                <span className="pct">+111.64%</span>
                <span className="label">Tech ETF · MA (this strategy)</span>
              </span>
              <span className="rot">
                <span className="sw" />
                <span className="pct">+84.20%</span>
                <span className="label">Megacaps · Rotor</span>
              </span>
              <span className="qqq">
                <span className="sw" />
                <span className="pct">+72.00%</span>
                <span className="label">QQQ buy &amp; hold</span>
              </span>
            </ChartLegend>
          </ChartFrame>
          <CashRow>
            <div className="c"><div className="lbl">Total return</div><div className="v pos">+{backtest.totalReturn.toFixed(2)}%</div></div>
            <div className="c"><div className="lbl">Sharpe</div><div className="v">{backtest.sharpe.toFixed(2)}</div></div>
            <div className="c"><div className="lbl">Max DD</div><div className="v neg">{backtest.maxDd.toFixed(2)}%</div></div>
          </CashRow>
        </Anchor>
      </Act>
    </Container>
  );
}
