'use client';

import { useMemo, useState } from 'react';
import styled from 'styled-components';
import { useSimulationData } from '../contexts/SimulationDataContext';
import { SimHeader, type StatusPillKind } from '@/app/components/shared/v2';
import type { KpiItem } from '@/app/components/shared/v2';

const LabelWithInfo = styled.span`
  display: inline-flex;
  align-items: center;
`;

const InfoDot = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 13px;
  height: 13px;
  margin-left: 5px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 50%;
  background: transparent;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9px;
  line-height: 1;
  letter-spacing: 0;
  text-transform: none;
  cursor: pointer;

  &:hover {
    color: ${p => p.theme.colors.info};
    border-color: ${p => p.theme.colors.info};
  }
  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: 1px;
  }
`;

const DefsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-top: none;
  border-radius: 0 0 4px 4px;
  overflow: hidden;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }

  .d {
    background: ${p => p.theme.colors.background};
    padding: 9px 14px 10px;
  }
  .n {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.foreground};
  }
  .d.hl .n {
    color: ${p => p.theme.colors.info};
  }
  p {
    margin: 3px 0 0;
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12px;
    line-height: 1.5;
    color: ${p => p.theme.colors.fgMuted};
    text-wrap: pretty;
  }
`;

const numberFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pctFmt = (v: number) => `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%`;

function statusToPillKind(status: string | undefined, paused: boolean | undefined): StatusPillKind {
  if (paused) return 'paper';
  switch (status) {
    case 'LIVE': return 'live';
    case 'SIMULATING': return 'simulating';
    case 'COMPLETED': return 'completed';
    case 'FAILED': return 'failed';
    case 'PENDING':
    default:
      return 'pending';
  }
}

function statusLabel(status: string | undefined, paused: boolean | undefined): string {
  if (paused) return 'Paused';
  switch (status) {
    case 'LIVE': return 'Live';
    case 'SIMULATING': return 'Backtest';
    case 'COMPLETED': return 'Completed';
    case 'FAILED': return 'Failed';
    case 'PENDING': return 'Pending';
    default: return status ?? '—';
  }
}

function brokerPill(brokerType: string | undefined): { kind: StatusPillKind; label: string } | null {
  switch (brokerType) {
    case 'ALPACA_LIVE': return { kind: 'live', label: 'Live broker' };
    case 'ALPACA_PAPER': return { kind: 'paper', label: 'Paper' };
    case 'MOCK': return { kind: 'done', label: 'Mock' };
    default: return null;
  }
}

function shortDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch {
    return iso;
  }
}

function durationBetween(startIso: string | null | undefined, endIso: string | null | undefined): string {
  if (!startIso) return '—';
  const start = new Date(startIso).getTime();
  const end = endIso ? new Date(endIso).getTime() : Date.now();
  const ms = Math.max(0, end - start);
  const days = Math.floor(ms / (1000 * 60 * 60 * 24));
  const hours = Math.floor((ms % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
  return `${days}d ${hours.toString().padStart(2, '0')}h`;
}

/**
 * Page-level header for the Simulation Detail screen.
 *
 * Renders title + ticker + status pills + a 5/6-KPI strip using data from
 * SimulationDataContext. The KPIs surface what a trader scans first:
 * starting value, current value, total return, vs-benchmark delta, max
 * drawdown, and (when DCA is active) contributed.
 */
export function SimulationDetailHeader() {
  const [defsOpen, setDefsOpen] = useState(false);
  const {
    simulation,
    strategy,
    portfolioData,
    portfolioSnapshots,
    configuredTickers,
  } = useSimulationData();

  const data = useMemo(() => {
    if (!simulation) return null;
    // Backend serialises starting capital as `initialCash` (older code paths
    // also surfaced `initialCapital` / `budgetUsd` — keep them as fallbacks).
    const sim = simulation as unknown as {
      initialCash?: number; initialCapital?: number; budgetUsd?: number;
      totalInvested?: number; maxDrawdownPercent?: number;
      mwrPercent?: number | null;
      contributions?: Array<unknown>;
    };
    const startingCapital = sim.initialCash ?? sim.initialCapital ?? sim.budgetUsd ?? 0;
    const currentValue = portfolioData?.totalValue ?? startingCapital;
    // Return basis is TOTAL INVESTED (seed + Σ contributions). A DCA top-up is added
    // capital, not profit — measuring against the seed alone would report the
    // injected cash as gains. Equals the seed when there are no contributions.
    const investedBase = sim.totalInvested ?? startingCapital;
    const contributed = Math.max(0, investedBase - startingCapital);
    const hasContributions = contributed > 0;
    const totalReturnAbs = currentValue - investedBase;
    const totalReturnPct = investedBase > 0 ? (totalReturnAbs / investedBase) * 100 : 0;

    // Max drawdown: prefer the backend's contribution-normalized value — it strips
    // the cash-injection step that would otherwise read as a ~50% draw. Fall back to a
    // raw-snapshot scan only when the backend value is absent (e.g. legacy responses).
    let maxDrawdownPct: number | null = null;
    let drawdownDate: string | null = null;
    if (sim.maxDrawdownPercent != null) {
      maxDrawdownPct = sim.maxDrawdownPercent;
    } else if (portfolioSnapshots && portfolioSnapshots.length > 0) {
      let peak = -Infinity;
      let worst = 0;
      let worstAt: string | null = null;
      for (const snap of portfolioSnapshots) {
        const total = (snap as unknown as { totalValue?: number }).totalValue ?? 0;
        if (total > peak) peak = total;
        if (peak > 0) {
          const dd = (total - peak) / peak;
          if (dd < worst) {
            worst = dd;
            worstAt = (snap as unknown as { timestamp?: string }).timestamp ?? null;
          }
        }
      }
      if (worst < 0) {
        maxDrawdownPct = worst * 100;
        drawdownDate = worstAt;
      }
    }

    const tickers = [
      ...(configuredTickers.stock ?? []),
      ...(configuredTickers.crypto ?? []),
    ];
    const tickerLabel = tickers.slice(0, 3).join(' · ') + (tickers.length > 3 ? ` +${tickers.length - 3}` : '');

    return {
      title: strategy?.name ?? simulation.id?.slice(0, 8) ?? 'Simulation',
      tickerLabel,
      status: (simulation as unknown as { status?: string }).status,
      paused: (simulation as unknown as { paused?: boolean }).paused,
      brokerType: (simulation as unknown as { brokerType?: string }).brokerType,
      simId: simulation.id,
      // Use the simulation period dates (start/end of the backtest window) — what a
      // trader cares about — not `startedAt`/`completedAt` (when the user clicked Start).
      startedAt: (simulation as unknown as { startDate?: string; startedAt?: string }).startDate
        ?? (simulation as unknown as { startedAt?: string }).startedAt
        ?? null,
      endedAt: (simulation as unknown as { endDate?: string; completedAt?: string }).endDate
        ?? (simulation as unknown as { completedAt?: string }).completedAt
        ?? null,
      startingCapital,
      investedBase,
      contributed,
      hasContributions,
      contributionCount: sim.contributions?.length ?? 0,
      hasMwr: sim.mwrPercent != null,
      currentValue,
      totalReturnAbs,
      totalReturnPct,
      maxDrawdownPct,
      drawdownDate,
    };
  }, [simulation, strategy, portfolioData, portfolioSnapshots, configuredTickers]);

  if (!data) return null;

  const pills: Array<{ kind: StatusPillKind; label: string }> = [
    { kind: statusToPillKind(data.status, data.paused), label: statusLabel(data.status, data.paused) },
  ];
  const broker = brokerPill(data.brokerType);
  if (broker) pills.push(broker);

  const meta = [
    { label: 'Sim id', value: data.simId?.slice(0, 8) ?? '—' },
    { label: 'Started', value: shortDate(data.startedAt) },
    { label: data.endedAt ? 'Ended' : 'Now', value: data.endedAt ? shortDate(data.endedAt) : shortDate(new Date().toISOString()) },
    { label: 'Duration', value: durationBetween(data.startedAt, data.endedAt) },
  ];

  const returnCell: KpiItem = {
    label: (
      <LabelWithInfo>
        Total return
        <InfoDot
          type="button"
          aria-expanded={defsOpen}
          aria-label="Which return answers which question?"
          title="Which return answers which question?"
          onClick={() => setDefsOpen(v => !v)}
        >
          i
        </InfoDot>
      </LabelWithInfo>
    ),
    value: pctFmt(data.totalReturnPct),
    delta: `${data.totalReturnAbs >= 0 ? '+' : '−'}${numberFmt.format(Math.abs(data.totalReturnAbs))}`,
    tone: data.totalReturnPct >= 0 ? 'positive' : 'negative',
  };
  const drawdownCell: KpiItem = {
    label: 'Max drawdown',
    value: data.maxDrawdownPct != null ? pctFmt(data.maxDrawdownPct) : '—',
    tone: 'dim',
  };

  // With contributions the strip reads as an equation — seed + contributed =
  // invested → current value = return — so the $500 that started it is stated
  // outright instead of being recoverable only by subtracting two other KPIs.
  const kpis: KpiItem[] = data.hasContributions
    ? [
        {
          label: 'Seed capital',
          value: numberFmt.format(data.startingCapital),
          delta: data.startedAt ? `at start · ${shortDate(data.startedAt)}` : undefined,
        },
        {
          label: 'Contributed',
          value: `+${numberFmt.format(data.contributed)}`,
          delta: `${data.contributionCount} deposit${data.contributionCount === 1 ? '' : 's'}`,
        },
        {
          label: 'Invested',
          value: numberFmt.format(data.investedBase),
          delta: 'seed + contributions',
        },
        { label: 'Current value', value: numberFmt.format(data.currentValue) },
        returnCell,
        drawdownCell,
      ]
    : [
        { label: 'Starting value', value: numberFmt.format(data.startingCapital) },
        { label: 'Current value', value: numberFmt.format(data.currentValue) },
        returnCell,
        drawdownCell,
      ];

  const kpiOps = data.hasContributions
    ? [null, '+', '=', '→', '=', null]
    : [null, '→', '=', null];

  return (
    <SimHeader
      title={data.title}
      ticker={data.tickerLabel || undefined}
      pills={pills}
      meta={meta}
      kpis={kpis}
      kpiOps={kpiOps}
      kpiFooter={defsOpen ? <ReturnDefinitions hasMwr={data.hasMwr} /> : undefined}
      dense
    />
  );
}

/**
 * The three returns this page can show, and which question each answers.
 *
 * Only the MWR copy varies: when the backend reports null we say so plainly
 * rather than describing a number that is not on screen. The backend does not
 * report *why* it is null, so this does not guess at a reason.
 */
function ReturnDefinitions({ hasMwr }: { hasMwr: boolean }) {
  return (
    <DefsGrid>
      <div className="d hl">
        <div className="n">Total return — the headline</div>
        <p>
          Value now against everything paid in (seed + deposits), whenever it arrived.
          The answer to &ldquo;what happened to my money&rdquo;.
        </p>
      </div>
      <div className="d">
        <div className="n">TWR — time-weighted</div>
        <p>
          Strips out deposit timing to grade the strategy itself. Use it to compare
          runs. Shown in the DCA timeline.
        </p>
      </div>
      <div className="d">
        <div className="n">MWR (IRR) — money-weighted</div>
        <p>
          The annualised rate your actual cashflows earned.
          {!hasMwr && ' Not computed on this simulation — the backend reports no value.'}
        </p>
      </div>
    </DefsGrid>
  );
}
