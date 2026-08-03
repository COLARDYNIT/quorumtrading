'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { Panel, HedgedValue } from '@/app/components/shared/v2';
import { useSimulationData } from '../../contexts/SimulationDataContext';
import type { ContributionResponse, SimulationResponse } from '@/app/lib/types/api';

/** Collapse the ledger past this many rows, keeping the panel scannable. */
const COLLAPSE_AFTER = 5;
const VISIBLE_WHEN_COLLAPSED = 4;

/** When the cash actually landed; `contributionDate` is a date-only fallback. */
function contributionMs(c: ContributionResponse): number {
  const created = c.createdAt ? new Date(c.createdAt).getTime() : NaN;
  return Number.isNaN(created) ? new Date(c.contributionDate).getTime() : created;
}

const moneyFmt = (v: number, ccy = 'USD') => {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: ccy, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v);
  } catch {
    return `${ccy} ${v.toFixed(2)}`;
  }
};
const pctFmt = (v: number | null | undefined) => v == null ? '—' : `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%`;

/** Sign-driven colour class for a percentage stat; blank when there is no value. */
function toneClass(v: number | null | undefined): string {
  if (v == null) return '';
  return v >= 0 ? 'pos' : 'neg';
}

const Stats = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  .s {
    background: ${p => p.theme.colors.surface};
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .lbl {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .v {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.fgStrong};
    font-size: 14px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .v.pos { color: ${p => p.theme.colors.positive}; }
  .v.neg { color: ${p => p.theme.colors.negative}; }
`;

const List = styled.div`
  display: flex;
  flex-direction: column;
  max-height: 220px;
  overflow-y: auto;
  scrollbar-gutter: stable;
`;

const Row = styled.div`
  display: grid;
  grid-template-columns: 64px 92px minmax(0, 1fr) 92px;
  gap: 10px;
  align-items: center;
  padding: 6px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 12.5px;

  &:last-child { border-bottom: none; }

  &.hdr span {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  .r { text-align: right; }

  /* The seed reads as an anchor, not another deposit. */
  &.seed {
    background: rgba(0, 217, 255, 0.035);
  }
  &.seed .amt {
    color: ${p => p.theme.colors.foreground};
  }
  &.seed .src .tag {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.info};
    border: 1px solid rgba(0, 217, 255, 0.4);
    border-radius: 2px;
    padding: 0 5px;
    margin-left: 6px;
  }

  .run {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.fgDimmed};
    text-align: right;
    font-size: 12px;
  }

  .when {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.fgMuted};
    white-space: nowrap;
  }
  .amt {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.positive};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    text-align: right;
  }
  .src {
    color: ${p => p.theme.colors.fgMuted};
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .src .label {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgStrong};
  }
`;

const EmptyMsg = styled.div`
  padding: 20px 12px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 13px;
`;

const MoreButton = styled.button`
  display: block;
  width: 100%;
  text-align: center;
  padding: 7px;
  background: transparent;
  border: none;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  color: ${p => p.theme.colors.fgMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  cursor: pointer;

  &:hover { color: ${p => p.theme.colors.info}; }
  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: -2px;
  }
`;

/**
 * The sum the panel's own rows make. Shown so a reader can check the ledger
 * against Total invested without doing the arithmetic — the mismatch this
 * closes is what made the previous version look broken.
 */
const Recon = styled.div<{ $ok: boolean }>`
  display: flex;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 7px 12px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: rgba(255, 255, 255, 0.015);
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  color: ${p => (p.$ok ? p.theme.colors.fgMuted : p.theme.colors.terminalAmber)};

  b {
    color: ${p => (p.$ok ? p.theme.colors.fgStrong : p.theme.colors.terminalAmber)};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

function shortDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString('en-US', { day: '2-digit', month: 'short' });
  } catch {
    return iso;
  }
}

export function DcaPanel() {
  const { simulation } = useSimulationData();
  const [showAll, setShowAll] = useState(false);

  const sim = simulation as unknown as SimulationResponse | null;

  const data = useMemo(() => {
    if (!sim) return null;
    const contributions: ContributionResponse[] = sim.contributions ?? [];
    const seed = sim.initialCash ?? 0;
    // Oldest first to accumulate the running total, then reversed for display —
    // the ledger reads newest-first but each row still shows the balance *after*
    // that deposit, so the list visibly climbs to Total invested.
    const chronological = contributions.slice().sort(
      (a, b) => contributionMs(a) - contributionMs(b)
    );
    let running = seed;
    const withRunning = chronological.map(c => {
      running += c.amount;
      return { ...c, runningTotal: running };
    });
    const totalInvested = sim.totalInvested ?? null;
    const twr = sim.twrPercent ?? null;
    const mwr = sim.mwrPercent ?? null;
    // Does the ledger actually add up? If the backend's totalInvested disagrees
    // with seed + Σ contributions, say so rather than printing a tick.
    const summed = seed + contributions.reduce((acc, c) => acc + c.amount, 0);
    const reconciles = totalInvested == null || Math.abs(summed - totalInvested) < 0.005;
    return {
      contributions: withRunning.slice().reverse(),
      totalInvested,
      twr,
      mwr,
      initial: seed,
      contributed: summed - seed,
      summed,
      reconciles,
      startDate: sim.startDate,
    };
  }, [sim]);

  if (!data) {
    return (
      <Panel title="DCA timeline" count="—">
        <EmptyMsg>Loading…</EmptyMsg>
      </Panel>
    );
  }

  // Keyed on contributions, not totalInvested: the backend reports totalInvested
  // for every simulation (it equals the seed when nothing was added), so keying
  // on it rendered a "DCA timeline" of one seed row for lump-sum runs.
  if (data.contributions.length === 0) {
    return (
      <Panel title="DCA timeline" count="no plan">
        <EmptyMsg>
          No contributions on this simulation. Enable DCA on a LIVE simulation to add recurring deposits.
        </EmptyMsg>
      </Panel>
    );
  }

  const visible = showAll || data.contributions.length <= COLLAPSE_AFTER
    ? data.contributions
    : data.contributions.slice(0, VISIBLE_WHEN_COLLAPSED);

  return (
    <Panel
      title="DCA timeline"
      count={`${data.contributions.length} contribution${data.contributions.length === 1 ? '' : 's'}`}
      noBodyPadding
    >
      <Stats>
        <div className="s">
          <div className="lbl">Total invested</div>
          <div className="v">{moneyFmt(data.totalInvested ?? data.initial ?? 0)}</div>
        </div>
        <div className="s">
          <div className="lbl">TWR</div>
          <div className={`v ${toneClass(data.twr)}`}>{pctFmt(data.twr)}</div>
        </div>
        <div className="s">
          <div className="lbl">MWR (IRR)</div>
          <div className={`v ${toneClass(data.mwr)}`}>
            {data.mwr != null
              ? pctFmt(data.mwr)
              : <HedgedValue label="not computed" title="mwrPercent is null on this simulation — the backend does not report a reason" />}
          </div>
        </div>
      </Stats>
      <List>
        <Row className="hdr">
          <span>When</span>
          <span className="r">Amount</span>
          <span>Source</span>
          <span className="r">Running</span>
        </Row>
        {visible.map(c => (
          <Row key={c.id}>
            <span className="when">{shortDate(c.contributionDate)}</span>
            <span className="amt">+{moneyFmt(c.amount, c.currency)}</span>
            <span className="src">
              {c.sourcePlanId
                ? <><span className="label">Plan</span>{' '}
                    <Link href={`/investment-plans/${c.sourcePlanId}`}>{c.sourcePlanId.slice(0, 8).toUpperCase()}</Link></>
                : 'Manual injection'}
            </span>
            <span className="run">{moneyFmt(c.runningTotal, c.currency)}</span>
          </Row>
        ))}
        {!showAll && data.contributions.length > COLLAPSE_AFTER && (
          <MoreButton type="button" onClick={() => setShowAll(true)}>
            show {data.contributions.length - VISIBLE_WHEN_COLLAPSED} earlier contributions ↓
          </MoreButton>
        )}
        {/* The seed is not a contribution — it is the opening balance the
            contributions accumulate onto. Without it the rows cannot sum to
            Total invested, which is what made the panel look wrong. */}
        <Row className="seed">
          <span className="when">{shortDate(data.startDate)}</span>
          <span className="amt">{moneyFmt(data.initial)}</span>
          <span className="src">Opening balance<span className="tag">seed</span></span>
          <span className="run">{moneyFmt(data.initial)}</span>
        </Row>
      </List>
      <Recon $ok={data.reconciles}>
        <span>seed {moneyFmt(data.initial)} + contributions {moneyFmt(data.contributed)}</span>
        {data.reconciles
          ? <span>= <b>{moneyFmt(data.summed)}</b> ✓</span>
          : <span title="These do not match the backend's totalInvested — the ledger below is the sum of the rows shown">
              = <b>{moneyFmt(data.summed)}</b>, reported {moneyFmt(data.totalInvested ?? 0)}
            </span>}
      </Recon>
    </Panel>
  );
}
