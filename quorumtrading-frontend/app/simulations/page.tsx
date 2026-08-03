'use client';

import { Fragment, Suspense, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styled, { css, keyframes } from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { HedgedValue } from '@/app/components/shared/v2';
import { useSimulations, useStrategies } from '@/app/lib/api/hooks';
import { useContextualHelp } from '@/app/lib/hooks';
import {
  hueColor,
  labelColor,
  labelCompact,
  pillBase,
  tagBase,
  tagIdentifier,
  type LabelKind,
} from '@/app/styles/labels';
import {
  parseStrategyFrontmatter,
  allTickers,
} from '@/app/strategies/public/components/v2/parseStrategyFrontmatter';
import type {
  SimulationResponse,
  StrategyResponse,
  StrategyLineageOrigin,
} from '@/app/lib/types/api';

// ─── Domain helpers ─────────────────────────────────────────────────────────

type SimState = 'live' | 'simulating' | 'waiting' | 'pending' | 'done' | 'failed';
type SimMode = 'live' | 'paper' | 'mock';
type ShowFilter = 'active' | 'archive' | 'all';
type SortKey = 'strategy' | 'range';
type SortDir = 'asc' | 'desc';

function simState(s: SimulationResponse): SimState {
  if (s.status === 'WAITING_FOR_DATA') return 'waiting';
  if (s.status === 'PENDING') return 'pending';
  if (s.status === 'SIMULATING') return 'simulating';
  if (s.status === 'LIVE') return 'live';
  if (s.status === 'COMPLETED') return 'done';
  return 'failed';
}

function simMode(s: SimulationResponse): SimMode {
  if (s.brokerType === 'ALPACA_LIVE') return 'live';
  if (s.brokerType === 'ALPACA_PAPER') return 'paper';
  return 'mock';
}

const MODE_ORDER: SimMode[] = ['live', 'paper', 'mock'];
const STATE_ORDER: SimState[] = ['live', 'simulating', 'waiting', 'pending', 'done', 'failed'];

const MODE_TITLE: Record<SimMode, { name: string; sub: string }> = {
  live:  { name: 'Alpaca Live',  sub: 'real money' },
  paper: { name: 'Alpaca Paper', sub: 'practice' },
  mock:  { name: 'Mock broker',  sub: 'backtests + free promote' },
};

const STATE_LABEL: Record<SimState, string> = {
  live: 'Live',
  simulating: 'Simulating',
  waiting: 'Awaiting data',
  pending: 'Queued',
  done: 'Completed',
  failed: 'Failed',
};

// Stable enum keys we've seen in the wild; fall back to the raw value for
// anything we haven't catalogued yet so the UI never blocks on an unknown reason.
const EXTERNAL_PAUSE_LABEL: Record<string, string> = {
  AI_BUDGET_DEPLETED: 'daily AI budget depleted',
  BROKER_DISCONNECTED: 'broker disconnected',
  DATA_FEED_STALE: 'data feed stale',
};

function externalReasonLabel(raw: string | null | undefined): string {
  if (!raw) return 'system paused';
  return EXTERNAL_PAUSE_LABEL[raw] ?? raw.toLowerCase().replace(/_/g, ' ');
}

function matchShow(s: SimulationResponse, f: ShowFilter): boolean {
  if (f === 'all') return true;
  const active = ['LIVE', 'SIMULATING', 'PENDING', 'WAITING_FOR_DATA'];
  if (f === 'active') return active.includes(s.status);
  return ['COMPLETED', 'FAILED'].includes(s.status);
}

function fmtMonthDay(iso: string | null | undefined): string {
  if (!iso) return 'now';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const day = String(d.getUTCDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getUTCMonth()];
  return `${day} ${mo} '${String(d.getUTCFullYear()).slice(2)}`;
}

function fmtRange(s: SimulationResponse): string {
  return `${fmtMonthDay(s.startDate)} → ${fmtMonthDay(s.endDate)}`;
}

function fmtCompletion(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const day = String(d.getUTCDate()).padStart(2, '0');
  const mo = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getUTCMonth()];
  return `${day} ${mo}`;
}

function daysRunning(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const ms = Date.now() - d.getTime();
  if (ms < 0) return 0;
  return Math.max(1, Math.floor(ms / 86_400_000));
}

function isStuck(s: SimulationResponse): boolean {
  const skipped = s.skippedEvaluationCount ?? 0;
  const total = s.totalEvaluationCount ?? 0;
  if (total === 0) return false;
  return skipped / total > 0.05;
}

function displayName(s: SimulationResponse, strategy: StrategyResponse | undefined): string {
  if (s.name && s.name.trim().length > 0) return s.name;
  const base = strategy?.name ?? 'Untitled strategy';
  const yr = (iso: string | null | undefined) => (iso ? `'${iso.slice(2, 4)}` : '');
  switch (s.status) {
    case 'LIVE': return `${base} — since ${yr(s.startDate) || '?'}`;
    case 'SIMULATING': return `${base} — backtest ${yr(s.startDate)}–${yr(s.endDate)}`;
    case 'WAITING_FOR_DATA': return `${base} — backfill pending`;
    case 'PENDING': return `${base} — queued`;
    case 'COMPLETED': return `${base} — ${yr(s.startDate)}–${yr(s.endDate)}`;
    case 'FAILED': return `${base} — halted`;
    default: return base;
  }
}

// ─── Page ───────────────────────────────────────────────────────────────────

function SimulationsPageContent() {
  const router = useRouter();
  const { data: simulations, isLoading, error } = useSimulations();
  const { data: strategies } = useStrategies();

  const strategyById = useMemo(() => {
    const m = new Map<string, StrategyResponse>();
    strategies?.forEach((s) => m.set(s.id, s));
    return m;
  }, [strategies]);

  const tickersById = useMemo(() => {
    const m = new Map<string, string[]>();
    strategies?.forEach((s) => m.set(s.id, allTickers(parseStrategyFrontmatter(s.markdownContent))));
    return m;
  }, [strategies]);

  const [filter, setFilter] = useState<ShowFilter>('active');
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState<SortKey>('strategy');
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const handleSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir(key === 'range' ? 'desc' : 'asc');
    }
  };

  const rows = useMemo<SimulationResponse[]>(() => simulations ?? [], [simulations]);
  const help = useContextualHelp('simulations', rows.length > 0);

  const counts = useMemo(() => ({
    all: rows.length,
    active: rows.filter((s) => matchShow(s, 'active')).length,
    archive: rows.filter((s) => matchShow(s, 'archive')).length,
  }), [rows]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((s) => {
      if (!matchShow(s, filter)) return false;
      if (!q) return true;
      const strat = strategyById.get(s.strategyId);
      const tk = tickersById.get(s.strategyId) ?? [];
      const hay = [
        s.id,
        s.name ?? '',
        strat?.name ?? '',
        s.brokerPreset ?? '',
        tk.join(' '),
      ].join(' ').toLowerCase();
      return hay.includes(q);
    });
  }, [rows, filter, search, strategyById, tickersById]);

  const hiddenByMode = useMemo(() => {
    const out: Record<SimMode, number> = { live: 0, paper: 0, mock: 0 };
    rows.forEach((s) => {
      if (matchShow(s, filter)) return;
      out[simMode(s)] += 1;
    });
    return out;
  }, [rows, filter]);

  const grouped = useMemo(() => {
    const out: Record<SimMode, Record<SimState, SimulationResponse[]>> = {
      live:  { live: [], simulating: [], waiting: [], pending: [], done: [], failed: [] },
      paper: { live: [], simulating: [], waiting: [], pending: [], done: [], failed: [] },
      mock:  { live: [], simulating: [], waiting: [], pending: [], done: [], failed: [] },
    };
    filtered.forEach((s) => {
      out[simMode(s)][simState(s)].push(s);
    });
    // Apply the active sort within each (mode × state) bucket so the grouping
    // structure stays intact.
    const dir = sortDir === 'asc' ? 1 : -1;
    const cmp = (a: SimulationResponse, b: SimulationResponse) => {
      if (sortKey === 'strategy') {
        const an = (strategyById.get(a.strategyId)?.name ?? '').toLocaleLowerCase();
        const bn = (strategyById.get(b.strategyId)?.name ?? '').toLocaleLowerCase();
        const c = an.localeCompare(bn);
        if (c !== 0) return c * dir;
      } else {
        const at = new Date(a.startDate).getTime();
        const bt = new Date(b.startDate).getTime();
        if (at !== bt) return (at - bt) * dir;
      }
      // Stable tiebreak: createdAt desc, then id.
      const ac = new Date(a.createdAt).getTime();
      const bc = new Date(b.createdAt).getTime();
      if (ac !== bc) return bc - ac;
      return a.id.localeCompare(b.id);
    };
    MODE_ORDER.forEach((m) => STATE_ORDER.forEach((st) => out[m][st].sort(cmp)));
    return out;
  }, [filtered, sortKey, sortDir, strategyById]);

  if (error) {
    return (
      <Page>
        <ErrorBox>
          Failed to load simulations — {error instanceof Error ? error.message : 'unknown error'}.
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

  const isEmpty = rows.length === 0;
  const meta = headerMeta(rows);
  const showOnboarding = help.isExpanded;

  return (
    <Page>
      <PageHeader>
        <HeaderLeft>
          <H1>My simulations</H1>
          <MetaRow>
            <strong>{meta.total}</strong> total
            <Sep>·</Sep>
            <LiveMeta>
              <PulseDot /> {meta.live} live
            </LiveMeta>
            <Sep>·</Sep>
            {meta.queued} queued
            <Sep>·</Sep>
            {meta.done} completed
            {meta.failed > 0 && (
              <>
                <Sep>·</Sep>
                <ErrMeta>{meta.failed} failed</ErrMeta>
              </>
            )}
            {meta.paused > 0 && (
              <>
                <Sep>·</Sep>
                <WarnMeta>{meta.paused} paused</WarnMeta>
              </>
            )}
          </MetaRow>
        </HeaderLeft>
        <HeaderRight>
          <GuideToggle type="button" onClick={help.toggle}>
            <span className="g">?</span>
            {showOnboarding ? 'Hide guide' : 'What is a simulation?'}
          </GuideToggle>
          <SolidLink href="/benchmarks/new">
            <span>+</span>New simulation
          </SolidLink>
        </HeaderRight>
      </PageHeader>

      {showOnboarding && <OnboardingExplain onDismiss={help.toggle} />}

      {isEmpty ? (
        <EmptyState />
      ) : (
        <Ledger
          rows={rows}
          filtered={filtered}
          grouped={grouped}
          hiddenByMode={hiddenByMode}
          counts={counts}
          filter={filter}
          search={search}
          strategyById={strategyById}
          tickersById={tickersById}
          onFilterChange={setFilter}
          onSearchChange={setSearch}
          sortKey={sortKey}
          sortDir={sortDir}
          onSort={handleSort}
          onOpen={(id) => router.push(`/simulations/${id}`)}
        />
      )}
    </Page>
  );
}

export default function SimulationsPage() {
  return (
    <DashboardLayout>
      <Suspense fallback={null}>
        <SimulationsPageContent />
      </Suspense>
    </DashboardLayout>
  );
}

function headerMeta(rows: SimulationResponse[]) {
  const live = rows.filter((s) => s.status === 'LIVE').length;
  const queued = rows.filter((s) => ['SIMULATING', 'PENDING', 'WAITING_FOR_DATA'].includes(s.status)).length;
  const done = rows.filter((s) => s.status === 'COMPLETED').length;
  const failed = rows.filter((s) => s.status === 'FAILED').length;
  const paused = rows.filter((s) => s.paused).length;
  return { total: rows.length, live, queued, done, failed, paused };
}

// ─── Ledger ─────────────────────────────────────────────────────────────────

interface LedgerProps {
  rows: SimulationResponse[];
  filtered: SimulationResponse[];
  grouped: Record<SimMode, Record<SimState, SimulationResponse[]>>;
  hiddenByMode: Record<SimMode, number>;
  counts: { all: number; active: number; archive: number };
  filter: ShowFilter;
  search: string;
  strategyById: Map<string, StrategyResponse>;
  tickersById: Map<string, string[]>;
  onFilterChange: (f: ShowFilter) => void;
  onSearchChange: (s: string) => void;
  sortKey: SortKey;
  sortDir: SortDir;
  onSort: (key: SortKey) => void;
  onOpen: (id: string) => void;
}

function Ledger(p: LedgerProps) {
  const hiddenTotal = p.rows.length - p.filtered.length;

  return (
    <LedgerCard>
      <Toolbar>
        <Filters>
          <FilterLabel>Show</FilterLabel>
          <Seg>
            <SegBtn type="button" $on={p.filter === 'active'} onClick={() => p.onFilterChange('active')}>
              Active <SegCount>{p.counts.active}</SegCount>
            </SegBtn>
            <SegBtn type="button" $on={p.filter === 'archive'} onClick={() => p.onFilterChange('archive')}>
              Archive <SegCount>{p.counts.archive}</SegCount>
            </SegBtn>
            <SegBtn type="button" $on={p.filter === 'all'} onClick={() => p.onFilterChange('all')}>
              All <SegCount>{p.counts.all}</SegCount>
            </SegBtn>
          </Seg>
        </Filters>
        <SearchInput
          type="text"
          placeholder="Filter by id, strategy, ticker, or preset"
          value={p.search}
          onChange={(e) => p.onSearchChange(e.target.value)}
        />
      </Toolbar>

      <TableWrap>
        <Table>
          <thead>
            <tr>
              <Th className="status">Status</Th>
              <SortableTh
                className={`strategy${p.sortKey === 'strategy' ? ' sorted' : ''}`}
                onClick={() => p.onSort('strategy')}
                aria-sort={p.sortKey === 'strategy' ? (p.sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
              >
                Strategy
                <SortGlyph $active={p.sortKey === 'strategy'}>
                  {p.sortKey === 'strategy' ? (p.sortDir === 'asc' ? '↑' : '↓') : '↕'}
                </SortGlyph>
                <span className="hint">· tickers</span>
              </SortableTh>
              <Th className="name">Simulation</Th>
              <SortableTh
                className={`range${p.sortKey === 'range' ? ' sorted' : ''}`}
                onClick={() => p.onSort('range')}
                aria-sort={p.sortKey === 'range' ? (p.sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
              >
                Range
                <SortGlyph $active={p.sortKey === 'range'}>
                  {p.sortKey === 'range' ? (p.sortDir === 'asc' ? '↑' : '↓') : '↕'}
                </SortGlyph>
              </SortableTh>
              <Th className="prog">Progress · ended</Th>
              <Th className="cash r">Initial</Th>
              <Th className="ret r">Return<DeltaMark>Δ</DeltaMark></Th>
              <Th className="warn" />
              <Th className="acts" />
            </tr>
          </thead>
          <tbody>
            {p.filtered.length === 0 && hiddenTotal === 0 ? (
              <tr>
                <EmptyTd colSpan={9}>No simulations match this filter.</EmptyTd>
              </tr>
            ) : (
              MODE_ORDER.map((mode) => {
                const bucket = p.grouped[mode];
                const flat = STATE_ORDER.flatMap((st) => bucket[st]);
                if (flat.length === 0 && p.hiddenByMode[mode] === 0) return null;

                const capital = mode === 'live'
                  ? flat
                      .filter((s) => simState(s) === 'live')
                      .reduce((n, s) => n + (s.initialCash ?? 0), 0)
                  : 0;

                return (
                  <Fragment key={mode}>
                    <ModeBand
                      mode={mode}
                      count={flat.length}
                      capital={capital}
                      hidden={p.hiddenByMode[mode]}
                      onShowAll={() => p.onFilterChange('all')}
                    />
                    {flat.length === 0 ? (
                      <BandEmptyRow>
                        <td colSpan={9}>
                          <span>
                            {p.filter === 'active' ? 'no active sims in this mode' : 'no archived sims in this mode'}
                            {p.hiddenByMode[mode] > 0 && (
                              <>
                                {' · '}
                                <LinkBtn type="button" onClick={() => p.onFilterChange('all')}>
                                  show {p.hiddenByMode[mode]} {p.filter === 'active' ? 'archived' : 'active'}
                                </LinkBtn>
                              </>
                            )}
                          </span>
                        </td>
                      </BandEmptyRow>
                    ) : (
                      STATE_ORDER.map((st) => {
                        const subset = bucket[st];
                        if (subset.length === 0) return null;
                        return (
                          <Fragment key={st}>
                            <SubStateRow state={st} count={subset.length} />
                            {subset.map((s) => (
                              <LedgerRow
                                key={s.id}
                                sim={s}
                                strategy={p.strategyById.get(s.strategyId)}
                                tickers={p.tickersById.get(s.strategyId) ?? []}
                                onOpen={p.onOpen}
                              />
                            ))}
                          </Fragment>
                        );
                      })
                    )}
                  </Fragment>
                );
              })
            )}
          </tbody>
        </Table>
      </TableWrap>

      <LedgerFoot>
        <span>
          Showing {p.filtered.length} of {p.rows.length}
          <FootSep>·</FootSep>
          grouped by mode · risk gradient
          {p.filter !== 'all' && hiddenTotal > 0 && (
            <>
              <FootSep>·</FootSep>
              <LinkBtn type="button" onClick={() => p.onFilterChange('all')}>
                {hiddenTotal} hidden by &apos;{p.filter}&apos; · show all
              </LinkBtn>
            </>
          )}
        </span>
      </LedgerFoot>
    </LedgerCard>
  );
}

// ─── Mode band ──────────────────────────────────────────────────────────────

function ModeBand({ mode, count, capital, hidden, onShowAll }: {
  mode: SimMode; count: number; capital: number; hidden: number; onShowAll: () => void;
}) {
  const title = MODE_TITLE[mode];
  return (
    <ModeBandRow $mode={mode}>
      <td colSpan={9}>
        <ModeBandInner>
          <span className="left">
            <ModeChip $mode={mode} $size="lg">
              <span className="g" />
              <span className="lbl">{mode === 'live' ? 'Live' : mode === 'paper' ? 'Paper' : 'Mock'}</span>
            </ModeChip>
            <span className="title">
              {title.name} <span className="sub">· {title.sub}</span>
            </span>
            <span className="count">· {count} sim{count === 1 ? '' : 's'}</span>
            {mode === 'live' && capital > 0 && (
              <CapPip title="Δ derived · sum of initialCash across LIVE Alpaca real-money sims; current portfolio value not on the list endpoint">
                <span className="lbl">capital at work</span>
                <strong>${capital.toLocaleString('en-US')}</strong>
                <DeltaMark>Δ</DeltaMark>
              </CapPip>
            )}
          </span>
          <span className="right">
            {hidden > 0 && (
              <HiddenPip type="button" onClick={onShowAll} title="Switch to All and reveal these">
                + {hidden} hidden <span className="dim">· show</span>
              </HiddenPip>
            )}
          </span>
        </ModeBandInner>
      </td>
    </ModeBandRow>
  );
}

function SubStateRow({ state, count }: { state: SimState; count: number }) {
  return (
    <SubStateTr $state={state}>
      <td colSpan={9}>
        <StateDot $state={state} />
        <span className="lbl">{STATE_LABEL[state]}</span>
        <span className="count">{count}</span>
      </td>
    </SubStateTr>
  );
}

// ─── Row ────────────────────────────────────────────────────────────────────

function LedgerRow({ sim, strategy, tickers, onOpen }: {
  sim: SimulationResponse;
  strategy: StrategyResponse | undefined;
  tickers: string[];
  onOpen: (id: string) => void;
}) {
  const state = simState(sim);
  const paused = !!sim.paused;
  const pausedExt = paused && !!sim.pausedForExternalReason;
  const lineageOrigin: StrategyLineageOrigin | null = sim.strategyLineage?.origin ?? null;

  return (
    <RowTr
      $state={state}
      $paused={paused}
      $pausedManual={paused && !pausedExt}
      $pausedExt={pausedExt}
      onClick={() => onOpen(sim.id)}
      role="link"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onOpen(sim.id);
        }
      }}
    >
      <Td className="status">
        <StatusPill sim={sim} />
      </Td>
      <Td className="strategy">
        <StrategyCell strategy={strategy} fallbackId={sim.strategyId} tickers={tickers} lineageOrigin={lineageOrigin} />
      </Td>
      <Td className="name">
        <SimNameStack>
          <span className="nm">{displayName(sim, strategy)}</span>
          <span className="sub">
            <span className="id">#{sim.id.slice(0, 8).toUpperCase()}</span>
            {sim.brokerPreset && (
              <>
                <span className="dot">·</span>
                <span className="preset">{sim.brokerPreset.toLowerCase().replace(/_/g, ' ')}</span>
              </>
            )}
          </span>
        </SimNameStack>
      </Td>
      <Td className="range">
        <Mono>{fmtRange(sim)}</Mono>
      </Td>
      <Td className="prog">
        <ProgressCell sim={sim} />
      </Td>
      <Td className="cash r">
        <Mono>${(sim.initialCash ?? 0).toLocaleString('en-US')}</Mono>
      </Td>
      <Td className="ret r">
        <ReturnCell sim={sim} />
      </Td>
      <Td className="warn">
        {isStuck(sim) && (
          <WarnPip
            title={`${sim.skippedEvaluationCount ?? 0} of ${sim.totalEvaluationCount ?? 0} evaluations skipped — circuit breaker likely tripping`}
          >
            <span className="d" />
            <span className="n">{sim.skippedEvaluationCount ?? 0}</span>
          </WarnPip>
        )}
      </Td>
      <Td className="acts" onClick={(e) => e.stopPropagation()}>
        <OpenHint className="open-hint">open →</OpenHint>
      </Td>
    </RowTr>
  );
}

function StatusPill({ sim }: { sim: SimulationResponse }) {
  if (sim.paused) {
    const ext = !!sim.pausedForExternalReason;
    return (
      <Pill $kind={ext ? 'pausedExt' : 'pausedManual'}>
        <PillBars />
        {ext ? 'Paused · system' : 'Paused'}
      </Pill>
    );
  }
  const state = simState(sim);
  const real = simMode(sim) === 'live';
  const kind: PillKind =
    state === 'live' && real ? 'liveReal' :
    state === 'live' ? 'livePaper' :
    state;
  return (
    <Pill $kind={kind}>
      <PillDot $kind={kind} />
      {STATE_LABEL[state]}
    </Pill>
  );
}

function StrategyCell({ strategy, fallbackId, tickers, lineageOrigin }: {
  strategy: StrategyResponse | undefined;
  fallbackId: string;
  tickers: string[];
  lineageOrigin: StrategyLineageOrigin | null;
}) {
  if (!strategy) {
    return (
      <StratStack>
        <span className="nm dim">unknown strategy</span>
        <span className="row2"><span className="ver">{fallbackId.slice(0, 8).toUpperCase()}</span></span>
      </StratStack>
    );
  }
  const shown = tickers.slice(0, 3);
  const rest = Math.max(0, tickers.length - 3);
  return (
    <StratStack>
      <span className="nm" title={strategy.name}>{strategy.name}</span>
      <span className="row2">
        <span className="ver">v{strategy.version}</span>
        {lineageOrigin && (
          <LineageChip
            $origin={lineageOrigin}
            title={lineageOrigin === 'PUBLIC' ? 'Forked from the public library — your private copy' : 'Authored by you — not shared'}
          >
            {lineageOrigin === 'PUBLIC' ? 'forked' : 'private'}
          </LineageChip>
        )}
        <span className="tkrs">
          {shown.map((t) => <Ticker key={t}>{t}</Ticker>)}
          {rest > 0 && <Ticker $rest>+{rest}</Ticker>}
        </span>
      </span>
    </StratStack>
  );
}

function ProgressCell({ sim }: { sim: SimulationResponse }) {
  if (sim.paused) {
    const ext = !!sim.pausedForExternalReason;
    const reason = ext ? externalReasonLabel(sim.externalPauseReason) : 'paused by you';
    return (
      <PauseChip $ext={ext} title={ext ? `Externally paused — ${reason}` : 'Manually paused by you'}>
        <PauseChipBars />
        <span className="lbl">{reason}</span>
      </PauseChip>
    );
  }
  const state = simState(sim);
  if (state === 'simulating') {
    const p = Math.round(sim.progress ?? 0);
    return (
      <ProgWrap title={`backtest ${p}% complete`}>
        <span className="bar"><i style={{ width: `${p}%` }} /></span>
        <span className="pct">{p}%</span>
      </ProgWrap>
    );
  }
  if (state === 'waiting') {
    const job = sim.backfillJobId ? sim.backfillJobId.replace(/^BFJ-/, '') : null;
    return (
      <BackfillPill title={sim.backfillJobId ? `Backfill job ${sim.backfillJobId} in progress` : 'Backfill in progress'}>
        <span className="bar" />
        <span className="lbl">backfill</span>
        {job && <span className="id">#{job}</span>}
      </BackfillPill>
    );
  }
  if (state === 'live') {
    const days = daysRunning(sim.startedAt);
    return <Mono className="dim">{days != null ? `${days}d running` : '—'}</Mono>;
  }
  if (state === 'pending') {
    return <Mono className="dim">queued</Mono>;
  }
  // done / failed
  return <Mono>{fmtCompletion(sim.completedAt)}</Mono>;
}

function ReturnCell({ sim }: { sim: SimulationResponse }) {
  // The list endpoint does not carry twrPercent / mwrPercent. Once it does,
  // render the real number here and drop HedgedValue.
  const state = simState(sim);
  if (state === 'pending' || state === 'waiting') {
    return <RetWrap><span className="dash">—</span></RetWrap>;
  }
  return (
    <RetWrap>
      <HedgedValue label="— —" />
    </RetWrap>
  );
}

// ─── Onboarding band ────────────────────────────────────────────────────────

function OnboardingExplain({ onDismiss }: { onDismiss: () => void }) {
  return (
    <ExplainCard>
      <ExplainTop>
        <div>
          <Eyebrow><EyebrowDot />First-run guide · dismiss after you&apos;ve shipped one</Eyebrow>
          <ExplainTitle>A simulation runs one strategy against one broker for a date range.</ExplainTitle>
          <ExplainBody>
            The strategy doesn&apos;t change — only the broker does. Same agent council, same{' '}
            <code>strategy.md</code>, three escalating levels of risk. Promote when the previous step
            looks healthy; demote whenever you want to re-test in safety.
          </ExplainBody>
        </div>
        <DismissBtn type="button" onClick={onDismiss} aria-label="Dismiss">×</DismissBtn>
      </ExplainTop>
      <ExplainSteps>
        <Step>
          <StepNum>1</StepNum>
          <h4>Mock backtest</h4>
          <p>Replay historical bars locally — no broker. Five years in &lt; 30s. Zero $ at stake.</p>
        </Step>
        <Step>
          <StepNum>2</StepNum>
          <h4>Alpaca paper</h4>
          <p>Real market data, fake account. Fills are real-ish; nothing settles to your wallet.</p>
        </Step>
        <Step>
          <StepNum>3</StepNum>
          <h4>Alpaca live</h4>
          <p>Real money. The pulse dot on the list is the only one earning or losing anything.</p>
        </Step>
        <Step>
          <StepNum>4</StepNum>
          <h4>Pause anytime</h4>
          <p>Manual pauses + system pauses (e.g. daily AI budget) show as an amber overlay on the row.</p>
        </Step>
      </ExplainSteps>
    </ExplainCard>
  );
}

// ─── Empty state ────────────────────────────────────────────────────────────

function EmptyState() {
  return (
    <EmptyBands>
      <EmptyBand $mode="live">
        <ModeChip $mode="live" $size="lg"><span className="g" /><span className="lbl">Live</span></ModeChip>
        <div className="copy">
          <h4>Alpaca Live</h4>
          <p>Real money. Don&apos;t start here. Get a paper sim healthy first, then promote.</p>
        </div>
        <GhostButton type="button" disabled>Locked · promote a paper sim first</GhostButton>
      </EmptyBand>
      <EmptyBand $mode="paper">
        <ModeChip $mode="paper" $size="lg"><span className="g" /><span className="lbl">Paper</span></ModeChip>
        <div className="copy">
          <h4>Alpaca Paper</h4>
          <p>Real market data, fake account. Validate a mock backtest before risking anything.</p>
        </div>
        <GhostLink href="/settings/broker-accounts">Connect Alpaca paper account</GhostLink>
      </EmptyBand>
      <EmptyBand $mode="mock">
        <ModeChip $mode="mock" $size="lg"><span className="g" /><span className="lbl">Mock</span></ModeChip>
        <div className="copy">
          <h4>Mock broker</h4>
          <p>Replay history locally — no broker, no risk. Start here. Five years in &lt; 30 seconds.</p>
        </div>
        <SolidLink href="/benchmarks/new"><span>+</span>Mock backtest</SolidLink>
      </EmptyBand>
    </EmptyBands>
  );
}

// ─── Loading skeleton ───────────────────────────────────────────────────────

function LoadingSkeleton() {
  return (
    <>
      <PageHeader>
        <HeaderLeft>
          <SkelLine $w={180} $h={28} style={{ borderRadius: 2 }} />
          <SkelLine $w={300} $h={11} style={{ borderRadius: 2, marginTop: 8 }} />
        </HeaderLeft>
        <HeaderRight>
          <SkelLine $w={140} $h={28} style={{ borderRadius: 2 }} />
          <SkelLine $w={130} $h={28} style={{ borderRadius: 2 }} />
        </HeaderRight>
      </PageHeader>
      <LedgerCard>
        <Toolbar>
          <SkelLine $w={320} $h={24} style={{ borderRadius: 2 }} />
          <SkelLine $w={240} $h={24} style={{ borderRadius: 2 }} />
        </Toolbar>
        {(['live', 'paper', 'mock'] as SimMode[]).map((mode) => (
          <SkelMode key={mode} $mode={mode}>
            <SkelBand $mode={mode}>
              <ModeChip $mode={mode} $size="lg">
                <span className="g" />
                <span className="lbl">{mode === 'live' ? 'Live' : mode === 'paper' ? 'Paper' : 'Mock'}</span>
              </ModeChip>
              <SkelLine $w={200} $h={11} style={{ marginLeft: 12 }} />
            </SkelBand>
            {Array.from({ length: mode === 'mock' ? 4 : 2 }).map((_, r) => (
              <SkelRow key={r}>
                <SkelLine $w={84} $h={18} style={{ borderRadius: 999 }} />
                <span className="stack">
                  <SkelLine $w={200} $h={12} />
                  <SkelLine $w={150} $h={9} style={{ marginTop: 4 }} />
                </span>
                <span className="stack">
                  <SkelLine $w={240} $h={12} />
                  <SkelLine $w={160} $h={9} style={{ marginTop: 4 }} />
                </span>
                <SkelLine $w={110} $h={10} />
                <SkelLine $w={110} $h={10} />
                <SkelLine $w={60} $h={10} />
                <SkelLine $w={70} $h={10} />
                <SkelLine $w={80} $h={18} style={{ borderRadius: 2 }} />
              </SkelRow>
            ))}
          </SkelMode>
        ))}
      </LedgerCard>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// Styled components
// ═══════════════════════════════════════════════════════════════════════════

type PillKind = SimState | 'liveReal' | 'livePaper' | 'pausedManual' | 'pausedExt';

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

const ErrMeta = styled.span` color: ${p => p.theme.colors.negative}; `;
const WarnMeta = styled.span` color: ${p => p.theme.colors.terminalAmber}; `;

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

  &:hover { color: ${p => p.theme.colors.foreground}; border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }

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

  &:hover:not(:disabled) { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
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

// ─── Onboarding ─────────────────────────────────────────────────────────────

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
    background: transparent; border: 0; padding: 0;
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

// ─── Ledger ─────────────────────────────────────────────────────────────────

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
  width: 280px;
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
  min-width: 1100px;

  @media (max-width: 768px) {
    min-width: 0;
  }
`;

const Th = styled.th<{ className?: string }>`
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
  box-sizing: border-box;

  & .hint {
    color: ${p => p.theme.colors.fgDimmed};
    text-transform: none;
    letter-spacing: 0;
    font-weight: ${p => p.theme.typography.fontWeight.normal};
    margin-left: 6px;
    font-size: 11px;
  }

  &.sorted { color: ${p => p.theme.colors.fgStrong}; }
  &.r { text-align: right; }
  &.status   { width: 116px; padding-left: 16px; }
  &.strategy { width: 268px; }
  &.name     { /* flex */ }
  &.range    { width: 152px; }
  &.prog     { width: 148px; }
  &.cash     { width: 92px;  text-align: right; }
  &.ret      { width: 88px;  text-align: right; }
  &.warn     { width: 48px;  text-align: center; }
  &.acts     { width: 96px;  text-align: right; padding-right: 16px; }

  @media (max-width: 768px) {
    &.strategy,
    &.range,
    &.prog,
    &.cash,
    &.ret,
    &.warn { display: none; }
    &.acts { width: 64px; }
  }
`;

const SortGlyph = styled.span<{ $active?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9px;
  color: ${p => p.$active ? p.theme.colors.brand : p.theme.colors.fgDimmed};
  margin-left: 4px;
`;

const SortableTh = styled(Th)`
  cursor: pointer;
  user-select: none;
  transition: color 120ms ease;

  &:hover { color: ${p => p.theme.colors.foreground}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: -2px; }
`;

const DeltaMark = styled.sup`
  color: ${p => p.theme.colors.terminalAmber};
  font-size: 8px;
  margin-left: 2px;
`;

const Td = styled.td<{ className?: string }>`
  padding: 10px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 13px;
  vertical-align: middle;
  box-sizing: border-box;

  &.status   { width: 116px; padding-left: 16px; }
  &.strategy { width: 268px; }
  &.range    { width: 152px; }
  &.prog     { width: 148px; }
  &.cash     { width: 92px;  text-align: right; }
  &.ret      { width: 88px;  text-align: right; }
  &.warn     { width: 48px;  text-align: center; }
  &.acts     { width: 96px;  text-align: right; padding-right: 16px; position: relative; }

  @media (max-width: 768px) {
    &.strategy,
    &.range,
    &.prog,
    &.cash,
    &.ret,
    &.warn { display: none; }
    &.acts { width: 64px; }
  }
`;

const EmptyTd = styled.td`
  padding: 28px 16px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
`;

const RowTr = styled.tr<{
  $state: SimState; $paused: boolean; $pausedManual: boolean; $pausedExt: boolean;
}>`
  cursor: pointer;
  transition: background 120ms ease;
  ${p => p.$pausedManual && `opacity: 0.62;`}

  & > td {
    background: ${p =>
      p.$pausedExt ? 'rgba(255, 176, 0, 0.025)' :
      p.$state === 'live' ? 'rgba(0, 255, 65, 0.012)' :
      'transparent'};
  }

  &:hover {
    ${p => p.$pausedManual && `opacity: 0.92;`}
    & > td {
      background: ${p =>
        p.$pausedExt ? 'rgba(255, 176, 0, 0.06)' :
        p.$state === 'live' ? 'rgba(0, 255, 65, 0.04)' :
        'rgba(255, 255, 255, 0.025)'};
    }
    .open-hint { opacity: 1; }
  }

  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: -2px;
  }
`;

// ─── Status pill ────────────────────────────────────────────────────────────

/**
 * Row status → hue. The eight hand-rolled rgba blocks this replaces set colour, border
 * and fill per kind; under DS v0.4.0 `color` is the only per-kind property and the other
 * two mix out of `currentColor`, so a kind can no longer drift from the palette.
 *
 * `livePaper` shares the live hue but not the live dot — see `PillDot`.
 */
const PILL_HUE: Record<PillKind, LabelKind> = {
  live: 'live',
  liveReal: 'live',
  livePaper: 'live',
  simulating: 'simulating',
  waiting: 'waiting',
  pending: 'pending',
  done: 'done',
  failed: 'failed',
  pausedManual: 'paused',
  pausedExt: 'pausedExt',
};

const Pill = styled.span<{ $kind: PillKind }>`
  ${pillBase}
  color: ${p => labelColor[PILL_HUE[p.$kind]](p.theme.colors)};
`;

const pillPulse = keyframes`
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
`;

const PillDot = styled.span<{ $kind: PillKind }>`
  width: 5px;
  height: 5px;
  border-radius: 50%;
  display: inline-block;
  background: currentColor;
  box-shadow: ${p => p.$kind === 'liveReal' ? '0 0 6px currentColor' : 'none'};
  ${/* css`` and not a plain string: interpolating a keyframes object into an untagged template
       calls its toString(), which styled-components throws on. That crashed the whole
       /simulations page to an unrecoverable error boundary — intermittently, because it only
       fires once a row carries one of these three statuses. */
    p => (p.$kind === 'liveReal' || p.$kind === 'simulating' || p.$kind === 'waiting')
      && css`animation: ${pillPulse} 1.4s ease-in-out infinite;`}
  ${p => p.$kind === 'pending' && `background: transparent; box-shadow: inset 0 0 0 1px currentColor;`}
`;

const PillBars = styled.span`
  width: 5px;
  height: 7px;
  display: inline-block;
  background: linear-gradient(
    to right,
    currentColor 0 33%,
    transparent 33% 66%,
    currentColor 66% 100%
  );
`;

// ─── Strategy cell ──────────────────────────────────────────────────────────

const StratStack = styled.span`
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;

  & > .nm {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    color: ${p => p.theme.colors.foreground};
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }
  & > .nm.dim { color: ${p => p.theme.colors.fgDimmed}; font-style: italic; font-weight: ${p => p.theme.typography.fontWeight.normal}; }

  & > .row2 {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex-wrap: wrap;
  }
  & > .row2 .ver {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
    flex-shrink: 0;
  }
  & > .row2 .tkrs {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    flex-wrap: nowrap;
    overflow: hidden;
  }
`;

const LineageChip = styled.span<{ $origin: StrategyLineageOrigin }>`
  ${tagBase}
  ${labelCompact}
  cursor: help;
  flex-shrink: 0;
  color: ${p => (p.$origin === 'PUBLIC' ? hueColor.cyan : hueColor.dim)(p.theme.colors)};
`;

const Ticker = styled.span<{ $rest?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.04em;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.$rest ? p.theme.colors.fgDimmed : p.theme.colors.fgMuted};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.$rest ? p.theme.colors.borderSubtle : p.theme.colors.border};
  border-style: ${p => p.$rest ? 'dashed' : 'solid'};
  border-radius: 2px;
  padding: 1px 6px;
  line-height: 1.4;
  white-space: nowrap;
`;

// ─── Sim name stack ─────────────────────────────────────────────────────────

const SimNameStack = styled.span`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  & > .nm {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    color: ${p => p.theme.colors.fgStrong};
    letter-spacing: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  & > .sub {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgMuted};
    display: inline-flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  & > .sub .id { color: ${p => p.theme.colors.fgMuted}; }
  & > .sub .dot { color: ${p => p.theme.colors.border}; }
  & > .sub .preset { color: ${p => p.theme.colors.fgSubtle}; }

  @media (max-width: 768px) {
    & > .sub { white-space: normal; line-height: 1.4; }
  }
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  font-variant-numeric: tabular-nums;

  &.dim { color: ${p => p.theme.colors.fgDimmed}; }
`;

// ─── Progress / backfill / pause-chip ───────────────────────────────────────

const ProgWrap = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;

  & .bar {
    position: relative;
    flex: 1;
    height: 5px;
    border-radius: 2px;
    background: ${p => p.theme.colors.borderSubtle};
    overflow: hidden;
    max-width: 90px;
  }
  & .bar i {
    display: block;
    height: 100%;
    background: ${p => p.theme.colors.info};
    box-shadow: 0 0 4px rgba(0, 217, 255, 0.5);
  }
  & .pct {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.info};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    min-width: 32px;
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
`;

const bfScan = keyframes`
  0%   { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
`;

const BackfillPill = styled.span`
  ${pillBase}
  /* Syncing: the scan bar is the motion, and it is honest — a backfill really is in flight. */
  color: ${p => labelColor.syncing(p.theme.colors)};
  position: relative;
  overflow: hidden;

  & .bar {
    position: absolute;
    inset: 0;
    background: linear-gradient(
      90deg,
      transparent 0%,
      color-mix(in srgb, currentColor 18%, transparent) 50%,
      transparent 100%
    );
    animation: ${bfScan} 1.6s linear infinite;
    pointer-events: none;
  }
  & .lbl { position: relative; z-index: 1; }
  & .id {
    ${tagIdentifier}
    color: ${p => p.theme.colors.fgMuted};
    position: relative;
    z-index: 1;
  }
`;

const PauseChip = styled.span<{ $ext: boolean }>`
  ${pillBase}
  padding: 2px 8px 2px 7px;
  max-width: 100%;
  /* Paused is settled — the bars glyph never animates. */
  color: ${p => labelColor[p.$ext ? 'pausedExt' : 'paused'](p.theme.colors)};

  & .lbl {
    overflow: hidden;
    text-overflow: ellipsis;
  }
`;

const PauseChipBars = styled.span`
  width: 5px;
  height: 7px;
  display: inline-block;
  flex-shrink: 0;
  background: linear-gradient(
    to right,
    currentColor 0 33%,
    transparent 33% 66%,
    currentColor 66% 100%
  );
`;

const RetWrap = styled.span`
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  justify-content: flex-end;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgSubtle};
  font-variant-numeric: tabular-nums;

  & .dash { letter-spacing: 0.04em; }
`;

// ─── Warnings / open hint ───────────────────────────────────────────────────

const WarnPip = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 6px 1px 4px;
  border-radius: 2px;
  border: 1px solid rgba(255, 176, 0, 0.4);
  background: rgba(255, 176, 0, 0.06);
  cursor: help;

  & .d {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: ${p => p.theme.colors.terminalAmber};
    box-shadow: 0 0 4px rgba(255, 176, 0, 0.5);
  }
  & .n {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.terminalAmber};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
`;

const OpenHint = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgSubtle};
  opacity: 0;
  transition: opacity 120ms ease;
  display: inline-block;
`;

// ─── Mode band ──────────────────────────────────────────────────────────────

const ModeBandRow = styled.tr<{ $mode: SimMode }>`
  & > td {
    padding: 16px 16px 12px;
    background: ${p => {
      if (p.$mode === 'live') return `linear-gradient(90deg, rgba(0, 255, 65, 0.06), rgba(0, 255, 65, 0.015) 60%, transparent 100%), ${p.theme.colors.surface}`;
      if (p.$mode === 'paper') return `linear-gradient(90deg, rgba(255, 176, 0, 0.05), rgba(255, 176, 0, 0.012) 60%, transparent 100%), ${p.theme.colors.surface}`;
      return p.theme.colors.surface;
    }};
    border-top: ${p => p.$mode === 'live' ? '1px solid rgba(0, 255, 65, 0.25)' : `1px solid ${p.theme.colors.borderSubtle}`};
    border-bottom: 1px solid ${p => p.theme.colors.border};
  }
  &:first-child > td { border-top: none; }
`;

const ModeBandInner = styled.span`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;

  & > .left {
    display: inline-flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    flex: 1 1 auto;
    flex-wrap: wrap;
  }
  & > .right {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
    white-space: nowrap;
  }
  & > .left .title {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 15px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    letter-spacing: -0.005em;
  }
  & > .left .title .sub {
    color: ${p => p.theme.colors.fgDimmed};
    font-weight: ${p => p.theme.typography.fontWeight.normal};
    font-size: 14px;
    margin-left: 4px;
    letter-spacing: 0;
  }
  & > .left .count {
    color: ${p => p.theme.colors.fgDimmed};
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    font-variant-numeric: tabular-nums;
  }
`;

/** Broker mode is categorical metadata, so it is a tag. Mock has no kind of its own — it is neutral. */
const MODE_HUE: Record<SimMode, LabelKind> = {
  live: 'live',
  paper: 'paper',
  mock: 'neutral',
};

/**
 * The one size-variant label. `sm` is the compact ledger stop, `lg` the band heading —
 * both sit on `borderRadius.label`, including the glyph, which used to carry its own
 * interpolated 1px/2px radius and so survived every literal-radius sweep.
 */
const ModeChip = styled.span<{ $mode: SimMode; $size?: 'sm' | 'lg' }>`
  ${tagBase}
  ${p => p.$size !== 'lg' && labelCompact}
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: ${p => p.$size === 'lg' ? '5px 12px 5px 10px' : '2px 8px 2px 6px'};
  gap: ${p => p.$size === 'lg' ? '8px' : '6px'};
  line-height: 1;
  color: ${p => labelColor[MODE_HUE[p.$mode]](p.theme.colors)};

  & .g {
    width: ${p => p.$size === 'lg' ? '8px' : '6px'};
    height: ${p => p.$size === 'lg' ? '8px' : '6px'};
    border-radius: ${p => p.theme.borderRadius.label};
    flex-shrink: 0;
    background: currentColor;
  }

  /* Glow is a real-money signal. Paper borrowed it and must not: same shape, no halo. */
  ${p => p.$mode === 'live' && css`& .g { box-shadow: 0 0 4px currentColor; }`}
`;

const CapPip = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: 2px;
  border: 1px solid rgba(0, 255, 65, 0.4);
  background: rgba(0, 255, 65, 0.04);
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  cursor: help;

  & .lbl { color: ${p => p.theme.colors.fgDimmed}; }
  & strong { color: ${p => p.theme.colors.brand}; font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
`;

const HiddenPip = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgMuted};
  background: transparent;
  border: 1px dashed ${p => p.theme.colors.border};
  padding: 3px 9px;
  border-radius: 2px;
  cursor: pointer;
  letter-spacing: 0;
  text-transform: none;

  &:hover { color: ${p => p.theme.colors.foreground}; border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }

  & .dim { color: ${p => p.theme.colors.fgDimmed}; }
`;

const BandEmptyRow = styled.tr`
  & > td {
    padding: 14px 22px 18px;
    background: ${p => p.theme.colors.background};
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const LinkBtn = styled.button`
  background: transparent;
  border: none;
  padding: 0;
  font: inherit;
  color: ${p => p.theme.colors.info};
  cursor: pointer;
  letter-spacing: 0;
  text-transform: none;

  &:hover { color: ${p => p.theme.colors.fgStrong}; text-decoration: underline; text-underline-offset: 2px; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

// ─── Sub-state row ──────────────────────────────────────────────────────────

const SubStateTr = styled.tr<{ $state: SimState }>`
  & > td {
    padding: 8px 18px 4px;
    background: ${p => p.theme.colors.background};
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    display: flex;
    align-items: center;
    gap: 8px;
  }
  & > td .lbl {
    color: ${p =>
      p.$state === 'live' ? p.theme.colors.brand :
      p.$state === 'failed' ? p.theme.colors.negative :
      p.theme.colors.fgMuted};
  }
  & > td .count { color: ${p => p.theme.colors.fgDimmed}; }
`;

const StateDot = styled.span<{ $state: SimState }>`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: ${p => {
    switch (p.$state) {
      case 'live': return p.theme.colors.brand;
      case 'simulating':
      case 'waiting': return p.theme.colors.info;
      case 'pending': return 'transparent';
      case 'done': return p.theme.colors.fgMuted;
      case 'failed': return p.theme.colors.negative;
      default: return p.theme.colors.fgMuted;
    }
  }};
  box-shadow: ${p => {
    if (p.$state === 'live') return '0 0 6px rgba(0, 255, 65, 0.45)';
    if (p.$state === 'simulating' || p.$state === 'waiting') return '0 0 6px rgba(0, 217, 255, 0.45)';
    if (p.$state === 'failed') return '0 0 6px rgba(255, 51, 102, 0.45)';
    if (p.$state === 'pending') return `inset 0 0 0 1px ${p.theme.colors.fgMuted}`;
    return 'none';
  }};
`;

// ─── Ledger foot ────────────────────────────────────────────────────────────

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

// ─── Empty bands ────────────────────────────────────────────────────────────

const EmptyBands = styled.div`
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  background: ${p => p.theme.colors.surface};
  overflow: hidden;
  display: flex;
  flex-direction: column;
`;

const EmptyBand = styled.div<{ $mode: SimMode }>`
  display: grid;
  grid-template-columns: 110px 1fr auto;
  gap: 18px;
  align-items: center;
  padding: 22px 24px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => {
    if (p.$mode === 'live') return 'linear-gradient(90deg, rgba(0, 255, 65, 0.04), rgba(0, 255, 65, 0.005) 60%, transparent)';
    if (p.$mode === 'paper') return 'linear-gradient(90deg, rgba(255, 176, 0, 0.035), rgba(255, 176, 0, 0.005) 60%, transparent)';
    return 'transparent';
  }};

  &:last-child { border-bottom: none; }

  & .copy h4 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 15px;
    color: ${p => p.theme.colors.fgStrong};
    margin: 0 0 4px;
    letter-spacing: -0.003em;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
  & .copy p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    color: ${p => p.theme.colors.fgMuted};
    line-height: 1.5;
    margin: 0;
    max-width: 62ch;
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 10px;
  }
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

const SkelMode = styled.div<{ $mode: SimMode }>`
  display: flex;
  flex-direction: column;
`;

const SkelBand = styled.div<{ $mode: SimMode }>`
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p =>
    p.$mode === 'live' ? 'rgba(0, 255, 65, 0.03)' :
    p.$mode === 'paper' ? 'rgba(255, 176, 0, 0.025)' :
    'transparent'};
`;

const SkelRow = styled.div`
  display: grid;
  grid-template-columns: 100px 220px 1fr 110px 110px 60px 70px 100px;
  gap: 14px;
  align-items: center;
  padding: 12px 18px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};

  & .stack { display: inline-flex; flex-direction: column; }

  @media (max-width: 768px) {
    grid-template-columns: 100px 1fr;
  }
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

