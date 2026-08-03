'use client';

import { use, useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import styled, { keyframes, css } from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { TerminalButton } from '@/app/components/terminal/TerminalButton';
import { TerminalBadge } from '@/app/components/terminal/TerminalBadge';
import { TerminalText } from '@/app/components/terminal/TerminalText';
import { TerminalSkeleton } from '@/app/components/terminal/TerminalSkeleton';
import { DeleteExecutionModal } from '@/app/components/market-research/DeleteExecutionModal';
import { ResearchAgentModal } from '@/app/components/market-research/ResearchAgentModal';
import {
  useMarketResearchDetails,
  usePauseMarketResearch,
  useResumeMarketResearch,
  useExecuteMarketResearch,
  useDeleteMarketResearch,
  useDeleteResearchExecution,
} from '@/app/lib/api/hooks';
import { useResearchExecutionProgress } from '@/app/lib/websocket';
import type { CompletedAgent } from '@/app/lib/websocket';
import type {
  ResearchStatus,
  ResearchOutcome,
  ResearchAgentType,
  ResearchExecutionResponse,
  AgentDecisionResponse,
} from '@/app/lib/types/api';
import { assetTicker } from '@/app/lib/utils/targetAssets';
import { textGlow } from '@/app/styles/theme/effects';
import { fadeInUp, pulse, shadows, textShadows } from '@/app/styles/animations';

// ============================================================================
// Animations
// ============================================================================

const spin = keyframes`
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
`;

const pulseBlue = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(1.1); }
`;

// ============================================================================
// Layout Components (matching strategies page)
// ============================================================================

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 24px;
  min-width: 0;
`;

const Header = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0 0;
`;

const HeaderTop = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  flex-wrap: wrap;

  @media (max-width: 768px) {
    flex-direction: column;
  }
`;

const HeaderContent = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
`;

const Title = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
  word-break: break-word;

  @media (max-width: 480px) {
    font-size: 19px;
  }
`;

const MetaRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const MetaBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
`;

const MetaLabel = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-size: 11.5px;
`;

const MetaValue = styled.span`
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-variant-numeric: tabular-nums;
`;

const ActionButtons = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  flex-shrink: 0;

  @media (max-width: 968px) {
    width: 100%;
    flex-wrap: wrap;
  }

  @media (max-width: 480px) {
    flex-shrink: 1;
  }
`;

// ============================================================================
// Stats Grid (matching strategies page)
// ============================================================================

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: ${p => p.theme.spacing.lg};
  margin-bottom: ${p => p.theme.spacing.xl};

  & > *:nth-child(1) { animation: ${fadeInUp} 0.5s ease-out 0.1s both; }
  & > *:nth-child(2) { animation: ${fadeInUp} 0.5s ease-out 0.15s both; }
  & > *:nth-child(3) { animation: ${fadeInUp} 0.5s ease-out 0.2s both; }
  & > *:nth-child(4) { animation: ${fadeInUp} 0.5s ease-out 0.25s both; }
  & > *:nth-child(5) { animation: ${fadeInUp} 0.5s ease-out 0.3s both; }

  @media (max-width: 480px) {
    grid-template-columns: repeat(2, 1fr);
    gap: ${p => p.theme.spacing.sm};
  }
`;

const StatCard = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
`;

const StatLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const StatValue = styled.div<{ $positive?: boolean; $negative?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 19px;
  color: ${p =>
    p.$positive ? p.theme.colors.positive :
    p.$negative ? p.theme.colors.negative :
    p.theme.colors.fgStrong
  };
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

// ============================================================================
// Section Components (matching strategies page)
// ============================================================================

const Section = styled.section`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
`;

const SectionHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;

  @media (max-width: 480px) {
    flex-direction: column;
    align-items: flex-start;
  }
`;

const SectionTitle = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
  letter-spacing: 0;
`;

const QuickConfigGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: ${p => p.theme.spacing.md};
  margin-bottom: ${p => p.theme.spacing.xl};

  @media (max-width: 480px) {
    grid-template-columns: 1fr 1fr;
    gap: ${p => p.theme.spacing.sm};
  }
`;

const QuickConfigCard = styled.div`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.md};
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: ${shadows.card};
    border-color: ${p => p.theme.colors.primary}44;
  }
`;

const QuickConfigLabel = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const QuickConfigValue = styled.div`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalBlue};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const TickerList = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.xs};
`;

const TickerBadge = styled.span`
  display: inline-block;
  padding: 2px ${p => p.theme.spacing.xs};
  background: ${p => p.theme.colors.terminalGreen}15;
  border: 1px solid ${p => p.theme.colors.terminalGreen}40;
  border-radius: ${p => p.theme.borderRadius.sm};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.terminalGreen};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const CodeBlock = styled.pre`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.md};
  overflow-x: auto;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.foreground};
  margin: ${p => p.theme.spacing.md} 0 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 500px;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  &::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.border};
  }

  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.foreground}40;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: ${p => p.theme.colors.foreground}60;
  }
`;

// ============================================================================
// Execution History Components
// ============================================================================

const ExecutionList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const ExecutionCard = styled.div`
  display: flex;
  flex-direction: column;
  background-color: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  box-shadow: ${shadows.card};

  &:hover {
    transform: translateY(-2px);
    border-color: ${p => p.theme.colors.primary}66;
    box-shadow: ${shadows.cardHover};
  }
`;

const ExecutionHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};

  @media (max-width: 480px) {
    flex-direction: column;
    align-items: flex-start;
    gap: ${p => p.theme.spacing.sm};
  }
`;

const ExecutionMeta = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const ExecutionTime = styled(TerminalText)`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const AgentDecisionsList = styled.div`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const AgentDecision = styled.div<{ $agentType?: string }>`
  padding: ${p => p.theme.spacing.sm};
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid ${p => p.theme.colors.border};
  border-left: 3px solid ${p => {
    switch (p.$agentType) {
      case 'NEWS_SENTIMENT_ANALYST': return p.theme.colors.agents.newsSentiment;
      case 'STRATEGY_DESIGNER': return p.theme.colors.agents.strategyDesigner;
      case 'VIABILITY_ASSESSOR': return p.theme.colors.agents.viabilityAssessor;
      default: return p.theme.colors.primary;
    }
  }};
  border-radius: ${p => p.theme.borderRadius.sm};
  cursor: pointer;
  transition: all ${p => p.theme.transition.fast};

  &:hover {
    transform: translateY(-2px);
    border-color: ${p => p.theme.colors.primary};
    border-left-color: ${p => {
      switch (p.$agentType) {
        case 'NEWS_SENTIMENT_ANALYST': return p.theme.colors.agents.newsSentiment;
        case 'STRATEGY_DESIGNER': return p.theme.colors.agents.strategyDesigner;
        case 'VIABILITY_ASSESSOR': return p.theme.colors.agents.viabilityAssessor;
        default: return p.theme.colors.primary;
      }
    }};
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3), 0 0 8px ${p => p.theme.colors.primary}22;
  }

  &:active {
    transform: translateY(0);
  }
`;

const AgentHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const AgentInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
`;

const AgentIcon = styled.span`
  font-size: 0.9rem;
`;

const AgentName = styled(TerminalText).attrs({ size: 'xs', weight: 'bold' })`
  color: ${p => p.theme.colors.text};
`;

const ToolCallCount = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  background-color: ${p => p.theme.colors.terminalGreen}15;
  border: 1px solid ${p => p.theme.colors.terminalGreen}30;
  border-radius: ${p => p.theme.borderRadius.sm};
  font-size: 9px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.terminalGreen};
`;

const CostBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  background-color: rgba(0, 0, 0, 0.3);
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  font-size: 9px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
`;

const TotalCostRow = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.sm} 0;
  margin-top: ${p => p.theme.spacing.sm};
  border-top: 1px dashed ${p => p.theme.colors.border};
`;

const AgentCostInfo = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  flex-wrap: wrap;
  margin-top: ${p => p.theme.spacing.xs};
`;

const CardSection = styled.div`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

const CardSectionTitle = styled.span`
  display: block;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const ReasoningText = styled(TerminalText).attrs({ size: 'xs' })`
  line-height: 1.5;
`;

const ViabilityBadge = styled.span<{ $score?: number }>`
  display: inline-block;
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  border-radius: ${p => p.theme.borderRadius.md};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  letter-spacing: 0.05em;
  background: ${p => {
    const score = p.$score ?? 0;
    if (score >= 70) return `${p.theme.colors.success}33`;
    if (score >= 50) return `${p.theme.colors.warning}33`;
    return `${p.theme.colors.error}33`;
  }};
  color: ${p => {
    const score = p.$score ?? 0;
    if (score >= 70) return p.theme.colors.success;
    if (score >= 50) return p.theme.colors.warning;
    return p.theme.colors.error;
  }};
  border: 1px solid ${p => {
    const score = p.$score ?? 0;
    if (score >= 70) return p.theme.colors.success;
    if (score >= 50) return p.theme.colors.warning;
    return p.theme.colors.error;
  }};
`;

const StatusTagRow = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  flex-wrap: wrap;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
`;

// ============================================================================
// In-Progress Execution Card
// ============================================================================

const InProgressCard = styled.div`
  padding: ${p => p.theme.spacing.lg};
  background-color: ${p => p.theme.colors.surface};
  border: 2px solid ${p => p.theme.colors.primary};
  box-shadow: ${shadows.card}, 0 0 20px ${p => p.theme.colors.primary}33;
  animation: ${fadeInUp} 0.5s ease-out;
`;

const InProgressHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${p => p.theme.spacing.md};
  padding-bottom: ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

const InProgressTitle = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  font-size: ${p => p.theme.typography.fontSize.lg};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.primary};
  text-shadow: ${textShadows.primary};
`;

const HourglassIcon = styled.span`
  font-size: 1.2rem;
  animation: ${pulse} 1.5s ease-in-out infinite;
`;

const ElapsedTime = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const AgentStepsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const AgentStep = styled.div<{ $state: 'pending' | 'working' | 'completed' | 'failed' }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  border-radius: ${p => p.theme.borderRadius.sm};
  background-color: ${p => {
    switch (p.$state) {
      case 'working': return `${p.theme.colors.info}15`;
      case 'completed': return `${p.theme.colors.success}10`;
      case 'failed': return `${p.theme.colors.error}15`;
      default: return 'transparent';
    }
  }};
  transition: background-color 0.3s ease;
`;

const StepIndicator = styled.span<{ $state: 'pending' | 'working' | 'completed' | 'failed' }>`
  font-size: 1rem;
  color: ${p => {
    switch (p.$state) {
      case 'pending': return p.theme.colors.textMuted;
      case 'working': return p.theme.colors.info;
      case 'completed': return p.theme.colors.success;
      case 'failed': return p.theme.colors.error;
      default: return p.theme.colors.textMuted;
    }
  }};
  ${p => p.$state === 'working' && css`animation: ${pulseBlue} 1s ease-in-out infinite;`}
`;

const StepName = styled.span<{ $state: 'pending' | 'working' | 'completed' | 'failed' }>`
  flex: 1;
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => {
    switch (p.$state) {
      case 'pending': return p.theme.colors.textMuted;
      case 'working': return p.theme.colors.text;
      case 'completed': return p.theme.colors.text;
      case 'failed': return p.theme.colors.error;
      default: return p.theme.colors.textMuted;
    }
  }};
  font-weight: ${p => p.$state === 'working' ? p.theme.typography.fontWeight.semibold : 'normal'};
`;

const StepStatus = styled.span<{ $state: 'pending' | 'working' | 'completed' | 'failed' }>`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => {
    switch (p.$state) {
      case 'pending': return p.theme.colors.textMuted;
      case 'working': return p.theme.colors.info;
      case 'completed': return p.theme.colors.textMuted;
      case 'failed': return p.theme.colors.error;
      default: return p.theme.colors.textMuted;
    }
  }};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

// ============================================================================
// Other UI Components
// ============================================================================

const LoadingSpinner = styled.span`
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: ${spin} 0.8s linear infinite;
  margin-right: 8px;
`;

const LoadingMessage = styled.div`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  text-align: center;
  padding: ${p => p.theme.spacing.xl};
`;

const ErrorMessage = styled.div`
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  padding: ${p => p.theme.spacing.lg};
  border: 1px solid ${p => p.theme.colors.error};
  background: ${p => p.theme.colors.error}10;
  margin-bottom: ${p => p.theme.spacing.lg};
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${p => p.theme.spacing.lg};
  padding: ${p => p.theme.spacing['2xl']};
  text-align: center;
  border: 1px dashed ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  background: linear-gradient(135deg, ${p => p.theme.colors.surface}60, ${p => p.theme.colors.background});
  animation: ${fadeInUp} 0.5s ease-out 0.1s both;
`;

const EmptyStateText = styled.p`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  margin: 0;
`;

const EmptyStateHint = styled.p`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  margin: 0;
  opacity: 0.7;
`;

const StatusBadge = styled(TerminalBadge).withConfig({
  shouldForwardProp: (prop) => prop !== 'status',
})<{ status: string }>`
  ${p => {
    switch (p.status) {
      case 'ACTIVE':
        return `
          background: ${p.theme.colors.success}20;
          color: ${p.theme.colors.success};
          border-color: ${p.theme.colors.success};
        `;
      case 'PAUSED':
        return `
          background: ${p.theme.colors.warning}20;
          color: ${p.theme.colors.warning};
          border-color: ${p.theme.colors.warning};
        `;
      default:
        return '';
    }
  }}
`;

// ============================================================================
// Utility Functions
// ============================================================================

function getOutcomeBadgeVariant(outcome: ResearchOutcome): 'success' | 'info' | 'error' {
  switch (outcome) {
    case 'STRATEGY_CREATED': return 'success';
    case 'NO_OPPORTUNITY': return 'info';
    case 'FAILED': return 'error';
    default: return 'info';
  }
}

function formatInterval(amount: number, unit: string): string {
  const unitLower = unit.toLowerCase();
  const singular = unitLower.slice(0, -1);
  return amount === 1 ? `1 ${singular}` : `${amount} ${unitLower}`;
}

function formatDate(isoString: string | undefined): string {
  if (!isoString) return 'Never';
  const date = new Date(isoString);
  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatAgentType(agentType: ResearchAgentType): string {
  return agentType.split('_').map(word =>
    word.charAt(0) + word.slice(1).toLowerCase()
  ).join(' ');
}

function getAgentIcon(agentType: ResearchAgentType): string {
  switch (agentType) {
    case 'NEWS_SENTIMENT_ANALYST': return '📰';
    case 'STRATEGY_DESIGNER': return '📊';
    case 'VIABILITY_ASSESSOR': return '⚖️';
    default: return '🤖';
  }
}

function formatCost(cost: number | undefined): string {
  if (cost === undefined || cost === null) return '-';
  if (cost === 0) return '$0.00';
  if (cost < 0.01) return `$${cost.toFixed(4)}`;
  return `$${cost.toFixed(2)}`;
}

function formatTokens(tokens: number | undefined): string {
  if (tokens === undefined || tokens === null) return '-';
  if (tokens >= 1000) return `${(tokens / 1000).toFixed(1)}K`;
  return tokens.toString();
}

function getShortModelName(model: string | undefined): string {
  if (!model) return 'Unknown';
  const parts = model.split('/');
  return parts[parts.length - 1];
}

function formatElapsedTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

function formatDuration(ms: number): string {
  const seconds = ms / 1000;
  return `${seconds.toFixed(1)}s`;
}

function getAgentDisplayName(agentType: string): string {
  switch (agentType) {
    case 'NEWS_SENTIMENT_ANALYST': return 'News & Sentiment Analysis';
    case 'STRATEGY_DESIGNER': return 'Strategy Designer';
    case 'VIABILITY_ASSESSOR': return 'Viability Assessor';
    case 'COORDINATOR': return 'Coordinator';
    default: return agentType.split('_').map(word =>
      word.charAt(0) + word.slice(1).toLowerCase()
    ).join(' ');
  }
}

// ============================================================================
// In-Progress Execution Card Component
// ============================================================================

const AGENT_EXECUTION_ORDER = [
  'NEWS_SENTIMENT_ANALYST',
  'VIABILITY_ASSESSOR',
  'STRATEGY_DESIGNER',
];

type AgentStepState = 'pending' | 'working' | 'completed' | 'failed';

interface InProgressExecutionCardProps {
  elapsedTime: number;
  currentAgent: string | null;
  completedAgents: CompletedAgent[];
  error: string | null;
}

function InProgressExecutionCardComponent({
  elapsedTime,
  currentAgent,
  completedAgents,
  error,
}: InProgressExecutionCardProps) {
  const getAgentState = useCallback((agentType: string): AgentStepState => {
    if (error && currentAgent === agentType) return 'failed';
    if (completedAgents.some(a => a.type === agentType)) return 'completed';
    if (currentAgent === agentType) return 'working';
    return 'pending';
  }, [currentAgent, completedAgents, error]);

  const getAgentDuration = useCallback((agentType: string): number | null => {
    const completed = completedAgents.find(a => a.type === agentType);
    return completed ? completed.durationMs : null;
  }, [completedAgents]);

  const getIndicatorSymbol = (state: AgentStepState): string => {
    switch (state) {
      case 'pending': return '\u25CB';
      case 'working': return '\u25CF';
      case 'completed': return '\u2713';
      case 'failed': return '\u2717';
    }
  };

  const getStatusText = (state: AgentStepState, duration: number | null): string => {
    switch (state) {
      case 'pending': return 'pending';
      case 'working': return 'working...';
      case 'completed': return duration ? `(${formatDuration(duration)})` : 'done';
      case 'failed': return 'failed';
    }
  };

  return (
    <InProgressCard>
      <InProgressHeader>
        <InProgressTitle>
          <HourglassIcon>&#x23F3;</HourglassIcon>
          Execution In Progress
        </InProgressTitle>
        <ElapsedTime>{formatElapsedTime(elapsedTime)} elapsed</ElapsedTime>
      </InProgressHeader>
      <AgentStepsList>
        {AGENT_EXECUTION_ORDER.map(agentType => {
          const state = getAgentState(agentType);
          const duration = getAgentDuration(agentType);
          return (
            <AgentStep key={agentType} $state={state}>
              <StepIndicator $state={state}>
                {getIndicatorSymbol(state)}
              </StepIndicator>
              <StepName $state={state}>
                {getAgentDisplayName(agentType)}
              </StepName>
              <StepStatus $state={state}>
                {getStatusText(state, duration)}
              </StepStatus>
            </AgentStep>
          );
        })}
      </AgentStepsList>
    </InProgressCard>
  );
}

// ============================================================================
// Execution Card Component
// ============================================================================

interface ExecutionCardComponentProps {
  execution: ResearchExecutionResponse;
  onDelete?: (showModal: boolean) => void;
  isDeleting?: boolean;
}

function getToolCallCount(decision: AgentDecisionResponse): number {
  if (!decision.toolCalls) return 0;
  try {
    const parsed = JSON.parse(decision.toolCalls);
    return Array.isArray(parsed) ? parsed.length : 0;
  } catch {
    return 0;
  }
}

function ExecutionCardComponent({ execution, onDelete, isDeleting }: ExecutionCardComponentProps) {
  const router = useRouter();
  const [selectedDecision, setSelectedDecision] = useState<AgentDecisionResponse | null>(null);

  const handleViewStrategy = () => {
    if (execution.createdStrategyId) {
      router.push(`/strategies/${execution.createdStrategyId}`);
    }
  };

  const handleViewSimulation = () => {
    if (execution.simulationId) {
      router.push(`/simulations/${execution.simulationId}`);
    }
  };

  return (
    <ExecutionCard>
      {/* Header: date + outcome badge + actions */}
      <ExecutionHeader>
        <ExecutionMeta>
          <ExecutionTime>{formatDate(execution.executedAt)}</ExecutionTime>
        </ExecutionMeta>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          {execution.createdStrategyId && (
            <TerminalButton size="sm" onClick={handleViewStrategy}>
              View Strategy
            </TerminalButton>
          )}
          {execution.simulationId && (
            <TerminalButton size="sm" onClick={handleViewSimulation}>
              View Simulation
            </TerminalButton>
          )}
          {onDelete && (
            <TerminalButton
              size="sm"
              variant="ghost"
              onClick={() => onDelete(execution.outcome !== 'FAILED')}
              disabled={isDeleting}
            >
              {isDeleting ? 'Deleting...' : 'Delete'}
            </TerminalButton>
          )}
        </div>
      </ExecutionHeader>

      {/* Status tags */}
      <StatusTagRow>
        <TerminalBadge variant={getOutcomeBadgeVariant(execution.outcome)}>
          {execution.outcome.replace('_', ' ')}
        </TerminalBadge>
        {execution.viabilityScore !== undefined && execution.viabilityScore !== null && (
          <ViabilityBadge $score={execution.viabilityScore}>
            {execution.viabilityScore}% viability
          </ViabilityBadge>
        )}
      </StatusTagRow>

      {/* Orchestrator Conclusion */}
      <CardSection>
        <CardSectionTitle>Orchestrator Conclusion</CardSectionTitle>
        <ReasoningText>{execution.outcomeReasoning}</ReasoningText>
      </CardSection>

      {/* Agent Discussion */}
      {execution.agentDecisions && execution.agentDecisions.length > 0 && (
        <CardSection>
          <CardSectionTitle>
            Agent Discussion ({execution.agentDecisions.length})
          </CardSectionTitle>
          <AgentDecisionsList style={{ borderTop: 'none', padding: 0 }}>
            {execution.agentDecisions.map(decision => {
              const toolCount = getToolCallCount(decision);
              return (
                <AgentDecision key={decision.id} $agentType={decision.agentType} onClick={() => setSelectedDecision(decision)}>
                  <AgentHeader>
                    <AgentInfo>
                      <AgentIcon>
                        {getAgentIcon(decision.agentType)}
                      </AgentIcon>
                      <div>
                        <AgentName>{formatAgentType(decision.agentType)}</AgentName>
                        <AgentCostInfo>
                          {decision.modelUsed && (
                            <CostBadge title={decision.modelUsed}>
                              {getShortModelName(decision.modelUsed)}
                            </CostBadge>
                          )}
                          {decision.totalTokens !== undefined && decision.totalTokens > 0 && (
                            <CostBadge title={`${decision.promptTokens || 0} prompt + ${decision.completionTokens || 0} completion`}>
                              {formatTokens(decision.totalTokens)} tok
                            </CostBadge>
                          )}
                          {decision.rawCostUsd !== undefined && decision.rawCostUsd > 0 && (
                            <CostBadge title="Raw LLM cost (before markup)">
                              {formatCost(decision.rawCostUsd)}
                            </CostBadge>
                          )}
                          {toolCount > 0 && (
                            <ToolCallCount title={`${toolCount} tool call${toolCount !== 1 ? 's' : ''} used`}>
                              {toolCount} tool{toolCount !== 1 ? 's' : ''}
                            </ToolCallCount>
                          )}
                        </AgentCostInfo>
                      </div>
                    </AgentInfo>
                  </AgentHeader>
                </AgentDecision>
              );
            })}
          </AgentDecisionsList>
          {execution.billedCostUsd !== undefined && execution.billedCostUsd > 0 && (
            <TotalCostRow>
              <TerminalText size="xs" variant="muted">Total Billed Cost:</TerminalText>
              <CostBadge title="Total billed cost (platform markup included)">
                {formatCost(execution.billedCostUsd)}
              </CostBadge>
            </TotalCostRow>
          )}
        </CardSection>
      )}

      <ResearchAgentModal
        isOpen={selectedDecision !== null}
        onClose={() => setSelectedDecision(null)}
        decision={selectedDecision}
      />
    </ExecutionCard>
  );
}

// ============================================================================
// Main Page Component
// ============================================================================

interface MarketResearchDetailPageProps {
  params: Promise<{ id: string }>;
}

export default function MarketResearchDetailPage({ params }: MarketResearchDetailPageProps) {
  const resolvedParams = use(params);
  const router = useRouter();
  const [executionToDelete, setExecutionToDelete] = useState<{ id: string; date: string; hasLinkedStrategy: boolean } | null>(null);
  const { data, isLoading, error } = useMarketResearchDetails(resolvedParams.id);
  const pauseMutation = usePauseMarketResearch();
  const resumeMutation = useResumeMarketResearch();
  const executeMutation = useExecuteMarketResearch();
  const deleteMutation = useDeleteMarketResearch();
  const deleteExecutionMutation = useDeleteResearchExecution();

  // Subscribe to real-time execution progress via WebSocket
  const executionProgress = useResearchExecutionProgress(resolvedParams.id);

  // Optimistic execution state
  const [optimisticExecuting, setOptimisticExecuting] = useState(false);
  const optimisticTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (executionProgress.isExecuting || executionProgress.outcome) {
      setOptimisticExecuting(false);
      if (optimisticTimeoutRef.current) {
        clearTimeout(optimisticTimeoutRef.current);
        optimisticTimeoutRef.current = null;
      }
    }
  }, [executionProgress.isExecuting, executionProgress.outcome]);

  useEffect(() => {
    return () => {
      if (optimisticTimeoutRef.current) {
        clearTimeout(optimisticTimeoutRef.current);
      }
    };
  }, []);

  const isExecuting = executeMutation.isPending || optimisticExecuting || executionProgress.isExecuting;
  const canExecute = !isExecuting && executionProgress.isSubscribed;

  const handlePause = () => {
    pauseMutation.mutate(resolvedParams.id);
  };

  const handleResume = () => {
    resumeMutation.mutate(resolvedParams.id);
  };

  const handleExecute = () => {
    setOptimisticExecuting(true);
    if (optimisticTimeoutRef.current) {
      clearTimeout(optimisticTimeoutRef.current);
    }
    optimisticTimeoutRef.current = setTimeout(() => {
      setOptimisticExecuting(false);
      optimisticTimeoutRef.current = null;
    }, 120000);

    executeMutation.mutate(resolvedParams.id, {
      onError: () => {
        setOptimisticExecuting(false);
        if (optimisticTimeoutRef.current) {
          clearTimeout(optimisticTimeoutRef.current);
          optimisticTimeoutRef.current = null;
        }
      },
    });
  };

  const handleDelete = () => {
    if (confirm(`Are you sure you want to delete "${data?.research.name}"?`)) {
      deleteMutation.mutate(resolvedParams.id, {
        onSuccess: () => {
          router.push('/research');
        },
      });
    }
  };

  const handleDeleteExecution = (executionId: string, executionDate: string, hasLinkedStrategy: boolean, showModal: boolean) => {
    if (showModal) {
      setExecutionToDelete({ id: executionId, date: executionDate, hasLinkedStrategy });
    } else {
      deleteExecutionMutation.mutate({
        researchId: resolvedParams.id,
        executionId,
      });
    }
  };

  const handleConfirmDeleteExecution = () => {
    if (executionToDelete) {
      deleteExecutionMutation.mutate(
        {
          researchId: resolvedParams.id,
          executionId: executionToDelete.id,
        },
        {
          onSuccess: () => {
            setExecutionToDelete(null);
          },
        }
      );
    }
  };

  if (isLoading) {
    return (
      <DashboardLayout>
        <Container>
          <LoadingMessage>Loading market research...</LoadingMessage>
        </Container>
      </DashboardLayout>
    );
  }

  if (error || !data) {
    return (
      <DashboardLayout>
        <Container>
          <ErrorMessage>
            Failed to load market research: {error instanceof Error ? error.message : 'Unknown error'}
          </ErrorMessage>
          <Link href="/research">
            <TerminalButton variant="secondary">Back to Research</TerminalButton>
          </Link>
        </Container>
      </DashboardLayout>
    );
  }

  const { research, executions, totalExecutions, successfulExecutions } = data;
  const successRate = totalExecutions > 0 ? Math.round((successfulExecutions / totalExecutions) * 100) : 0;
  const failedExecutions = totalExecutions - successfulExecutions;

  return (
    <DashboardLayout>
      <Container>
        {/* Header - matching strategies page */}
        <Header>
          <HeaderTop>
            <HeaderContent>
              <Title>{research.name}</Title>
              <MetaRow>
                <StatusBadge status={research.status}>
                  {research.status}
                </StatusBadge>
                <MetaBadge>
                  <MetaLabel>Assets</MetaLabel>
                  <MetaValue>{research.targetAssets.length}</MetaValue>
                </MetaBadge>
                <MetaBadge>
                  <MetaLabel>Interval</MetaLabel>
                  <MetaValue>{formatInterval(research.interval.amount, research.interval.unit)}</MetaValue>
                </MetaBadge>
                {research.autoBacktest && (
                  <MetaBadge>
                    <MetaLabel>Auto-backtest</MetaLabel>
                    <MetaValue>On</MetaValue>
                  </MetaBadge>
                )}
              </MetaRow>
            </HeaderContent>
            <ActionButtons>
              <Link href={`/research/${resolvedParams.id}/edit`}>
                <TerminalButton variant="secondary" size="sm">Edit</TerminalButton>
              </Link>
              {research.status === 'ACTIVE' && (
                <>
                  <TerminalButton
                      variant="primary"
                      size="sm"
                      onClick={handleExecute}
                      disabled={!canExecute}
                    >
                      {isExecuting && <LoadingSpinner />}
                      {isExecuting ? 'Executing...' : 'Execute Now'}
                    </TerminalButton>
                  <TerminalButton variant="secondary" size="sm" onClick={handlePause}>
                    Pause
                  </TerminalButton>
                </>
              )}
              {research.status === 'PAUSED' && (
                <TerminalButton variant="primary" size="sm" onClick={handleResume}>
                  Resume
                </TerminalButton>
              )}
              <TerminalButton variant="danger" size="sm" onClick={handleDelete}>
                Delete
              </TerminalButton>
              <Link href="/research">
                <TerminalButton variant="ghost" size="sm">← Research</TerminalButton>
              </Link>
            </ActionButtons>
          </HeaderTop>
        </Header>

        {/* Stats Grid */}
        <StatsGrid>
          <StatCard>
            <StatLabel>Total Executions</StatLabel>
            <StatValue>{totalExecutions}</StatValue>
          </StatCard>
          <StatCard>
            <StatLabel>Successful</StatLabel>
            <StatValue $positive={successfulExecutions > 0}>
              {successfulExecutions}
            </StatValue>
          </StatCard>
          <StatCard>
            <StatLabel>Failed</StatLabel>
            <StatValue $negative={failedExecutions > 0}>
              {failedExecutions}
            </StatValue>
          </StatCard>
          <StatCard>
            <StatLabel>Success Rate</StatLabel>
            <StatValue $positive={successRate >= 50}>
              {totalExecutions > 0 ? `${successRate}%` : 'N/A'}
            </StatValue>
          </StatCard>
          <StatCard>
            <StatLabel>Last Executed</StatLabel>
            <StatValue style={{ fontSize: 'inherit' }}>
              {research.lastExecutedAt
                ? new Date(research.lastExecutedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
                : 'Never'}
            </StatValue>
          </StatCard>
        </StatsGrid>

        {/* Execution History Section */}
        <Section>
          <SectionHeader>
            <SectionTitle>Execution History ({executions.length})</SectionTitle>
            {research.status === 'ACTIVE' && !isExecuting && (
              <TerminalButton variant="primary" size="sm" onClick={handleExecute} disabled={!canExecute}>
                Execute Now
              </TerminalButton>
            )}
          </SectionHeader>

          {!isExecuting && executions.length === 0 ? (
            <EmptyState>
              <EmptyStateText>No executions yet</EmptyStateText>
              <EmptyStateHint>Run your first execution to start discovering opportunities</EmptyStateHint>
              <TerminalButton onClick={handleExecute} disabled={!canExecute}>
                {isExecuting && <LoadingSpinner />}
                {isExecuting ? 'Executing...' : 'Run First Execution'}
              </TerminalButton>
            </EmptyState>
          ) : (
            <ExecutionList>
              {isExecuting && (
                <InProgressExecutionCardComponent
                  elapsedTime={executionProgress.elapsedTime}
                  currentAgent={executionProgress.currentAgent}
                  completedAgents={executionProgress.completedAgents}
                  error={executionProgress.error}
                />
              )}
              {[...executions].reverse().map(execution => (
                <ExecutionCardComponent
                  key={execution.id}
                  execution={execution}
                  onDelete={(showModal) => handleDeleteExecution(
                    execution.id,
                    formatDate(execution.executedAt),
                    !!execution.createdStrategyId,
                    showModal
                  )}
                  isDeleting={deleteExecutionMutation.isPending && (
                    executionToDelete?.id === execution.id ||
                    deleteExecutionMutation.variables?.executionId === execution.id
                  )}
                />
              ))}
            </ExecutionList>
          )}
        </Section>

        {/* Research Configuration Section */}
        <Section>
          <SectionTitle>Research Configuration</SectionTitle>

          <QuickConfigGrid>
            {research.targetAssets.length > 0 && (
              <QuickConfigCard>
                <QuickConfigLabel>Target Assets</QuickConfigLabel>
                <TickerList>
                  {research.targetAssets.map(ticker => (
                    <TickerBadge key={ticker}>{assetTicker(ticker)}</TickerBadge>
                  ))}
                </TickerList>
              </QuickConfigCard>
            )}
            <QuickConfigCard>
              <QuickConfigLabel>Interval</QuickConfigLabel>
              <QuickConfigValue>
                {formatInterval(research.interval.amount, research.interval.unit)}
              </QuickConfigValue>
            </QuickConfigCard>
            <QuickConfigCard>
              <QuickConfigLabel>Auto-backtest</QuickConfigLabel>
              <QuickConfigValue>
                {research.autoBacktest ? 'Enabled' : 'Disabled'}
              </QuickConfigValue>
            </QuickConfigCard>
            {research.backtestConfig && (
              <QuickConfigCard>
                <QuickConfigLabel>Backtest Length</QuickConfigLabel>
                <QuickConfigValue>{research.backtestConfig.backtestLength}</QuickConfigValue>
              </QuickConfigCard>
            )}
            <QuickConfigCard>
              <QuickConfigLabel>Created</QuickConfigLabel>
              <QuickConfigValue>
                {new Date(research.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
              </QuickConfigValue>
            </QuickConfigCard>
            <QuickConfigCard>
              <QuickConfigLabel>Next Scheduled</QuickConfigLabel>
              <QuickConfigValue>
                {research.status === 'PAUSED'
                  ? 'Paused'
                  : (research.nextScheduledRun
                    ? new Date(research.nextScheduledRun).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
                    : 'Not scheduled')}
              </QuickConfigValue>
            </QuickConfigCard>
          </QuickConfigGrid>

          {research.description && (
            <CodeBlock>{research.description}</CodeBlock>
          )}
        </Section>

        <DeleteExecutionModal
          isOpen={executionToDelete !== null}
          onClose={() => setExecutionToDelete(null)}
          onConfirm={handleConfirmDeleteExecution}
          isDeleting={deleteExecutionMutation.isPending}
          executionDate={executionToDelete?.date}
          hasLinkedStrategy={executionToDelete?.hasLinkedStrategy}
        />
      </Container>
    </DashboardLayout>
  );
}
