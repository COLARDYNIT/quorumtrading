'use client';

import { Suspense, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { Panel, StatusPill } from '@/app/components/shared/v2';
import {
  useMarketResearch,
  usePauseMarketResearch,
  useResumeMarketResearch,
  useDeleteMarketResearch,
  usePruneUnusedResearch,
} from '@/app/lib/api/hooks';
import type { MarketResearchResponse, ResearchStatus } from '@/app/lib/types/api';
import { assetTicker } from '@/app/lib/utils/targetAssets';

type FilterType = 'all' | 'active' | 'paused' | 'completed';

function statusKind(status: ResearchStatus): 'simulating' | 'pending' | 'done' {
  if (status === 'ACTIVE') return 'simulating';
  if (status === 'PAUSED') return 'pending';
  return 'done';
}

function formatInterval(amount: number, unit: string): string {
  const u = unit.toLowerCase();
  const singular = u.replace(/s$/, '');
  return amount === 1 ? `1 ${singular}` : `${amount} ${u.endsWith('s') ? u : u + 's'}`;
}

function formatDate(iso: string | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  const yr = d.getFullYear();
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${day} ${mo} · ${hh}:${mm}`;
}

function MarketResearchListPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { data, isLoading, error } = useMarketResearch();
  const pauseMutation = usePauseMarketResearch();
  const resumeMutation = useResumeMarketResearch();
  const deleteMutation = useDeleteMarketResearch();
  const pruneMutation = usePruneUnusedResearch();

  const list = data ?? [];

  const filterParam = searchParams.get('filter') as FilterType | null;
  const [filter, setFilter] = useState<FilterType>(
    filterParam && ['all', 'active', 'paused', 'completed'].includes(filterParam) ? filterParam : 'all'
  );
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (filterParam && ['all', 'active', 'paused', 'completed'].includes(filterParam)) {
      setFilter(filterParam);
    }
  }, [filterParam]);

  const counts = useMemo(() => ({
    all: list.length,
    active: list.filter((r) => r.status === 'ACTIVE').length,
    paused: list.filter((r) => r.status === 'PAUSED').length,
    completed: list.filter((r) => r.status === 'COMPLETED').length,
  }), [list]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return list
      .filter((r) => filter === 'all' || r.status === filter.toUpperCase())
      .filter((r) => !q || r.name.toLowerCase().includes(q) || r.targetAssets.some((t) => t.toLowerCase().includes(q)));
  }, [list, filter, search]);

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>Research</H1>
            <Desc>
              Scheduled council runs against historical bars. Each tick votes a strategy idea — auto-backtest can
              promote winners straight into your strategy library.
            </Desc>
          </HeaderLeft>
          <HeaderRight>
            <GhostBtn
              type="button"
              onClick={() => pruneMutation.mutate()}
              disabled={pruneMutation.isPending}
            >
              {pruneMutation.isPending ? 'Cleaning…' : 'Clean up unused'}
            </GhostBtn>
            <NewLink href="/research/new">
              <span aria-hidden="true">+</span> New research
            </NewLink>
          </HeaderRight>
        </PageHeader>

        <Toolbar>
          <ToolbarLeft>
            <ToolbarLabel>Status</ToolbarLabel>
            <Seg>
              {(['all', 'active', 'paused', 'completed'] as FilterType[]).map((f) => (
                <SegBtn key={f} $on={filter === f} type="button" onClick={() => setFilter(f)}>
                  {f === 'all' ? 'All' : f.charAt(0).toUpperCase() + f.slice(1)}
                  <SegCount>{counts[f]}</SegCount>
                </SegBtn>
              ))}
            </Seg>
          </ToolbarLeft>
          <SearchInput
            type="search"
            placeholder="Filter by name or ticker"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </Toolbar>

        <Panel title="Research" count={filtered.length} noBodyPadding>
          {error ? (
            <EmptyState>
              <EmptyIcon>!</EmptyIcon>
              <EmptyTitle>Couldn&apos;t load research.</EmptyTitle>
              <EmptyBody>{(error as Error).message}</EmptyBody>
            </EmptyState>
          ) : isLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 4 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="60px" $h="20px" />
                  <SkelBar $w="55%" $h="13px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="60px" $h="20px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : filtered.length === 0 ? (
            <EmptyState>
              <EmptyIcon>?</EmptyIcon>
              <EmptyTitle>
                {filter === 'all' ? 'No research yet.' : `No ${filter} research found.`}
              </EmptyTitle>
              <EmptyBody>
                A council vote against historical bars — cheaper than a backtest, faster to iterate.
                {' '}Auto-backtest promotes the winners straight into your strategy library.
              </EmptyBody>
              {filter === 'all' && (
                <NewLink href="/research/new" style={{ marginTop: 8 }}>
                  <span aria-hidden="true">+</span> Start your first research
                </NewLink>
              )}
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span>Status</span>
                <span>Name · tickers</span>
                <span>Interval</span>
                <span>Last run</span>
                <span>Next run</span>
                <span>Strategies</span>
                <RightAligned>Actions</RightAligned>
              </HeaderRowBar>
              {filtered.map((r) => (
                <ResearchRow
                  key={r.id}
                  research={r}
                  onOpen={() => router.push(`/research/${r.id}`)}
                  onPause={() => pauseMutation.mutate(r.id)}
                  onResume={() => resumeMutation.mutate(r.id)}
                  onDelete={() => {
                    if (confirm(`Delete "${r.name}"?`)) deleteMutation.mutate(r.id);
                  }}
                />
              ))}
            </Rows>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

interface ResearchRowProps {
  research: MarketResearchResponse;
  onOpen: () => void;
  onPause: () => void;
  onResume: () => void;
  onDelete: () => void;
}

function ResearchRow({ research, onOpen, onPause, onResume, onDelete }: ResearchRowProps) {
  const tickers = research.targetAssets.slice(0, 3);
  const extra = research.targetAssets.length - tickers.length;

  return (
    <Row onClick={onOpen}>
      <StatusCell>
        <StatusPill kind={statusKind(research.status)}>{research.status.toLowerCase()}</StatusPill>
      </StatusCell>
      <NameCell>
        <NameTop>{research.name}</NameTop>
        <NameSub>
          <Tickers>
            {tickers.map((t) => <Ticker key={t}>{assetTicker(t)}</Ticker>)}
            {extra > 0 && <Ticker $more>+{extra}</Ticker>}
          </Tickers>
          <SubSep>·</SubSep>
          <Mono>#{research.id.slice(0, 8)}</Mono>
          {research.autoBacktest && (
            <>
              <SubSep>·</SubSep>
              <span>auto-backtest</span>
            </>
          )}
        </NameSub>
      </NameCell>
      <Interval>{formatInterval(research.interval.amount, research.interval.unit)}</Interval>
      <Cell>
        {research.lastExecutedAt ? formatDate(research.lastExecutedAt) : <Dim>never</Dim>}
      </Cell>
      <Cell>
        {research.status === 'PAUSED'
          ? <Warn>paused</Warn>
          : research.nextScheduledRun
            ? formatDate(research.nextScheduledRun)
            : <Dim>—</Dim>}
      </Cell>
      <StratCount>
        {research.createdStrategiesCount}
        <StratCountSub>created</StratCountSub>
      </StratCount>
      <RowActs onClick={(e) => e.stopPropagation()}>
        <RowActBtn $primary type="button" onClick={onOpen}>Open</RowActBtn>
        {research.status === 'ACTIVE' && (
          <RowActBtn type="button" onClick={onPause}>Pause</RowActBtn>
        )}
        {research.status === 'PAUSED' && (
          <RowActBtn $primary type="button" onClick={onResume}>Resume</RowActBtn>
        )}
        <RowActBtn $danger type="button" onClick={onDelete} title="Delete" aria-label="Delete">×</RowActBtn>
      </RowActs>
    </Row>
  );
}

export default function MarketResearchListPage() {
  return (
    <Suspense fallback={
      <DashboardLayout>
        <Page>
          <SkelBar $w="200px" $h="22px" />
        </Page>
      </DashboardLayout>
    }>
      <MarketResearchListPageContent />
    </Suspense>
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

const HeaderRight = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
`;

const baseBtnStyles = css`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 6px 12px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  text-decoration: none;
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostBtn = styled.button`
  ${baseBtnStyles}
  &:hover:not(:disabled) {
    color: ${p => p.theme.colors.fgStrong};
    border-color: ${p => p.theme.colors.fgMuted};
    background: rgba(255,255,255,0.02);
  }
`;

const NewLink = styled(Link)`
  ${baseBtnStyles}
  color: ${p => p.theme.colors.brand};
  border-color: rgba(0,255,65,0.4);
  &:hover {
    color: ${p => p.theme.colors.brandHover};
    border-color: ${p => p.theme.colors.brand};
    background: rgba(0,255,65,0.08);
  }
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

const SearchInput = styled.input`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  padding: 6px 10px;
  color: ${p => p.theme.colors.foreground};
  min-width: 260px;
  outline: none;
  transition: border-color 120ms ease;

  &:focus { border-color: ${p => p.theme.colors.focusRing}; }
  &::placeholder { color: ${p => p.theme.colors.fgDimmed}; }

  @media (max-width: 640px) {
    min-width: 0;
    width: 100%;
  }
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ROW_COLS = '90px minmax(0, 1fr) 130px 150px 150px 110px auto';

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
  cursor: pointer;
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

const Tickers = styled.span`
  display: inline-flex;
  gap: 4px;
`;

const Ticker = styled.span<{ $more?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.$more ? p.theme.colors.fgMuted : p.theme.colors.foreground};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 1px 6px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const SubSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const Interval = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.foreground};
`;

const Cell = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: ${p => p.theme.colors.foreground};
`;

const Dim = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
`;

const Warn = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
`;

const StratCount = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
  display: flex;
  flex-direction: column;
  gap: 1px;
`;

const StratCountSub = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const RowActs = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: end;

  @media (max-width: 768px) {
    justify-self: stretch;
    flex-wrap: wrap;
  }
`;

const RowActBtn = styled.button<{ $primary?: boolean; $danger?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 4px 9px;
  border-radius: 2px;
  border: 1px solid
    ${p => p.$primary
      ? 'rgba(0,255,65,0.4)'
      : p.$danger
        ? 'rgba(255,51,102,0.35)'
        : p.theme.colors.border};
  background: transparent;
  color: ${p => p.$primary
    ? p.theme.colors.brand
    : p.$danger
      ? p.theme.colors.negative
      : p.theme.colors.fgMuted};
  cursor: pointer;
  white-space: nowrap;
  line-height: 1;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:hover {
    ${p => p.$primary && css`
      color: ${p.theme.colors.brandHover};
      border-color: ${p.theme.colors.brand};
      background: rgba(0,255,65,0.08);
    `}
    ${p => p.$danger && css`
      border-color: ${p.theme.colors.negative};
      background: rgba(255,51,102,0.08);
    `}
    ${p => !p.$primary && !p.$danger && css`
      color: ${p.theme.colors.fgStrong};
      border-color: ${p.theme.colors.fgMuted};
      background: rgba(255,255,255,0.02);
    `}
  }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
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
