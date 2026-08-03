'use client';

import { useState, useCallback } from 'react';
import styled from 'styled-components';
import { TerminalText } from '../terminal/TerminalText';
import { boxGlow } from '@/app/styles/theme/effects';

const PanelContainer = styled.div`
  display: flex;
  flex-direction: column;
  background-color: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
  min-height: 0;
  ${boxGlow}
`;

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  background-color: ${p => p.theme.colors.primary}11;
  flex-shrink: 0;
`;

const HeaderTitle = styled(TerminalText)`
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
`;

const SectionsContainer = styled.div`
  overflow-y: auto;
  min-height: 0;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.primary}40;
    border-radius: 3px;
    &:hover {
      background: ${p => p.theme.colors.primary}60;
    }
  }
`;

const GroupHeader = styled.div`
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.md};
  background-color: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border}40;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.1em;
`;

const Section = styled.div`
  border-bottom: 1px solid ${p => p.theme.colors.border}40;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};

  &:last-child {
    border-bottom: none;
  }
`;

const SectionTitle = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.primary};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const Desc = styled.p`
  margin: 0 0 ${p => p.theme.spacing.xs} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
`;

const CodeBlockWrapper = styled.div`
  position: relative;
  margin: ${p => p.theme.spacing.xs} 0;

  &:hover button {
    opacity: 1;
  }
`;

const CodeBlockPre = styled.pre`
  margin: 0;
  padding: ${p => p.theme.spacing.sm};
  padding-right: 36px;
  background-color: ${p => p.theme.colors.background};
  border-left: 2px solid ${p => p.theme.colors.primary}60;
  border-radius: ${p => p.theme.borderRadius.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.text};
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre;
`;

const CopyBtn = styled.button`
  opacity: 0;
  position: absolute;
  top: 4px;
  right: 4px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: 3px 5px;
  cursor: pointer;
  color: ${p => p.theme.colors.textMuted};
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s ease, color 0.15s ease;

  &:hover {
    color: ${p => p.theme.colors.primary};
  }

  svg {
    width: 14px;
    height: 14px;
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
`;

const CheckMark = styled.span`
  color: ${p => p.theme.colors.success};
  font-size: 12px;
  line-height: 1;
`;

const RefCode = styled.code`
  color: ${p => p.theme.colors.text};
  background-color: ${p => p.theme.colors.background};
  padding: 1px 4px;
  border-radius: 2px;
  font-size: inherit;
`;

const DefList = styled.dl`
  margin: ${p => p.theme.spacing.xs} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
`;

const DefTerm = styled.dt`
  color: ${p => p.theme.colors.text};
  margin-top: ${p => p.theme.spacing.xs};

  &:first-child {
    margin-top: 0;
  }
`;

const DefDesc = styled.dd`
  margin: 0;
  padding-left: ${p => p.theme.spacing.sm};
`;

const BulletList = styled.ul`
  margin: ${p => p.theme.spacing.xs} 0;
  padding-left: ${p => p.theme.spacing.md};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.6;

  li {
    margin-bottom: 2px;
  }
`;

function CodeBlock({ children }: { children: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(children);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }, [children]);

  return (
    <CodeBlockWrapper>
      <CodeBlockPre>{children}</CodeBlockPre>
      <CopyBtn onClick={handleCopy} type="button" title="Copy to clipboard">
        {copied ? (
          <CheckMark>✓</CheckMark>
        ) : (
          <svg viewBox="0 0 24 24">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
          </svg>
        )}
      </CopyBtn>
    </CodeBlockWrapper>
  );
}

export function CheatsheetPanel() {
  return (
    <PanelContainer>
      <PanelHeader>
        <HeaderTitle size="sm">Cheatsheet</HeaderTitle>
      </PanelHeader>
      <SectionsContainer>
        {/* How it works */}
        <Section>
          <SectionTitle>How it works</SectionTitle>
          <Desc>
            Frontmatter (<RefCode>---</RefCode> block) = machine-parsed config.
            Exact field names and values matter.
          </Desc>
          <Desc>
            Markdown body (after <RefCode>---</RefCode>) = free-form instructions.
            Write naturally &mdash; the AI agents read this to decide how to trade.
          </Desc>
        </Section>

        {/* ── FRONTMATTER CONFIG ── */}
        <GroupHeader>Frontmatter config</GroupHeader>

        {/* Stock schedule */}
        <Section>
          <SectionTitle>Stock schedule</SectionTitle>
          <CodeBlock>{`live_schedule:
  timezone: "America/New_York"
  trading_days: [MONDAY, FRIDAY]
  evaluation_times: ["10:00", "14:00"]`}</CodeBlock>
          <Desc>
            <RefCode>trading_days</RefCode> accepts full names (MONDAY) or 3-letter
            abbreviations (MON). Omit for Mon&ndash;Fri default.
          </Desc>
        </Section>

        {/* Crypto schedule */}
        <Section>
          <SectionTitle>Crypto schedule</SectionTitle>
          <CodeBlock>{`live_schedule:
  interval: 1h`}</CodeBlock>
          <Desc>
            Valid intervals: <RefCode>30s</RefCode> <RefCode>5m</RefCode>{' '}
            <RefCode>1h</RefCode> <RefCode>6h</RefCode> <RefCode>1d</RefCode>{' '}
            <RefCode>1w</RefCode>
          </Desc>
        </Section>

        {/* Backtest schedule */}
        <Section>
          <SectionTitle>Backtest schedule</SectionTitle>
          <Desc>
            Optional. Defaults to Wednesday 10:00 (stocks) or 1w (crypto).
          </Desc>
          <CodeBlock>{`backtest_schedule:
  timezone: "America/New_York"
  trading_days: [WEDNESDAY]
  evaluation_times: ["10:00"]`}</CodeBlock>
        </Section>

        {/* Consensus & agents */}
        <Section>
          <SectionTitle>Consensus &amp; agents</SectionTitle>
          <CodeBlock>{`consensus_method: MAJORITY`}</CodeBlock>
          <BulletList>
            <li><RefCode>MAJORITY</RefCode> &mdash; agent votes determine the final action directly; no orchestrator LLM call. Ties default to HOLD. HOLD agents with confidence below <RefCode>consensus_threshold</RefCode> abstain (don&apos;t vote).</li>
            <li><RefCode>ORCHESTRATOR_OVERRIDE</RefCode> &mdash; an LLM orchestrator reviews all agent recommendations and makes the final decision.</li>
          </BulletList>
          <Desc>
            All 3 agents run by default (TECHNICAL, FUNDAMENTAL, SENTIMENT).
            Only add <RefCode>agents:</RefCode> to use a subset:
          </Desc>
          <CodeBlock>{`agents:
  - TECHNICAL
  - FUNDAMENTAL`}</CodeBlock>
          <Desc>ORCHESTRATOR runs only with <RefCode>ORCHESTRATOR_OVERRIDE</RefCode> &mdash; don&apos;t list it in agents.</Desc>
        </Section>

        {/* Other fields */}
        <Section>
          <SectionTitle>Other fields</SectionTitle>
          <BulletList>
            <li><RefCode>consensus_threshold: 0.6</RefCode> &mdash; 0.0 to 1.0; HOLD agents below this confidence abstain in MAJORITY voting</li>
            <li><RefCode>reserve_cash_percentage: 0.2</RefCode> &mdash; 0.0 to 1.0, cash reserve</li>
            <li><RefCode>position_limits:</RefCode> &mdash; ticker to max allocation. A BUY without an explicit quantity fills straight to this cap &mdash; it is the default order size, not a safety net</li>
            <li><RefCode>position_cooldown: 14d</RefCode> &mdash; skip consensus after BUY/SELL (e.g. 14d, 3w, 1h)</li>
            <li><RefCode>llm_model:</RefCode> &mdash; pin a specific model</li>
            <li><RefCode>llm_models:</RefCode> &mdash; per-agent model overrides</li>
          </BulletList>
        </Section>

        {/* ── AGENT GUIDANCE (markdown body) ── */}
        <GroupHeader>Agent guidance (markdown body)</GroupHeader>

        {/* Technical agent */}
        <Section>
          <SectionTitle>Technical agent</SectionTitle>
          <Desc>Mention indicators and thresholds in your markdown. The agent looks for:</Desc>
          <DefList>
            <DefTerm>RSI</DefTerm>
            <DefDesc>0&ndash;100. &lt;30 oversold, &gt;70 overbought</DefDesc>
            <DefTerm>MACD</DefTerm>
            <DefDesc>Above signal line = bullish</DefDesc>
            <DefTerm>SMA</DefTerm>
            <DefDesc>20/50/200 period. SMA-50 &gt; SMA-200 = golden cross</DefDesc>
            <DefTerm>Bollinger Bands</DefTerm>
            <DefDesc>Price below lower band = oversold</DefDesc>
          </DefList>
        </Section>

        {/* Fundamental agent */}
        <Section>
          <SectionTitle>Fundamental agent</SectionTitle>
          <Desc>
            Describe what financial data matters: earnings surprises, P/E ratios,
            revenue growth, sector trends, macro conditions.
          </Desc>
        </Section>

        {/* Sentiment agent */}
        <Section>
          <SectionTitle>Sentiment agent</SectionTitle>
          <Desc>
            Guide what sentiment sources to weigh: news headlines, social media buzz,
            fear &amp; greed index, analyst upgrades/downgrades.
          </Desc>
        </Section>

      </SectionsContainer>
    </PanelContainer>
  );
}
