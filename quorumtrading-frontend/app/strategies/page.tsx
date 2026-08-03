'use client';

import { useMemo, useState, Suspense } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import styled, { keyframes } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { Tag } from '@/app/components/shared/v2';
import { useAuth } from '../hooks/useAuth';
import {
  useStrategies,
  useSimulations,
  useDeleteStrategy,
  usePruneUnusedStrategies,
} from '@/app/lib/api/hooks';
import { useContextualHelp } from '@/app/lib/hooks';
import { DeleteStrategyModal } from '../components/strategies/DeleteStrategyModal';
import {
  parseStrategyFrontmatter,
  allTickers,
} from '@/app/strategies/public/components/v2/parseStrategyFrontmatter';
import type { StrategyResponse, SimulationResponse } from '@/app/lib/types/api';

// ─── Types ──────────────────────────────────────────────────────────────────

type StrategyStatus = 'live' | 'backtest' | 'unused';
type StatusFilter = 'all' | StrategyStatus;

interface EnrichedStrategy {
  raw: StrategyResponse;
  status: StrategyStatus;
  sims: { live: number; backtest: number };
  tickers: string[];
  age: string;
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function isLiveSimulation(sim: SimulationResponse): boolean {
  return sim.status === 'LIVE';
}

function deriveStatus(sims: { live: number; backtest: number }): StrategyStatus {
  if (sims.live > 0) return 'live';
  if (sims.backtest > 0) return 'backtest';
  return 'unused';
}

function formatAge(iso: string | undefined): string {
  if (!iso) return '—';
  const ms = Date.now() - new Date(iso).getTime();
  if (!Number.isFinite(ms) || ms < 0) return 'today';
  const days = Math.floor(ms / 86_400_000);
  if (days <= 0) return 'today';
  if (days === 1) return '1 day';
  if (days < 14) return `${days} days`;
  const weeks = Math.floor(days / 7);
  if (weeks < 52) return `${weeks} week${weeks === 1 ? '' : 's'}`;
  const years = Math.floor(days / 365);
  return `${years} yr${years === 1 ? '' : 's'}`;
}

// ─── Page ───────────────────────────────────────────────────────────────────

function StrategiesPageContent() {
  const router = useRouter();
  const { isLoading: authLoading } = useAuth();
  const { data: strategies, isLoading: strategiesLoading, error } = useStrategies();
  const { data: simulations, isLoading: simsLoading } = useSimulations();

  const isLoading = authLoading || strategiesLoading || simsLoading;

  const [filter, setFilter] = useState<StatusFilter>('all');
  const [search, setSearch] = useState('');
  const [deleteModal, setDeleteModal] = useState<{
    id: string; name: string; simCount: number; hasLive: boolean;
  } | null>(null);

  const deleteMutation = useDeleteStrategy();
  const pruneMutation = usePruneUnusedStrategies();

  // Per-strategy sim counts derived from the simulations list.
  const enriched = useMemo<EnrichedStrategy[]>(() => {
    if (!strategies) return [];
    const counts: Record<string, { live: number; backtest: number }> = {};
    (simulations ?? []).forEach((sim) => {
      const c = (counts[sim.strategyId] ||= { live: 0, backtest: 0 });
      if (isLiveSimulation(sim)) c.live += 1;
      else c.backtest += 1;
    });

    return strategies.map((s) => {
      const sims = counts[s.id] ?? { live: 0, backtest: 0 };
      const fm = parseStrategyFrontmatter(s.markdownContent);
      return {
        raw: s,
        status: deriveStatus(sims),
        sims,
        tickers: allTickers(fm),
        age: formatAge(s.createdAt),
      };
    });
  }, [strategies, simulations]);

  const groupCounts = useMemo(() => {
    const live = enriched.filter((s) => s.status === 'live');
    const backtest = enriched.filter((s) => s.status === 'backtest');
    const unused = enriched.filter((s) => s.status === 'unused');
    return {
      total: enriched.length,
      live,
      backtest,
      unused,
      liveSims: live.reduce((n, s) => n + s.sims.live, 0),
      backtestSims: backtest.reduce((n, s) => n + s.sims.backtest, 0),
      published: enriched.filter((s) => s.raw.publicVisible).length,
    };
  }, [enriched]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return enriched.filter((e) => {
      if (filter !== 'all' && e.status !== filter) return false;
      if (!q) return true;
      const hay = `${e.raw.name} ${e.raw.description ?? ''} ${e.tickers.join(' ')}`.toLowerCase();
      return hay.includes(q);
    });
  }, [enriched, filter, search]);

  // Contextual help — expanded by default while the user has no strategies.
  const help = useContextualHelp('strategies', enriched.length > 0);

  const handleOpen = (id: string) => router.push(`/strategies/${id}`);
  const handleEdit = (e: React.MouseEvent, id: string) => {
    e.preventDefault(); e.stopPropagation();
    router.push(`/strategies/${id}/edit`);
  };
  const handleSimulate = (e: React.MouseEvent, id: string) => {
    e.preventDefault(); e.stopPropagation();
    router.push(`/benchmarks/new?strategyId=${id}`);
  };
  const handleDelete = (e: React.MouseEvent, strat: EnrichedStrategy) => {
    e.preventDefault(); e.stopPropagation();
    const total = strat.sims.live + strat.sims.backtest;
    if (total === 0) {
      deleteMutation.mutate(strat.raw.id);
    } else {
      setDeleteModal({
        id: strat.raw.id,
        name: strat.raw.name,
        simCount: total,
        hasLive: strat.sims.live > 0,
      });
    }
  };

  const showOnboarding = help.isExpanded;

  if (error) {
    return (
      <Page>
        <ErrorBox>
          Failed to load strategies — {error instanceof Error ? error.message : 'unknown error'}.
        </ErrorBox>
      </Page>
    );
  }

  if (isLoading) {
    return (
      <Page>
        <LoadingSkeleton />
      </Page>
    );
  }

  const isEmpty = enriched.length === 0;

  return (
    <Page>
      <PageHeader>
        <HeaderLeft>
          <H1>My strategies</H1>
          <MetaRow>
            <strong>{groupCounts.total}</strong>{' '}
            strateg{groupCounts.total === 1 ? 'y' : 'ies'}
            <Sep>·</Sep>
            <LiveMeta>
              <PulseDot /> {groupCounts.liveSims} live sim{groupCounts.liveSims === 1 ? '' : 's'}
            </LiveMeta>
            <Sep>·</Sep>
            {groupCounts.backtestSims} backtest{groupCounts.backtestSims === 1 ? '' : 's'}
            {groupCounts.unused.length > 0 && (
              <>
                <Sep>·</Sep>
                <WarnMeta>{groupCounts.unused.length} unused</WarnMeta>
              </>
            )}
            {groupCounts.published > 0 && (
              <>
                <Sep>·</Sep>
                {groupCounts.published} published
              </>
            )}
          </MetaRow>
        </HeaderLeft>
        <HeaderRight>
          <GuideToggle type="button" onClick={help.toggle}>
            <span className="g">?</span>
            {showOnboarding ? 'Hide guide' : 'What is a strategy?'}
          </GuideToggle>
          {groupCounts.unused.length > 0 && (
            <GhostButton
              type="button"
              onClick={() => pruneMutation.mutate()}
              disabled={pruneMutation.isPending}
            >
              <span className="g">↺</span>
              {pruneMutation.isPending ? 'Cleaning…' : 'Clean up unused'}
              <CountBadge>{groupCounts.unused.length}</CountBadge>
            </GhostButton>
          )}
          <SolidLink href="/strategies/new">
            <span>+</span>New strategy
          </SolidLink>
        </HeaderRight>
      </PageHeader>

      {showOnboarding && <OnboardingExplain onDismiss={help.toggle} />}

      {isEmpty ? (
        <EmptyState />
      ) : (
        <Ledger
          enriched={filtered}
          totalCount={enriched.length}
          groupCounts={groupCounts}
          filter={filter}
          search={search}
          onFilterChange={setFilter}
          onSearchChange={setSearch}
          onOpen={handleOpen}
          onEdit={handleEdit}
          onSimulate={handleSimulate}
          onDelete={handleDelete}
          onCleanUpUnused={() => pruneMutation.mutate()}
          cleanUpPending={pruneMutation.isPending}
        />
      )}

      {deleteModal && (
        <DeleteStrategyModal
          isOpen
          onClose={() => setDeleteModal(null)}
          strategyId={deleteModal.id}
          strategyName={deleteModal.name}
          simulationCount={deleteModal.simCount}
          hasLiveSims={deleteModal.hasLive}
        />
      )}
    </Page>
  );
}

export default function StrategiesListPage() {
  return (
    <DashboardLayout>
      <Suspense fallback={null}>
        <StrategiesPageContent />
      </Suspense>
    </DashboardLayout>
  );
}

// ─── Ledger (toolbar + table + group rows + footer) ─────────────────────────

interface LedgerProps {
  enriched: EnrichedStrategy[];
  totalCount: number;
  groupCounts: {
    total: number;
    live: EnrichedStrategy[];
    backtest: EnrichedStrategy[];
    unused: EnrichedStrategy[];
    liveSims: number;
    backtestSims: number;
    published: number;
  };
  filter: StatusFilter;
  search: string;
  onFilterChange: (f: StatusFilter) => void;
  onSearchChange: (s: string) => void;
  onOpen: (id: string) => void;
  onEdit: (e: React.MouseEvent, id: string) => void;
  onSimulate: (e: React.MouseEvent, id: string) => void;
  onDelete: (e: React.MouseEvent, s: EnrichedStrategy) => void;
  onCleanUpUnused: () => void;
  cleanUpPending: boolean;
}

function Ledger({
  enriched, totalCount, groupCounts, filter, search,
  onFilterChange, onSearchChange,
  onOpen, onEdit, onSimulate, onDelete,
  onCleanUpUnused, cleanUpPending,
}: LedgerProps) {
  const live = enriched.filter((e) => e.status === 'live');
  const backtest = enriched.filter((e) => e.status === 'backtest');
  const unused = enriched.filter((e) => e.status === 'unused');
  const firstGroup = live.length > 0 ? 'live' : backtest.length > 0 ? 'backtest' : 'unused';

  return (
    <LedgerCard>
      <Toolbar>
        <Filters>
          <FilterLabel>Show</FilterLabel>
          <Seg>
            <SegBtn type="button" $on={filter === 'all'} onClick={() => onFilterChange('all')}>
              All <SegCount>{groupCounts.total}</SegCount>
            </SegBtn>
            <SegBtn type="button" $on={filter === 'live'} onClick={() => onFilterChange('live')}>
              Live <SegCount>{groupCounts.live.length}</SegCount>
            </SegBtn>
            <SegBtn type="button" $on={filter === 'backtest'} onClick={() => onFilterChange('backtest')}>
              Backtest only <SegCount>{groupCounts.backtest.length}</SegCount>
            </SegBtn>
            <SegBtn type="button" $on={filter === 'unused'} onClick={() => onFilterChange('unused')}>
              Unused <SegCount>{groupCounts.unused.length}</SegCount>
            </SegBtn>
          </Seg>
        </Filters>
        <SearchInput
          type="text"
          placeholder="Filter by name, ticker, or version"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </Toolbar>

      <TableWrap>
        <Table>
          <thead>
            <tr>
              <Th className="status" aria-label="Status" />
              <Th className="name sorted">Strategy<SortGlyph>↓</SortGlyph></Th>
              <Th className="ver">Version</Th>
              <Th className="tickers">Tickers</Th>
              <Th className="spark">Spark<DeltaMark>Δ</DeltaMark></Th>
              <Th className="sims r">Sims (live · bt)</Th>
              <Th className="public">Visibility</Th>
              <Th className="age r">Age</Th>
              <Th className="last r">Last decision<DeltaMark>Δ</DeltaMark></Th>
              <Th className="acts" />
            </tr>
          </thead>
          <tbody>
            {enriched.length === 0 ? (
              <tr>
                <EmptyTd colSpan={10}>No strategies match this filter.</EmptyTd>
              </tr>
            ) : (
              <>
                {live.length > 0 && (
                  <>
                    <GroupHeader
                      status="live"
                      label={`Live · ${live.length}`}
                      meta={`${live.reduce((n, s) => n + s.sims.live, 0)} live sim${live.reduce((n, s) => n + s.sims.live, 0) === 1 ? '' : 's'} across these`}
                      first={firstGroup === 'live'}
                    />
                    {live.map((s) => (
                      <LedgerRow
                        key={s.raw.id} s={s}
                        onOpen={onOpen} onEdit={onEdit} onSimulate={onSimulate} onDelete={onDelete}
                      />
                    ))}
                  </>
                )}
                {backtest.length > 0 && (
                  <>
                    <GroupHeader
                      status="backtest"
                      label={`Backtest only · ${backtest.length}`}
                      meta={`${backtest.reduce((n, s) => n + s.sims.backtest, 0)} backtest run${backtest.reduce((n, s) => n + s.sims.backtest, 0) === 1 ? '' : 's'} · not yet promoted`}
                      first={firstGroup === 'backtest'}
                    />
                    {backtest.map((s) => (
                      <LedgerRow
                        key={s.raw.id} s={s}
                        onOpen={onOpen} onEdit={onEdit} onSimulate={onSimulate} onDelete={onDelete}
                      />
                    ))}
                  </>
                )}
                {unused.length > 0 && (
                  <>
                    <GroupHeader
                      status="unused"
                      label={`Unused · ${unused.length}`}
                      meta="authored but never run · safe to delete"
                      first={firstGroup === 'unused'}
                      action={
                        <CleanupInline
                          type="button"
                          onClick={onCleanUpUnused}
                          disabled={cleanUpPending}
                        >
                          ↺ {cleanUpPending ? 'cleaning…' : `clean up ${unused.length}`}
                        </CleanupInline>
                      }
                    />
                    {unused.map((s) => (
                      <LedgerRow
                        key={s.raw.id} s={s}
                        onOpen={onOpen} onEdit={onEdit} onSimulate={onSimulate} onDelete={onDelete}
                      />
                    ))}
                  </>
                )}
              </>
            )}
          </tbody>
        </Table>
      </TableWrap>

      <LedgerFoot>
        <span>
          Showing {enriched.length} of {totalCount} · grouped by status
          <FootSep>·</FootSep>
          within each group, sorted by name ↓
        </span>
      </LedgerFoot>
    </LedgerCard>
  );
}

// ─── Row ────────────────────────────────────────────────────────────────────

interface LedgerRowProps {
  s: EnrichedStrategy;
  onOpen: (id: string) => void;
  onEdit: (e: React.MouseEvent, id: string) => void;
  onSimulate: (e: React.MouseEvent, id: string) => void;
  onDelete: (e: React.MouseEvent, s: EnrichedStrategy) => void;
}

function LedgerRow({ s, onOpen, onEdit, onSimulate, onDelete }: LedgerRowProps) {
  return (
    <Tr
      $live={s.status === 'live'}
      $unused={s.status === 'unused'}
      onClick={() => onOpen(s.raw.id)}
      role="link"
    >
      <Td className="status">
        <StatusDot $status={s.status} />
      </Td>
      <Td className="name">
        <StratName>
          <span className="nm" title={s.raw.name}>{s.raw.name}</span>
          {s.raw.description && (
            <span className="sub" title={s.raw.description}>{s.raw.description}</span>
          )}
        </StratName>
      </Td>
      <Td className="ver">
        <Ver>v{s.raw.version}</Ver>
      </Td>
      <Td className="tickers">
        <TickerCluster tickers={s.tickers} />
      </Td>
      <Td className="spark">
        {/* Δ derived — slope/spark would need per-strategy aggregates from the list endpoint.
            Render a dashed placeholder so the column is reserved and the Δ marker in the
            header carries the meaning. */}
        <SparkPlaceholder aria-label="spark pending list-endpoint aggregation" />
      </Td>
      <Td className="sims r">
        <SimCounts>
          <SimCell $kind="live" $on={s.sims.live > 0}>
            <span className="n">{s.sims.live}</span>
            <span className="l">live</span>
          </SimCell>
          <SimCell $kind="bt" $on={s.sims.backtest > 0}>
            <span className="n">{s.sims.backtest}</span>
            <span className="l">bt</span>
          </SimCell>
        </SimCounts>
      </Td>
      <Td className="public">
        {s.raw.publicVisible
          ? <Tag hue="cyan">Published</Tag>
          : <PrivateLabel>private</PrivateLabel>}
      </Td>
      <Td className="age r">
        <Age>{s.age}</Age>
      </Td>
      <Td className="last r">
        <LastPlaceholder>—</LastPlaceholder>
      </Td>
      <Td className="acts" onClick={(e) => e.stopPropagation()}>
        <OpenHint className="open-hint">open →</OpenHint>
        <RowActions className="row-acts">
          <ActBtn type="button" onClick={(e) => onEdit(e, s.raw.id)} title="Open strategy editor">
            Edit
          </ActBtn>
          <ActBtn $primary type="button" onClick={(e) => onSimulate(e, s.raw.id)} title="Start a new simulation">
            Simulate
          </ActBtn>
          <ActIcon type="button" onClick={(e) => onDelete(e, s)} title="Delete" aria-label="Delete">
            ×
          </ActIcon>
        </RowActions>
      </Td>
    </Tr>
  );
}

// ─── Group header ───────────────────────────────────────────────────────────

interface GroupHeaderProps {
  status: StrategyStatus;
  label: string;
  meta: string;
  first: boolean;
  action?: React.ReactNode;
}

function GroupHeader({ status, label, meta, first, action }: GroupHeaderProps) {
  return (
    <GroupRow $first={first} $status={status}>
      <td colSpan={10}>
        <GroupInner>
          <span className="left">
            <StatusDot $status={status} $small />
            <span className="lbl">{label}</span>
            <span className="meta">{meta}</span>
          </span>
          {action}
        </GroupInner>
      </td>
    </GroupRow>
  );
}

// ─── Ticker cluster ─────────────────────────────────────────────────────────

function TickerCluster({ tickers, max = 3 }: { tickers: string[]; max?: number }) {
  if (tickers.length === 0) {
    return <NoTickers>—</NoTickers>;
  }
  const shown = tickers.slice(0, max);
  const rest = Math.max(0, tickers.length - max);
  return (
    <TickerRow>
      {shown.map((t) => <Ticker key={t}>{t}</Ticker>)}
      {rest > 0 && <Ticker $rest>+{rest}</Ticker>}
    </TickerRow>
  );
}

// ─── Onboarding explain band ────────────────────────────────────────────────

function OnboardingExplain({ onDismiss }: { onDismiss: () => void }) {
  return (
    <ExplainCard>
      <ExplainTop>
        <div>
          <Eyebrow><EyebrowDot />First-run guide · dismiss after you&apos;ve shipped one</Eyebrow>
          <ExplainTitle>A strategy is a markdown brief with a short YAML config.</ExplainTitle>
          <ExplainBody>
            Author the rules in plain English. Backtest against history. Promote to Mock or
            paper. The council reads <code>strategy.md</code> on every tick — your description
            is part of the prompt, so write it for a curious analyst.
          </ExplainBody>
        </div>
        <DismissBtn type="button" onClick={onDismiss} aria-label="Dismiss">×</DismissBtn>
      </ExplainTop>
      <ExplainSteps>
        <Step>
          <StepNum>1</StepNum>
          <h4>Write the brief</h4>
          <p>Markdown body for the council to read; YAML frontmatter (tickers, cadence, consensus method) for the engine.</p>
        </Step>
        <Step>
          <StepNum>2</StepNum>
          <h4>Backtest</h4>
          <p>1–10 years of historical bars. The council votes on each tick as if it were live.</p>
        </Step>
        <Step>
          <StepNum>3</StepNum>
          <h4>Promote to a sim</h4>
          <p>Mock → Alpaca paper → Alpaca live. The strategy doesn&apos;t change; only the broker does.</p>
        </Step>
        <Step>
          <StepNum>4</StepNum>
          <h4>Publish (optional)</h4>
          <p>Flip <code>publicVisible</code> and the strategy lands on /strategies/public — markdown only, no live capital.</p>
        </Step>
      </ExplainSteps>
    </ExplainCard>
  );
}

// ─── Empty state ────────────────────────────────────────────────────────────

function EmptyState() {
  return (
    <EmptyCard>
      <EmptyGlyph>∅</EmptyGlyph>
      <EmptyTitle>You haven&apos;t authored a strategy yet.</EmptyTitle>
      <EmptyBody>
        Author a markdown brief, backtest it against 1–10 years of bars, then promote to Mock or
        Alpaca paper. The public library is one click away if you&apos;d rather fork than start blank.
      </EmptyBody>
      <EmptyActions>
        <GhostLink href="/strategies/public">Fork from public library →</GhostLink>
        <SolidLink href="/strategies/new"><span>+</span>New strategy</SolidLink>
      </EmptyActions>
      <EmptyTip>
        tip · the council can&apos;t read a strategy whose description is blank — write for a curious analyst
      </EmptyTip>
    </EmptyCard>
  );
}

// ─── Loading skeleton ───────────────────────────────────────────────────────

function LoadingSkeleton() {
  const widths = [16, 240, 60, 80, 88, 110, 70, 60, 80, 100];
  return (
    <>
      <PageHeader>
        <HeaderLeft>
          <SkelLine $w={120} $h={28} style={{ borderRadius: 2 }} />
          <SkelLine $w={260} $h={11} style={{ borderRadius: 2, marginTop: 8 }} />
        </HeaderLeft>
        <HeaderRight>
          <SkelLine $w={120} $h={28} style={{ borderRadius: 2 }} />
          <SkelLine $w={100} $h={28} style={{ borderRadius: 2 }} />
          <SkelLine $w={120} $h={28} style={{ borderRadius: 2 }} />
        </HeaderRight>
      </PageHeader>
      <LedgerCard>
        <Toolbar>
          <SkelLine $w={300} $h={22} style={{ borderRadius: 2 }} />
          <SkelLine $w={240} $h={22} style={{ borderRadius: 2 }} />
        </Toolbar>
        <TableWrap>
          <Table>
            <thead>
              <tr>
                <Th className="status" />
                <Th className="name">Strategy</Th>
                <Th className="ver">Version</Th>
                <Th className="tickers">Tickers</Th>
                <Th className="spark">Spark</Th>
                <Th className="sims r">Sims</Th>
                <Th className="public">Visibility</Th>
                <Th className="age r">Age</Th>
                <Th className="last r">Last decision</Th>
                <Th className="acts" />
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: 10 }).map((_, r) => (
                <tr key={r}>
                  {widths.map((w, i) => (
                    <Td key={i} className={i === 0 ? 'status' : i === 9 ? 'acts' : ''}>
                      <SkelLine $w={w} $h={i === 1 ? 13 : 9} style={{ display: 'block' }} />
                      {i === 1 && (
                        <SkelLine $w={180} $h={8} style={{ display: 'block', marginTop: 4 }} />
                      )}
                    </Td>
                  ))}
                </tr>
              ))}
            </tbody>
          </Table>
        </TableWrap>
      </LedgerCard>
    </>
  );
}

// ─── Styled components ─────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 18px;
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
  gap: 6px;
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

const MetaRow = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};

  & > strong {
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const Sep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const pulse = keyframes`
  0%, 100% { box-shadow: 0 0 6px rgba(0, 255, 65, 0.5); }
  50% { box-shadow: 0 0 12px rgba(0, 255, 65, 0.8); }
`;

const PulseDot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${p => p.theme.colors.brand};
  display: inline-block;
  animation: ${pulse} 1.8s ease-in-out infinite;
`;

const LiveMeta = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: ${p => p.theme.colors.brand};
`;

const WarnMeta = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
`;

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
`;

const GuideToggle = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgMuted};
  background: transparent;
  border: 1px dashed ${p => p.theme.colors.border};
  border-radius: 2px;
  padding: 6px 11px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: color 120ms ease, border-color 120ms ease;

  &:hover {
    color: ${p => p.theme.colors.foreground};
    border-color: ${p => p.theme.colors.fgMuted};
  }
  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: 2px;
  }

  & > .g {
    width: 14px; height: 14px; border-radius: 50%;
    border: 1px solid currentColor;
    display: inline-flex; align-items: center; justify-content: center;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9px;
  }
`;

const GhostButton = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  padding: 7px 13px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: background 120ms ease, border-color 120ms ease;

  &:hover { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }

  & > .g {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const CountBadge = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const SolidLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  text-decoration: none;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.foreground};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  text-decoration: none;

  &:hover { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

// ─── Onboarding explain ─────────────────────────────────────────────────────

const ExplainCard = styled.div`
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  background:
    radial-gradient(ellipse 380px 100px at 0% 0%, rgba(0, 255, 65, 0.04), transparent 70%),
    ${p => p.theme.colors.surface};
  padding: 22px 24px 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow: hidden;
`;

const ExplainTop = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 16px;
`;

const Eyebrow = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
`;

const EyebrowDot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: ${p => p.theme.colors.brand};
  box-shadow: 0 0 6px rgba(0, 255, 65, 0.45);
`;

const ExplainTitle = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 19px;
  letter-spacing: -0.008em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0 0 6px;
`;

const ExplainBody = styled.p`
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 76ch;

  & code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.foreground};
    background: transparent;
    border: 0;
    padding: 0;
  }
`;

const DismissBtn = styled.button`
  background: transparent;
  border: none;
  color: ${p => p.theme.colors.fgDimmed};
  cursor: pointer;
  font-size: 19px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 2px;
  flex-shrink: 0;

  &:hover { color: ${p => p.theme.colors.fgStrong}; background: rgba(255, 255, 255, 0.04); }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const ExplainSteps = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  margin: 0 -24px;
  background: ${p => p.theme.colors.background};
  overflow: hidden;

  @media (max-width: 768px) {
    grid-template-columns: 1fr 1fr;
  }
`;

const Step = styled.div`
  padding: 16px 18px 18px;
  border-right: 1px solid ${p => p.theme.colors.borderSubtle};
  display: flex;
  flex-direction: column;
  gap: 4px;

  &:last-child { border-right: none; }

  & h4 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    margin: 0;
  }
  & p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12.5px;
    color: ${p => p.theme.colors.fgMuted};
    line-height: 1.5;
    margin: 0;
  }
  & code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.foreground};
    background: transparent;
    padding: 0;
  }
`;

const StepNum = styled.div`
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid ${p => p.theme.colors.border};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  margin-bottom: 6px;
`;

// ─── Ledger card chrome ─────────────────────────────────────────────────────

const LedgerCard = styled.div`
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  background: ${p => p.theme.colors.surface};
  overflow: hidden;
`;

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  gap: 12px;
  flex-wrap: wrap;
`;

const Filters = styled.div`
  display: inline-flex;
  gap: 8px;
  align-items: center;
`;

const FilterLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Seg = styled.span`
  display: inline-flex;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  overflow: hidden;
`;

const SegBtn = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  padding: 4px 10px;
  border: none;
  border-radius: 0;
  background: ${p => p.$on ? 'rgba(255, 255, 255, 0.04)' : 'transparent'};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  cursor: pointer;
  transition: color 120ms ease, background 120ms ease;

  & + & { border-left: 1px solid ${p => p.theme.colors.border}; }
  &:hover { color: ${p => p.theme.colors.foreground}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: -2px; }
`;

const SegCount = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
  margin-left: 4px;
`;

const SearchInput = styled.input`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 2px;
  padding: 4px 9px;
  color: ${p => p.theme.colors.foreground};
  width: 240px;
  max-width: 100%;

  &::placeholder { color: ${p => p.theme.colors.fgDimmed}; }
  &:focus { outline: none; border-color: ${p => p.theme.colors.focusRing}; }
`;

const TableWrap = styled.div`
  overflow-x: auto;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  table-layout: fixed;
  /* Every other column has a fixed width and together they came to ~982px, more than this
     min-width allowed. Under table-layout: fixed the unsized column absorbs whatever is left,
     so the strategy name — the one column a user actually reads — rendered at zero width and
     the list looked empty. Sized so the whole row fits; TableWrap scrolls when it does not. */
  min-width: 1240px;
`;

const Th = styled.th`
  position: sticky;
  top: 0;
  background: ${p => p.theme.colors.surface};
  z-index: 1;
  text-align: left;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 9.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 9px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.border};
  white-space: nowrap;
  user-select: none;

  &.sorted { color: ${p => p.theme.colors.fgStrong}; }
  &.r { text-align: right; }
  &.status { width: 22px; padding-left: 16px; padding-right: 0; }
  &.name { width: 258px; }
  &.ver { width: 70px; }
  &.tickers { width: 140px; }
  &.spark { width: 110px; }
  &.sims { width: 130px; text-align: right; }
  &.public { width: 100px; }
  &.age { width: 80px; text-align: right; }
  &.last { width: 130px; text-align: right; }
  &.acts { width: 200px; text-align: right; padding-right: 16px; }
`;

const SortGlyph = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9px;
  color: ${p => p.theme.colors.brand};
  margin-left: 4px;
`;

const DeltaMark = styled.sup`
  color: ${p => p.theme.colors.terminalAmber};
  font-size: 8px;
  margin-left: 2px;
`;

const Tr = styled.tr<{ $live: boolean; $unused: boolean }>`
  cursor: pointer;
  transition: background 120ms ease;
  background: ${p => p.$live ? 'rgba(0, 255, 65, 0.012)' : 'transparent'};

  & > td { color: ${p => p.$unused ? p.theme.colors.fgMuted : p.theme.colors.foreground}; }

  &:hover > td {
    background: ${p => p.$live ? 'rgba(0, 255, 65, 0.04)' : 'rgba(255, 255, 255, 0.025)'};
  }
  &:hover .row-acts { opacity: 1; }
  &:hover .open-hint { opacity: 0; }
`;

const Td = styled.td`
  padding: 9px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 13px;
  vertical-align: middle;

  &.status { width: 22px; padding-left: 16px; padding-right: 0; }
  &.name { width: 258px; }
  &.ver { width: 70px; }
  &.tickers { width: 140px; }
  &.spark { width: 110px; }
  &.sims { width: 130px; text-align: right; }
  &.public { width: 100px; }
  &.age { width: 80px; text-align: right; }
  &.last { width: 130px; text-align: right; }
  &.acts { width: 200px; text-align: right; padding-right: 16px; }
`;

const EmptyTd = styled.td`
  padding: 28px 16px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
`;

const StratName = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  & > .nm {
    color: ${p => p.theme.colors.fgStrong};
    font-size: 13.5px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    letter-spacing: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  & > .sub {
    color: ${p => p.theme.colors.fgMuted};
    font-size: 12px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 56ch;
  }
`;

const Ver = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.foreground};
`;

const TickerRow = styled.span`
  display: inline-flex;
  gap: 3px;
  flex-wrap: wrap;
`;

const Ticker = styled.span<{ $rest?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: ${p => p.$rest ? p.theme.colors.fgMuted : p.theme.colors.foreground};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 2px;
  padding: 1px 5px;
`;

const NoTickers = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgSubtle};
`;

const SparkPlaceholder = styled.span`
  display: inline-block;
  width: 96px;
  height: 2px;
  background: repeating-linear-gradient(
    to right,
    ${p => p.theme.colors.fgSubtle} 0 4px,
    transparent 4px 8px
  );
  vertical-align: middle;
`;

const SimCounts = styled.span`
  display: inline-flex;
  gap: 4px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const SimCell = styled.span<{ $kind: 'live' | 'bt'; $on: boolean }>`
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  padding: 2px 7px;
  border-radius: 2px;
  border: 1px solid ${p =>
    p.$kind === 'live' && p.$on ? 'rgba(0, 255, 65, 0.4)' : p.theme.colors.borderSubtle};
  background: ${p =>
    p.$kind === 'live' && p.$on ? 'rgba(0, 255, 65, 0.06)' : p.theme.colors.background};
  font-size: 12px;

  & > .n {
    color: ${p =>
      !p.$on ? p.theme.colors.fgDimmed :
      p.$kind === 'live' ? p.theme.colors.brand :
      p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
  & > .l {
    font-size: 9px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p =>
      p.$kind === 'live' && p.$on ? p.theme.colors.brand : p.theme.colors.fgDimmed};
  }
`;

const PrivateLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgSubtle};
`;

const Age = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
`;

const LastPlaceholder = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const OpenHint = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgSubtle};
  transition: opacity 120ms ease;
`;

const RowActions = styled.span`
  display: inline-flex;
  gap: 4px;
  align-items: center;
  opacity: 0;
  transition: opacity 120ms ease;

  @media (max-width: 768px) {
    opacity: 1;
  }
`;

const ActBtn = styled.button<{ $primary?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 4px 9px;
  border-radius: 2px;
  background: transparent;
  border: 1px solid ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.border};
  color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.foreground};
  cursor: pointer;
  white-space: nowrap;
  transition: background 120ms ease, border-color 120ms ease;

  &:hover {
    background: ${p => p.$primary ? 'rgba(0, 255, 65, 0.08)' : 'rgba(255, 255, 255, 0.04)'};
    border-color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.fgMuted};
    color: ${p => p.$primary ? p.theme.colors.brand : p.theme.colors.fgStrong};
  }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const ActIcon = styled.button`
  width: 24px;
  height: 24px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 15px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color 120ms ease, border-color 120ms ease;

  &:hover { color: ${p => p.theme.colors.fgStrong}; border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GroupRow = styled.tr<{ $first: boolean; $status: StrategyStatus }>`
  & > td {
    padding: ${p => p.$first ? '8px 16px 7px' : '14px 16px 7px'};
    border-top: ${p => p.$first ? '0' : `1px solid ${p.theme.colors.borderSubtle}`};
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    background: ${p => p.theme.colors.surface};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const GroupInner = styled.span`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  & > .left {
    display: inline-flex;
    align-items: center;
    gap: 9px;
  }
  & > .left .lbl {
    color: ${p => p.theme.colors.fgMuted};
  }
  & > .left .meta {
    color: ${p => p.theme.colors.fgSubtle};
    text-transform: none;
    letter-spacing: 0;
    font-size: 11.5px;
  }
`;

const CleanupInline = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 2px 6px;
  letter-spacing: 0;
  text-transform: none;

  &:hover:not(:disabled) { color: ${p => p.theme.colors.foreground}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

// Status dot — distinct from sim-status pill. Brand-green for live, muted for
// backtest, hollow outline for unused.
const StatusDot = styled.span<{ $status: StrategyStatus; $small?: boolean }>`
  width: ${p => p.$small ? '7px' : '8px'};
  height: ${p => p.$small ? '7px' : '8px'};
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
  background: ${p =>
    p.$status === 'live' ? p.theme.colors.brand :
    p.$status === 'backtest' ? p.theme.colors.fgMuted :
    'transparent'};
  box-shadow: ${p =>
    p.$status === 'live' ? '0 0 8px rgba(0, 255, 65, 0.4)' :
    p.$status === 'unused' ? `inset 0 0 0 1px ${p.theme.colors.fgSubtle}` :
    'none'};
  position: relative;
`;

const LedgerFoot = styled.div`
  padding: 9px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.surface};
  flex-wrap: wrap;
`;

const FootSep = styled.span`
  color: ${p => p.theme.colors.fgSubtle};
  margin: 0 8px;
`;

// ─── Empty state ────────────────────────────────────────────────────────────

const EmptyCard = styled.div`
  border: 1px dashed ${p => p.theme.colors.border};
  border-radius: 4px;
  background:
    radial-gradient(ellipse 600px 180px at 50% 0%, rgba(0, 255, 65, 0.03), transparent 70%),
    ${p => p.theme.colors.surface};
  padding: 56px 32px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
`;

const EmptyGlyph = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 8px;
  border: 1px dashed ${p => p.theme.colors.border};
  display: grid;
  place-items: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 24px;
  color: ${p => p.theme.colors.fgMuted};
  background: ${p => p.theme.colors.background};
`;

const EmptyTitle = styled.h2`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 24px;
  color: ${p => p.theme.colors.fgStrong};
  letter-spacing: -0.012em;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const EmptyBody = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.55;
  max-width: 60ch;
  margin: 0;
`;

const EmptyActions = styled.div`
  display: flex;
  gap: 10px;
  margin-top: 4px;
  flex-wrap: wrap;
  justify-content: center;
`;

const EmptyTip = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  margin-top: 4px;
`;

// ─── Error ──────────────────────────────────────────────────────────────────

const ErrorBox = styled.div`
  border: 1px solid rgba(255, 51, 102, 0.4);
  background: rgba(255, 51, 102, 0.06);
  color: ${p => p.theme.colors.negative};
  padding: 14px 16px;
  border-radius: 4px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13.5px;
`;

// ─── Skeleton ───────────────────────────────────────────────────────────────

const shimmer = keyframes`
  0% { background-position: -200px 0; }
  100% { background-position: 200px 0; }
`;

const SkelLine = styled.span<{ $w: number; $h: number }>`
  display: inline-block;
  width: ${p => p.$w}px;
  height: ${p => p.$h}px;
  background: linear-gradient(
    90deg,
    ${p => p.theme.colors.borderSubtle} 0%,
    rgba(255, 255, 255, 0.04) 50%,
    ${p => p.theme.colors.borderSubtle} 100%
  );
  background-size: 200px 100%;
  border-radius: 1px;
  animation: ${shimmer} 1.6s linear infinite;
`;

