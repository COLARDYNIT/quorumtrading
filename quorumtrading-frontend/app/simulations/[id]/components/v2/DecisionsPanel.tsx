'use client';

import { useMemo, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import styled from 'styled-components';
import { Panel, ActionPill, VoteChip } from '@/app/components/shared/v2';
import type { ActionKind } from '@/app/components/shared/v2';
import { useSimulationData } from '../../contexts/SimulationDataContext';
import { useConsensusWithAgents } from '@/app/lib/api/hooks';
import { parseActionLabel } from '@/app/lib/utils/decisionAction';
import type { ConsensusWithAgentsResponse, LLMDecision } from '@/app/lib/types/api';

const priceFmt = (v: number | undefined | null): string => {
  if (v == null) return '—';
  return `$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
};

const shortTimestamp = (iso: string): string => {
  try {
    const d = new Date(iso);
    return d.toLocaleString('en-US', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
  } catch {
    return iso;
  }
};

const FilterRow = styled.div`
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
`;

const FilterPill = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 2px;
  border: 1px solid ${p => p.$on ? p.theme.colors.fgMuted : p.theme.colors.border};
  color: ${p => p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted};
  background: ${p => p.$on ? 'rgba(255, 255, 255, 0.04)' : 'transparent'};
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background 120ms ease;

  &:hover {
    color: ${p => p.theme.colors.foreground};
    border-color: ${p => p.theme.colors.fgMuted};
  }
`;

const TableScroll = styled.div`
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-family: ${p => p.theme.typography.fontFamily.sans};

  thead th {
    text-align: left;
    font-size: 9.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: ${p => p.theme.colors.fgDimmed};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    padding: 6px 10px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    background: ${p => p.theme.colors.background};
    position: sticky;
    top: 0;
    z-index: 1;
  }
  thead th.right { text-align: right; }
  td {
    padding: 7px 10px;
    border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    vertical-align: top;
    font-size: 12.5px;
  }
  tr:last-child td { border-bottom: none; }
  tr.clickable:hover td { background: rgba(255, 255, 255, 0.015); cursor: pointer; }

  .ts {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.fgMuted};
    white-space: nowrap;
    width: 1%;
  }
  .px {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-variant-numeric: tabular-nums;
    color: ${p => p.theme.colors.foreground};
    white-space: nowrap;
    text-align: right;
  }
  .reason {
    color: ${p => p.theme.colors.fgMuted};
    font-size: 12px;
    line-height: 1.45;
  }
  .id {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgSubtle};
    font-size: 11.5px;
    margin-left: 6px;
  }
  .symbol {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }

  /* Mobile: flatten each row into a stacked card so we don't need
     horizontal scrolling on narrow viewports. */
  @media (max-width: 640px) {
    display: block;

    thead { display: none; }
    tbody { display: block; }

    tr {
      display: grid;
      grid-template-columns: auto 1fr auto;
      grid-template-areas:
        "action symbol price"
        "votes  votes  votes"
        "reason reason reason"
        "ts     ts     ts";
      column-gap: 10px;
      row-gap: 6px;
      align-items: center;
      padding: 12px 14px;
      border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
    }
    tr:last-child { border-bottom: none; }
    tr.clickable:hover { background: rgba(255, 255, 255, 0.015); cursor: pointer; }
    tr.clickable:hover td { background: transparent; }

    td {
      display: block;
      padding: 0;
      border-bottom: none;
    }
    /* td:nth-child order matches the JSX:
       1: timestamp, 2: action, 3: symbol, 4: votes, 5: price, 6: reasoning */
    td:nth-child(1) { grid-area: ts; }
    td:nth-child(2) { grid-area: action; }
    td:nth-child(3) { grid-area: symbol; }
    td:nth-child(4) { grid-area: votes; }
    td:nth-child(5) { grid-area: price; }
    td:nth-child(6) { grid-area: reason; }

    .ts { width: auto; font-size: 11px; }
    .px { font-size: 13px; }
    .reason { font-size: 12.5px; }
    .id { display: inline; margin-left: 6px; }
  }
`;

const EmptyMsg = styled.div`
  padding: 28px 16px;
  text-align: center;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 13px;
`;

type FilterKey = 'TRADES' | 'ALL' | 'BUY' | 'SELL' | 'HOLD' | 'SKIPPED' | 'OVERRIDES';

interface DecisionsPanelProps {
  onRestartFrom?: (consensusDecisionId: string, decisionTimestamp: string) => void;
  canRestart?: boolean;
  onInspect?: (consensusId: string) => void;
}

type AgentVote = { tech: boolean; fund: boolean; sent: boolean; override: boolean };

function deriveVotes(agents: LLMDecision[], finalAction: string): AgentVote {
  const findVote = (type: string) => agents.find(a => a.agentType?.toUpperCase() === type);
  const tech = findVote('TECHNICAL');
  const fund = findVote('FUNDAMENTAL');
  const sent = findVote('SENTIMENT');
  const orch = findVote('ORCHESTRATOR');

  const finalLabel = parseActionLabel(finalAction);
  const matches = (a: LLMDecision | undefined) =>
    !!a && parseActionLabel(a.suggestedAction) === finalLabel && finalLabel !== '';

  const orchMatches = matches(orch);
  // If 2+ of the 3 base agents matched final action, no override. Otherwise it's an orchestrator override.
  const baseMatching = [matches(tech), matches(fund), matches(sent)].filter(Boolean).length;
  const override = orchMatches && baseMatching < 2;

  return {
    tech: matches(tech),
    fund: matches(fund),
    sent: matches(sent),
    override,
  };
}

function actionToKind(action: string): ActionKind {
  const a = action.toUpperCase();
  if (a === 'BUY') return 'buy';
  if (a === 'SELL') return 'sell';
  if (a === 'HOLD') return 'hold';
  // SKIP must render as skip, not fall through to an amber HOLD pill: a run whose engine
  // failed every round used to look like a run that kept deciding to hold.
  if (a === 'SKIP') return 'skip';
  return 'hold';
}

export function DecisionsPanel({ onRestartFrom, canRestart = false, onInspect }: DecisionsPanelProps) {
  // consensusHistory in the shared context is the lightweight ConsensusDecision[] feed
  // (drives chart markers and the timeline). For the table we need each decision PLUS
  // its agent votes — that's the consensus-with-agents endpoint.
  useSimulationData();
  const params = useParams();
  const simulationId = (params?.id as string | undefined) ?? '';
  // Endpoint caps `limit` at 100 — keep within bounds.
  const { data: consensusWithAgents } = useConsensusWithAgents(simulationId, undefined, 100);
  // Default to TRADES (BUY + SELL) — HOLDs are noisy on simulations that
  // evaluate daily but rarely act, so they hide behind the explicit filters.
  const [filter, setFilter] = useState<FilterKey>('TRADES');

  const rows = useMemo(() => {
    const all = (consensusWithAgents ?? []) as ConsensusWithAgentsResponse[];
    const sorted = all
      .slice()
      .sort((a, b) =>
        new Date(b.consensusDecision.decisionTimestamp).getTime() -
        new Date(a.consensusDecision.decisionTimestamp).getTime()
      );

    return sorted.map(c => {
      const votes = deriveVotes(c.agentDecisions ?? [], c.consensusDecision.finalAction);
      // Find the most descriptive reasoning available — orchestrator first.
      const agents = c.agentDecisions ?? [];
      const orch = agents.find(a => a.agentType?.toUpperCase() === 'ORCHESTRATOR');
      const tech = agents.find(a => a.agentType?.toUpperCase() === 'TECHNICAL');
      const reason = c.consensusDecision.notes || orch?.reasoning || tech?.reasoning || '';
      return {
        id: c.consensusDecision.id,
        timestamp: c.consensusDecision.decisionTimestamp,
        action: parseActionLabel(c.consensusDecision.finalAction),
        symbol: c.consensusDecision.symbol ?? '—',
        executionPrice: c.consensusDecision.executionPrice ?? null,
        executed: c.consensusDecision.executed,
        votes,
        reason: reason ? (reason.length > 180 ? reason.slice(0, 177) + '…' : reason) : '—',
      };
    });
  }, [consensusWithAgents]);

  const filtered = useMemo(() => {
    if (filter === 'ALL') return rows;
    if (filter === 'TRADES') return rows.filter(r => r.action === 'BUY' || r.action === 'SELL');
    if (filter === 'OVERRIDES') return rows.filter(r => r.votes.override);
    if (filter === 'SKIPPED') return rows.filter(r => r.action === 'SKIP');
    return rows.filter(r => r.action === filter);
  }, [rows, filter]);

  const handleRowClick = useCallback((id: string) => {
    if (onInspect) onInspect(id);
  }, [onInspect]);

  // Restart-from is now reached from inside the inspect modal; keep the prop for
  // the page-level wiring but suppress the lint warning about unused arg.
  void onRestartFrom;
  void canRestart;

  const filters: Array<{ key: FilterKey; label: string }> = [
    { key: 'TRADES', label: 'Trades' },
    { key: 'BUY', label: 'Buy' },
    { key: 'SELL', label: 'Sell' },
    { key: 'HOLD', label: 'Hold' },
    { key: 'SKIPPED', label: 'Skipped' },
    { key: 'OVERRIDES', label: 'Overrides' },
    { key: 'ALL', label: 'All' },
  ];

  return (
    <Panel
      title="Decision history"
      count={`${filtered.length} of ${rows.length}`}
      noBodyPadding
      bodyMaxHeight={620}
      headerActions={
        <FilterRow>
          {filters.map(f => (
            <FilterPill
              key={f.key}
              type="button"
              $on={filter === f.key}
              onClick={() => setFilter(f.key)}
            >
              {f.label}
            </FilterPill>
          ))}
        </FilterRow>
      }
    >
      {filtered.length === 0 ? (
        <EmptyMsg>
          {rows.length === 0
            ? 'No decisions yet. The agent council convenes at the next evaluation.'
            : filter === 'TRADES'
              ? 'No buy or sell decisions in this view. Switch to "All" to include holds and skips.'
              : filter === 'OVERRIDES'
                ? 'No orchestrator overrides in this view.'
                : filter === 'SKIPPED'
                  ? 'No skipped rounds — every evaluation produced a decision.'
                  : `No ${filter.toLowerCase()} decisions in this view.`}
        </EmptyMsg>
      ) : (
        <TableScroll>
        <Table>
          <thead>
            <tr>
              <th>When</th>
              <th>Action</th>
              <th>Symbol</th>
              <th>Votes (tech · fund · sent)</th>
              <th className="right">Price</th>
              <th>Reasoning</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((r) => {
              const clickable = !!onInspect;
              return (
              <tr
                key={r.id}
                className={clickable ? 'clickable' : undefined}
                onClick={clickable ? () => handleRowClick(r.id) : undefined}
                title={clickable ? 'Inspect this decision' : undefined}
              >
                <td className="ts">{shortTimestamp(r.timestamp)}</td>
                <td>
                  <ActionPill action={actionToKind(r.action)} override={r.votes.override} />
                </td>
                <td><span className="symbol">{r.symbol}</span></td>
                <td>
                  <span style={{ display: 'inline-flex', gap: 4 }}>
                    <VoteChip agent="tech" ok={r.votes.tech} />
                    <VoteChip agent="fund" ok={r.votes.fund} />
                    <VoteChip agent="sent" ok={r.votes.sent} />
                  </span>
                </td>
                <td className="px">{priceFmt(r.executionPrice)}</td>
                <td className="reason">
                  {r.reason}
                  <span className="id">#{r.id.slice(0, 7).toUpperCase()}</span>
                </td>
              </tr>
              );
            })}
          </tbody>
        </Table>
        </TableScroll>
      )}
    </Panel>
  );
}
