'use client';

import styled from 'styled-components';
import { Panel, Tag } from '@/app/components/shared/v2';
import { pillBase } from '@/app/styles/labels';
import type { StrategyPerformanceResponse } from '@/app/lib/types/api';

interface Props {
  performance: StrategyPerformanceResponse | undefined;
  liveCount: number;
  completedCount: number;
  failedCount: number;
  publicVisible: boolean;
  canTogglePublish: boolean;
  onTogglePublish?: () => void;
  togglePending?: boolean;
}

type Status = 'live' | 'backtest' | 'unused';

function statusOf(live: number, completed: number): Status {
  if (live > 0) return 'live';
  if (completed > 0) return 'backtest';
  return 'unused';
}

function fmtPct(v: number, digits = 2): string {
  return `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(digits)}%`;
}

/**
 * Right-rail "At a glance" panel. Replaces the wide 6-cell KPI strip from the
 * v1 header — same metrics, denser layout, leaves the top of the page for the
 * description paragraph. All values shown here come from
 * `useStrategyPerformance()` (real), except the 90-day spark and last-decision
 * timestamp which are flagged Δ derived and rendered as placeholders until
 * the list endpoint exposes them.
 */
export function AtAGlancePanel({
  performance, liveCount, completedCount, failedCount,
  publicVisible, canTogglePublish, onTogglePublish, togglePending,
}: Props) {
  const status = statusOf(liveCount, completedCount);
  const total = performance?.totalSimulations ?? 0;
  const hasPerf = performance && performance.completedSimulations > 0;

  return (
    <Panel
      title="At a glance"
      count={total > 0 ? `across ${total} sim${total === 1 ? '' : 's'}` : 'no sims yet'}
      noBodyPadding
    >
      <Rows>
        <Row>
          <Label>Status</Label>
          <StatusBadge $status={status}>
            <StatusDot $status={status} />
            {status === 'live' ? 'Live' : status === 'backtest' ? 'Backtest only' : 'Unused'}
          </StatusBadge>
        </Row>
        <Row>
          <Label>Simulations</Label>
          <Value>
            <Mono>{total}</Mono>
            {total > 0 && (
              <Aux>
                ({liveCount} live · {completedCount} done
                {failedCount > 0 ? ` · ${failedCount} failed` : ''})
              </Aux>
            )}
          </Value>
        </Row>
        <Row>
          <Label>Avg return</Label>
          <Value>
            {hasPerf
              ? <MonoTone $tone={performance!.avgReturn >= 0 ? 'pos' : 'neg'}>{fmtPct(performance!.avgReturn)}</MonoTone>
              : <Dim>—</Dim>}
          </Value>
        </Row>
        <Row>
          <Label>Win rate</Label>
          <Value>
            {hasPerf
              ? (
                <>
                  <MonoTone $tone={performance!.winRate >= 50 ? 'pos' : 'neg'}>
                    {performance!.winRate.toFixed(0)}%
                  </MonoTone>
                  <Aux>
                    {Math.round((performance!.winRate / 100) * performance!.completedSimulations)} of {performance!.completedSimulations}
                  </Aux>
                </>
              )
              : <Dim>—</Dim>}
          </Value>
        </Row>
        <Row>
          <Label>Sharpe (avg)</Label>
          <Value>
            {/* null means the ratio could not be computed (too few daily snapshots), which is
                not the same as 0.00 — rendering a fabricated 0 read as a measured risk-neutral
                result. The em-dash already means "not available" on this panel. */}
            {hasPerf && performance!.avgSharpeRatio !== null
              ? <MonoSharpe $v={performance!.avgSharpeRatio}>{performance!.avgSharpeRatio.toFixed(2)}</MonoSharpe>
              : <Dim>—</Dim>}
          </Value>
        </Row>
        <Row>
          <Label>Best run</Label>
          <Value>
            {hasPerf
              ? <MonoTone $tone={performance!.bestReturn >= 0 ? 'pos' : 'neg'}>{fmtPct(performance!.bestReturn)}</MonoTone>
              : <Dim>—</Dim>}
          </Value>
        </Row>
        <Row>
          <Label>Worst run</Label>
          <Value>
            {hasPerf
              ? <MonoTone $tone={performance!.worstReturn >= 0 ? 'pos' : 'neg'}>{fmtPct(performance!.worstReturn)}</MonoTone>
              : <Dim>—</Dim>}
          </Value>
        </Row>
        <Row>
          <Label>
            Last decision<DeltaMark>Δ</DeltaMark>
          </Label>
          <Value>
            <Dim>—</Dim>
          </Value>
        </Row>
        <Row>
          <Label>Visibility</Label>
          <Value>
            {publicVisible
              ? <Tag hue="cyan">Published</Tag>
              : <PrivateText>private</PrivateText>}
            {canTogglePublish && onTogglePublish && (
              <ToggleIcon
                type="button"
                onClick={onTogglePublish}
                disabled={togglePending}
                title={publicVisible ? 'Unpublish from library' : 'Publish to library'}
                aria-label={publicVisible ? 'Unpublish' : 'Publish'}
              >
                {publicVisible ? '↧' : '↥'}
              </ToggleIcon>
            )}
          </Value>
        </Row>
        <SparkRow>
          <SparkHead>
            90-day spark<DeltaMark>Δ</DeltaMark>
          </SparkHead>
          <SparkPlaceholder aria-label="spark pending list-endpoint aggregation" />
          <SparkFoot>
            <span>90d ago</span><span>today</span>
          </SparkFoot>
        </SparkRow>
      </Rows>
    </Panel>
  );
}

// ─── styled ─────────────────────────────────────────────────────────────────

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const Row = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-size: 13px;

  &:last-of-type { border-bottom: 1px solid ${p => p.theme.colors.borderSubtle}; }
`;

const Label = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  color: ${p => p.theme.colors.fgMuted};
  display: inline-flex;
  align-items: center;
`;

const DeltaMark = styled.sup`
  color: ${p => p.theme.colors.terminalAmber};
  font-size: 8px;
  margin-left: 4px;
`;

const Value = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
`;

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  color: ${p => p.theme.colors.fgStrong};
`;

const Aux = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Dim = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.fgDimmed};
`;

const MonoTone = styled.span<{ $tone: 'pos' | 'neg' }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  color: ${p => p.$tone === 'pos' ? p.theme.colors.positive : p.theme.colors.negative};
`;

const MonoSharpe = styled.span<{ $v: number }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  color: ${p =>
    p.$v >= 1 ? p.theme.colors.positive :
    p.$v >= 0.5 ? p.theme.colors.terminalAmber :
    p.$v > 0 ? p.theme.colors.fgMuted :
    p.theme.colors.negative};
`;

const StatusDot = styled.span<{ $status: Status }>`
  width: 7px;
  height: 7px;
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
`;

const StatusBadge = styled.span<{ $status: Status }>`
  ${pillBase}
  color: ${p =>
    p.$status === 'live' ? p.theme.colors.brand :
    p.$status === 'backtest' ? p.theme.colors.fgMuted :
    p.theme.colors.fgDimmed};
`;

const PrivateText = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const ToggleIcon = styled.button`
  width: 22px;
  height: 22px;
  border-radius: 2px;
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  color: ${p => p.theme.colors.fgMuted};
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color 120ms ease, border-color 120ms ease;

  &:hover { color: ${p => p.theme.colors.fgStrong}; border-color: ${p => p.theme.colors.fgMuted}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const SparkRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px 14px;
`;

const SparkHead = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  display: inline-flex;
  align-items: center;
`;

const SparkPlaceholder = styled.span`
  display: block;
  width: 100%;
  height: 32px;
  background: repeating-linear-gradient(
    to right,
    ${p => p.theme.colors.fgSubtle} 0 4px,
    transparent 4px 8px
  );
  background-position: 0 center;
  background-size: 100% 2px;
  background-repeat: no-repeat;
`;

const SparkFoot = styled.div`
  display: flex;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;
