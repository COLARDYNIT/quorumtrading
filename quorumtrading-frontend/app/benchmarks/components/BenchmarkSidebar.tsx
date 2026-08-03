'use client';

import React from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { BenchmarkEntry, OverlayResponse } from '@/app/lib/types/benchmark';
import { useResumeSimulation } from '@/app/lib/api/hooks';
import { TerminalButton } from '@/app/components/terminal';
import { getOverlayColor } from '@/app/lib/utils/colors';

interface BenchmarkSidebarProps {
  leaders: BenchmarkEntry[];
  overlays?: OverlayResponse[];
}

const SidebarContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: transparent;
  border-left: 2px solid ${p => p.theme.colors.border};
  padding: 20px;
  font-family: 'IBM Plex Mono', monospace;
  color: ${p => p.theme.colors.textWhite};
  overflow-y: auto;
`;

const SidebarHeader = styled.h2`
  font-size: 15px;
  font-weight: bold;
  color: ${p => p.theme.colors.terminalBlue};
  margin: 0 0 20px 0;
  text-transform: uppercase;
  letter-spacing: 2px;
`;

const Section = styled.div`
  margin-bottom: 30px;
`;

const SectionTitle = styled.h3`
  font-size: 13px;
  font-weight: bold;
  color: ${p => p.theme.colors.terminalBlue};
  margin: 0 0 15px 0;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const LeaderList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const LeaderItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: ${p => p.theme.spacing.sm};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const LeaderRow = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
`;

const RankBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 3px;
  font-size: 11px;
  font-weight: bold;
  color: ${p => p.theme.colors.terminalBlue};
`;

const LeaderDot = styled.div<{ $color: string }>`
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background-color: ${props => props.$color};
  flex-shrink: 0;
`;

const LeaderName = styled(Link)`
  font-size: 12px;
  color: ${p => p.theme.colors.textWhite};
  flex: 1;
  text-decoration: none;
  transition: all 0.2s;

  &:hover {
    color: ${p => p.theme.colors.terminalBlue};
    text-decoration: underline;
  }
`;

const LeaderStats = styled.div`
  display: flex;
  justify-content: space-between;
  padding-left: 18px;
  font-size: 12px;
`;

const LeaderValue = styled.span`
  color: ${p => p.theme.colors.textMuted};
  font-variant-numeric: tabular-nums;
`;

const LeaderReturn = styled.span<{ $positive: boolean }>`
  color: ${props => props.$positive ? props.theme.colors.positive : props.theme.colors.negative};
  font-variant-numeric: tabular-nums;
  font-weight: bold;
`;

const StatusRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 18px;
  gap: ${p => p.theme.spacing.xs};
`;

const StatusBadge = styled.span<{ $status: string }>`
  font-size: 9px;
  font-weight: bold;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 2px 6px;
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid ${p => {
    switch (p.$status) {
      case 'COMPLETED': return p.theme.colors.success;
      case 'FAILED': return p.theme.colors.error;
      case 'SIMULATING':
      case 'LIVE': return p.theme.colors.primary;
      default: return p.theme.colors.textMuted;
    }
  }};
  color: ${p => {
    switch (p.$status) {
      case 'COMPLETED': return p.theme.colors.success;
      case 'FAILED': return p.theme.colors.error;
      case 'SIMULATING':
      case 'LIVE': return p.theme.colors.primary;
      default: return p.theme.colors.textMuted;
    }
  }};
  background: ${p => {
    switch (p.$status) {
      case 'COMPLETED': return `${p.theme.colors.success}10`;
      case 'FAILED': return `${p.theme.colors.error}10`;
      case 'SIMULATING':
      case 'LIVE': return `${p.theme.colors.primary}10`;
      default: return `${p.theme.colors.textMuted}10`;
    }
  }};
`;

const ResumeButtonWrapper = styled.div`
  button {
    font-size: 9px;
    padding: 4px 8px;
    min-height: 20px;
  }
`;

const Separator = styled.div`
  height: 1px;
  background: linear-gradient(
    to right,
    transparent,
    ${p => p.theme.colors.border} 20%,
    ${p => p.theme.colors.border} 80%,
    transparent
  );
  margin: 20px 0;
`;

const EmptyMessage = styled.div`
  font-size: 12px;
  color: ${p => p.theme.colors.textMuted};
  font-style: italic;
  text-align: center;
  padding: 20px 0;
`;



const OverlayLabel = styled.span`
  font-size: 12px;
  color: ${p => p.theme.colors.textWhite};
  flex: 1;
`;

const OverlayItem = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: ${p => p.theme.colors.surface};
  border: 1px dashed ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const OverlayDash = styled.div<{ $color: string }>`
  width: 14px;
  height: 0;
  border-top: 2px dashed ${p => p.$color};
  flex-shrink: 0;
`;

const OverlayInfo = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const OverlayName = styled.span`
  font-size: 12px;
  color: ${p => p.theme.colors.textWhite};
`;

const OverlayMeta = styled.span`
  font-size: 11px;
  color: ${p => p.theme.colors.textMuted};
`;

export function BenchmarkSidebar({ leaders, overlays = [] }: BenchmarkSidebarProps) {
  const resumeSimulation = useResumeSimulation();

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  };

  const formatPercent = (value: number) => {
    const sign = value >= 0 ? '+' : '';
    return `${sign}${value.toFixed(2)}%`;
  };

  const handleResume = (id: string) => {
    resumeSimulation.mutate(id);
  };

  const shouldShowResumeButton = (status: string) => {
    // Show resume button for failed simulations or completed ones that might need restart
    return status === 'FAILED' || status === 'SIMULATING' || status === 'LIVE';
  };

  return (
    <SidebarContainer>
      <SidebarHeader>Benchmark</SidebarHeader>

      <Section>
        <SectionTitle>All Performers</SectionTitle>
        {leaders.length > 0 ? (
          <LeaderList>
            {leaders.map((leader, index) => {
              const showStatusBadge = leader.status !== 'PENDING';

              return (
                <LeaderItem key={leader.id}>
                  <LeaderRow>
                    <RankBadge>{index + 1}</RankBadge>
                    <LeaderDot $color={leader.color} />
                    <LeaderName href={`/simulations/${leader.id}`}>
                      {leader.strategyName}
                    </LeaderName>
                  </LeaderRow>
                  <LeaderStats>
                    <LeaderValue>{formatCurrency(leader.value)}</LeaderValue>
                    <LeaderReturn $positive={leader.returnPercent >= 0}>
                      {formatPercent(leader.returnPercent)}
                    </LeaderReturn>
                  </LeaderStats>
                  {showStatusBadge && (
                    <StatusRow>
                      <StatusBadge $status={leader.status}>
                        {leader.status}
                      </StatusBadge>
                      {shouldShowResumeButton(leader.status) && (
                        <ResumeButtonWrapper>
                          <TerminalButton
                            variant="secondary"
                            onClick={() => handleResume(leader.id)}
                            disabled={resumeSimulation.isPending}
                          >
                            {resumeSimulation.isPending ? '...' : 'Resume'}
                          </TerminalButton>
                        </ResumeButtonWrapper>
                      )}
                    </StatusRow>
                  )}
                </LeaderItem>
              );
            })}
          </LeaderList>
        ) : (
          <EmptyMessage>No simulations running</EmptyMessage>
        )}
      </Section>

      {/* Market Comparison section */}
      {overlays.filter(o => o.status === 'READY').length > 0 && (
        <>
          <Separator />
          <Section>
            <SectionTitle>Market Comparison</SectionTitle>
            <LeaderList>
              {overlays
                .filter(o => o.status === 'READY')
                .map(overlay => {
                  const lastPoint = overlay.dataPoints[overlay.dataPoints.length - 1];
                  const color = getOverlayColor(overlay.symbol);
                  return (
                    <LeaderItem key={overlay.symbol}>
                      <LeaderRow>
                        <OverlayDash $color={color} />
                        <OverlayLabel>
                          {overlay.symbol} — Buy &amp; Hold
                        </OverlayLabel>
                      </LeaderRow>
                      <LeaderStats>
                        <LeaderValue>
                          {lastPoint ? formatCurrency(lastPoint.portfolioValue) : '—'}
                        </LeaderValue>
                        {overlay.returnPercent != null && (
                          <LeaderReturn $positive={overlay.returnPercent >= 0}>
                            {formatPercent(overlay.returnPercent)}
                          </LeaderReturn>
                        )}
                      </LeaderStats>
                    </LeaderItem>
                  );
                })}
            </LeaderList>
          </Section>
        </>
      )}

    </SidebarContainer>
  );
}
