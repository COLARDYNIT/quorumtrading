'use client';

import { Suspense, useMemo } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import styled, { css } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { Panel, KpiStrip, type KpiItem } from '@/app/components/shared/v2';
import { useBenchmarks } from '@/app/lib/api/hooks';
import {
  type BenchmarkApiResponse,
  formatBenchmarkDateRange,
  getBenchmarkMode,
} from '@/app/lib/types/benchmark';

type BenchmarkType = 'all' | 'live' | 'backtest';

function formatDate(iso: string): string {
  const d = new Date(iso);
  const day = String(d.getDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
  const yr = d.getFullYear();
  return `${day} ${mo} ${yr}`;
}

function BenchmarksPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const typeParam = searchParams.get('type') as BenchmarkType | null;
  const activeFilter: BenchmarkType =
    typeParam === 'live' || typeParam === 'backtest' ? typeParam : 'all';

  const { data, isLoading, error } = useBenchmarks();
  const benchmarks = data ?? [];

  const counts = useMemo(() => ({
    all: benchmarks.length,
    live: benchmarks.filter((b) => b.isLive).length,
    backtest: benchmarks.filter((b) => !b.isLive).length,
  }), [benchmarks]);

  const totalSimulations = useMemo(
    () => benchmarks.reduce((s, b) => s + b.simulationIds.length, 0),
    [benchmarks]
  );
  const newestCreatedAt = useMemo(() => {
    if (benchmarks.length === 0) return null;
    return benchmarks
      .map((b) => b.createdAt)
      .sort()
      .slice(-1)[0];
  }, [benchmarks]);

  const filtered = useMemo(() => {
    if (activeFilter === 'all') return benchmarks;
    return benchmarks.filter((b) => getBenchmarkMode(b) === activeFilter);
  }, [benchmarks, activeFilter]);

  const handleFilterChange = (type: BenchmarkType) => {
    if (type === 'all') router.push('/benchmarks');
    else router.push(`/benchmarks?type=${type}`);
  };

  const kpis: KpiItem[] = [
    {
      label: 'Benchmarks',
      value: String(benchmarks.length),
      delta: (
        <>
          <Positive>{counts.live} live</Positive>
          <DeltaSep>·</DeltaSep>
          {counts.backtest} backtest
        </>
      ),
    },
    {
      label: 'Total simulations',
      value: String(totalSimulations),
      delta: `across ${benchmarks.length} benchmark${benchmarks.length === 1 ? '' : 's'}`,
    },
    {
      label: 'Last updated',
      value: newestCreatedAt ? formatDate(newestCreatedAt) : '—',
      delta: 'most recent benchmark',
    },
  ];

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>Benchmarks</H1>
            <Desc>
              Side-by-side comparisons of your simulations. Group versions of one strategy to track drift,
              or pit two strategies against a baseline ticker.
            </Desc>
          </HeaderLeft>
          <HeaderRight>
            <NewLink href="/benchmarks/new">
              <span aria-hidden="true">+</span> New benchmark
            </NewLink>
          </HeaderRight>
        </PageHeader>

        {benchmarks.length > 0 && <KpiStrip items={kpis} />}

        <Toolbar>
          <ToolbarLeft>
            <ToolbarLabel>Type</ToolbarLabel>
            <Seg>
              {(['all', 'live', 'backtest'] as BenchmarkType[]).map((t) => (
                <SegBtn
                  key={t}
                  $on={activeFilter === t}
                  type="button"
                  onClick={() => handleFilterChange(t)}
                >
                  {t === 'all' ? 'All' : t.charAt(0).toUpperCase() + t.slice(1)}
                  <SegCount>{counts[t]}</SegCount>
                </SegBtn>
              ))}
            </Seg>
          </ToolbarLeft>
        </Toolbar>

        <Panel title="Benchmarks" count={filtered.length} noBodyPadding>
          {error ? (
            <EmptyState>
              <EmptyIcon>!</EmptyIcon>
              <EmptyTitle>Couldn&apos;t load benchmarks.</EmptyTitle>
              <EmptyBody>{(error as Error).message}</EmptyBody>
            </EmptyState>
          ) : isLoading ? (
            <SkeletonBlock>
              {Array.from({ length: 4 }).map((_, i) => (
                <SkeletonRow key={i}>
                  <SkelBar $w="70px" $h="20px" />
                  <SkelBar $w="55%" $h="13px" />
                  <SkelBar $w="180px" $h="12px" />
                  <SkelBar $w="80px" $h="12px" />
                  <SkelBar $w="60px" $h="20px" />
                </SkeletonRow>
              ))}
            </SkeletonBlock>
          ) : filtered.length === 0 ? (
            <EmptyState>
              <EmptyIcon>=</EmptyIcon>
              <EmptyTitle>
                {activeFilter === 'all' ? 'No benchmarks yet.' : `No ${activeFilter} benchmarks.`}
              </EmptyTitle>
              <EmptyBody>
                Create a benchmark to compare two or more simulations side-by-side. Especially useful for tracking
                version drift on a single strategy.
              </EmptyBody>
              {activeFilter === 'all' && (
                <NewLink href="/benchmarks/new" style={{ marginTop: 8 }}>
                  <span aria-hidden="true">+</span> Create your first benchmark
                </NewLink>
              )}
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span>Type</span>
                <span>Benchmark</span>
                <span>Date range</span>
                <span>Created</span>
                <span>Sims</span>
                <RightAligned>Actions</RightAligned>
              </HeaderRowBar>
              {filtered.map((b) => <BenchRow key={b.id} bench={b} />)}
            </Rows>
          )}
        </Panel>
      </Page>
    </DashboardLayout>
  );
}

interface BenchRowProps {
  bench: BenchmarkApiResponse;
}

function BenchRow({ bench }: BenchRowProps) {
  const mode = getBenchmarkMode(bench);
  return (
    <Row href={`/benchmarks/${bench.id}`}>
      <TypeChip $mode={mode}>{mode}</TypeChip>
      <NameCell>
        <NameTop>{bench.name}</NameTop>
        <NameSub>
          <Mono>#{bench.id.slice(0, 8)}</Mono>
        </NameSub>
      </NameCell>
      <Cell>{formatBenchmarkDateRange(bench)}</Cell>
      <Cell>{formatDate(bench.createdAt)}</Cell>
      <SimCount>
        {bench.simulationIds.length}
        <SimCountSub>sims</SimCountSub>
      </SimCount>
      <RowActs>
        <RowActArrow>→</RowActArrow>
      </RowActs>
    </Row>
  );
}

export default function BenchmarksPage() {
  return (
    <Suspense fallback={
      <DashboardLayout>
        <Page><SkelBar $w="200px" $h="22px" /></Page>
      </DashboardLayout>
    }>
      <BenchmarksPageContent />
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

  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
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

const Positive = styled.span` color: ${p => p.theme.colors.positive}; `;
const DeltaSep = styled.span`
  margin: 0 4px;
  color: ${p => p.theme.colors.border};
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

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ROW_COLS = '100px minmax(0, 1fr) 220px 120px 90px 50px';

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
    gap: 8px;
    padding: 14px 18px;
  }
`;

const TypeChip = styled.span<{ $mode: 'live' | 'backtest' }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 2px 7px;
  border-radius: 2px;
  white-space: nowrap;
  width: max-content;
  ${p => p.$mode === 'live' ? css`
    color: ${p.theme.colors.brand};
    border: 1px solid rgba(0,255,65,0.4);
    background: rgba(0,255,65,0.04);
  ` : css`
    color: ${p.theme.colors.foreground};
    border: 1px solid ${p.theme.colors.border};
  `}
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
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Cell = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: ${p => p.theme.colors.foreground};
`;

const SimCount = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
  display: flex;
  flex-direction: column;
  gap: 1px;
`;

const SimCountSub = styled.span`
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
    display: none;
  }
`;

const RowActArrow = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 15px;
  color: ${p => p.theme.colors.fgDimmed};
  transition: color 120ms ease, transform 120ms ease;

  ${Row}:hover & {
    color: ${p => p.theme.colors.brand};
    transform: translateX(2px);
  }
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
