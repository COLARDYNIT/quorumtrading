'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import styled from 'styled-components';
import { Panel } from '@/app/components/shared/v2';
import type { SimulationResponse } from '@/app/lib/types/api';

interface Props {
  simulations: SimulationResponse[];
  newHref?: string;
}

type Group = 'live' | 'completed' | 'failed';

function groupOf(status: SimulationResponse['status']): Group {
  if (status === 'LIVE' || status === 'SIMULATING' || status === 'PENDING' || status === 'WAITING_FOR_DATA') return 'live';
  if (status === 'COMPLETED') return 'completed';
  return 'failed';
}

function shortDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: '2-digit' });
}

/**
 * Strategy-scoped simulations panel — v2. Rows group by state (Live →
 * Completed → Failed) so the grouping IS the filter; the toggle row from v1
 * is gone. Per-row content stays honest to the API: name + period + progress.
 * Buy/sell/hold counts and per-row Sharpe aren't on `SimulationResponse` —
 * hidden until engineering exposes them.
 */
export function SimsTablePanel({ simulations, newHref }: Props) {
  const groups = useMemo(() => {
    const live: SimulationResponse[] = [];
    const completed: SimulationResponse[] = [];
    const failed: SimulationResponse[] = [];
    const sorted = simulations.slice().sort((a, b) => {
      const ta = new Date(a.startDate || a.createdAt).getTime();
      const tb = new Date(b.startDate || b.createdAt).getTime();
      return tb - ta;
    });
    for (const s of sorted) {
      const g = groupOf(s.status);
      if (g === 'live') live.push(s);
      else if (g === 'completed') completed.push(s);
      else failed.push(s);
    }
    return { live, completed, failed };
  }, [simulations]);

  const total = simulations.length;
  const counts = `${groups.live.length} live · ${groups.completed.length} completed${groups.failed.length > 0 ? ` · ${groups.failed.length} failed` : ''}`;

  return (
    <Panel
      title="Simulations"
      count={total === 0 ? 'no runs yet' : `${total} total · ${counts}`}
      noBodyPadding
    >
      {total === 0 ? (
        <Empty>
          <h4>No simulations recorded</h4>
          <p>
            Each run shows up here with its period, state, and progress, grouped by Live →
            Completed → Failed. Promote this strategy to a sim above to fill the list.
          </p>
        </Empty>
      ) : (
        <Body>
          {groups.live.length > 0 && (
            <Section>
              <SectionHead $status="live">
                <span className="left">
                  <Dot $status="live" />
                  <span className="lbl">Live · {groups.live.length}</span>
                  <span className="meta">executing council ticks</span>
                </span>
              </SectionHead>
              {groups.live.map((s) => <SimRow key={s.id} sim={s} />)}
            </Section>
          )}
          {groups.completed.length > 0 && (
            <Section>
              <SectionHead $status="completed">
                <span className="left">
                  <Dot $status="completed" />
                  <span className="lbl">Completed · {groups.completed.length}</span>
                  <span className="meta">closed runs · click to re-open trail</span>
                </span>
              </SectionHead>
              {groups.completed.map((s) => <SimRow key={s.id} sim={s} />)}
            </Section>
          )}
          {groups.failed.length > 0 && (
            <Section>
              <SectionHead $status="failed">
                <span className="left">
                  <Dot $status="failed" />
                  <span className="lbl">Failed · {groups.failed.length}</span>
                  <span className="meta">halted before completion</span>
                </span>
              </SectionHead>
              {groups.failed.map((s) => <SimRow key={s.id} sim={s} />)}
            </Section>
          )}
        </Body>
      )}
      {newHref && total > 0 && (
        <Foot>
          <NewLink href={newHref}>+ New simulation</NewLink>
        </Foot>
      )}
    </Panel>
  );
}

function SimRow({ sim }: { sim: SimulationResponse }) {
  const from = shortDate(sim.startDate);
  const to = sim.endDate ? shortDate(sim.endDate) : 'now';
  const progress = sim.progress != null ? Math.max(0, Math.min(100, sim.progress)) : null;
  const showProgress = sim.status !== 'COMPLETED' && progress != null;
  const stateLabel = sim.status === 'WAITING_FOR_DATA'
    ? 'loading data'
    : sim.status.toLowerCase();
  return (
    <Row href={`/simulations/${sim.id}`}>
      <Cell className="name">
        <span className="main">{sim.name || 'Untitled run'}</span>
        <span className="id">#{sim.id.slice(0, 8).toUpperCase()}</span>
      </Cell>
      <Cell className="period">
        <span className="from">{from}</span>
        <span className="to">→ {to}</span>
      </Cell>
      <Cell className="progress">
        {showProgress ? (
          <ProgressTrack>
            <ProgressFill style={{ width: `${progress}%` }} />
          </ProgressTrack>
        ) : (
          <ProgressDash>—</ProgressDash>
        )}
      </Cell>
      <Cell className="state">
        <StateChip $status={groupOf(sim.status)}>{stateLabel}</StateChip>
      </Cell>
    </Row>
  );
}

// ─── styled ─────────────────────────────────────────────────────────────────

const Body = styled.div``;

const Section = styled.div``;

const SectionHead = styled.div<{ $status: Group }>`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  & > .left {
    display: inline-flex;
    align-items: center;
    gap: 9px;
  }
  & > .left .lbl {
    color: ${p => p.$status === 'live' ? p.theme.colors.brand : p.theme.colors.fgMuted};
  }
  & > .left .meta {
    color: ${p => p.theme.colors.fgSubtle};
    text-transform: none;
    letter-spacing: 0;
    font-size: 11.5px;
  }
`;

const Dot = styled.span<{ $status: Group }>`
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
  background: ${p =>
    p.$status === 'live' ? p.theme.colors.brand :
    p.$status === 'completed' ? p.theme.colors.fgMuted :
    p.theme.colors.negative};
  box-shadow: ${p =>
    p.$status === 'live' ? '0 0 8px rgba(0, 255, 65, 0.4)' : 'none'};
`;

const Row = styled(Link)`
  display: grid;
  grid-template-columns: 1fr 130px 80px 90px;
  gap: 12px;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  text-decoration: none;
  color: inherit;
  transition: background 120ms ease;

  &:hover { background: rgba(255, 255, 255, 0.02); }
  &:last-child { border-bottom: 0; }

  @media (max-width: 768px) {
    grid-template-columns: 1fr auto;

    & > .progress, & > .period { display: none; }
  }
`;

const Cell = styled.div`
  min-width: 0;

  &.name { display: flex; flex-direction: column; gap: 1px; }
  &.name .main {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &.name .id {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgSubtle};
    letter-spacing: 0.02em;
  }
  &.period {
    line-height: 1.35;
  }
  &.period .from, &.period .to {
    display: block;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11.5px;
    color: ${p => p.theme.colors.fgMuted};
  }
  &.period .to { color: ${p => p.theme.colors.fgSubtle}; }
  &.progress { text-align: right; }
  &.state { text-align: right; }
`;

const ProgressTrack = styled.div`
  width: 60px;
  height: 4px;
  background: ${p => p.theme.colors.borderSubtle};
  border-radius: 1px;
  margin-left: auto;
  overflow: hidden;
  position: relative;
`;

const ProgressFill = styled.div`
  position: absolute;
  inset: 0 auto 0 0;
  background: ${p => p.theme.colors.brand};
  border-radius: 1px;
`;

const ProgressDash = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const StateChip = styled.span<{ $status: Group }>`
  display: inline-block;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 2px 6px;
  border-radius: 2px;
  color: ${p =>
    p.$status === 'live' ? p.theme.colors.brand :
    p.$status === 'completed' ? p.theme.colors.fgMuted :
    p.theme.colors.negative};
  background: ${p =>
    p.$status === 'live' ? 'rgba(0, 255, 65, 0.06)' :
    p.$status === 'completed' ? 'transparent' :
    'rgba(255, 51, 102, 0.06)'};
  border: 1px solid ${p =>
    p.$status === 'live' ? 'rgba(0, 255, 65, 0.4)' :
    p.$status === 'completed' ? p.theme.colors.borderSubtle :
    'rgba(255, 51, 102, 0.4)'};
`;

const Empty = styled.div`
  padding: 32px 24px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};

  & h4 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    color: ${p => p.theme.colors.fgStrong};
    margin: 0 0 6px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }
  & p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13px;
    line-height: 1.5;
    max-width: 420px;
    margin: 0 auto;
  }
`;

const Foot = styled.div`
  padding: 9px 14px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const NewLink = styled(Link)`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  background: ${p => p.theme.colors.brandTint};
  border-radius: 2px;
  padding: 4px 10px;
  text-decoration: none;

  &:hover { background: rgba(0, 255, 65, 0.16); }
  &:focus-visible { outline: 2px solid ${p => p.theme.colors.focusRing}; outline-offset: 2px; }
`;
