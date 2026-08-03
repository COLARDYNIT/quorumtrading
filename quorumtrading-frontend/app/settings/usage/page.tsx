'use client';

import { useMemo } from 'react';
import styled from 'styled-components';
import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { Panel, KpiStrip, type KpiItem } from '@/app/components/shared/v2';
import { useLLMUsage } from '@/app/lib/api/hooks';

function formatCurrency(value: number): string {
  // Round any usage UP to the next cent so even sub-half-cent spend shows as $0.01.
  const ceiledToCent = Math.ceil(value * 100) / 100;
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(ceiledToCent);
}

function formatTokens(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`;
  return value.toLocaleString();
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  return `${day} ${mo}`;
}

function weekdayShort(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('en-US', { weekday: 'short' });
}

export default function UsagePage() {
  const { data: usage, isLoading, error } = useLLMUsage();

  const sortedDailyUsage = useMemo(() => {
    if (!usage?.dailyUsage) return [];
    return [...usage.dailyUsage].sort(
      (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
    );
    // `usage`, not `usage?.dailyUsage` — an optional-chained dependency is narrower than what the
    // React Compiler infers, so it skips compiling this component rather than risk changing its
    // memoization semantics.
  }, [usage]);

  const maxDailyCost = useMemo(() => {
    if (sortedDailyUsage.length === 0) return 0;
    return Math.max(...sortedDailyUsage.map((d) => d.billedCostUsd));
  }, [sortedDailyUsage]);

  const activeDays = sortedDailyUsage.filter((d) => d.billedCostUsd > 0).length;

  const kpis: KpiItem[] = usage
    ? [
        {
          label: 'This week',
          value: formatCurrency(usage.weeklyTotalUsd),
          delta: 'last 7 days',
        },
        {
          label: 'This month',
          value: formatCurrency(usage.monthlyTotalUsd),
          delta: `${activeDays} day${activeDays === 1 ? '' : 's'} active`,
        },
        {
          label: 'All time',
          value: formatCurrency(usage.allTimeTotalUsd),
          delta: 'since account creation',
        },
      ]
    : [];

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>LLM usage</H1>
            <Desc>
              Track your AI usage costs across consensus decisions, market research and chat.
              Spend reflects the billed cost (raw LLM cost plus the platform markup) and updates in real time as
              the council votes.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        {usage && <KpiStrip items={kpis} />}

        <Panel
          title="Daily usage"
          count={sortedDailyUsage.length}
          headerActions={
            <HeaderNote>billed cost · last 30 days</HeaderNote>
          }
          noBodyPadding
        >
          {error ? (
            <EmptyState>
              <EmptyIcon>!</EmptyIcon>
              <EmptyTitle>Couldn&apos;t load usage.</EmptyTitle>
              <EmptyBody>{(error as Error).message}</EmptyBody>
            </EmptyState>
          ) : isLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 4 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="60px" $h="13px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="80%" $h="4px" />
                  <SkelBar $w="60px" $h="13px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : sortedDailyUsage.length === 0 ? (
            <EmptyState>
              <EmptyIcon>·</EmptyIcon>
              <EmptyTitle>No usage recorded yet.</EmptyTitle>
              <EmptyBody>
                Run a backtest, research execution or chat conversation and the meter will start
                counting.
              </EmptyBody>
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span>Day</span>
                <RightAligned>Tokens</RightAligned>
                <span>Share</span>
                <RightAligned>Cost</RightAligned>
              </HeaderRowBar>
              {sortedDailyUsage.map((day) => (
                <Row key={day.date}>
                  <DayCell>
                    <DayName>{weekdayShort(day.date)}</DayName>
                    <DayDate>{formatDate(day.date)}</DayDate>
                  </DayCell>
                  <Tokens>
                    {formatTokens(day.totalTokens)}
                    <TokensSub>tokens</TokensSub>
                  </Tokens>
                  <ShareCell>
                    <ShareBar>
                      <ShareFill
                        style={{
                          width: maxDailyCost > 0
                            ? `${(day.billedCostUsd / maxDailyCost) * 100}%`
                            : '0%',
                        }}
                      />
                    </ShareBar>
                  </ShareCell>
                  <Cost>{formatCurrency(day.billedCostUsd)}</Cost>
                </Row>
              ))}
            </Rows>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

// ─── Styled ──────────────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 24px;
  min-width: 0;
`;

const PageHeader = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 4px 0 0;
`;

const HeaderLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const Desc = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const HeaderNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ROW_COLS = '110px 110px minmax(0, 1fr) 100px';

const HeaderRowBar = styled.div`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 10px 18px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  @media (max-width: 768px) {
    display: none;
  }
`;

const RightAligned = styled.span`
  text-align: right;
`;

const Row = styled.div`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 11px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  @media (max-width: 768px) {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    padding: 14px 18px;
  }
`;

const DayCell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1px;
`;

const DayName = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const DayDate = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
`;

const Tokens = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 13.5px;
  color: ${p => p.theme.colors.foreground};
`;

const TokensSub = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  font-variant-numeric: normal;
`;

const ShareCell = styled.div`
  display: flex;
  align-items: center;
  width: 100%;

  @media (max-width: 768px) {
    grid-column: 1 / -1;
  }
`;

const ShareBar = styled.div`
  width: 100%;
  height: 4px;
  background: ${p => p.theme.colors.surfaceElevated};
  border-radius: 2px;
  overflow: hidden;
`;

const ShareFill = styled.div`
  height: 100%;
  background: ${p => p.theme.colors.brand};
  border-radius: 2px;
  transition: width 200ms ease;
`;

const Cost = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
  text-align: right;
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 32px;
  gap: 10px;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 14px;
  line-height: 1.55;
  max-width: 56ch;
  margin: 0 auto;
`;

const EmptyIcon = styled.div`
  width: 40px;
  height: 40px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 17px;
`;

const EmptyTitle = styled.h3`
  color: ${p => p.theme.colors.fgStrong};
  font-size: 15px;
  margin: 4px 0 0;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const EmptyBody = styled.p`
  margin: 0;
`;

const SkeletonBlock = styled.div`
  display: flex;
  flex-direction: column;
`;

const SkeletonRow = styled.div`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }
`;

const SkelBar = styled.div<{ $w: string; $h: string }>`
  width: ${p => p.$w};
  height: ${p => p.$h};
  border-radius: 3px;
  background: linear-gradient(
    90deg,
    ${p => p.theme.colors.surfaceElevated} 0%,
    ${p => p.theme.colors.borderSubtle} 50%,
    ${p => p.theme.colors.surfaceElevated} 100%
  );
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s linear infinite;

  @keyframes skel-shimmer {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }
`;
