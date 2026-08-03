'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
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
  strong { color: ${p => p.theme.colors.foreground}; }
  & + & { margin-top: 14px; }
`;

const CtaRow = styled.div`
  display: flex; gap: 12px; align-items: center; margin-top: 28px; flex-wrap: wrap;
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
  transition: border-color 120ms ease;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
  & > .gly { font-family: ${p => p.theme.typography.fontFamily.mono}; }
`;

const Panel = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
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
  button.copy {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
    background: transparent;
    border: 1px solid ${p => p.theme.colors.border};
    padding: 3px 8px;
    border-radius: 2px;
    cursor: pointer;
  }
  button.copy:hover { color: ${p => p.theme.colors.foreground}; border-color: ${p => p.theme.colors.fgMuted}; }
`;

const Frontmatter = styled.pre`
  margin: 0;
  padding: 14px 20px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  line-height: 1.6;
  color: ${p => p.theme.colors.fgMuted};
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  white-space: pre;
  overflow-x: auto;

  & .k { color: ${p => p.theme.colors.info}; }
  & .v { color: ${p => p.theme.colors.fgStrong}; }
  & .delim { color: ${p => p.theme.colors.fgSubtle}; }
`;

const Body = styled.div`
  padding: 18px 22px 22px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.65;
  color: ${p => p.theme.colors.foreground};

  h1 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 19px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    letter-spacing: -0.005em;
    margin: 0 0 14px;
    padding-bottom: 12px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  }
  h2 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.foreground};
    margin: 18px 0 6px;
    text-transform: none;
    letter-spacing: 0;
    display: flex; align-items: baseline; gap: 8px;
  }
  h2::before {
    content: '##';
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    color: ${p => p.theme.colors.fgDimmed};
    letter-spacing: 0;
  }
  h2:first-of-type { margin-top: 0; }
  p { margin: 0 0 6px; color: ${p => p.theme.colors.foreground}; }
  ul { margin: 0 0 4px; padding-left: 0; list-style: none; }
  ul li {
    position: relative;
    padding-left: 18px;
    margin-bottom: 3px;
    color: ${p => p.theme.colors.foreground};
    font-size: 13.5px;
  }
  ul li::before {
    content: '—';
    color: ${p => p.theme.colors.fgDimmed};
    position: absolute;
    left: 0;
    font-family: ${p => p.theme.typography.fontFamily.mono};
  }
  strong { color: ${p => p.theme.colors.fgStrong}; font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
`;

const FRONTMATTER = `---
version: v3.2
consensus_method: MAJORITY
consensus_threshold: 0.65
evaluation_interval_seconds: 3600
stock_tickers: [QQQ]
market_hours:
  timezone: "America/New_York"
  evaluation_times: ["10:00", "14:00"]
---`;

/** Full strategy markdown mirrored by the panel below — what the Copy button puts on the clipboard. */
const STRATEGY_MARKDOWN = `${FRONTMATTER}

# Tech ETF — Moving Average Crossover

A trend-following QQQ strategy. Enter on confirmed golden crosses, exit on death crosses or trailing-stop hits. The council weighs technical signals against earnings windows and macro sentiment.

## Entry signals

- 50-day SMA crosses above 200-day SMA (golden cross)
- Confirmed by 2 consecutive daily closes
- RSI not in overbought territory (< 70)

## Exit signals

- 50-day SMA crosses below 200-day SMA (death cross)
- 8% stop loss from entry
- Trailing stop at 20% below peak profit

## Risk caps

- Max 95% of portfolio in QQQ
- 5% cash floor at all times
- Drawdown halt at 25% from peak

## Council

- **Technical** · weight 1.0 · reads price action + indicators
- **Fundamental** · weight 1.0 · reads sector earnings, ETF flows
- **Sentiment** · weight 1.0 · reads news + social
- **Orchestrator** can override on earnings eve or VIX spike
`;

export function Act3StrategyPanel() {
  const { strategy } = LP_ANCHORS;
  const [copied, setCopied] = useState(false);
  const copiedTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (copiedTimer.current) clearTimeout(copiedTimer.current);
  }, []);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(STRATEGY_MARKDOWN);
      setCopied(true);
      if (copiedTimer.current) clearTimeout(copiedTimer.current);
      copiedTimer.current = setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard unavailable (permissions / insecure context) — leave label as-is.
    }
  };

  return (
    <Container id="strategies">
      <Act>
        <div>
          <Step><span className="num">Act 3</span>Author the strategy</Step>
          <H2>A markdown brief.<br />A short YAML config.</H2>
          <Sub>
            A QuorumTrading strategy is a markdown document with a short YAML frontmatter. The frontmatter holds
            the machine-readable rules — evaluation cadence, tickers, consensus method. The body is plain English:
            entry signals, exits, risk caps, what each agent should pay attention to.
          </Sub>
          <Sub>
            <strong>Three agents read both before they vote.</strong> Fork from the public library or start with
            a blank page. Revisions are stamped, diff-able, and one click to roll back.
          </Sub>
          <CtaRow>
            <CtaGhost href="/strategies/public">Browse public strategies<span className="gly">→</span></CtaGhost>
          </CtaRow>
        </div>

        <Panel>
          <PanelHead>
            <div className="ttl">
              <h3>strategy.md</h3>
              <span className="count">markdown + yaml · {strategy.version} · {strategy.ticker}</span>
            </div>
            <button className="copy" type="button" onClick={handleCopy} aria-label="Copy strategy markdown">
              {copied ? 'Copied' : 'Copy'}
            </button>
          </PanelHead>
          <Frontmatter dangerouslySetInnerHTML={{ __html: FRONTMATTER
            .replace(/(version|consensus_method|consensus_threshold|evaluation_interval_seconds|stock_tickers|market_hours|timezone|evaluation_times)/g, '<span class="k">$1</span>')
            .replace(/(:\s*)(v3\.2|MAJORITY|0\.65|3600|\[QQQ\]|"America\/New_York"|\["10:00", "14:00"\])/g, '$1<span class="v">$2</span>')
            .replace(/(^---$)/gm, '<span class="delim">$1</span>')
          }} />
          <Body>
            <h1>Tech ETF — Moving Average Crossover</h1>
            <p>A trend-following QQQ strategy. Enter on confirmed golden crosses, exit on death crosses or trailing-stop hits.
              The council weighs technical signals against earnings windows and macro sentiment.</p>

            <h2>Entry signals</h2>
            <ul>
              <li>50-day SMA crosses above 200-day SMA (golden cross)</li>
              <li>Confirmed by 2 consecutive daily closes</li>
              <li>RSI not in overbought territory (&lt; 70)</li>
            </ul>

            <h2>Exit signals</h2>
            <ul>
              <li>50-day SMA crosses below 200-day SMA (death cross)</li>
              <li>8% stop loss from entry</li>
              <li>Trailing stop at 20% below peak profit</li>
            </ul>

            <h2>Risk caps</h2>
            <ul>
              <li>Max 95% of portfolio in QQQ</li>
              <li>5% cash floor at all times</li>
              <li>Drawdown halt at 25% from peak</li>
            </ul>

            <h2>Council</h2>
            <ul>
              <li><strong>Technical</strong> · weight 1.0 · reads price action + indicators</li>
              <li><strong>Fundamental</strong> · weight 1.0 · reads sector earnings, ETF flows</li>
              <li><strong>Sentiment</strong> · weight 1.0 · reads news + social</li>
              <li><strong>Orchestrator</strong> can override on earnings eve or VIX spike</li>
            </ul>
          </Body>
        </Panel>
      </Act>
    </Container>
  );
}
