'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import styled, { keyframes, css } from 'styled-components';
import { useBrailleSpinner } from '@/app/lib/hooks';
import { SectionTitle, SectionSubtitle } from '../shared';

// ============================================================================
// Terminal conversation script — Claude Code interacting with QuorumTrading MCP
// ============================================================================

interface TerminalLine {
  type: 'prompt' | 'claude' | 'tool-call' | 'tool-result' | 'success' | 'divider' | 'empty';
  text: string;
  delay?: number; // ms pause before this line appears
}

const TERMINAL_SCRIPT: TerminalLine[] = [
  // --- User prompt ---
  { type: 'prompt', text: '> Backtest a BTC momentum strategy over the last 90 days', delay: 0 },
  { type: 'empty', text: '', delay: 400 },

  // --- Claude thinking + tool calls ---
  { type: 'claude', text: "I'll backfill BTC price data, create a momentum strategy, and run a backtest.", delay: 600 },
  { type: 'empty', text: '', delay: 300 },

  // Step 1: Backfill
  { type: 'tool-call', text: '⚡ backfill_price_data(ticker: "BTC", asset: CRYPTO, days: 120)', delay: 400 },
  { type: 'tool-result', text: '   ✓ 120 days of OHLCV data loaded (2,880 bars)', delay: 800 },
  { type: 'empty', text: '', delay: 200 },

  // Step 2: Indicators
  { type: 'tool-call', text: '⚡ get_indicators(ticker: "BTC")', delay: 300 },
  { type: 'tool-result', text: '   RSI-14: 58.3 | SMA-50: $94,210 | MACD: bullish crossover', delay: 700 },
  { type: 'empty', text: '', delay: 200 },

  // Step 3: Create strategy
  { type: 'tool-call', text: '⚡ create_strategy(name: "BTC Momentum Rider")', delay: 300 },
  { type: 'tool-result', text: '   ✓ Strategy created: id=a3f8..c2d1', delay: 600 },
  { type: 'empty', text: '', delay: 200 },

  // Step 4: Create + start simulation
  { type: 'tool-call', text: '⚡ create_simulation(strategy: a3f8..c2d1, symbol: BTC, cash: $10,000)', delay: 300 },
  { type: 'tool-result', text: '   ✓ Simulation created: id=7e2b..f914', delay: 500 },
  { type: 'tool-call', text: '⚡ start_simulation(id: 7e2b..f914)', delay: 200 },
  { type: 'tool-result', text: '   ⏳ Backtesting 90 days... 4 AI agents deliberating', delay: 1200 },
  { type: 'empty', text: '', delay: 200 },

  // Step 5: Results
  { type: 'tool-call', text: '⚡ query_portfolio_snapshots(id: 7e2b..f914)', delay: 400 },
  { type: 'empty', text: '', delay: 300 },
  { type: 'success', text: '┌─────────────────────────────────────────────┐', delay: 100 },
  { type: 'success', text: '│  BTC Momentum Rider — 90-Day Backtest       │', delay: 80 },
  { type: 'success', text: '│                                             │', delay: 80 },
  { type: 'success', text: '│  Return:      +18.4%    ($10,000 → $11,840) │', delay: 80 },
  { type: 'success', text: '│  Win Rate:    72%       (13/18 trades)      │', delay: 80 },
  { type: 'success', text: '│  Sharpe:      1.84      Max DD: -6.2%       │', delay: 80 },
  { type: 'success', text: '│  Consensus:   WEIGHTED  (4 agents)          │', delay: 80 },
  { type: 'success', text: '└─────────────────────────────────────────────┘', delay: 80 },
  { type: 'empty', text: '', delay: 400 },
  { type: 'claude', text: 'The strategy shows strong results. Want me to go live with paper trading?', delay: 500 },
];

// ============================================================================
// Animations
// ============================================================================

const blink = keyframes`
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
`;

const fadeInUp = keyframes`
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
`;

const shimmer = keyframes`
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
`;

const subtleGlow = keyframes`
  0%, 100% {
    box-shadow:
      var(--qt-shadow-card-premium),
      0 0 60px var(--qt-glow-10);
  }
  50% {
    box-shadow:
      var(--qt-shadow-card-premium),
      0 0 80px var(--qt-glow-10);
  }
`;

// ============================================================================
// Styled Components
// ============================================================================

const Section = styled.section`
  padding: ${p => p.theme.spacing['3xl']} ${p => p.theme.spacing.xl};
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  position: relative;

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing['2xl']} ${p => p.theme.spacing.md};
  }
`;

const SectionHeader = styled.div`
  text-align: center;
  margin-bottom: ${p => p.theme.spacing['2xl']};
`;

const TerminalContainer = styled.div`
  max-width: 720px;
  margin: 0 auto;
  animation: ${fadeInUp} 0.6s ease-out both;
`;

const TerminalWindow = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.lg};
  overflow: hidden;
  animation: ${subtleGlow} 4s ease-in-out infinite;
`;

const TerminalTitleBar = styled.div`
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  gap: 12px;

  @media (max-width: 768px) {
    padding: 10px 12px;
    gap: 8px;
  }
`;

const TrafficLights = styled.div`
  display: flex;
  gap: 7px;
  flex-shrink: 0;
`;

const Dot = styled.span<{ $color: string }>`
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: ${p => p.$color};
  opacity: 0.85;
`;

const TitleBarCenter = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
`;

const TitleBarText = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.textDimmed};
  letter-spacing: 0.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  @media (max-width: 768px) {
    font-size: 11px;
  }
`;

const TerminalBody = styled.div`
  padding: 20px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  line-height: 1.7;
  height: 440px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;

  /* Hide scrollbar for clean look, still scrollable */
  scrollbar-width: thin;
  scrollbar-color: ${p => p.theme.colors.border} transparent;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.border};
    border-radius: 2px;
  }

  @media (max-width: 768px) {
    padding: 14px;
    font-size: 11px;
    height: 380px;
  }
`;

const TerminalContent = styled.div`
  margin-top: auto;
`;

const Line = styled.div<{ $type: TerminalLine['type'] }>`
  min-height: 1.7em;
  white-space: pre-wrap;
  word-break: break-word;

  ${p => {
    switch (p.$type) {
      case 'prompt':
        return css`
          color: ${p.theme.colors.terminalGreen};
          font-weight: 600;
        `;
      case 'claude':
        return css`
          color: ${p.theme.colors.textLight};
        `;
      case 'tool-call':
        return css`
          color: ${p.theme.colors.terminalBlue};
          opacity: 0.9;
        `;
      case 'tool-result':
        return css`
          color: ${p.theme.colors.textMuted};
        `;
      case 'success':
        return css`
          color: ${p.theme.colors.terminalGreen};
          opacity: 0.95;
          white-space: pre;

          @media (max-width: 768px) {
            font-size: 9.5px;
          }
        `;
      case 'empty':
        return css`
          height: 0.5em;
          min-height: 0.5em;
        `;
      default:
        return css`
          color: ${p.theme.colors.textDimmed};
        `;
    }
  }}
`;

const Cursor = styled.span<{ $active?: boolean }>`
  display: inline-block;
  width: 8px;
  height: 1.1em;
  background: ${p => p.theme.colors.terminalGreen};
  margin-left: 1px;
  vertical-align: text-bottom;
  animation: ${blink} 1s step-end infinite;
`;

const BrailleSpinner = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
  font-weight: 600;
  font-family: monospace;
`;

const TitleBarSpacer = styled.div`
  width: 52px;
  flex-shrink: 0;

  @media (max-width: 768px) {
    width: 0;
    display: none;
  }
`;

const McpBadge = styled.span`
  display: inline-block;
  padding: 2px 8px;
  background: linear-gradient(135deg, ${p => p.theme.colors.hover}, transparent);
  border: 1px solid ${p => p.theme.colors.borderHighlight}4d;
  border-radius: 4px;
  font-size: 10px;
  color: ${p => p.theme.colors.terminalBlue};
  letter-spacing: 1.5px;
  text-transform: uppercase;
  font-weight: 700;
  margin-left: 8px;
  vertical-align: middle;
`;

const BottomBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: ${p => p.theme.colors.background};
  border-top: 1px solid ${p => p.theme.colors.border};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.textDimmed};

  @media (max-width: 768px) {
    padding: 6px 12px;
    font-size: 10px;
  }
`;

const StatusDot = styled.span<{ $color: string }>`
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${p => p.$color};
  margin-right: 6px;
`;

const ToolBadges = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.lg};
  justify-content: center;
  margin-top: ${p => p.theme.spacing.xl};
  flex-wrap: wrap;

  @media (max-width: 768px) {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: ${p => p.theme.spacing.sm};
    max-width: 320px;
    margin-left: auto;
    margin-right: auto;
  }
`;

const ToolBadge = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  transition: border-color 0.2s ease;

  &:hover {
    border-color: ${p => p.theme.colors.terminalBlue}66;
  }
`;

const ToolBadgeIcon = styled.span`
  font-size: 14px;
`;

const ToolBadgeName = styled.span`
  color: ${p => p.theme.colors.textLight};
  font-weight: 500;
`;

// ============================================================================
// Component
// ============================================================================

interface McpTerminalHeroProps {
  /** When true, renders just the terminal demo without section wrapper, title, or tool badges */
  embedded?: boolean;
}

export function McpTerminalHero({ embedded = false }: McpTerminalHeroProps) {
  const [visibleLines, setVisibleLines] = useState<number>(0);
  const [typingLineIndex, setTypingLineIndex] = useState<number>(0);
  const [typedChars, setTypedChars] = useState<number>(0);
  const [isTypingLine, setIsTypingLine] = useState<boolean>(true);
  const [cycleKey, setCycleKey] = useState(0);
  const bodyRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    if (bodyRef.current) {
      bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
    }
  }, [visibleLines, typedChars]);

  // Main animation driver
  useEffect(() => {
    if (typingLineIndex >= TERMINAL_SCRIPT.length) {
      // All lines done — hold, then restart
      const timer = setTimeout(() => {
        setVisibleLines(0);
        setTypingLineIndex(0);
        setTypedChars(0);
        setIsTypingLine(true);
        setCycleKey(k => k + 1);
      }, 6000);
      return () => clearTimeout(timer);
    }

    const currentLine = TERMINAL_SCRIPT[typingLineIndex];
    const lineDelay = currentLine.delay ?? 200;

    if (currentLine.type === 'empty' || currentLine.type === 'divider') {
      // Non-typed lines: just appear after delay
      const timer = setTimeout(() => {
        setVisibleLines(typingLineIndex + 1);
        setTypingLineIndex(i => i + 1);
        setTypedChars(0);
      }, lineDelay);
      return () => clearTimeout(timer);
    }

    if (currentLine.type === 'prompt') {
      // User prompt: type character by character
      if (isTypingLine) {
        // Wait for initial delay before starting to type
        if (typedChars === 0 && lineDelay > 0) {
          const timer = setTimeout(() => {
            setVisibleLines(typingLineIndex + 1);
            setTypedChars(1);
          }, lineDelay);
          return () => clearTimeout(timer);
        }

        const promptText = currentLine.text.slice(2); // remove "> "
        if (typedChars <= promptText.length) {
          const timer = setTimeout(() => {
            setTypedChars(c => c + 1);
          }, 35 + Math.random() * 25); // realistic typing speed
          return () => clearTimeout(timer);
        } else {
          // Done typing this prompt
          setIsTypingLine(false);
          setTypingLineIndex(i => i + 1);
          setTypedChars(0);
          setIsTypingLine(true);
          return;
        }
      }
    } else {
      // Claude/tool lines: appear after delay (no char-by-char)
      const timer = setTimeout(() => {
        setVisibleLines(typingLineIndex + 1);
        setTypingLineIndex(i => i + 1);
        setTypedChars(0);
      }, lineDelay);
      return () => clearTimeout(timer);
    }
  }, [typingLineIndex, typedChars, isTypingLine, cycleKey]);

  const isComplete = typingLineIndex >= TERMINAL_SCRIPT.length;
  const isThinking = !isComplete &&
    typingLineIndex > 0 &&
    TERMINAL_SCRIPT[typingLineIndex]?.type !== 'prompt' &&
    TERMINAL_SCRIPT[typingLineIndex]?.type !== 'empty' &&
    typingLineIndex < TERMINAL_SCRIPT.length;
  // Keep spinner animating continuously while the script runs — don't tie to
  // isThinking which toggles per-line and resets the animation to frame 0.
  const spinnerFrame = useBrailleSpinner(!isComplete && visibleLines > 0);

  const terminalDemo = (
    <TerminalContainer>
        <TerminalWindow>
          <TerminalTitleBar>
            <TrafficLights>
              <Dot $color="#ff5f56" />
              <Dot $color="#ffbd2e" />
              <Dot $color="#27ca40" />
            </TrafficLights>
            <TitleBarCenter>
              <TitleBarText>claude — quorumtrading</TitleBarText>
              <McpBadge>MCP</McpBadge>
            </TitleBarCenter>
            <TitleBarSpacer />
          </TerminalTitleBar>

          <TerminalBody ref={bodyRef}>
            <TerminalContent>
              {TERMINAL_SCRIPT.map((line, i) => {
                // Only show lines up to visibleLines (fully revealed) + the currently-typing line
                const isCurrentlyTyping = i === typingLineIndex && line.type === 'prompt' && typedChars > 0;
                if (i >= visibleLines && !isCurrentlyTyping) return null;

                if (line.type === 'empty') {
                  return <Line key={`${cycleKey}-${i}`} $type="empty">&nbsp;</Line>;
                }

                // Currently-typing prompt line
                if (isCurrentlyTyping && line.type === 'prompt') {
                  const promptText = line.text.slice(2);
                  const typed = promptText.slice(0, typedChars);
                  return (
                    <Line key={`${cycleKey}-${i}`} $type="prompt">
                      {'> '}{typed}<Cursor />
                    </Line>
                  );
                }

                return (
                  <Line key={`${cycleKey}-${i}`} $type={line.type}>
                    {line.text}
                  </Line>
                );
              })}

              {/* Show thinking indicator while Claude is working */}
              {isThinking && visibleLines > 0 && (
                <Line $type="divider">
                  <BrailleSpinner>{spinnerFrame}</BrailleSpinner> thinking
                </Line>
              )}
            </TerminalContent>
          </TerminalBody>

          <BottomBar>
            <div>
              <StatusDot $color={isComplete ? 'var(--qt-agent-technical)' : 'var(--qt-action-hold)'} />
              {isComplete ? 'Done' : 'Running'}
            </div>
            <div>quorumtrading MCP · 15 tools</div>
          </BottomBar>
        </TerminalWindow>
    </TerminalContainer>
  );

  if (embedded) {
    return terminalDemo;
  }

  return (
    <Section>
      <SectionHeader>
        <SectionTitle $color="cyan" $centered $size="xl">
          Trade from Your Terminal
        </SectionTitle>
        <SectionSubtitle $centered>
          Connect Claude Code, Cursor, or any AI agent via MCP. Describe a strategy in plain English — AI agents handle the rest.
        </SectionSubtitle>
      </SectionHeader>

      {terminalDemo}

      <ToolBadges>
        <ToolBadge>
          <ToolBadgeIcon>⌨️</ToolBadgeIcon>
          <ToolBadgeName>Claude Code</ToolBadgeName>
        </ToolBadge>
        <ToolBadge>
          <ToolBadgeIcon>🖱️</ToolBadgeIcon>
          <ToolBadgeName>Cursor</ToolBadgeName>
        </ToolBadge>
        <ToolBadge>
          <ToolBadgeIcon>🪟</ToolBadgeIcon>
          <ToolBadgeName>Windsurf</ToolBadgeName>
        </ToolBadge>
        <ToolBadge>
          <ToolBadgeIcon>🔌</ToolBadgeIcon>
          <ToolBadgeName>Any MCP Client</ToolBadgeName>
        </ToolBadge>
      </ToolBadges>
    </Section>
  );
}
