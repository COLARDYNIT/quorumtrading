'use client';

import { useState, useEffect, useCallback } from 'react';
import styled, { keyframes, css } from 'styled-components';
import Link from 'next/link';
import { fadeInUp, blink } from '../../styles/animations';
import { SectionTitle, SectionSubtitle } from '../shared';

// Strategy configurations
const CRYPTO_STRATEGY = {
  filename: 'btc-dip-buyer.md',
  preLoaded: [
    '# BTC Dip Buyer',
    '',
    'BUY when:',
    '- Price drops 5% in 24h',
    '- Social sentiment not fearful',
  ],
  typed: [
    '- Council consensus > 75%',
    '',
    'SELL when:',
    '- Breaking negative news',
    '- Or: +15% profit',
  ],
};

const STOCK_STRATEGY = {
  filename: 'nvda-momentum.md',
  preLoaded: [] as string[],
  typed: [
    '# NVDA Momentum',
    '',
    'BUY when:',
    '- Earnings beat estimates',
    '- RSI crosses above 30',
    '- Council consensus > 70%',
    '',
    'SELL when:',
    '- P/E exceeds sector by 40%',
    '- Or: +20% profit',
  ],
};

// Animation phases for spotlight relay
type AnimationPhase = 'left-typing' | 'right-typing' | 'both-complete' | 'resetting';

// Spotlight glow animation (uses CSS custom properties for theme support)
const spotlightPulse = keyframes`
  0%, 100% { box-shadow: var(--qt-shadow-card-premium), 0 0 20px var(--qt-glow-10); }
  50% { box-shadow: var(--qt-shadow-card-premium), 0 0 30px var(--qt-glow-20); }
`;

// Styled Components
const Section = styled.section`
  padding: ${p => p.theme.spacing['3xl']} ${p => p.theme.spacing.xl};
  text-align: center;
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.border};

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing['2xl']} ${p => p.theme.spacing.md};
  }
`;

const Container = styled.div`
  max-width: 900px;
  margin: 0 auto;
`;

const EditorsContainer = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.lg};
  justify-content: center;
  margin-top: ${p => p.theme.spacing['2xl']};
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;

  @media (max-width: 900px) {
    flex-direction: column;
    align-items: center;
    max-width: 420px;
  }
`;

const EditorWrapper = styled.div<{ $delay?: number }>`
  flex: 1;
  max-width: 420px;
  min-width: 320px;
  animation: ${fadeInUp} 0.6s ease-out both;
  animation-delay: ${p => p.$delay || 0}ms;

  @media (max-width: 900px) {
    width: 100%;
    max-width: 420px;
  }
`;

interface EditorWindowProps {
  $spotlight?: boolean;
  $dimmed?: boolean;
}

const EditorWindow = styled.div<EditorWindowProps>`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.$spotlight ? p.theme.colors.terminalGreen : p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.lg};
  overflow: hidden;
  transition: all 0.4s ease;

  ${p => p.$spotlight && css`
    animation: ${spotlightPulse} 2s ease-in-out infinite;
  `}

  ${p => p.$dimmed && css`
    opacity: 0.6;
    box-shadow: var(--qt-shadow-card);
  `}

  ${p => !p.$spotlight && !p.$dimmed && css`
    box-shadow: var(--qt-shadow-card-premium);
  `}
`;

const EditorHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

const EditorTitleText = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
`;

const WindowControls = styled.div`
  display: flex;
  gap: 7px;
`;

const WindowDot = styled.span<{ $color: string }>`
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: ${p => p.$color};
  opacity: 0.35;
`;

const EditorContent = styled.div`
  padding: ${p => p.theme.spacing.md};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  line-height: 1.6;
  text-align: left;
  min-height: 220px;

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing.sm};
    font-size: 12px;
    min-height: 200px;
  }
`;

interface CodeLineProps {
  $isHeader?: boolean;
  $isSubHeader?: boolean;
  $isMuted?: boolean;
}

const CodeLine = styled.div<CodeLineProps>`
  color: ${p =>
    p.$isHeader ? p.theme.colors.terminalBlue :
    p.$isSubHeader ? p.theme.colors.terminalAmber :
    p.$isMuted ? p.theme.colors.textMuted :
    p.theme.colors.textPrimary
  };
  min-height: 1.6em;
`;

const Cursor = styled.span`
  display: inline-block;
  width: 7px;
  height: 1em;
  background: ${p => p.theme.colors.terminalGreen};
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: ${blink} 1s step-end infinite;
`;

const CtaRow = styled.div`
  text-align: center;
  margin-top: ${p => p.theme.spacing.xl};
`;

const CtaLink = styled(Link)`
  display: inline-block;
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalGreen};
  text-decoration: none;
  border: 1px solid ${p => p.theme.colors.terminalGreen}40;
  border-radius: ${p => p.theme.borderRadius.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  transition: background 0.2s ease, border-color 0.2s ease;

  &:hover {
    background: ${p => p.theme.colors.terminalGreen}15;
    border-color: ${p => p.theme.colors.terminalGreen};
  }
`;

// Helper to determine line styling
const getLineStyle = (line: string): CodeLineProps => ({
  $isHeader: line.startsWith('# '),
  $isSubHeader: line.startsWith('## '),
  $isMuted: line === '',
});

// Strategy Editor Component
interface StrategyEditorProps {
  strategy: typeof CRYPTO_STRATEGY;
  isTyping: boolean;
  spotlight?: boolean;
  dimmed?: boolean;
  showCursor?: boolean;
  onTypingComplete?: () => void;
  resetKey?: number; // Change this to trigger a reset
}

function StrategyEditor({
  strategy,
  isTyping,
  spotlight = false,
  dimmed = false,
  showCursor = true,
  onTypingComplete,
  resetKey = 0
}: StrategyEditorProps) {
  const [typedContent, setTypedContent] = useState<string[]>([]);
  const [currentLineIndex, setCurrentLineIndex] = useState(0);
  const [currentCharIndex, setCurrentCharIndex] = useState(0);
  const [isTypingComplete, setIsTypingComplete] = useState(false);

  // Reset when resetKey changes
  useEffect(() => {
    setTypedContent([]);
    setCurrentLineIndex(0);
    setCurrentCharIndex(0);
    setIsTypingComplete(false);
  }, [resetKey]);

  useEffect(() => {
    if (!isTyping) return;

    if (currentLineIndex >= strategy.typed.length) {
      if (!isTypingComplete) {
        setIsTypingComplete(true);
        onTypingComplete?.();
      }
      return;
    }

    const currentLine = strategy.typed[currentLineIndex];

    if (currentCharIndex <= currentLine.length) {
      const charDelay = currentLine === '' ? 80 : 30;
      const timer = setTimeout(() => {
        setTypedContent(prev => {
          const newContent = [...prev];
          newContent[currentLineIndex] = currentLine.slice(0, currentCharIndex);
          return newContent;
        });
        setCurrentCharIndex(prev => prev + 1);
      }, charDelay);
      return () => clearTimeout(timer);
    } else {
      const lineDelay = 200;
      const timer = setTimeout(() => {
        setCurrentLineIndex(prev => prev + 1);
        setCurrentCharIndex(0);
      }, lineDelay);
      return () => clearTimeout(timer);
    }
  }, [currentLineIndex, currentCharIndex, isTyping, isTypingComplete, onTypingComplete, strategy.typed]);

  return (
    <EditorWindow $spotlight={spotlight} $dimmed={dimmed}>
      <EditorHeader>
        <EditorTitleText>{strategy.filename}</EditorTitleText>
        <WindowControls>
          <WindowDot $color="#ff5f56" />
          <WindowDot $color="#ffbd2e" />
          <WindowDot $color="#27ca40" />
        </WindowControls>
      </EditorHeader>
      <EditorContent>
        {/* Pre-loaded content */}
        {strategy.preLoaded.map((line, i) => {
          const style = getLineStyle(line);
          const displayLine = line.replace(/^#+ /, '');
          return (
            <CodeLine key={i} {...style}>
              {line.startsWith('# ') && <span style={{ opacity: 0.5 }}># </span>}
              {displayLine}
            </CodeLine>
          );
        })}

        {/* Typed content */}
        {strategy.typed.map((line, i) => {
          const typedLine = typedContent[i] || '';
          const isCurrentLine = i === currentLineIndex && !isTypingComplete && isTyping;
          const isPastLine = i < currentLineIndex || isTypingComplete;
          const style = getLineStyle(line);

          // Show empty lines for content not yet typed
          if (i > currentLineIndex && !isTypingComplete) {
            return <CodeLine key={`typed-${i}`} $isMuted>&nbsp;</CodeLine>;
          }

          // If not typing yet and not complete, show empty
          if (!isTyping && !isTypingComplete && i >= currentLineIndex) {
            return <CodeLine key={`typed-${i}`} $isMuted>&nbsp;</CodeLine>;
          }

          const displayContent = isPastLine ? line : typedLine;
          const displayLine = displayContent.replace(/^#+ /, '');

          return (
            <CodeLine key={`typed-${i}`} {...style}>
              {displayContent.startsWith('# ') && <span style={{ opacity: 0.5 }}># </span>}
              {displayLine}
              {showCursor && isCurrentLine && <Cursor />}
              {showCursor && isTypingComplete && i === strategy.typed.length - 1 && <Cursor />}
            </CodeLine>
          );
        })}
      </EditorContent>
    </EditorWindow>
  );
}

export function HeroStrategyEditor() {
  const [phase, setPhase] = useState<AnimationPhase>('left-typing');
  const [resetKey, setResetKey] = useState(0);

  const handleLeftComplete = useCallback(() => {
    // Brief pause before starting right
    setTimeout(() => setPhase('right-typing'), 300);
  }, []);

  const handleRightComplete = useCallback(() => {
    setPhase('both-complete');
    // Hold complete state, then reset
    setTimeout(() => {
      setPhase('resetting');
      setResetKey(k => k + 1);
      // Start left again after a brief reset
      setTimeout(() => setPhase('left-typing'), 500);
    }, 4000);
  }, []);

  // Determine spotlight/dim states based on phase
  const leftSpotlight = phase === 'left-typing';
  const leftDimmed = phase === 'right-typing';
  const rightSpotlight = phase === 'right-typing';
  const rightDimmed = phase === 'left-typing';

  return (
    <Section data-testid="hero-section">
      <Container>
        <SectionTitle $color="cyan" $centered $size="xl">Your Strategy, in Plain English</SectionTitle>
        <SectionSubtitle $centered>
          Define trading rules in readable markdown. Four AI agents analyze crypto and stocks with full transparency.
        </SectionSubtitle>

        <EditorsContainer>
          <EditorWrapper $delay={0}>
            <StrategyEditor
              strategy={CRYPTO_STRATEGY}
              isTyping={phase === 'left-typing'}
              spotlight={leftSpotlight}
              dimmed={leftDimmed}
              showCursor={phase !== 'both-complete' || leftSpotlight}
              onTypingComplete={handleLeftComplete}
              resetKey={resetKey}
            />
          </EditorWrapper>
          <EditorWrapper $delay={200}>
            <StrategyEditor
              strategy={STOCK_STRATEGY}
              isTyping={phase === 'right-typing'}
              spotlight={rightSpotlight}
              dimmed={rightDimmed}
              showCursor={phase !== 'both-complete' || rightSpotlight}
              onTypingComplete={handleRightComplete}
              resetKey={resetKey}
            />
          </EditorWrapper>
        </EditorsContainer>

      </Container>
    </Section>
  );
}
