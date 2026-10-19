'use client';

import styled from 'styled-components';
import { HedgedValue } from '@/app/components/shared/v2';
import type { CoverageVerdict } from './coverageDerive';

interface VerdictBandProps {
  verdict: CoverageVerdict | null;
  /** Range label, e.g. "Mar" — derived from selectedMonth, real. */
  rangeShort: string;
  /** Range sub label, e.g. "2022 · 31 days" — real. */
  rangeSub: string;
  /** True while coverage is loading (renders hedged placeholders, no fakes). */
  loading?: boolean;
}

const COV_PRESENT = '#06ffa5';
const COV_ACTION = '#ffb000';

const Wrap = styled.div`
  display: grid;
  grid-template-columns: 1.4fr 1px repeat(4, 1fr);
  align-items: stretch;
  gap: 0;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  background: ${p => p.theme.colors.surface};
  overflow: hidden;

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
    & > .v-sep { display: none; }
  }
`;

const Hero = styled.div`
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
`;

const Sep = styled.div`
  background: ${p => p.theme.colors.borderSubtle};
`;

const VerdictLine = styled.div`
  display: flex;
  align-items: center;
  gap: 11px;
`;

const Icon = styled.span<{ $kind: 'ok' | 'work' | 'run' }>`
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 16px;
  font-weight: 700;
  ${p =>
    p.$kind === 'ok'
      ? `color: ${COV_PRESENT}; border: 1.5px solid rgba(6,255,165,0.5); background: rgba(6,255,165,0.08);`
      : p.$kind === 'work'
        ? `color: ${COV_ACTION}; border: 1.5px solid rgba(255,176,0,0.5); background: rgba(255,176,0,0.08);`
        : `color: ${p.theme.colors.terminalBlue}; border: 1.5px solid rgba(0,217,255,0.5); background: rgba(0,217,255,0.08);`}
`;

const Txt = styled.span`
  b {
    font-size: 19px;
    font-weight: 600;
    color: ${p => p.theme.colors.fgStrong};
    letter-spacing: -0.01em;
    display: block;
    line-height: 1.25;
  }
  span {
    font-size: 12.5px;
    color: ${p => p.theme.colors.fgMuted};
  }
`;

const Meter = styled.div`
  height: 6px;
  border-radius: 3px;
  background: ${p => p.theme.colors.background};
  overflow: hidden;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  i {
    display: block;
    height: 100%;
    background: ${COV_PRESENT};
  }
`;

const Kpi = styled.div`
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  justify-content: center;
  border-left: 1px solid ${p => p.theme.colors.borderSubtle};

  @media (max-width: 1100px) {
    border-left: none;
    border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  }

  .lbl {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
  }
  .val {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 25px;
    font-weight: 500;
    color: ${p => p.theme.colors.fgStrong};
    line-height: 1.05;
    font-variant-numeric: tabular-nums;
  }
  .val.action {
    color: ${COV_ACTION};
  }
  .val.muted {
    color: ${p => p.theme.colors.fgDimmed};
  }
  .val.ok {
    color: ${COV_PRESENT};
  }
  .sub {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10.5px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

export function VerdictBand({ verdict, rangeShort, rangeSub, loading }: VerdictBandProps) {
  const pct = verdict?.pct ?? 0;
  const actionable = verdict?.actionableCount ?? 0;
  const setAside = verdict?.setAsideCount ?? 0;

  let kind: 'ok' | 'work' | 'run' = 'work';
  let iconCh = '!';
  let headline = '';
  let sub = '';

  if (loading || !verdict) {
    kind = 'run';
    iconCh = '…';
    headline = 'Analysing coverage';
    sub = 'Reading availability for this selection';
  } else if (actionable === 0) {
    kind = 'ok';
    iconCh = '✓';
    headline = 'Everything backfillable is present';
    sub = 'Nothing to do for this selection';
  } else {
    kind = 'work';
    iconCh = '!';
    headline = `${actionable} actionable ${actionable === 1 ? 'gap' : 'gaps'} before this selection is complete`;
    sub = `${setAside} item${setAside === 1 ? '' : 's'} set aside (nothing to do)`;
  }

  return (
    <Wrap>
      <Hero>
        <VerdictLine>
          <Icon $kind={kind}>{iconCh}</Icon>
          <Txt>
            <b>{headline}</b>
            <span>{sub}</span>
          </Txt>
        </VerdictLine>
        <Meter title="Completeness of the possible">
          <i style={{ width: `${pct}%` }} />
        </Meter>
      </Hero>
      <Sep className="v-sep" />
      <Kpi>
        <span className="lbl">Backfillable present</span>
        <span className="val ok">
          {loading || !verdict ? <HedgedValue title="Computing from dailyCoverage" /> : `${pct}%`}
        </span>
        <span className="sub">
          {loading || !verdict ? '—' : `${verdict.present}/${verdict.expected} expected slots`}
        </span>
      </Kpi>
      <Kpi>
        <span className="lbl">Actionable gaps</span>
        <span className={`val ${actionable ? 'action' : 'ok'}`}>
          {loading || !verdict ? <HedgedValue /> : actionable}
        </span>
        <span className="sub">backfillable &amp; missing</span>
      </Kpi>
      <Kpi>
        <span className="lbl">Set aside</span>
        <span className="val muted">
          {loading || !verdict ? <HedgedValue /> : setAside}
        </span>
        <span className="sub">closed · future · pre-horizon · no key</span>
      </Kpi>
      <Kpi>
        <span className="lbl">Range</span>
        <span className="val" style={{ fontSize: 16 }}>
          {rangeShort}
        </span>
        <span className="sub">{rangeSub}</span>
      </Kpi>
    </Wrap>
  );
}
