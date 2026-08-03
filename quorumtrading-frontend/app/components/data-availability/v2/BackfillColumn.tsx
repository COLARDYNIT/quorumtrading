'use client';

import { useMemo } from 'react';
import styled from 'styled-components';
import { format, parseISO } from 'date-fns';
import { HedgedValue } from '@/app/components/shared/v2';
import { useRequestBackfill } from '@/app/lib/api/hooks';
import { useBackfillProgress } from '@/app/lib/websocket/useBackfillProgress';
import { EventType, GapStatus } from '@/app/lib/types/dataAvailability';
import type { DataGap, GapProgressDTO } from '@/app/lib/types/dataAvailability';
import { Panel } from '@/app/components/shared/v2';
import { dotBase, labelCompact, pillBase, tagBase } from '@/app/styles/labels';
import { summariseJob, type JobSummary } from './coverageDerive';

interface BackfillColumnProps {
  /** Real actionable gaps (DataGap[]) for the current selection. */
  gaps: DataGap[];
  symbols: string[];
  dataTypes: EventType[];
  from: string;
  to: string;
  /** `/horizon` map (symbol → earliest ISO date). Drives the pre-coverage set-aside. */
  horizon?: Record<string, string>;
}

const PENDING_C = '#9d8cff';
const COV_PRESENT = '#06ffa5';
const COV_ACTION = '#ffb000';
const COV_FAILED = '#ff3366';

const Body = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const GapList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 7px;
`;

const GapRow = styled.div`
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid rgba(255, 176, 0, 0.3);
  background: rgba(255, 176, 0, 0.05);
  border-radius: 4px;

  .main {
    min-width: 0;
  }
  .line1 {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgStrong};
  }
  .line2 {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10.5px;
    color: ${p => p.theme.colors.fgDimmed};
    display: flex;
    gap: 6px;
    align-items: baseline;
  }
  .est {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${COV_ACTION};
    white-space: nowrap;
    justify-self: end;
  }
`;

const StartBtn = styled.button`
  width: 100%;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  padding: 9px 14px;
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid ${p => p.theme.colors.brand};
  background: ${p => p.theme.colors.brandTint};
  color: ${p => p.theme.colors.brand};
  cursor: pointer;
  transition: ${p => p.theme.transition.fast};

  &:hover:not(:disabled) {
    background: rgba(0, 255, 65, 0.16);
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const SetAside = styled.div`
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .heading {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  .sa-item {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    align-items: baseline;
  }
  .sa-why {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
  }
  .sa-tag {
    ${tagBase}
    ${labelCompact}
    color: ${p => p.theme.colors.fgDimmed};
    flex-shrink: 0;
  }
`;

const Centered = styled.div`
  text-align: center;
  padding: 18px 8px;
  .ok {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 13px;
    color: ${COV_PRESENT};
  }
  .note {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
    margin-top: 5px;
  }
`;

// ── live job panels ──────────────────────────────────────────────────────
const Job = styled.div`
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  background: ${p => p.theme.colors.background};
  overflow: hidden;
`;

const JobHd = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 11px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  .jid {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const JobSummaryRow = styled.div`
  display: flex;
  gap: 16px;
  padding: 11px 14px;
  flex-wrap: wrap;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  .item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .n {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 18px;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    line-height: 1;
  }
  .n.ev {
    color: ${COV_PRESENT};
  }
  .n.sk {
    color: ${p => p.theme.colors.fgMuted};
  }
  .n.nd {
    color: ${PENDING_C};
  }
  .n.fl {
    color: ${COV_FAILED};
  }
  .k {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const JobRows = styled.div`
  display: flex;
  flex-direction: column;
`;

const Jr = styled.div<{ $status: GapStatus }>`
  display: grid;
  grid-template-columns: 20px 1fr auto;
  align-items: center;
  gap: 11px;
  padding: 9px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  &:last-child {
    border-bottom: none;
  }

  .jg {
    width: 17px;
    height: 17px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    border: 1px solid ${p => p.theme.colors.border};
    ${p =>
      p.$status === GapStatus.SUCCESS
        ? `color: ${COV_PRESENT}; border-color: rgba(6,255,165,0.5); background: rgba(6,255,165,0.07);`
        : p.$status === GapStatus.FAILED
          ? `color: ${COV_FAILED}; border-color: rgba(255,51,102,0.5); background: rgba(255,51,102,0.07);`
          : p.$status === GapStatus.NO_DATA
            ? `color: ${PENDING_C}; border-color: rgba(157,140,255,0.5); background: rgba(157,140,255,0.07);`
            : `color: ${p.theme.colors.fgDimmed};`}
  }
  .jmain {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
  }
  .jmain .t {
    font-size: 12px;
    color: ${p => p.theme.colors.fgStrong};
  }
  .jmain .t .dim {
    color: ${p => p.theme.colors.fgDimmed};
  }
  .jmain .m {
    font-size: 10.5px;
    color: ${p =>
      p.$status === GapStatus.FAILED
        ? COV_FAILED
        : p.$status === GapStatus.SKIPPED
          ? COV_ACTION
          : p.$status === GapStatus.NO_DATA
            ? PENDING_C
            : p.theme.colors.fgDimmed};
  }
  .jright {
    font-size: 12px;
    text-align: right;
    white-space: nowrap;
    ${p => p.$status === GapStatus.SUCCESS && `color: ${COV_PRESENT};`}
  }
  .jright .u {
    color: ${p => p.theme.colors.fgDimmed};
    font-size: 10px;
  }
`;

const JProg = styled.div`
  padding: 12px 14px;
  .bar {
    height: 8px;
    border-radius: 4px;
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.borderSubtle};
    overflow: hidden;
  }
  .bar i {
    display: block;
    height: 100%;
    background: ${p => p.theme.colors.terminalBlue};
    box-shadow: 0 0 10px rgba(0, 217, 255, 0.4);
    transition: width 0.3s;
  }
  .ptxt {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
    margin-top: 7px;
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }
`;

const Pill = styled.span<{ $live?: boolean }>`
  ${pillBase}
  ${dotBase}
  color: ${p => (p.$live ? p.theme.colors.brand : p.theme.colors.fgMuted)};
`;

function statusGlyph(status: GapStatus): string {
  switch (status) {
    case GapStatus.SUCCESS:
      return '✓';
    case GapStatus.FAILED:
      return '✕';
    case GapStatus.NO_DATA:
      return '⊘';
    case GapStatus.SKIPPED:
      return '⊘';
    case GapStatus.IN_PROGRESS:
      return '◐';
    case GapStatus.PENDING:
      return '○';
    default:
      return '·';
  }
}

function gapKey(g: DataGap): string {
  return `${g.symbol}-${g.dataType}-${g.from}-${g.to}`;
}

/** Today (local) ISO date prefix, for the classification-absent fallback. */
function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Is this gap actionable (fixable now)? Prefer the backend classification;
 * fall back to the local past-date heuristic when it is absent.
 */
function isActionable(g: DataGap): boolean {
  if (g.classification) return g.classification === 'ACTIONABLE';
  return g.from.slice(0, 10) <= todayIso();
}

/** Human label for a non-actionable (set-aside) classification. */
function setAsideLabel(classification: string | undefined): string {
  switch (classification) {
    case 'FUTURE':
      return 'not yet occurred';
    case 'NO_SOURCE':
      return 'no source';
    case 'BEFORE_HORIZON':
      return 'before provider coverage';
    default:
      return 'set aside';
  }
}

/** Truthful "Last backfill" result panel from real GapProgressDTO[]. */
function JobResultView({ job }: { job: JobSummary }) {
  return (
    <Job>
      <JobHd>
        <Pill>
          <span className="dot" />
          {job.status}
        </Pill>
        <span className="jid">job {job.jobId.slice(0, 8)}</span>
      </JobHd>
      <JobSummaryRow>
        <span className="item">
          <span className="n">{job.processed}</span>
          <span className="k">gaps processed</span>
        </span>
        <span className="item">
          <span className="n ev">{job.events}</span>
          <span className="k">events fetched</span>
        </span>
        <span className="item">
          <span className="n">{job.success}</span>
          <span className="k">succeeded</span>
        </span>
        <span className="item">
          <span className="n sk">{job.skipped}</span>
          <span className="k">skipped</span>
        </span>
        {job.noData > 0 && (
          <span className="item">
            <span className="n nd">{job.noData}</span>
            <span className="k">no data</span>
          </span>
        )}
        <span className="item">
          <span className="n fl">{job.failed}</span>
          <span className="k">failed</span>
        </span>
      </JobSummaryRow>
      <JobRows>
        {job.rows.map((r: GapProgressDTO, i: number) => (
          <Jr key={i} $status={r.status}>
            <span className="jg">{statusGlyph(r.status)}</span>
            <span className="jmain">
              <span className="t">
                {r.symbol === 'MARKET' ? 'MARKET' : r.symbol} · {r.dataType}{' '}
                <span className="dim">
                  · {r.from.slice(0, 10)} → {r.to.slice(0, 10)}
                </span>
              </span>
              <span className="m">
                {r.status === GapStatus.SUCCESS && r.provider && `via ${r.provider}`}
                {r.status === GapStatus.NO_DATA && 'no data — none available for this range'}
                {(r.status === GapStatus.FAILED || r.status === GapStatus.SKIPPED) &&
                  (r.error ?? r.status.toLowerCase())}
              </span>
            </span>
            <span className="jright">
              {r.status === GapStatus.SUCCESS && r.eventsFetched !== null && (
                <>
                  {r.eventsFetched}
                  <span className="u"> events</span>
                </>
              )}
            </span>
          </Jr>
        ))}
      </JobRows>
    </Job>
  );
}

/** Live in-progress panel from real progress data. */
function JobProgressView({ job }: { job: JobSummary }) {
  const pct = job.progressPercentage ?? 0;
  return (
    <Job>
      <JobHd>
        <Pill $live>
          <span className="dot" />
          IN PROGRESS
        </Pill>
        <span className="jid">job {job.jobId.slice(0, 8)} · live</span>
      </JobHd>
      <JProg>
        <div className="bar">
          <i style={{ width: `${pct}%` }} />
        </div>
        <div className="ptxt">
          <span>{Math.round(pct)}% · {job.message}</span>
          <span>
            {job.success + job.failed + job.skipped + job.noData}/{job.processed} gaps
          </span>
        </div>
      </JProg>
      {job.rows.length > 0 && (
        <JobRows>
          {job.rows.map((r, i) => (
            <Jr key={i} $status={r.status}>
              <span className="jg">{statusGlyph(r.status)}</span>
              <span className="jmain">
                <span className="t">
                  {r.symbol === 'MARKET' ? 'MARKET' : r.symbol} · {r.dataType}
                </span>
                <span className="m">
                  {r.status === GapStatus.SUCCESS && r.provider
                    ? `via ${r.provider}`
                    : r.status === GapStatus.IN_PROGRESS
                      ? r.provider
                        ? `fetching via ${r.provider}…`
                        : 'fetching…'
                      : r.status === GapStatus.PENDING
                        ? 'queued'
                        : r.error ?? ''}
                </span>
              </span>
              <span className="jright">
                {r.status === GapStatus.SUCCESS && r.eventsFetched !== null && (
                  <>
                    {r.eventsFetched}
                    <span className="u"> events</span>
                  </>
                )}
              </span>
            </Jr>
          ))}
        </JobRows>
      )}
    </Job>
  );
}

export function BackfillColumn({ gaps, symbols, dataTypes, from, to, horizon }: BackfillColumnProps) {
  const { mutate: requestBackfill, isPending } = useRequestBackfill();
  const { allJobs } = useBackfillProgress();

  // Symbols whose PRICE is "before coverage" for THIS view: the entire selected
  // month (from..to) sits before the provider's earliest available date. Only
  // symbols with a real horizon date qualify — unknown horizons are never listed.
  const preCoverage = useMemo(() => {
    return symbols
      .map(sym => {
        const earliest = horizon?.[sym];
        if (!earliest) return null;
        const earliestIso = earliest.slice(0, 10);
        // whole month before earliest ⇔ the month's last day (to) < earliest
        if (to.slice(0, 10) < earliestIso) {
          return { sym, label: format(parseISO(earliestIso), 'MMM yyyy') };
        }
        return null;
      })
      .filter((x): x is { sym: string; label: string } => x !== null);
  }, [symbols, horizon, to]);

  // Find the most-recent job (real). Active jobs render progress; otherwise the
  // most-recently-updated completed job renders the truthful result panel.
  const { activeJob, lastJob } = useMemo(() => {
    const sorted = [...allJobs].sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    );
    const active = sorted.find(j => j.status === 'PENDING' || j.status === 'IN_PROGRESS');
    const last = sorted.find(j => j.status === 'COMPLETED' || j.status === 'FAILED');
    return {
      activeJob: active ? summariseJob(active) : null,
      lastJob: last ? summariseJob(last) : null,
    };
  }, [allJobs]);

  const handleStart = () => {
    requestBackfill({ symbols, dataTypes, from, to, forceBackfill: false });
  };

  // Live job in progress → show progress panel.
  if (activeJob) {
    return (
      <Panel title="Backfill" count="live">
        <JobProgressView job={activeJob} />
      </Panel>
    );
  }

  // Split the worklist on the backend classification (with a local fallback for
  // older responses): ACTIONABLE → the actionable worklist; FUTURE / NO_SOURCE /
  // BEFORE_HORIZON → the set-aside section with a fitting label.
  const actionable = gaps.filter(isActionable);
  const setAsideGaps = gaps.filter(g => !isActionable(g));

  // Backend-classified set-aside gaps (FUTURE / NO_SOURCE / BEFORE_HORIZON).
  const classifiedSetAsideSection = setAsideGaps.length > 0 && (
    <SetAside>
      <div className="heading">Set aside</div>
      {setAsideGaps.map(g => (
        <div className="sa-item" key={gapKey(g)}>
          <span className="sa-why">
            {g.symbol === 'MARKET' ? 'MARKET' : g.symbol} · {g.dataType} — {g.from.slice(0, 10)} →{' '}
            {g.to.slice(0, 10)}
          </span>
          <span className="sa-tag">{setAsideLabel(g.classification)}</span>
        </div>
      ))}
    </SetAside>
  );

  // Real "before provider coverage" set-aside for the current month, from /horizon.
  const preCoverageSection = preCoverage.length > 0 && (
    <SetAside>
      <div className="heading">Before provider coverage</div>
      {preCoverage.map(p => (
        <div className="sa-item" key={p.sym}>
          <span className="sa-why">
            {p.sym} · PRICE — selected month precedes coverage
          </span>
          <span className="sa-tag">coverage from {p.label}</span>
        </div>
      ))}
    </SetAside>
  );

  // No actionable gaps. If a completed job exists, show its truthful result.
  if (actionable.length === 0) {
    if (lastJob) {
      return (
        <Panel title="Last backfill">
          <Body>
            <JobResultView job={lastJob} />
            {classifiedSetAsideSection}
            {preCoverageSection}
          </Body>
        </Panel>
      );
    }
    return (
      <Panel title="Backfill" count="0 actionable">
        <Body>
          <Centered>
            <div className="ok">✓ Range complete</div>
            <div className="note">All backfillable data present for this selection.</div>
          </Centered>
          {classifiedSetAsideSection}
          {preCoverageSection}
        </Body>
      </Panel>
    );
  }

  return (
    <Panel title="Backfill" count={`${actionable.length} actionable`}>
      <Body>
        <GapList>
          {actionable.map(g => (
            <GapRow key={gapKey(g)}>
              <div className="main">
                <div className="line1">
                  {g.symbol === 'MARKET' ? 'MARKET' : g.symbol} · {g.dataType}
                </div>
                <div className="line2">
                  <span>
                    {g.from.slice(0, 10)} → {g.to.slice(0, 10)}
                  </span>
                  <span>
                    ·{' '}
                    {g.provider ? (
                      g.provider
                    ) : (
                      <HedgedValue title="No provider on this gap" label="provider" />
                    )}
                  </span>
                </div>
              </div>
              {/* Real estimated records per gap from the backend classification. */}
              <span className="est">
                {typeof g.estimatedRecords === 'number' ? (
                  `~${g.estimatedRecords} rec`
                ) : (
                  <HedgedValue title="Est. records — not on the gap endpoint yet" label="est" />
                )}
              </span>
            </GapRow>
          ))}
        </GapList>

        <StartBtn onClick={handleStart} disabled={isPending || symbols.length === 0}>
          {isPending
            ? 'Requesting…'
            : `Start backfill · ${actionable.length} ${actionable.length === 1 ? 'gap' : 'gaps'}`}
        </StartBtn>

        {/* Backend-classified set-aside: FUTURE / NO_SOURCE / BEFORE_HORIZON gaps
            that aren't backfillable now. */}
        {classifiedSetAsideSection}

        {/* Pre-coverage set-aside is also fed by the live /horizon endpoint:
            symbols whose entire selected month precedes the provider's earliest
            available date. */}
        {preCoverageSection}

        {lastJob && (
          <SetAside>
            <div className="heading">Last result</div>
            <JobResultView job={lastJob} />
          </SetAside>
        )}
      </Body>
    </Panel>
  );
}
