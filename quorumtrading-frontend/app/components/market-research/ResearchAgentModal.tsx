'use client';

import { useEffect, useMemo } from 'react';
import styled from 'styled-components';
import { createPortal } from 'react-dom';
import { fadeInUp } from '@/app/styles/animations';
import { AgentResponseRenderer } from '@/app/components/terminal/AgentResponseRenderer';
import type { AgentDecisionResponse, ResearchAgentType } from '@/app/lib/types/api';

interface ToolCallRecord {
  tool: string;
  args: string;
  result: string;
}

interface ResearchAgentModalProps {
  isOpen: boolean;
  onClose: () => void;
  decision: AgentDecisionResponse | null;
}

function getAgentColor(agentType: ResearchAgentType): string {
  switch (agentType) {
    case 'NEWS_SENTIMENT_ANALYST': return 'var(--qt-agent-sentiment)';
    case 'STRATEGY_DESIGNER': return 'var(--qt-agent-technical)';
    case 'VIABILITY_ASSESSOR': return 'var(--qt-agent-fundamental)';
  }
}

function getAgentDisplayName(agentType: ResearchAgentType): string {
  switch (agentType) {
    case 'NEWS_SENTIMENT_ANALYST': return 'News & Sentiment Analyst';
    case 'STRATEGY_DESIGNER': return 'Strategy Designer';
    case 'VIABILITY_ASSESSOR': return 'Viability Assessor';
  }
}

function getAgentIcon(agentType: ResearchAgentType): string {
  switch (agentType) {
    case 'NEWS_SENTIMENT_ANALYST': return '\u{1F4F0}';
    case 'STRATEGY_DESIGNER': return '\u{1F4CA}';
    case 'VIABILITY_ASSESSOR': return '\u{2696}\uFE0F';
  }
}

function formatTimestamp(ts: string): string {
  const date = new Date(ts);
  return date.toLocaleString('en-GB', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'America/New_York',
  }) + ' ET';
}

function formatCost(cost: number): string {
  if (cost === 0) return '$0.00';
  if (cost < 0.01) return `$${cost.toFixed(4)}`;
  return `$${cost.toFixed(2)}`;
}

function formatTokens(tokens: number): string {
  if (tokens >= 1000) return `${(tokens / 1000).toFixed(1)}K`;
  return tokens.toString();
}

export function ResearchAgentModal({ isOpen, onClose, decision }: ResearchAgentModalProps) {
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) onClose();
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [isOpen, onClose]);

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => { document.body.style.overflow = 'unset'; };
  }, [isOpen]);

  const toolCalls = useMemo<ToolCallRecord[]>(() => {
    if (!decision?.toolCalls) return [];
    try {
      return JSON.parse(decision.toolCalls);
    } catch {
      return [];
    }
    // Depend on `decision`, not `decision?.toolCalls`: the React Compiler infers the whole object
    // as the dependency (reading the property requires reading the object) and, finding a narrower
    // hand-written array, bails out of compiling this component entirely — which is what
    // react-hooks/preserve-manual-memoization reports as "Compilation Skipped".
  }, [decision]);

  if (!isOpen || !decision) return null;

  const agentColor = getAgentColor(decision.agentType);

  const modalContent = (
    <Backdrop onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <Header>
          <HeaderLeft>
            <AgentIconSpan>{getAgentIcon(decision.agentType)}</AgentIconSpan>
            <AgentTitle $color={agentColor}>
              {getAgentDisplayName(decision.agentType)}
            </AgentTitle>
          </HeaderLeft>
          <CloseButton onClick={onClose} aria-label="Close modal">&times;</CloseButton>
        </Header>

        {/* Reasoning / Full Response */}
        <Section>
          <SectionTitle>Response</SectionTitle>
          <ResponseContent>
            <AgentResponseRenderer content={decision.advice} agentType={decision.agentType} />
          </ResponseContent>
        </Section>

        {/* Metadata */}
        <Section>
          <SectionTitle>Metadata</SectionTitle>
          <MetadataGrid>
            {decision.modelUsed && (
              <MetadataItem>
                <MetadataLabel>Model</MetadataLabel>
                <MetadataValue>{decision.modelUsed}</MetadataValue>
              </MetadataItem>
            )}
            {decision.decidedAt && (
              <MetadataItem>
                <MetadataLabel>Timestamp</MetadataLabel>
                <MetadataValue>{formatTimestamp(decision.decidedAt)}</MetadataValue>
              </MetadataItem>
            )}
            {decision.totalTokens !== undefined && decision.totalTokens > 0 && (
              <MetadataItem>
                <MetadataLabel>Tokens</MetadataLabel>
                <MetadataValue>
                  {formatTokens(decision.totalTokens)}
                  {decision.promptTokens !== undefined && decision.completionTokens !== undefined && (
                    <TokenBreakdown>
                      ({formatTokens(decision.promptTokens)} in / {formatTokens(decision.completionTokens)} out)
                    </TokenBreakdown>
                  )}
                </MetadataValue>
              </MetadataItem>
            )}
            {decision.rawCostUsd !== undefined && decision.rawCostUsd > 0 && (
              <MetadataItem>
                <MetadataLabel>Cost</MetadataLabel>
                <MetadataValue>{formatCost(decision.rawCostUsd)}</MetadataValue>
              </MetadataItem>
            )}
          </MetadataGrid>
        </Section>

        {/* Tool Calls Section */}
        {toolCalls.length > 0 && (
          <Section>
            <SectionTitle>Tool Calls ({toolCalls.length})</SectionTitle>
            <ToolCallsList>
              {toolCalls.map((tc, idx) => (
                <ToolCallItem key={idx}>
                  <ToolCallHeader>
                    <ToolCallName>{tc.tool}</ToolCallName>
                    <ToolCallArgs>{tc.args}</ToolCallArgs>
                  </ToolCallHeader>
                  {tc.result && (
                    <ToolCallResult>{tc.result}</ToolCallResult>
                  )}
                </ToolCallItem>
              ))}
            </ToolCallsList>
          </Section>
        )}
      </ModalContainer>
    </Backdrop>
  );

  return createPortal(modalContent, document.body);
}

// ============ Styled Components ============

const Backdrop = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
  backdrop-filter: blur(8px);
`;

const ModalContainer = styled.div`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.lg};
  max-width: 780px;
  width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  animation: ${fadeInUp} 0.3s ease-out;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.border};
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: ${p => p.theme.colors.textMuted};
  }
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px 20px;
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const AgentIconSpan = styled.span`
  font-size: 1.4rem;
`;

const AgentTitle = styled.h2<{ $color: string }>`
  font-size: ${p => p.theme.typography.fontSize['2xl']};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$color};
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.textMuted};
  font-size: 28px;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  transition: color 0.2s ease;
  font-family: ${p => p.theme.typography.fontFamily.mono};

  &:hover { color: ${p => p.theme.colors.terminalBlue}; }
`;

const Section = styled.div`
  padding: 24px 28px;
  border-bottom: 1px solid ${p => p.theme.colors.border};

  &:last-child { border-bottom: none; }
`;

const SectionTitle = styled.h3`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalBlue};
  text-transform: uppercase;
  letter-spacing: 1.5px;
  margin: 0 0 16px 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const ResponseContent = styled.div`
  font-size: ${p => p.theme.typography.fontSize.base};
  line-height: 1.7;
  color: ${p => p.theme.colors.textMuted};
  word-wrap: break-word;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

// Tool calls styles
const ToolCallsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ToolCallItem = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;
`;

const ToolCallHeader = styled.div`
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.2);
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

const ToolCallName = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalGreen};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  white-space: nowrap;
`;

const ToolCallArgs = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const ToolCallResult = styled.pre`
  margin: 0;
  padding: 10px 14px;
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  max-height: 200px;
  overflow-y: auto;
`;

// Metadata styles
const MetadataGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 20px;
`;

const MetadataItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const MetadataLabel = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const MetadataValue = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalGreen};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const TokenBreakdown = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  margin-left: 6px;
`;
