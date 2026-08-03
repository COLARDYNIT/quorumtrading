'use client';

import { Suspense, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { Panel, StatusPill } from '@/app/components/shared/v2';
import { useChallenges } from '@/app/lib/api/hooks';
import {
  type ChallengeResponse,
  formatChallengeTimeRemaining,
} from '@/app/lib/types/challenge';

type ChallengeState = 'UPCOMING' | 'ACTIVE' | 'COMPLETED';

const STATE_ORDER: ChallengeState[] = ['UPCOMING', 'ACTIVE', 'COMPLETED'];

const STATE_LABEL: Record<ChallengeState, string> = {
  UPCOMING: 'Upcoming',
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
};

const STATE_HEADER_NOTE: Record<ChallengeState, string> = {
  UPCOMING: 'queued · entries close at start',
  ACTIVE: 'live · accepting entries',
  COMPLETED: 'archived · viewable only',
};

function stateKind(state: ChallengeState): 'pending' | 'live' | 'completed' {
  if (state === 'UPCOMING') return 'pending';
  if (state === 'ACTIVE') return 'live';
  return 'completed';
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  const yr = d.getFullYear();
  return `${day} ${mo} ${yr}`;
}

function formatCash(amount: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(amount);
}

function daysFromNow(iso: string): number {
  const now = Date.now();
  const then = new Date(iso).getTime();
  return Math.max(0, Math.ceil((then - now) / (24 * 60 * 60 * 1000)));
}

export default function ChallengesPage() {
  return (
    <Suspense>
      <ChallengesPageContent />
    </Suspense>
  );
}

function ChallengesPageContent() {
  const searchParams = useSearchParams();
  const statusParam = searchParams.get('status') as ChallengeState | 'all' | null;
  const [filter, setFilter] = useState<ChallengeState | 'all'>(
    statusParam && ['ACTIVE', 'UPCOMING', 'COMPLETED'].includes(statusParam) ? statusParam : 'all'
  );

  const { data, isLoading } = useChallenges();
  const challenges = data ?? [];

  const counts = useMemo(() => ({
    all: challenges.length,
    UPCOMING: challenges.filter((c) => c.status === 'UPCOMING').length,
    ACTIVE: challenges.filter((c) => c.status === 'ACTIVE').length,
    COMPLETED: challenges.filter((c) => c.status === 'COMPLETED').length,
  }), [challenges]);

  const visibleStates = filter === 'all' ? STATE_ORDER : [filter];

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>Challenges</H1>
            <Desc>
              Time-bounded public arenas. Submit a strategy, get ranked alongside everyone else&apos;s
              against the same ticker and time range.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        <Toolbar>
          <ToolbarLeft>
            <ToolbarLabel>State</ToolbarLabel>
            <Seg>
              {(['all', 'UPCOMING', 'ACTIVE', 'COMPLETED'] as const).map((s) => (
                <SegBtn
                  key={s}
                  $on={filter === s}
                  type="button"
                  onClick={() => setFilter(s as typeof filter)}
                >
                  {s === 'all' ? 'All' : STATE_LABEL[s]}
                  <SegCount>{counts[s]}</SegCount>
                </SegBtn>
              ))}
            </Seg>
          </ToolbarLeft>
        </Toolbar>

        {isLoading ? (
          <Panel title="Challenges" noBodyPadding>
            <SkeletonBlock>
              {Array.from({ length: 3 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="60px" $h="20px" />
                  <SkelBar $w="55%" $h="13px" />
                  <SkelBar $w="60px" $h="20px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="120px" $h="14px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          </Panel>
        ) : challenges.length === 0 ? (
          <Panel title="Challenges" noBodyPadding>
            <EmptyState>
              <EmptyIcon>◆</EmptyIcon>
              <EmptyTitle>No challenges yet.</EmptyTitle>
              <EmptyBody>
                The next challenge is being assembled. Check back soon, or follow the changelog for announcements.
              </EmptyBody>
            </EmptyState>
          </Panel>
        ) : (
          visibleStates.map((s) => {
            const rows = challenges.filter((c) => c.status === s);
            return (
              <Panel
                key={s}
                title={STATE_LABEL[s]}
                count={rows.length}
                noBodyPadding
                headerActions={<HeaderNote $state={s}>{STATE_HEADER_NOTE[s]}</HeaderNote>}
              >
                {rows.length === 0 ? (
                  <EmptyInline>no challenges in this state</EmptyInline>
                ) : (
                  <Rows>
                    <HeaderRowBar>
                      <span>State</span>
                      <span>Name · range</span>
                      <span>Ticker</span>
                      <span>Entries</span>
                      <span>{s === 'COMPLETED' ? 'Winner' : s === 'ACTIVE' ? 'Leader' : 'Status'}</span>
                      <RightAligned>Actions</RightAligned>
                    </HeaderRowBar>
                    {rows.map((c) => <ChallengeRow key={c.id} challenge={c} />)}
                  </Rows>
                )}
              </Panel>
            );
          })
        )}
      </Page>
    </DashboardLayout>
  );
}

interface ChallengeRowProps {
  challenge: ChallengeResponse;
}

function ChallengeRow({ challenge: c }: ChallengeRowProps) {
  const top = c.entries.length > 0 ? c.entries[0] : null;
  return (
    <Row href={`/challenges/${c.slug}`}>
      <StatusCell>
        <StatusPill kind={stateKind(c.status)}>
          {c.status === 'UPCOMING' ? 'upcoming' : c.status === 'ACTIVE' ? 'live' : 'done'}
        </StatusPill>
      </StatusCell>
      <NameCell>
        <NameTop>{c.name}</NameTop>
        <NameSub>
          <Range>{formatDate(c.simulationStartDate)} → {formatDate(c.simulationEndDate)}</Range>
          <SubSep>·</SubSep>
          <span>initial {formatCash(c.initialCash)}</span>
        </NameSub>
      </NameCell>
      <TickerChip>{c.ticker}</TickerChip>
      <EntriesCell>
        {c.participantCount}
        <CellSub>entries</CellSub>
      </EntriesCell>
      <LeaderCell>
        {top ? (
          <>
            <LeaderLabel>{c.status === 'COMPLETED' ? 'Winner' : 'Leader'}</LeaderLabel>
            <LeaderUser>@{top.ownerDisplayName}</LeaderUser>
            {top.returnPercent != null && (
              <LeaderReturn $positive={Number(top.returnPercent) >= 0}>
                {Number(top.returnPercent) >= 0 ? '+' : ''}{Number(top.returnPercent).toFixed(1)}%
              </LeaderReturn>
            )}
          </>
        ) : c.status === 'UPCOMING' ? (
          <>
            <LeaderLabel>Starts</LeaderLabel>
            <LeaderUser>{daysFromNow(c.simulationStartDate)}d</LeaderUser>
          </>
        ) : c.status === 'ACTIVE' ? (
          <>
            <LeaderLabel>Closes</LeaderLabel>
            <LeaderUser>{formatChallengeTimeRemaining(c.participationClose)}</LeaderUser>
          </>
        ) : (
          <LeaderLabel>—</LeaderLabel>
        )}
      </LeaderCell>
      <RowActs onClick={(e) => e.stopPropagation()}>
        <RowActSpan>Open →</RowActSpan>
      </RowActs>
    </Row>
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

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
`;

const ToolbarLeft = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
`;

const ToolbarLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Seg = styled.div`
  display: inline-flex;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  overflow: hidden;
`;

const SegBtn = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  background: ${p => p.$on ? 'rgba(255,255,255,0.05)' : 'transparent'};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  border: none;
  padding: 5px 12px;
  cursor: pointer;
  border-right: 1px solid ${p => p.theme.colors.border};
  transition: color 120ms ease, background 120ms ease;
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &:last-child { border-right: none; }
  &:hover { color: ${p => p.theme.colors.fgStrong}; }
`;

const SegCount = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const HeaderNote = styled.span<{ $state: ChallengeState }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  display: inline-flex;
  align-items: center;
  gap: 6px;

  ${p => p.$state === 'ACTIVE' && css`
    &::before {
      content: '●';
      color: ${p.theme.colors.brand};
    }
  `}
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ROW_COLS = '80px minmax(0, 1fr) 100px 90px 140px 90px';

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

const Row = styled(Link)`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 11px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  text-decoration: none;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.02); }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 14px 18px;
  }
`;

const StatusCell = styled.div`
  display: inline-flex;
  align-items: center;
`;

const NameCell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
`;

const NameTop = styled.span`
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const NameSub = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 12.5px;
  flex-wrap: wrap;
`;

const Range = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
`;

const SubSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const TickerChip = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.foreground};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 1px 7px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  width: max-content;
`;

const EntriesCell = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
  display: flex;
  flex-direction: column;
  gap: 1px;
`;

const CellSub = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const LeaderCell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
`;

const LeaderLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const LeaderUser = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const LeaderReturn = styled.span<{ $positive: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12.5px;
  color: ${p => p.$positive ? p.theme.colors.positive : p.theme.colors.negative};
`;

const RowActs = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: end;

  @media (max-width: 768px) {
    justify-self: start;
  }
`;

const RowActSpan = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  transition: color 120ms ease, transform 120ms ease;

  ${Row}:hover & {
    color: ${p => p.theme.colors.brand};
  }
`;

const EmptyInline = styled.div`
  padding: 18px 18px;
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
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
  padding: 12px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  &:last-child { border-bottom: none; }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 8px;
  }
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
