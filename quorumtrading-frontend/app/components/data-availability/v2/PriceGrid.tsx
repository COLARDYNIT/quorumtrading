'use client';

import styled from 'styled-components';
import { format, parseISO } from 'date-fns';
import type { DataGap, GapProgressDTO, DailyCoverage } from '@/app/lib/types/dataAvailability';
import {
  buildDays,
  priceCellState,
  presenceState,
  cellWord,
  presWord,
  isCrypto,
  symbolNeedsKey,
  type AssetClassUsability,
  type DayCell,
  type PriceCellState,
  type PresenceCellState,
} from './coverageDerive';

interface PriceGridProps {
  symbols: string[];
  dataTypes: string[];
  selectedMonth: Date;
  daily: Map<string, DailyCoverage>;
  gaps: DataGap[];
  /** FAILED gaps from the live/most-recent job, drives `failed` cells. */
  failedGaps: GapProgressDTO[];
  /** `/horizon` map (symbol → earliest ISO date). Drives pre-horizon cells + row marker. */
  horizon?: Record<string, string>;
  /** Asset-class usability from `/market-data-status` — drives `nokey` cells. */
  usable?: AssetClassUsability;
  /** Symbols just filled by the verify-loop — get the just-filled halo. */
  justFilledSymbols?: string[];
  loading?: boolean;
}

const PENDING_C = '#9d8cff';
const HORIZON_LINE = 'rgba(124,108,200,0.22)';

const HEAD_W = 132;

const Grid = styled.div`
  width: 100%;
  overflow-x: auto;
`;

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
`;

const ToolbarLeft = styled.div`
  display: flex;
  align-items: baseline;
  gap: 10px;
  span.title {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 13px;
    font-weight: 600;
    color: ${p => p.theme.colors.fgStrong};
  }
`;

const MutedNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Legend = styled.div`
  display: inline-flex;
  gap: 14px;
  flex-wrap: wrap;
`;

const Leg = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  color: ${p => p.theme.colors.fgMuted};
  .sw {
    width: 11px;
    height: 11px;
    border-radius: 2px;
    flex-shrink: 0;
  }
  .sw.present {
    background: rgba(6, 255, 165, 0.42);
  }
  .sw.action {
    background: #ffb000;
  }
  .sw.closed {
    background: #161c26;
    background-image: repeating-linear-gradient(45deg, transparent 0 3px, rgba(255, 255, 255, 0.04) 3px 4px);
  }
  .sw.failed {
    background: #ff3366;
  }
  .sw.horizon {
    background: #17152a;
    background-image: repeating-linear-gradient(45deg, transparent 0 2px, ${HORIZON_LINE} 2px 3px),
      repeating-linear-gradient(-45deg, transparent 0 2px, ${HORIZON_LINE} 2px 3px);
  }
  .sw.nokey {
    background: #1d1712;
    background-image: repeating-linear-gradient(45deg, transparent 0 2px, rgba(255, 176, 0, 0.35) 2px 3px);
    border: 1px dashed rgba(255, 176, 0, 0.55);
  }
`;

const Table = styled.div`
  display: grid;
  gap: 3px;
  min-width: 560px;
`;

const RowHead = styled.div<{ $locked?: boolean }>`
  display: flex;
  flex-direction: column;
  gap: 1px;
  justify-content: center;
  padding-right: 12px;
  min-width: 0;
  .sym {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 13px;
    font-weight: 600;
    color: ${p => (p.$locked ? p.theme.colors.fgMuted : p.theme.colors.fgStrong)};
  }
  .mkt {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 10px;
    color: ${p => p.theme.colors.fgDimmed};
    letter-spacing: 0.04em;
  }
  .mkt.cls-eq {
    color: ${p => p.theme.colors.info};
  }
  .mkt.cls-cr {
    color: #06ffa5;
  }
  .horizon-mark {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9px;
    color: ${PENDING_C};
    margin-top: 1px;
  }
  .pending-dash {
    color: ${p => p.theme.colors.fgSubtle};
    font-family: ${p => p.theme.typography.fontFamily.mono};
  }
`;

const ColHead = styled.div<{ $we?: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  .dnum {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    color: ${p => (p.$we ? p.theme.colors.fgSubtle : p.theme.colors.fgDimmed)};
  }
`;

const Cell = styled.div<{ $st: PriceCellState; $jf?: boolean }>`
  aspect-ratio: 1;
  border-radius: 2px;
  position: relative;
  cursor: default;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  transition: ${p => p.theme.transition.fast};

  ${p => {
    switch (p.$st) {
      case 'present':
        return 'background: rgba(6,255,165,0.42); border: 1px solid rgba(6,255,165,0.16);';
      case 'partial':
        return 'background: rgba(6,255,165,0.22);';
      case 'action':
        return 'background: #ffb000; box-shadow: 0 0 0 1px rgba(255,176,0,0.4), 0 0 8px rgba(255,176,0,0.35);';
      case 'closed':
        return 'background: #161c26; background-image: repeating-linear-gradient(45deg, transparent 0 3px, rgba(255,255,255,0.05) 3px 4px);';
      case 'future':
        return 'background: #11151d; background-image: repeating-linear-gradient(45deg, transparent 0 3px, rgba(0,217,255,0.06) 3px 4px);';
      case 'failed':
        return 'background: #ff3366; box-shadow: 0 0 8px rgba(255,51,102,0.35);';
      case 'horizon':
        return `background: #17152a; background-image: repeating-linear-gradient(45deg, transparent 0 3px, ${HORIZON_LINE} 3px 4px), repeating-linear-gradient(-45deg, transparent 0 3px, ${HORIZON_LINE} 3px 4px);`;
      case 'nokey':
        return 'background: #1d1712; background-image: repeating-linear-gradient(45deg, transparent 0 3px, rgba(255,176,0,0.35) 3px 4px); border: 1px dashed rgba(255,176,0,0.55);';
      default:
        return '';
    }
  }}

  ${p =>
    p.$st === 'failed' &&
    `&::after { content: "\\00d7"; font-family: ${p.theme.typography.fontFamily.mono}; font-size: 11px; font-weight: 700; color: ${p.theme.colors.background}; }`}

  ${p => p.$jf && 'box-shadow: 0 0 0 2px rgba(6,255,165,0.6), 0 0 10px rgba(6,255,165,0.4); z-index: 2;'}

  &:hover {
    outline: 1px solid ${p => p.theme.colors.fgMuted};
    outline-offset: 1px;
    z-index: 3;
  }
`;

// ── news / social strip ──────────────────────────────────────────────────
const Strip = styled.div`
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const StripHead = styled.div`
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
  span.title {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 13px;
    font-weight: 600;
    color: ${p => p.theme.colors.fgStrong};
  }
`;

const NsRow = styled.div`
  display: grid;
  gap: 3px;
  align-items: center;
  min-width: 560px;
  & + & {
    margin-top: 3px;
  }
`;

const NsHead = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-right: 12px;
  .nm {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    font-weight: 600;
    color: ${p => p.theme.colors.fgStrong};
  }
  .scope {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    color: ${p => p.theme.colors.fgDimmed};
    letter-spacing: 0.04em;
  }
`;

const NsTick = styled.div<{ $st: PresenceCellState }>`
  height: 16px;
  border-radius: 2px;
  position: relative;
  ${p => {
    switch (p.$st) {
      case 'present':
        return 'background: rgba(6,255,165,0.42);';
      case 'action':
        return 'background: #ffb000; box-shadow: 0 0 8px rgba(255,176,0,0.35);';
      case 'nosrc':
        return 'background: #161c26; background-image: repeating-linear-gradient(45deg, transparent 0 3px, rgba(255,176,0,0.10) 3px 4px);';
      case 'future':
        return 'background: #11151d; background-image: repeating-linear-gradient(45deg, transparent 0 3px, rgba(0,217,255,0.06) 3px 4px);';
      default:
        return '';
    }
  }}
`;

export function PriceGrid({
  symbols,
  dataTypes,
  selectedMonth,
  daily,
  gaps,
  failedGaps,
  horizon,
  usable,
  justFilledSymbols,
  loading,
}: PriceGridProps) {
  const days: DayCell[] = buildDays(selectedMonth.getFullYear(), selectedMonth.getMonth());
  const n = days.length;
  const tpl = `${HEAD_W}px repeat(${n}, minmax(0, 1fr))`;

  const showPrice = dataTypes.includes('PRICE');
  const showNews = dataTypes.includes('NEWS');
  const showSocial = dataTypes.includes('SOCIAL');
  const justFilled = new Set(justFilledSymbols ?? []);

  const monthLabel = selectedMonth.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

  // Only advertise the "needs API key" legend when a displayed symbol can hit it.
  const anyNeedsKey = symbols.some(sym => symbolNeedsKey(sym, usable));

  const newsKinds = ([] as Array<{ kind: 'NEWS' | 'SOCIAL'; nm: string }>).concat(
    showNews ? [{ kind: 'NEWS', nm: 'NEWS' }] : [],
    showSocial ? [{ kind: 'SOCIAL', nm: 'SOCIAL' }] : []
  );

  return (
    <Grid>
      {showPrice && (
        <>
          <Toolbar>
            <ToolbarLeft>
              <span className="title">Price completeness</span>
              <MutedNote>
                per symbol · {monthLabel}
                {loading ? ' · loading…' : ''}
              </MutedNote>
            </ToolbarLeft>
            <Legend>
              <Leg>
                <span className="sw present" />
                present
              </Leg>
              <Leg>
                <span className="sw action" />
                missing · fixable
              </Leg>
              <Leg>
                <span className="sw closed" />
                market closed
              </Leg>
              <Leg>
                <span className="sw failed" />
                failed
              </Leg>
              {anyNeedsKey && (
                <Leg>
                  <span className="sw nokey" />
                  needs API key
                </Leg>
              )}
            </Legend>
          </Toolbar>

          <Table style={{ gridTemplateColumns: tpl }}>
            <RowHead style={{ alignSelf: 'end', paddingBottom: 4 }}>
              <span className="mkt" style={{ letterSpacing: '0.08em' }}>
                SYMBOL ╲ DAY
              </span>
            </RowHead>
            {days.map(d => (
              <ColHead key={`h${d.day}`} $we={d.weekend}>
                <span className="dnum">{d.day}</span>
              </ColHead>
            ))}
            {symbols.map(sym => {
              const crypto = isCrypto(sym);
              return (
                <RowHeadFragment
                  key={sym}
                  sym={sym}
                  crypto={crypto}
                  days={days}
                  daily={daily}
                  gaps={gaps}
                  failedGaps={failedGaps}
                  horizon={horizon}
                  usable={usable}
                  jf={justFilled.has(sym)}
                />
              );
            })}
          </Table>
        </>
      )}

      {newsKinds.length > 0 && (
        <Strip>
          <StripHead>
            <span className="title">News &amp; social presence</span>
            <MutedNote>market-wide — a single row exists, or it doesn&apos;t</MutedNote>
          </StripHead>
          <div>
            {newsKinds.map(k => (
              <NsRow key={k.kind} style={{ gridTemplateColumns: tpl }}>
                <NsHead>
                  <span className="nm">{k.nm}</span>
                  <span className="scope">market-wide · binary</span>
                </NsHead>
                {days.map(d => {
                  const st = presenceState(k.kind, d, { daily, gaps });
                  return (
                    <NsTick
                      key={`${k.kind}${d.day}`}
                      $st={st}
                      title={`${k.nm} · ${d.iso} · ${presWord(st)}`}
                    />
                  );
                })}
              </NsRow>
            ))}
          </div>
          <Legend style={{ marginTop: 10 }}>
            <Leg>
              <span className="sw present" />
              present
            </Leg>
            <Leg>
              <span className="sw action" />
              absent · fixable (past)
            </Leg>
          </Legend>
        </Strip>
      )}
    </Grid>
  );
}

// fragment helper to keep the cell-mapping readable and typed
function RowHeadFragment({
  sym,
  crypto,
  days,
  daily,
  gaps,
  failedGaps,
  horizon,
  usable,
  jf,
}: {
  sym: string;
  crypto: boolean;
  days: DayCell[];
  daily: Map<string, DailyCoverage>;
  gaps: DataGap[];
  failedGaps: GapProgressDTO[];
  horizon?: Record<string, string>;
  usable?: AssetClassUsability;
  jf: boolean;
}) {
  const earliest = horizon?.[sym];
  const locked = !!earliest;
  const needsKey = symbolNeedsKey(sym, usable);
  return (
    <>
      <RowHead $locked={locked}>
        <span className="sym">{sym}</span>
        <span className={`mkt ${crypto ? 'cls-cr' : 'cls-eq'}`}>{crypto ? '24/7' : 'exchange'}</span>
        {needsKey ? (
          // Any cached "coverage from" date is untrustworthy without a usable
          // provider (it may be a floor poisoned by a keyless backfill) — say
          // the actual blocker instead.
          <span className="horizon-mark" style={{ color: '#ffb000' }}>
            needs data key
          </span>
        ) : (
          <span className="horizon-mark">
            coverage from{' '}
            {earliest ? (
              format(parseISO(earliest.slice(0, 10)), 'MMM yyyy')
            ) : (
              <span className="pending-dash">—</span>
            )}
          </span>
        )}
      </RowHead>
      {days.map(d => {
        const st = priceCellState(sym, d, { daily, gaps, failedGaps, horizon, usable });
        return (
          <Cell key={`${sym}${d.day}`} $st={st} $jf={jf && st === 'present'} title={`${sym} · ${d.iso} · ${cellWord(st)}`} />
        );
      })}
    </>
  );
}
