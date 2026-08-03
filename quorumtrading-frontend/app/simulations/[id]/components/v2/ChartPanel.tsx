'use client';

import { useCallback, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import styled, { useTheme } from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import { useSimulationData } from '../../contexts/SimulationDataContext';
import { useSimulationOverlays, useConsensusWithAgents } from '@/app/lib/api/hooks';
import { POPULAR_OVERLAYS } from '@/app/lib/types/benchmark';
import { getOverlayColor } from '@/app/lib/utils/colors';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';
import { portfolioReturnPct } from '../../lib/strategyFrontmatter';
import { buildInvestedSteps, investedAt, type InvestedStep } from '../../lib/investedCapital';
import type { OverlayResponse } from '@/app/lib/types/benchmark';
import type { ContributionResponse, SimulationResponse, ConsensusWithAgentsResponse, LLMDecision } from '@/app/lib/types/api';

const numberFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 });
/** Whole-dollar money for in-chart labels, where two decimals only add noise. */
const compactMoneyFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 0, maximumFractionDigits: 0 });
const pctFmt = (v: number) => `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%`;

const Subtitle = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
`;

const HeaderActions = styled.div`
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
`;

const TfButton = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 2px;
  border: 1px solid ${p => p.$on ? p.theme.colors.fgMuted : p.theme.colors.border};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  background: ${p => p.$on ? 'rgba(255, 255, 255, 0.04)' : 'transparent'};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;
  &:hover { color: ${p => p.theme.colors.foreground}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const Toggle = styled.button<{ $on: boolean; $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 2px;
  border: 1px solid ${p => p.$on ? p.$color : p.theme.colors.border};
  color: ${p => p.$on ? p.$color : p.theme.colors.fgMuted};
  background: ${p => p.$on ? `${p.$color}15` : 'transparent'};
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;
  &:hover { color: ${p => p.theme.colors.foreground}; border-color: ${p => p.$color}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }

  & > .sw {
    width: 8px;
    height: 8px;
    border-radius: 1px;
    background: ${p => p.$color};
    flex-shrink: 0;
  }
  & > .dash {
    width: 12px;
    height: 0;
    border-top: 2px dashed ${p => p.$color};
  }
`;

const ChartHost = styled.div`
  position: relative;
  padding: 8px 4px 0;
`;

/**
 * Legend for the invested-capital view. Named rather than colour-only, because
 * "the gap is your P&L" is the whole point of the baseline and a bare swatch
 * does not say it.
 */
const ChartLegend = styled.div`
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  padding: 6px 14px 10px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  color: ${p => p.theme.colors.fgMuted};

  .k {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .sw {
    width: 14px;
    flex-shrink: 0;
  }
  .sw.value {
    height: 0;
    border-top: 2px solid ${p => p.theme.colors.brand};
  }
  .sw.step {
    height: 0;
    border-top: 2px dashed ${p => p.theme.colors.info};
  }
  .sw.gappos {
    height: 8px;
    background: rgba(6, 255, 165, 0.16);
  }
  .sw.gapneg {
    height: 8px;
    background: rgba(255, 51, 102, 0.2);
  }
`;

const StatusLine = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  padding: 16px;
  text-align: center;
`;

// Decision color (mint/pink/dimmed) — match design language for chart dots.
function decisionColor(action: string, theme: any): string {
  const a = action.toUpperCase();
  if (a === 'BUY') return theme.colors.action.buy;
  if (a === 'SELL') return theme.colors.action.sell;
  return theme.colors.fgDimmed;
}

// ============================================================================
// HOVER TIP — structured card per design (date / chip+votes / size@price / footer)
// ============================================================================

const TipRoot = styled.div<{ $action: string }>`
  width: 244px;
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.borderHighlight};
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.55);
  overflow: hidden;
  pointer-events: none;
  border-left: 3px solid ${p => {
    const a = p.$action.toUpperCase();
    if (a === 'BUY') return p.theme.colors.action.buy;
    if (a === 'SELL') return p.theme.colors.action.sell;
    return p.theme.colors.fgDimmed;
  }};
`;

const TipDate = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  padding: 8px 12px 6px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const TipActionRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  gap: 10px;
`;

const TipActionChip = styled.span<{ $action: string }>`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  border-radius: 2px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  background: ${p => {
    const a = p.$action.toUpperCase();
    if (a === 'BUY') return `${p.theme.colors.action.buy}29`;
    if (a === 'SELL') return `${p.theme.colors.action.sell}29`;
    return 'rgba(255,255,255,0.08)';
  }};
  color: ${p => {
    const a = p.$action.toUpperCase();
    if (a === 'BUY') return p.theme.colors.action.buy;
    if (a === 'SELL') return p.theme.colors.action.sell;
    return p.theme.colors.fgMuted;
  }};
  & > .glyph { font-size: 11px; line-height: 1; }
`;

const TipVotes = styled.div<{ $action: string }>`
  display: inline-flex;
  align-items: center;
  gap: 3px;

  & > .vote {
    width: 7px;
    height: 12px;
    background: ${p => p.theme.colors.border};
    border-radius: 1px;
  }
  & > .vote.on {
    background: ${p => {
      const a = p.$action.toUpperCase();
      if (a === 'BUY') return p.theme.colors.action.buy;
      if (a === 'SELL') return p.theme.colors.action.sell;
      return p.theme.colors.fgMuted;
    }};
  }
  & > .num {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    color: ${p => p.theme.colors.fgDimmed};
    letter-spacing: 0.04em;
    margin-left: 5px;
  }
`;

const TipTrade = styled.div`
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 10px;
  align-items: end;
  padding: 9px 12px 12px;
  border-top: 1px dashed ${p => p.theme.colors.borderSubtle};
  background: rgba(0, 0, 0, 0.22);

  & > .cell {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  & > .cell .lbl {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 8.5px;
    letter-spacing: 0.14em;
    color: ${p => p.theme.colors.fgDimmed};
  }
  & > .cell .val {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 14px;
    font-weight: 600;
    color: ${p => p.theme.colors.fgStrong};
    letter-spacing: 0.02em;
  }
  & > .sep {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 14px;
    color: ${p => p.theme.colors.fgDimmed};
    padding-bottom: 1px;
  }
`;

const TipNoTrade = styled.div`
  padding: 10px 12px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
  letter-spacing: 0.04em;
  border-top: 1px dashed ${p => p.theme.colors.borderSubtle};
  background: rgba(0, 0, 0, 0.22);
  text-align: center;
`;

const TipFoot = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 12px 8px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
  background: rgba(0, 0, 0, 0.32);
  font-size: 11px;

  & > .pf {
    display: inline-flex;
    gap: 6px;
    align-items: baseline;
    font-family: ${p => p.theme.typography.fontFamily.mono};
  }
  & > .pf .dim {
    color: ${p => p.theme.colors.fgDimmed};
    letter-spacing: 0.08em;
    text-transform: uppercase;
    font-size: 9px;
  }
  & > .pf .val {
    color: ${p => p.theme.colors.foreground};
    font-weight: 500;
  }
  & > .cta {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.info};
    font-size: 9.5px;
    letter-spacing: 0.06em;
  }
`;

interface TipProps {
  action: string;
  date: string;
  voted: number;
  total: number;
  size: string | null;
  price: number | null;
  portfolioValue: number;
}

function HoverTip({ action, date, voted, total, size, price, portfolioValue }: TipProps) {
  const a = action.toUpperCase();
  const glyph = a === 'BUY' ? '▲' : a === 'SELL' ? '▼' : '■';
  return (
    <TipRoot $action={a}>
      <TipDate className="mono">{date}</TipDate>
      <TipActionRow>
        <TipActionChip $action={a}>
          <span className="glyph">{glyph}</span>
          {a}
        </TipActionChip>
        <TipVotes $action={a} aria-label={`${voted} of ${total} agents voted`}>
          {Array.from({ length: total }).map((_, i) => (
            <span key={i} className={'vote' + (i < voted ? ' on' : '')} />
          ))}
          <span className="num">{voted}/{total}</span>
        </TipVotes>
      </TipActionRow>
      {size && price != null ? (
        <TipTrade>
          <div className="cell">
            <div className="lbl">SIZE</div>
            <div className="val">{size}</div>
          </div>
          <div className="sep">@</div>
          <div className="cell">
            <div className="lbl">PRICE</div>
            <div className="val">${price.toFixed(2)}</div>
          </div>
        </TipTrade>
      ) : (
        <TipNoTrade>no trade · position held</TipNoTrade>
      )}
      <TipFoot>
        <div className="pf">
          <span className="dim">portfolio</span>
          <span className="val">${portfolioValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
        </div>
        <div className="cta">click → inspect</div>
      </TipFoot>
    </TipRoot>
  );
}

// ============================================================================
// CHART (SVG) — portfolio line + dots + overlays + contribution markers
// ============================================================================

const TIMEFRAMES = ['1W', '1M', '3M', 'All'] as const;
type TimeFrame = (typeof TIMEFRAMES)[number];

function timeframeMs(tf: TimeFrame): number | null {
  if (tf === '1W') return 7 * 24 * 60 * 60 * 1000;
  if (tf === '1M') return 30 * 24 * 60 * 60 * 1000;
  if (tf === '3M') return 90 * 24 * 60 * 60 * 1000;
  return null;
}

interface DecisionMarker {
  id: string;
  ms: number;
  action: string;
  size: string | null;
  price: number | null;
  voted: number;
  total: number;
}

interface ChartViewProps {
  history: { ms: number; total: number }[];
  decisions: DecisionMarker[];
  /** Stepped invested-capital series: seed, then one riser per contribution. */
  investedSteps: InvestedStep[];
  overlays: OverlayResponse[];
  showDecisions: boolean;
  showHolds: boolean;
  showInvested: boolean;
  onInspect?: (consensusId: string) => void;
}

function ChartView({ history, decisions, investedSteps, overlays, showDecisions, showHolds, showInvested, onInspect }: ChartViewProps) {
  const theme = useTheme();
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  const W = 800;
  const H = 280;
  const padL = 50;
  const padR = 50;
  const padT = 16;
  const padB = 28;
  const innerW = W - padL - padR;
  const innerH = H - padT - padB;

  // Build per-overlay sorted arrays (epoch-ms, value).
  const overlaySeries = useMemo(() => {
    return overlays
      .filter(o => o.status === 'READY' && o.dataPoints.length > 0)
      .map(o => ({
        symbol: o.symbol,
        color: getOverlayColor(o.symbol),
        points: o.dataPoints
          .map(p => ({ ms: new Date(p.timestamp).getTime(), v: p.portfolioValue }))
          .sort((a, b) => a.ms - b.ms),
      }));
  }, [overlays]);

  if (history.length < 2) {
    return <StatusLine>Not enough portfolio data to render the chart yet.</StatusLine>;
  }

  const tMin = history[0].ms;
  const tMax = history[history.length - 1].ms;
  const tSpan = Math.max(1, tMax - tMin);

  // Invested capital in force at a given instant — a step function, never
  // interpolated: capital arrives in a lump, it does not ramp.
  const investedAtMs = (ms: number): number => investedAt(investedSteps, ms);

  let yMin = Infinity, yMax = -Infinity;
  for (const p of history) {
    if (p.total < yMin) yMin = p.total;
    if (p.total > yMax) yMax = p.total;
  }
  if (showInvested) {
    // The baseline must fit inside the plot or the "gap = P&L" reading breaks.
    for (const p of history) {
      const inv = investedAtMs(p.ms);
      if (inv < yMin) yMin = inv;
      if (inv > yMax) yMax = inv;
    }
  }
  for (const series of overlaySeries) {
    for (const p of series.points) {
      if (p.ms < tMin || p.ms > tMax) continue;
      if (p.v < yMin) yMin = p.v;
      if (p.v > yMax) yMax = p.v;
    }
  }
  if (!isFinite(yMin) || !isFinite(yMax) || yMax === yMin) {
    yMin = (yMin || 0) * 0.99;
    yMax = (yMax || 1) * 1.01;
  } else {
    const pad = (yMax - yMin) * 0.06;
    const floorAtZero = yMin >= 0;
    yMin -= pad;
    yMax += pad;
    // A portfolio value never goes below zero. Padding a series that starts at a
    // small seed and jumps after a contribution pushed the axis negative, so the
    // chart advertised a "$-0.2k" gridline it could never reach.
    if (floorAtZero && yMin < 0) yMin = 0;
  }

  const x = (ms: number) => padL + ((ms - tMin) / tSpan) * innerW;
  const y = (v: number) => padT + (1 - (v - yMin) / (yMax - yMin)) * innerH;

  const linePath = history.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.ms).toFixed(1)},${y(p.total).toFixed(1)}`).join(' ');
  const areaPath = `${linePath} L${x(tMax).toFixed(1)},${y(yMin).toFixed(1)} L${x(tMin).toFixed(1)},${y(yMin).toFixed(1)} Z`;

  // Baseline drawn as true steps: hold the level, then jump vertically on the
  // contribution instant. A sloped segment would imply the cash trickled in.
  const investedPoints: Array<{ ms: number; v: number }> = [];
  if (showInvested && history.length > 0) {
    investedPoints.push({ ms: tMin, v: investedAtMs(tMin) });
    for (const s of investedSteps) {
      if (s.ms <= tMin || s.ms > tMax) continue;
      investedPoints.push({ ms: s.ms, v: investedAtMs(s.ms - 1) });
      investedPoints.push({ ms: s.ms, v: s.total });
    }
    investedPoints.push({ ms: tMax, v: investedAtMs(tMax) });
  }
  const investedPath = investedPoints
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.ms).toFixed(1)},${y(p.v).toFixed(1)}`)
    .join(' ');
  // Closed band between the value line and the baseline — its area IS the P&L.
  const gapPath = investedPoints.length > 0
    ? `${linePath} ${investedPoints.slice().reverse().map(p => `L${x(p.ms).toFixed(1)},${y(p.v).toFixed(1)}`).join(' ')} Z`
    : '';
  const latestInvested = investedSteps.length > 0 ? investedSteps[investedSteps.length - 1].total : 0;
  const latestValue = history[history.length - 1].total;
  const gapAbs = latestValue - latestInvested;
  const gapIsLoss = gapAbs < 0;
  // Risers only — the opening seed is not a deposit and gets no dot.
  const visibleRisers = showInvested
    ? investedSteps.filter(s => s.added > 0 && s.ms >= tMin && s.ms <= tMax)
    : [];

  // Y ticks (5)
  const yTicks = Array.from({ length: 5 }).map((_, i) => yMin + ((yMax - yMin) * i) / 4);

  // X ticks: 5–6 evenly distributed by date
  const tickFmt = new Intl.DateTimeFormat('en-US', { month: 'short', day: '2-digit', timeZone: 'America/New_York' });
  const xTicks = Array.from({ length: 6 }).map((_, i) => {
    const ms = tMin + (tSpan * i) / 5;
    return { ms, label: tickFmt.format(new Date(ms)) };
  });

  // Linear-interpolate portfolio value at a given epoch-ms — matches the line
  // (which is drawn as straight segments between samples), so decision dots
  // land exactly on the line instead of forward-filled to the prior sample.
  const totalAt = (ms: number): number => {
    if (ms <= history[0].ms) return history[0].total;
    const last = history.length - 1;
    if (ms >= history[last].ms) return history[last].total;
    let lo = 0, hi = last, best = 0;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (history[mid].ms <= ms) { best = mid; lo = mid + 1; }
      else hi = mid - 1;
    }
    if (best >= last) return history[last].total;
    const a = history[best];
    const b = history[best + 1];
    const span = b.ms - a.ms;
    if (span <= 0) return a.total;
    const t = (ms - a.ms) / span;
    return a.total + t * (b.total - a.total);
  };

  const dateFmt = new Intl.DateTimeFormat('en-US', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', timeZone: 'America/New_York',
  });

  // Build overlay paths
  const overlayPaths = overlaySeries.map(series => {
    const pts = series.points.filter(p => p.ms >= tMin && p.ms <= tMax);
    if (pts.length < 2) return null;
    const d = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.ms).toFixed(1)},${y(p.v).toFixed(1)}`).join(' ');
    return { ...series, d };
  }).filter(Boolean) as { symbol: string; color: string; d: string }[];

  const visibleDecisions = showDecisions
    ? decisions.filter(d => {
        if (d.ms < tMin || d.ms > tMax) return false;
        // HOLDs are noisy on charts — they sit on the line without an action
        // to show. Hide them unless the user opts in via the Holds toggle.
        if (!showHolds && d.action.toUpperCase() === 'HOLD') return false;
        return true;
      })
    : [];
  const hover = hoverIdx != null ? visibleDecisions[hoverIdx] : null;

  return (
    <ChartHost>
      {/*
        height: 'auto' lets the SVG keep its intrinsic 800×260 viewBox aspect
        ratio. Hardcoding height stretches the y-axis independently of x and
        squishes the decision dots into ovals (Image #5 in design feedback).
      */}
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{ width: '100%', height: 'auto', display: 'block' }} role="img" aria-label="Portfolio value over time">
        {/* gridlines */}
        {yTicks.map((t, i) => (
          <line key={'gy' + i}
            x1={padL} x2={W - padR}
            y1={y(t)} y2={y(t)}
            stroke={theme.colors.chart.grid}
            strokeWidth={0.5}
            strokeDasharray="2 4"
          />
        ))}

        {/* profit/loss band between value and invested capital (behind the lines) */}
        {showInvested && gapPath && (
          <path
            d={gapPath}
            fill={gapIsLoss ? 'rgba(255, 51, 102, 0.14)' : 'rgba(6, 255, 165, 0.12)'}
          />
        )}

        {/* overlay (B&H) lines */}
        {overlayPaths.map((s, i) => (
          <path key={'ov' + i} d={s.d} fill="none" stroke={s.color}
            strokeWidth={1.4} strokeDasharray="6 4" opacity={0.85} />
        ))}

        {/* portfolio area + line */}
        <defs>
          <linearGradient id="ds-pf-fill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor={theme.colors.brand} stopOpacity={0.18} />
            <stop offset="100%" stopColor={theme.colors.brand} stopOpacity={0} />
          </linearGradient>
        </defs>
        <path d={areaPath} fill="url(#ds-pf-fill)" />
        {showInvested && investedPath && (
          <path d={investedPath} fill="none" stroke={theme.colors.info}
            strokeWidth={1.4} strokeDasharray="6 4" opacity={0.9} />
        )}
        <path d={linePath} fill="none" stroke={theme.colors.brand} strokeWidth={1.6} />
        {visibleRisers.map((s, i) => (
          <g key={'riser' + i}>
            <circle cx={x(s.ms)} cy={y(s.total)} r={2.6}
              fill={theme.colors.info} stroke={theme.colors.background} strokeWidth={1.4} />
            {visibleRisers.length <= 3 && (
              <text x={x(s.ms) + 7} y={y(s.total) - 7} fontSize={10}
                fontFamily="var(--font-mono)" fill={theme.colors.info}>
                +{compactMoneyFmt.format(s.added)}
              </text>
            )}
          </g>
        ))}

        {/* decision dots — color-only, on the line */}
        {visibleDecisions.map((m, i) => {
          const color = decisionColor(m.action, theme);
          const cx = x(m.ms);
          const cy = y(totalAt(m.ms));
          const focused = hoverIdx === i;
          const inspectable = !!onInspect && !!m.id;
          return (
            <g key={'dm' + i} style={{ cursor: inspectable ? 'pointer' : 'default' }}
              onMouseEnter={() => setHoverIdx(i)}
              onMouseLeave={() => setHoverIdx(prev => (prev === i ? null : prev))}
              onClick={inspectable ? (e) => { e.stopPropagation(); onInspect!(m.id); } : undefined}
              role={inspectable ? 'button' : undefined}
              aria-label={inspectable ? `Inspect ${m.action} decision` : undefined}>
              {focused && (
                <>
                  <line x1={cx} x2={cx} y1={padT} y2={H - padB}
                    stroke={theme.colors.info} strokeWidth={0.8} strokeDasharray="3 3" />
                  <circle cx={cx} cy={cy} r={9} fill="none" stroke={color} strokeWidth={1} opacity={0.4} />
                </>
              )}
              <circle cx={cx} cy={cy} r={focused ? 4.5 : 3.2}
                fill={color} stroke={theme.colors.background} strokeWidth={1.5} />
              {/* invisible larger hit target */}
              <circle cx={cx} cy={cy} r={12} fill="transparent" />
            </g>
          );
        })}

        {/* y-axis labels */}
        {yTicks.map((t, i) => (
          <text key={'yl' + i} x={padL - 8} y={y(t) + 3}
            fontSize={9.5} fontFamily="var(--font-mono)" fill={theme.colors.fgDimmed} textAnchor="end">
            ${(t / 1000).toFixed(1)}k
          </text>
        ))}

        {/* x-axis labels */}
        {xTicks.map((tk, i) => (
          <text key={'xl' + i} x={x(tk.ms)} y={H - 8}
            fontSize={10} fontFamily="var(--font-mono)" fill={theme.colors.fgDimmed}
            textAnchor={i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'}>
            {tk.label}
          </text>
        ))}

        {/* "now" marker — brand dot at last point */}
        <line x1={x(tMax)} x2={x(tMax)} y1={padT} y2={H - padB}
          stroke={theme.colors.brand} strokeWidth={0.5} opacity={0.4} />
        <circle cx={x(tMax)} cy={y(history[history.length - 1].total)} r={3.5} fill={theme.colors.brand} />
        <circle cx={x(tMax)} cy={y(history[history.length - 1].total)} r={7} fill={theme.colors.brand} opacity={0.2} />
      </svg>

      {hover && (() => {
        const cxPx = x(hover.ms);
        const cyPx = y(totalAt(hover.ms));
        // Convert SVG units to host-relative px (svg is responsive — use percent of container).
        // We render the tip via percent-based positioning relative to the host:
        const leftPct = (cxPx / W) * 100;
        const topPct = (cyPx / H) * 100;
        const anchorRight = leftPct > 60;
        return (
          <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
            <div style={{
              position: 'absolute',
              left: `${leftPct}%`,
              top: `${topPct}%`,
              transform: anchorRight ? 'translate(calc(-100% - 12px), -50%)' : 'translate(12px, -50%)',
            }}>
              <HoverTip
                action={hover.action}
                date={dateFmt.format(new Date(hover.ms))}
                voted={hover.voted}
                total={hover.total}
                size={hover.size}
                price={hover.price}
                portfolioValue={totalAt(hover.ms)}
              />
            </div>
          </div>
        );
      })()}

      {showInvested && (
        <ChartLegend>
          <span className="k"><span className="sw value" />Portfolio value</span>
          <span className="k"><span className="sw step" />Invested capital — each step is a deposit</span>
          <span className="k">
            <span className={`sw ${gapIsLoss ? 'gapneg' : 'gappos'}`} />
            gap = {gapIsLoss ? 'loss' : 'profit'} {numberFmt.format(Math.abs(gapAbs))}
          </span>
        </ChartLegend>
      )}
    </ChartHost>
  );
}

// ============================================================================
// PANEL — wires real data to ChartView; restores B&H overlay picker + toggles.
// ============================================================================

interface ChartPanelProps {
  onInspect?: (consensusId: string) => void;
}

export function ChartPanel({ onInspect }: ChartPanelProps = {}) {
  const { portfolioData, simulation, consensusInfo, executedConsensusDecisions, configuredTickers } = useSimulationData();
  const params = useParams();
  const simulationId = (params?.id as string | undefined) ?? '';
  // Pull the wrapped consensus+agents feed for vote bars in the hover tip.
  // (consensusHistory in context is the lightweight ConsensusDecision[] without agents.)
  // Endpoint caps `limit` at 100.
  const { data: consensusWithAgents } = useConsensusWithAgents(simulationId, undefined, 100);

  const sim = simulation as unknown as SimulationResponse | null;

  const [tf, setTf] = useState<TimeFrame>('All');
  const [showDecisions, setShowDecisions] = useState(true);
  // HOLDs hidden by default — opt-in via the Holds toggle.
  const [showHolds, setShowHolds] = useState(false);
  const [showInvested, setShowInvested] = useState(true);
  const [activeOverlays, setActiveOverlays] = useState<string[]>([]);

  const toggleOverlay = useCallback((sym: string) => {
    setActiveOverlays(prev => prev.includes(sym) ? prev.filter(s => s !== sym) : [...prev, sym]);
  }, []);

  const isCompleted = sim?.status === 'COMPLETED';
  const { data: overlayData } = useSimulationOverlays(sim?.id, activeOverlays, isCompleted);

  // Build the history array (epoch-ms + total) — single source of truth for time math.
  const fullHistory = useMemo(() => {
    if (!portfolioData?.valueHistory?.length) return [];
    return portfolioData.valueHistory
      .filter(p => p.fullTimestamp != null)
      .map(p => ({ ms: (p.fullTimestamp as Date).getTime(), total: p.total }))
      .sort((a, b) => a.ms - b.ms);
  }, [portfolioData]);

  // Filter to selected timeframe (anchored to most-recent point).
  const history = useMemo(() => {
    const win = timeframeMs(tf);
    if (win == null || fullHistory.length === 0) return fullHistory;
    const cutoff = fullHistory[fullHistory.length - 1].ms - win;
    const filtered = fullHistory.filter(p => p.ms >= cutoff);
    return filtered.length >= 2 ? filtered : fullHistory;
  }, [fullHistory, tf]);

  // Index executed decisions by ISO timestamp for quick size@price lookup.
  const execIndex = useMemo(() => {
    const map = new Map<string, { price: number | null; quantity: number | null }>();
    (executedConsensusDecisions ?? []).forEach((d: any) => {
      map.set(d.decisionTimestamp, {
        price: d.executionPrice ?? null,
        quantity: d.quantity ?? null,
      });
    });
    return map;
  }, [executedConsensusDecisions]);

  // Index consensus-with-agents by ISO timestamp for vote counts.
  const voteIndex = useMemo(() => {
    const map = new Map<string, { voted: number; total: number }>();
    const entries = (consensusWithAgents ?? []) as ConsensusWithAgentsResponse[];
    entries.forEach(entry => {
      const cd = entry?.consensusDecision;
      if (!cd?.decisionTimestamp) return;
      const finalLabel = parseActionLabel(cd.finalAction);
      const baseAgents = ['TECHNICAL', 'FUNDAMENTAL', 'SENTIMENT'] as const;
      const agents: LLMDecision[] = entry.agentDecisions ?? [];
      let voted = 0;
      let total = 0;
      baseAgents.forEach(type => {
        const a = agents.find(x => x.agentType?.toUpperCase() === type);
        if (a) {
          total += 1;
          if (finalLabel !== '' && parseActionLabel(a.suggestedAction) === finalLabel) voted += 1;
        }
      });
      // Fall back to a sensible default when agent data is missing.
      if (total === 0) total = 3;
      map.set(cd.decisionTimestamp, { voted, total });
    });
    return map;
  }, [consensusWithAgents]);

  const decisions: DecisionMarker[] = useMemo(() => {
    return consensusInfo.markers.map(m => {
      const iso = m.timestamp.toISOString();
      const exec = execIndex.get(iso);
      const vote = voteIndex.get(iso) ?? { voted: 0, total: 3 };
      const isTrade = m.action === 'BUY' || m.action === 'SELL';
      const qty = exec?.quantity;
      const price = exec?.price ?? null;
      const size = isTrade && qty != null ? `${qty.toLocaleString('en-US', { maximumFractionDigits: 4 })} ${m.symbol || ''}`.trim() : null;
      return {
        id: m.id,
        ms: m.timestamp.getTime(),
        action: m.action,
        size,
        price,
        voted: vote.voted,
        total: vote.total,
      };
    });
  }, [consensusInfo.markers, execIndex, voteIndex]);

  // Invested capital over time: the seed at the simulation's start, stepped up
  // by each contribution when it landed. Derived from data already on the
  // response — `initialCash` plus the contribution list — so the baseline is
  // measured, not modelled.
  const investedSteps = useMemo(() => {
    if (!sim) return [];
    const s = sim as unknown as { initialCash?: number; initialCapital?: number; budgetUsd?: number };
    const seed = s.initialCash ?? s.initialCapital ?? s.budgetUsd ?? 0;
    const startMs = new Date(sim.startDate).getTime();
    const contributions: ContributionResponse[] = sim.contributions ?? [];
    return buildInvestedSteps(seed, contributions, Number.isNaN(startMs) ? 0 : startMs);
  }, [sim]);

  const hasContributions = (sim?.contributions?.length ?? 0) > 0;

  const summary = useMemo(() => {
    if (!portfolioData || !sim) return null;
    // Backend serialises starting capital as `initialCash`.
    const s = sim as unknown as {
      initialCash?: number; initialCapital?: number; budgetUsd?: number; totalInvested?: number;
    };
    const starting = s.initialCash ?? s.initialCapital ?? s.budgetUsd ?? 0;
    // Measure against total invested (seed + Σ contributions), matching the header
    // KPI. Measuring against the seed alone counts every DCA top-up as profit: a
    // $500 seed topped up to $12,000 reported "+2210%" beside the header's −3.73%.
    const invested = s.totalInvested ?? starting;
    const current = portfolioData.totalValue;
    const pct = portfolioReturnPct(invested, current);
    const basis = invested > starting
      ? `${numberFmt.format(starting)} seed +${numberFmt.format(invested - starting)} added`
      : numberFmt.format(starting);
    return `${basis} → ${numberFmt.format(current)}  ·  ${pctFmt(pct)}`;
  }, [portfolioData, sim]);

  // Filter overlay chips to what's relevant for this strategy's asset class.
  // Stock-only sims hide BTC/ETH; crypto-only sims hide SPY/QQQ/VTI.
  const STOCK_OVERLAYS = new Set(['SPY', 'QQQ', 'VTI']);
  const CRYPTO_OVERLAYS = new Set(['BTC', 'ETH']);
  const hasStock = configuredTickers.stock.length > 0;
  const hasCrypto = configuredTickers.crypto.length > 0;
  const relevantOverlays = POPULAR_OVERLAYS.filter(o => {
    if (hasStock && hasCrypto) return true;
    if (hasStock) return STOCK_OVERLAYS.has(o.symbol);
    if (hasCrypto) return CRYPTO_OVERLAYS.has(o.symbol);
    return true;
  });

  const theme = useTheme();
  const headerActions = (
    <HeaderActions>
      {TIMEFRAMES.map(t => (
        <TfButton key={t} type="button" $on={tf === t} onClick={() => setTf(t)}>{t}</TfButton>
      ))}
      <span style={{ width: 4 }} />
      {relevantOverlays.map(o => {
        const on = activeOverlays.includes(o.symbol);
        const color = getOverlayColor(o.symbol);
        return (
          <Toggle key={o.symbol} type="button" $on={on} $color={color} onClick={() => toggleOverlay(o.symbol)} title={`${o.label} buy & hold`}>
            <span className="dash" />
            {o.symbol}
          </Toggle>
        );
      })}
      {hasContributions && (
        <Toggle type="button" $on={showInvested} $color={theme.colors.info}
          onClick={() => setShowInvested(v => !v)}
          title="Stepped invested-capital baseline — each riser is a deposit, and the gap to the value line is the profit or loss">
          <span className="sw" />
          Invested
        </Toggle>
      )}
      <Toggle type="button" $on={showDecisions} $color={theme.colors.brand} onClick={() => setShowDecisions(v => !v)}>
        <span className="sw" />
        Decisions
      </Toggle>
      <Toggle type="button" $on={showHolds} $color={theme.colors.fgDimmed} onClick={() => setShowHolds(v => !v)} title="Show HOLD decisions on the chart">
        <span className="sw" />
        Holds
      </Toggle>
    </HeaderActions>
  );

  return (
    <Panel
      title="Portfolio value"
      count={summary ? <Subtitle>{summary}</Subtitle> : null}
      headerActions={headerActions}
      noBodyPadding
    >
      {fullHistory.length < 2 ? (
        <StatusLine>No portfolio data yet — the chart appears once snapshots arrive.</StatusLine>
      ) : (
        <ChartView
          history={history}
          decisions={decisions}
          investedSteps={investedSteps}
          overlays={overlayData ?? []}
          showDecisions={showDecisions}
          showHolds={showHolds}
          showInvested={showInvested && hasContributions}
          onInspect={onInspect}
        />
      )}
    </Panel>
  );
}
