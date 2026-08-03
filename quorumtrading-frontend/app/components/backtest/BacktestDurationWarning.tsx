'use client';

import styled, { useTheme } from 'styled-components';
import { fadeInUp } from '@/app/styles/animations';
import { getDurationWarning, type WarningLevel } from '@/app/lib/utils/backtestDuration';
import { useCostEstimate, useNewsAvailability } from '@/app/lib/api/hooks';
import type { CostEstimateRequest } from '@/app/lib/types/config';
import { useMemo } from 'react';

interface BacktestDurationWarningProps {
  startDate: string;
  endDate: string;
  strategyIds?: string[];
}

const Banner = styled.div<{ $color: string }>`
  margin-top: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.md};
  border: 1px solid ${p => p.$color}40;
  border-left: 3px solid ${p => p.$color};
  background: ${p => p.$color}08;
  animation: ${fadeInUp} 0.3s ease-out;
`;

const Title = styled.div<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$color};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const Message = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.6;
`;

const CostLine = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.foreground};
  margin-top: ${p => p.theme.spacing.sm};
  line-height: 1.6;
`;

const CostAmount = styled.span`
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalGreen};
`;

const CostDetail = styled.span`
  color: ${p => p.theme.colors.textMuted};
  font-size: ${p => p.theme.typography.fontSize.xs};
`;

const Footer = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  margin-top: ${p => p.theme.spacing.xs};
  opacity: 0.7;
  font-style: italic;
`;

function useWarningColor(level: WarningLevel): string {
  const theme = useTheme();
  switch (level) {
    case 'mild': return theme.colors.terminalBlue;
    case 'moderate': return theme.colors.terminalAmber;
    case 'severe': return theme.colors.terminalPink;
    default: return theme.colors.terminalBlue;
  }
}

function formatDollars(value: number): string {
  return '$' + value.toFixed(2);
}

export function BacktestDurationWarning({ startDate, endDate, strategyIds }: BacktestDurationWarningProps) {
  const warning = getDurationWarning(startDate, endDate);

  // Build cost estimate request only when we have valid dates and strategies
  const costRequest = useMemo<CostEstimateRequest | null>(() => {
    if (!strategyIds || strategyIds.length === 0 || !startDate || !endDate) {
      return null;
    }
    try {
      const start = new Date(startDate);
      const end = new Date(endDate);
      if (isNaN(start.getTime()) || isNaN(end.getTime()) || end <= start) {
        return null;
      }
      return {
        strategyIds,
        startDate: start.toISOString(),
        endDate: end.toISOString(),
      };
    } catch {
      return null;
    }
  }, [strategyIds, startDate, endDate]);

  const { data: costEstimate, isLoading: costLoading } = useCostEstimate(costRequest);
  const { data: newsAvailability } = useNewsAvailability();

  // Check if start date falls before news data availability
  const newsWarning = useMemo(() => {
    if (!newsAvailability?.earliestNewsDate || !startDate) return false;
    try {
      const start = new Date(startDate);
      const cutoff = new Date(newsAvailability.earliestNewsDate + 'T00:00:00Z');
      return start < cutoff;
    } catch {
      return false;
    }
  }, [newsAvailability, startDate]);

  const color = useWarningColor(warning.level === 'none' ? 'mild' : warning.level);

  if (warning.level === 'none' && !costEstimate && !costLoading && !newsWarning) {
    return null;
  }

  // If there's no duration warning but we have cost data, show just the cost info
  const showDurationWarning = warning.level !== 'none';

  return (
    <Banner $color={color}>
      {showDurationWarning && (
        <>
          <Title $color={color}>{warning.title}</Title>
          <Message>{warning.message}</Message>
        </>
      )}
      {!showDurationWarning && costEstimate && (
        <Title $color={color}>Cost Estimate</Title>
      )}
      {costLoading && costRequest && (
        <CostLine>
          <CostDetail>Estimating AI budget cost...</CostDetail>
        </CostLine>
      )}
      {costEstimate && (
        <CostLine>
          Estimated AI budget cost: <CostAmount>{formatDollars(costEstimate.estimatedCost)}</CostAmount>
          {' '}({formatDollars(costEstimate.lowerBound)} &ndash; {formatDollars(costEstimate.upperBound)})
          <br />
          <CostDetail>
            ~{costEstimate.totalLLMCalls.toLocaleString()} LLM calls
            {' '}&middot; {costEstimate.tradingDays} trading days
            {' '}&middot; {costEstimate.tickerCount} tickers
            {' '}&middot; {costEstimate.agentsPerRound} agents/round
            {' '}&middot; {costEstimate.modelName}
          </CostDetail>
        </CostLine>
      )}
      {newsWarning && newsAvailability && (
        <CostLine>
          <CostDetail>
            Note: Historical news data is only available from {newsAvailability.earliestNewsDate} onward.
            Fundamental and Sentiment agents will have no news context before that date.
          </CostDetail>
        </CostLine>
      )}
      <Footer>This is informational only — you can proceed regardless.</Footer>
    </Banner>
  );
}
