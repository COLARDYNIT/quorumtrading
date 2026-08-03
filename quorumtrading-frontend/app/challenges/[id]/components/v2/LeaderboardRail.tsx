'use client';

import { useRouter } from 'next/navigation';
import styled from 'styled-components';
import { Panel, Tag } from '@/app/components/shared/v2';
import { labelColor, labelCompact, pillBase, pulse, type LabelKind } from '@/app/styles/labels';
import type { ChallengeResponse, ChallengeEntryResponse, ChallengeChartData } from '@/app/lib/types/challenge';
import {
  countdownTo,
  entriesBeatingBaseline,
  fmtDate,
  fmtExecutionTime,
  fmtMoney,
  fmtPct,
  leaderboardSplit,
} from './helpers';

interface Props {
  ch: ChallengeResponse;
  chart: ChallengeChartData | null | undefined;
  yourSimIds: Set<string>;
  onJoin: () => void;
  /** Optional: hide the rail's primary CTA (e.g. when the host page already renders one). */
  hideCta?: boolean;
}

export function LeaderboardRail({ ch, chart, yourSimIds, onJoin, hideCta }: Props) {
  const split = leaderboardSplit(ch.entries, yourSimIds);
  return (
    <Rail>
      {chart && chart.buyAndHold && (
        <BaselineCard ch={ch} chart={chart} />
      )}
      <Panel
        title="Leaderboard"
        count={
          <>
            top {split.top.length} of {ch.participantCount}
            {split.you && <YouMeta> · you #{split.you.rank}</YouMeta>}
          </>
        }
        noBodyPadding
      >
        {ch.entries.length === 0 ? (
          <EmptyBody>
            <h4>The leaderboard fills as entries land.</h4>
            <p>
              Top 5 ranks · plus your own when you join.{' '}
              {ch.status === 'ACTIVE' && ch.acceptingEntries
                ? 'No fabricated ranks — be the first.'
                : 'No fabricated ranks.'}
            </p>
          </EmptyBody>
        ) : (
          <RowList>
            {split.top.map((entry) => (
              <LeaderboardRow key={entry.simulationId} entry={entry} you={false} />
            ))}
            {split.you && split.gap && (
              <GapRow>
                <span className="dots">• • •</span>
                <span className="lbl">{split.you.rank - split.top.length - 1} more · not in top 5</span>
              </GapRow>
            )}
            {split.you && (split.gap || !split.top.includes(split.you)) && (
              <LeaderboardRow entry={split.you} you />
            )}
          </RowList>
        )}
      </Panel>
      {!hideCta && <RailCTA ch={ch} you={split.you} onJoin={onJoin} />}
    </Rail>
  );
}

// ─── Baseline card ──────────────────────────────────────────────────────────

function BaselineCard({ ch, chart }: { ch: ChallengeResponse; chart: ChallengeChartData }) {
  const bh = chart.buyAndHold!;
  const beat = entriesBeatingBaseline(ch.entries, bh);
  return (
    <BaselineRoot>
      <BaselineHead>
        <span className="eye">◇ THE LINE TO BEAT</span>
        <span className="src">buy-and-hold {bh.ticker}</span>
      </BaselineHead>
      <BaselineNum>
        <span className={`pct ${bh.returnPercent >= 0 ? 'pos' : 'neg'}`}>
          {fmtPct(bh.returnPercent)}
        </span>
        <span className="val">
          {fmtMoney(bh.finalValue)} final · from {fmtMoney(ch.initialCash)} start
        </span>
      </BaselineNum>
      {beat != null && ch.entries.length > 0 && (
        <BaselineFoot>
          <strong>{beat}</strong>
          <span>
            {' '}of {ch.entries.length} shown {beat === 1 ? 'entry beats' : 'entries beat'} the baseline
          </span>
        </BaselineFoot>
      )}
    </BaselineRoot>
  );
}

// ─── Row ────────────────────────────────────────────────────────────────────

function LeaderboardRow({ entry, you }: { entry: ChallengeEntryResponse; you: boolean }) {
  const router = useRouter();
  const ret = entry.returnPercent != null ? parseFloat(entry.returnPercent) : null;
  const retNum = ret != null && Number.isFinite(ret) ? ret : null;
  const cost = entry.llmCostUsd;
  const exec = fmtExecutionTime(entry.executionTimeSeconds);

  return (
    <RowRoot
      $you={you}
      onClick={() => router.push(`/simulations/${entry.simulationId}`)}
      role="link"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          router.push(`/simulations/${entry.simulationId}`);
        }
      }}
    >
      <RowRank>#{entry.rank}</RowRank>
      <RowName>
        <span className="nm" title={entry.strategyName}>{entry.strategyName}</span>
        <span className="meta">
          <span className="owner">{entry.ownerDisplayName}</span>
          {exec && (
            <>
              <span className="sep">·</span>
              <span>{exec}</span>
            </>
          )}
          {cost && (
            <>
              <span className="sep">·</span>
              <span>${cost} ai</span>
            </>
          )}
        </span>
      </RowName>
      <RowNum>
        <span className={`ret ${retNum == null ? 'na' : retNum >= 0 ? 'pos' : 'neg'}`}>
          {retNum == null ? '—' : `${retNum >= 0 ? '+' : '−'}${Math.abs(retNum).toFixed(2)}%`}
        </span>
        <span className="val">{fmtMoney(entry.currentValue)}</span>
      </RowNum>
      <RowState>
        {entry.status === 'SIMULATING' && <StatePill $kind="simulating"><Dot $kind="simulating" />running</StatePill>}
        {entry.status === 'LIVE'       && <StatePill $kind="live"><Dot $kind="live" />live</StatePill>}
        {entry.status === 'COMPLETED'  && <StatePill $kind="done">done</StatePill>}
        {entry.status === 'FAILED'     && <StatePill $kind="failed">halted</StatePill>}
        {entry.status === 'PENDING'    && <StatePill $kind="pending">queued</StatePill>}
        {entry.status === 'WAITING_FOR_DATA' && <StatePill $kind="pending">waiting</StatePill>}
        {you && <Tag hue="brand" compact>you</Tag>}
      </RowState>
    </RowRoot>
  );
}

// ─── Rail CTA ───────────────────────────────────────────────────────────────

function RailCTA({
  ch, you, onJoin,
}: {
  ch: ChallengeResponse;
  you: ChallengeEntryResponse | null;
  onJoin: () => void;
}) {
  if (ch.status === 'ACTIVE' && ch.acceptingEntries) {
    if (you) {
      return (
        <CtaRoot>
          <div className="copy">
            <strong>You&apos;re in · rank #{you.rank}</strong>
            <span>Joining again kills your current sim — the council can vary on a re-run.</span>
          </div>
          <GhostBtn type="button" onClick={onJoin}>Replace entry…</GhostBtn>
        </CtaRoot>
      );
    }
    const cd = countdownTo(ch.participationClose);
    return (
      <CtaRoot>
        <div className="copy">
          <strong className="brand">Closes in {cd.label}</strong>
          <span>Join with one of your {ch.ticker} strategies — cost estimate appears in the picker.</span>
        </div>
        <SolidBtn type="button" onClick={onJoin}>Join challenge</SolidBtn>
      </CtaRoot>
    );
  }
  if (ch.status === 'UPCOMING') {
    const cd = countdownTo(ch.participationOpen);
    return (
      <CtaRoot>
        <div className="copy">
          <strong>Opens in {cd.label}</strong>
          <span>Have a {ch.ticker} strategy ready · prepare in advance.</span>
        </div>
      </CtaRoot>
    );
  }
  return (
    <CtaRoot>
      <div className="copy">
        <strong>Closed · {fmtDate(ch.participationClose)}</strong>
        <span>
          {ch.strategiesPublic
            ? 'Open challenge — entrant strategies are now readable from each rank.'
            : 'Strategies stayed private — leaderboard only.'}
        </span>
      </div>
    </CtaRoot>
  );
}

// ─── Styles ─────────────────────────────────────────────────────────────────

const Rail = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const YouMeta = styled.span`
  color: ${p => p.theme.colors.brand};
`;

const EmptyBody = styled.div`
  padding: 28px 18px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;

  & h4 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
    margin: 0;
  }
  & p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    color: ${p => p.theme.colors.fgMuted};
    margin: 0 0 6px;
    line-height: 1.55;
  }
`;

// ─── Baseline card ──────────────────────────────────────────────────────────

const BaselineRoot = styled.div`
  border: 1px solid rgba(255, 176, 0, 0.35);
  background:
    linear-gradient(135deg, rgba(255, 176, 0, 0.05), transparent 60%),
    ${p => p.theme.colors.surface};
  border-radius: 4px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const BaselineHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;

  & .eye { color: ${p => p.theme.colors.terminalAmber}; font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
  & .src { color: ${p => p.theme.colors.fgDimmed}; letter-spacing: 0.04em; text-transform: none; }
`;

const BaselineNum = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;

  & .pct {
    font-size: 24px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    letter-spacing: -0.01em;
  }
  & .pct.pos { color: ${p => p.theme.colors.positive}; }
  & .pct.neg { color: ${p => p.theme.colors.negative}; }
  & .val {
    font-size: 12px;
    color: ${p => p.theme.colors.fgDimmed};
    text-align: right;
  }
`;

const BaselineFoot = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgMuted};
  & strong { color: ${p => p.theme.colors.brand}; font-weight: ${p => p.theme.typography.fontWeight.semibold}; }
`;

// ─── Row list ───────────────────────────────────────────────────────────────

const RowList = styled.div`
  display: flex;
  flex-direction: column;
`;

const RowRoot = styled.div<{ $you: boolean }>`
  display: grid;
  grid-template-columns: 40px 1fr auto auto;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
  ${p => p.$you && `
    background: rgba(0, 255, 65, 0.025);
    border-left: 2px solid ${p.theme.colors.brand};
  `}

  &:last-child { border-bottom: none; }
  &:hover {
    background: ${p => p.$you ? 'rgba(0, 255, 65, 0.05)' : 'rgba(255, 255, 255, 0.025)'};
  }
  &:focus-visible {
    outline: 2px solid ${p => p.theme.colors.focusRing};
    outline-offset: -2px;
  }
`;

const RowRank = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgMuted};
  font-variant-numeric: tabular-nums;
`;

const RowName = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  & .nm {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    color: ${p => p.theme.colors.fgStrong};
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  & .meta {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  & .meta .owner { color: ${p => p.theme.colors.fgMuted}; }
  & .meta .sep { color: ${p => p.theme.colors.border}; }
`;

const RowNum = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: right;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;

  & .ret {
    font-size: 13.5px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
  & .ret.pos { color: ${p => p.theme.colors.positive}; }
  & .ret.neg { color: ${p => p.theme.colors.negative}; }
  & .ret.na  { color: ${p => p.theme.colors.fgDimmed}; }
  & .val {
    font-size: 11px;
    color: ${p => p.theme.colors.fgDimmed};
  }
`;

const RowState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
`;

/**
 * Entry state. `color` is the only per-kind property — the five rgba border/fill
 * literals this used to carry now mix out of `currentColor` inside `pillBase`.
 */
type StateKind = Extract<LabelKind, 'simulating' | 'live' | 'done' | 'failed' | 'pending'>;

const StatePill = styled.span<{ $kind: StateKind }>`
  ${pillBase}
  ${labelCompact}
  color: ${p => labelColor[p.$kind](p.theme.colors)};
`;

const Dot = styled.span<{ $kind: 'simulating' | 'live' }>`
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  animation: ${pulse} 1.4s ease-in-out infinite;
`;

const GapRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgDimmed};

  & .dots { letter-spacing: 0.3em; color: ${p => p.theme.colors.fgSubtle}; }
  & .lbl { letter-spacing: 0.04em; }
`;

// ─── Rail CTA ───────────────────────────────────────────────────────────────

const CtaRoot = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.surface};
  border-radius: 4px;

  & .copy {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  & .copy strong {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.fgStrong};
  }
  & .copy strong.brand { color: ${p => p.theme.colors.brand}; }
  & .copy span {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgDimmed};
    line-height: 1.5;
  }
`;

const SolidBtn = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  padding: 7px 13px;
  border-radius: 2px;
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  color: ${p => p.theme.colors.fgInverse};
  cursor: pointer;
  align-self: flex-start;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;

const GhostBtn = styled.button`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  padding: 7px 13px;
  border-radius: 2px;
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.foreground};
  cursor: pointer;
  align-self: flex-start;

  &:hover { background: rgba(255, 255, 255, 0.04); border-color: ${p => p.theme.colors.fgMuted}; }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;
