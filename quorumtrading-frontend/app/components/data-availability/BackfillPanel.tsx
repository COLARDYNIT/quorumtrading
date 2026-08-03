'use client';

import { useState } from 'react';
import styled from 'styled-components';
import { TerminalButton } from '@/app/components/terminal';
import { useRequestBackfill } from '@/app/lib/api/hooks';
import { useBackfillProgress } from '@/app/lib/websocket/useBackfillProgress';
import { EventType, DataGap, GapStatus } from '@/app/lib/types/dataAvailability';

interface Props {
  gaps: DataGap[];
  symbols: string[];
  dataTypes: EventType[];
  from: string;
  to: string;
}

const Container = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.lg};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
`;

const SectionTitle = styled.h3`
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.primary};
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const GapsSummary = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const GapsCount = styled.p`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  margin: 0;
`;

const GapsList = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const GapItem = styled.li<{ $isMarketWide?: boolean }>`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$isMarketWide ? p.theme.colors.warning : p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  padding: ${p => p.theme.spacing.xs};
  background: ${p => p.$isMarketWide ? p.theme.colors.warning + '10' : p.theme.colors.error + '10'};
  border: 1px solid ${p => p.$isMarketWide ? p.theme.colors.warning + '40' : p.theme.colors.error + '40'};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const ActiveJobsSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
  padding-top: ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

const JobTitle = styled.p`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  margin: 0;
`;

const JobsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const JobCard = styled.div`
  background: ${p => p.theme.colors.background};
  padding: ${p => p.theme.spacing.sm};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const JobStatus = styled.div<{ $status: string }>`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => {
    if (p.$status === 'COMPLETED') return p.theme.colors.success;
    if (p.$status === 'FAILED') return p.theme.colors.error;
    if (p.$status === 'IN_PROGRESS') return p.theme.colors.warning;
    return p.theme.colors.textMuted;
  }};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const ProgressContainer = styled.div`
  margin-top: ${p => p.theme.spacing.xs};
`;

const ProgressBar = styled.div`
  height: 8px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  overflow: hidden;
`;

const ProgressFill = styled.div<{ $percentage: number }>`
  height: 100%;
  width: ${p => p.$percentage}%;
  background: ${p => p.theme.colors.primary};
  transition: width 0.3s ease;
  box-shadow: 0 0 10px ${p => p.theme.colors.primary}40;
`;

const ProgressText = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  margin-top: ${p => p.theme.spacing.xs};
`;

const GapDetailsToggle = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.primary};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  cursor: pointer;
  padding: ${p => p.theme.spacing.xs} 0;
  text-align: left;
  opacity: 0.8;

  &:hover {
    opacity: 1;
  }
`;

const GapDetailsSection = styled.div`
  margin-top: ${p => p.theme.spacing.sm};
  padding-top: ${p => p.theme.spacing.sm};
  border-top: 1px dashed ${p => p.theme.colors.border};
`;

const GapDetailsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
  max-height: 200px;
  overflow-y: auto;
`;

const GapDetailItem = styled.div<{ $status: GapStatus }>`
  display: flex;
  align-items: flex-start;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.xs};
  background: ${p => {
    if (p.$status === GapStatus.SUCCESS) return p.theme.colors.success + '10';
    if (p.$status === GapStatus.FAILED) return p.theme.colors.error + '10';
    if (p.$status === GapStatus.SKIPPED) return p.theme.colors.warning + '10';
    return 'transparent';
  }};
  border-left: 2px solid ${p => {
    if (p.$status === GapStatus.SUCCESS) return p.theme.colors.success;
    if (p.$status === GapStatus.FAILED) return p.theme.colors.error;
    if (p.$status === GapStatus.SKIPPED) return p.theme.colors.warning;
    return p.theme.colors.border;
  }};
  border-radius: 0 ${p => p.theme.borderRadius.sm} ${p => p.theme.borderRadius.sm} 0;
`;

const GapStatusIcon = styled.span<{ $status: GapStatus }>`
  font-size: ${p => p.theme.typography.fontSize.sm};
  min-width: 16px;
`;

const GapDetailContent = styled.div`
  flex: 1;
  min-width: 0;
`;

const GapDetailMain = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.text};
`;

const GapDetailMeta = styled.div`
  font-size: 10px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.textMuted};
  margin-top: 2px;
`;

const GapDetailError = styled.div`
  font-size: 10px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.error};
  margin-top: 2px;
`;

const GapSummary = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.md};
  margin-top: ${p => p.theme.spacing.xs};
  font-size: 10px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const GapSummaryItem = styled.span<{ $color: string }>`
  color: ${p => p.$color};
`;

function getStatusIcon(status: GapStatus): string {
  switch (status) {
    case GapStatus.SUCCESS: return '✓';
    case GapStatus.FAILED: return '✗';
    case GapStatus.SKIPPED: return '⊘';
    case GapStatus.IN_PROGRESS: return '◐';
    case GapStatus.PENDING: return '○';
    default: return '?';
  }
}

export function BackfillPanel({ gaps, symbols, dataTypes, from, to }: Props) {
  const { mutate: requestBackfill, isPending } = useRequestBackfill();
  const { allJobs } = useBackfillProgress();
  const [expandedJobs, setExpandedJobs] = useState<Set<string>>(new Set());

  console.log('[BackfillPanel] All jobs:', allJobs);

  const handleBackfill = () => {
    requestBackfill({
      symbols,
      dataTypes,
      from,
      to,
      forceBackfill: false,
    });
  };

  const toggleJobExpanded = (jobId: string) => {
    setExpandedJobs(prev => {
      const next = new Set(prev);
      if (next.has(jobId)) {
        next.delete(jobId);
      } else {
        next.add(jobId);
      }
      return next;
    });
  };

  // Show all jobs (active + recently completed) - not just active ones
  // Sort by updatedAt descending so newest are first
  const sortedJobs = [...allJobs].sort((a, b) =>
    new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  );

  // Active jobs (PENDING or IN_PROGRESS)
  const activeJobs = sortedJobs.filter(job =>
    job.status === 'PENDING' || job.status === 'IN_PROGRESS'
  );

  // Recently completed jobs (COMPLETED or FAILED)
  const completedJobs = sortedJobs.filter(job =>
    job.status === 'COMPLETED' || job.status === 'FAILED'
  );

  return (
    <Container>
      <SectionTitle>BACKFILL</SectionTitle>

      {/* Gaps Summary */}
      <GapsSummary>
        <GapsCount>
          {gaps.length > 0 ? `${gaps.length} gaps detected` : 'No gaps detected'}
        </GapsCount>
        {gaps.length > 0 && (
          <GapsList>
            {gaps.slice(0, 5).map((gap, i) => {
              const isMarketWide = gap.symbol === 'MARKET';
              return (
                <GapItem key={i} $isMarketWide={isMarketWide}>
                  {isMarketWide ? '🌐 ' : ''}{gap.symbol} - {gap.dataType} ({gap.from} to {gap.to})
                </GapItem>
              );
            })}
            {gaps.length > 5 && (
              <GapItem>...and {gaps.length - 5} more</GapItem>
            )}
          </GapsList>
        )}
      </GapsSummary>

      {/* Backfill Button */}
      <TerminalButton
        onClick={handleBackfill}
        disabled={isPending || symbols.length === 0}
        size="sm"
      >
        {isPending ? 'Requesting...' : 'Start Backfill'}
      </TerminalButton>

      {/* Active Jobs */}
      {activeJobs.length > 0 && (
        <ActiveJobsSection>
          <JobTitle>Active Jobs</JobTitle>
          <JobsList>
            {activeJobs.map(job => {
              const successCount = job.gapDetails.filter(g => g.status === GapStatus.SUCCESS).length;
              const failedCount = job.gapDetails.filter(g => g.status === GapStatus.FAILED).length;
              const skippedCount = job.gapDetails.filter(g => g.status === GapStatus.SKIPPED).length;
              const isExpanded = expandedJobs.has(job.jobId);

              return (
                <JobCard key={job.jobId}>
                  <JobStatus $status={job.status}>{job.status}</JobStatus>
                  {job.progressPercentage !== undefined && job.progressPercentage !== null && (
                    <ProgressContainer>
                      <ProgressBar>
                        <ProgressFill $percentage={job.progressPercentage} />
                      </ProgressBar>
                      <ProgressText>
                        {job.progressPercentage.toFixed(0)}% - {job.message}
                      </ProgressText>
                      {job.gapDetails.length > 0 && (
                        <GapSummary>
                          {successCount > 0 && (
                            <GapSummaryItem $color="#22c55e">✓ {successCount}</GapSummaryItem>
                          )}
                          {failedCount > 0 && (
                            <GapSummaryItem $color="#ef4444">✗ {failedCount}</GapSummaryItem>
                          )}
                          {skippedCount > 0 && (
                            <GapSummaryItem $color="#f59e0b">⊘ {skippedCount}</GapSummaryItem>
                          )}
                        </GapSummary>
                      )}
                    </ProgressContainer>
                  )}
                  {job.gapDetails.length > 0 && (
                    <>
                      <GapDetailsToggle onClick={() => toggleJobExpanded(job.jobId)}>
                        {isExpanded ? '▼ Hide gap details' : '▶ Show gap details'}
                      </GapDetailsToggle>
                      {isExpanded && (
                        <GapDetailsSection>
                          <GapDetailsList>
                            {job.gapDetails.map((gap, idx) => (
                              <GapDetailItem key={idx} $status={gap.status}>
                                <GapStatusIcon $status={gap.status}>
                                  {getStatusIcon(gap.status)}
                                </GapStatusIcon>
                                <GapDetailContent>
                                  <GapDetailMain>
                                    {gap.symbol === 'MARKET' ? '🌐 ' : ''}{gap.symbol} - {gap.dataType}
                                  </GapDetailMain>
                                  <GapDetailMeta>
                                    {gap.from} to {gap.to}
                                    {gap.provider && ` • via ${gap.provider}`}
                                    {gap.eventsFetched !== null && ` • ${gap.eventsFetched} events`}
                                  </GapDetailMeta>
                                  {gap.error && (
                                    <GapDetailError>{gap.error}</GapDetailError>
                                  )}
                                </GapDetailContent>
                              </GapDetailItem>
                            ))}
                          </GapDetailsList>
                        </GapDetailsSection>
                      )}
                    </>
                  )}
                </JobCard>
              );
            })}
          </JobsList>
        </ActiveJobsSection>
      )}

      {/* Recently Completed Jobs */}
      {completedJobs.length > 0 && (
        <ActiveJobsSection>
          <JobTitle>Recent Jobs</JobTitle>
          <JobsList>
            {completedJobs.slice(0, 5).map(job => {
              const successCount = job.gapDetails.filter(g => g.status === GapStatus.SUCCESS).length;
              const failedCount = job.gapDetails.filter(g => g.status === GapStatus.FAILED).length;
              const skippedCount = job.gapDetails.filter(g => g.status === GapStatus.SKIPPED).length;
              const isExpanded = expandedJobs.has(job.jobId);

              return (
                <JobCard key={job.jobId}>
                  <JobStatus $status={job.status}>{job.status}</JobStatus>
                  <ProgressText>{job.message}</ProgressText>
                  {job.gapDetails.length > 0 && (
                    <>
                      <GapSummary>
                        {successCount > 0 && (
                          <GapSummaryItem $color="#22c55e">✓ {successCount}</GapSummaryItem>
                        )}
                        {failedCount > 0 && (
                          <GapSummaryItem $color="#ef4444">✗ {failedCount}</GapSummaryItem>
                        )}
                        {skippedCount > 0 && (
                          <GapSummaryItem $color="#f59e0b">⊘ {skippedCount}</GapSummaryItem>
                        )}
                      </GapSummary>
                      <GapDetailsToggle onClick={() => toggleJobExpanded(job.jobId)}>
                        {isExpanded ? '▼ Hide gap details' : '▶ Show gap details'}
                      </GapDetailsToggle>
                      {isExpanded && (
                        <GapDetailsSection>
                          <GapDetailsList>
                            {job.gapDetails.map((gap, idx) => (
                              <GapDetailItem key={idx} $status={gap.status}>
                                <GapStatusIcon $status={gap.status}>
                                  {getStatusIcon(gap.status)}
                                </GapStatusIcon>
                                <GapDetailContent>
                                  <GapDetailMain>
                                    {gap.symbol === 'MARKET' ? '🌐 ' : ''}{gap.symbol} - {gap.dataType}
                                  </GapDetailMain>
                                  <GapDetailMeta>
                                    {gap.from} to {gap.to}
                                    {gap.provider && ` • via ${gap.provider}`}
                                    {gap.eventsFetched !== null && ` • ${gap.eventsFetched} events`}
                                  </GapDetailMeta>
                                  {gap.error && (
                                    <GapDetailError>{gap.error}</GapDetailError>
                                  )}
                                </GapDetailContent>
                              </GapDetailItem>
                            ))}
                          </GapDetailsList>
                        </GapDetailsSection>
                      )}
                    </>
                  )}
                </JobCard>
              );
            })}
          </JobsList>
        </ActiveJobsSection>
      )}
    </Container>
  );
}
