'use client';

import Link from 'next/link';
import styled, { css } from 'styled-components';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';
import type { BrokerType } from '@/app/lib/types/api';
import { hueColor, labelCompact, tagBase, type LabelHue } from '@/app/styles/labels';

export type ScopeId = 'all' | string;

type BrokerKind = 'all' | 'real' | 'paper' | 'mock';

export interface ScopeAccount {
  id: ScopeId;
  brokerType: BrokerType | 'ALL';
  label: string;
  /** Equity (cash + market value). Falls back to totalBalance when broker doesn't expose equity. */
  value: number;
  /** Number of live/simulating simulations bound to this account. */
  liveSims: number;
}

interface AccountScopeRowProps {
  accounts: BrokerAccount[];
  /** SimulationResponse rows that are LIVE/SIMULATING — used to count sims per brokerType. */
  liveSimsByBrokerType: Record<BrokerType, number>;
  activeId: ScopeId;
  onChange: (id: ScopeId) => void;
}

const Row = styled.div`
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  overflow: hidden;
`;

const Cell = styled.button<{ $active: boolean }>`
  background: ${p => p.theme.colors.surface};
  padding: 10px 14px 11px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  cursor: pointer;
  position: relative;
  transition: background 120ms ease, box-shadow 120ms ease;
  text-align: left;
  border: none;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  min-width: 0;

  &:hover { background: ${p => p.theme.colors.surfaceElevated}; }

  ${p => p.$active && css`
    background: ${p.theme.colors.surfaceElevated};
    box-shadow: inset 0 -2px 0 ${p.theme.colors.brand};
  `}
`;

const LabelRow = styled.span`
  display: flex; align-items: center; gap: 6px;
  min-width: 0;
`;

const TAG_HUE: Record<BrokerKind, LabelHue> = {
  all: 'dim',
  real: 'brand',
  paper: 'amber',
  mock: 'dim',
};

const Tag = styled.span<{ $kind: BrokerKind }>`
  ${tagBase}
  ${labelCompact}
  color: ${p => hueColor[TAG_HUE[p.$kind]](p.theme.colors)};
`;

const NameLabel = styled.span`
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Val = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 17px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const Meta = styled.span`
  display: flex; gap: 6px; align-items: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgMuted};
`;

const Connect = styled(Link)`
  display: flex; align-items: center; justify-content: center;
  background: ${p => p.theme.colors.surface};
  color: ${p => p.theme.colors.fgMuted};
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  padding: 0 18px;
  white-space: nowrap;
  text-decoration: none;

  &:hover { color: ${p => p.theme.colors.foreground}; background: ${p => p.theme.colors.surfaceElevated}; }
`;

function brokerKind(t: BrokerType | 'ALL'): BrokerKind {
  if (t === 'ALL') return 'all';
  if (t === 'ALPACA_LIVE') return 'real';
  if (t === 'ALPACA_PAPER') return 'paper';
  return 'mock';
}

function fmtMoney(n: number): string {
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 });
}

export function AccountScopeRow({ accounts, liveSimsByBrokerType, activeId, onChange }: AccountScopeRowProps) {
  const allEquity = accounts.reduce((sum, a) => sum + (a.equity?.amount ?? a.totalBalance.amount), 0);
  const allLive = (liveSimsByBrokerType.ALPACA_LIVE ?? 0) + (liveSimsByBrokerType.ALPACA_PAPER ?? 0) + (liveSimsByBrokerType.MOCK ?? 0);

  return (
    <Row role="tablist" aria-label="Account scope">
      <Cell
        type="button"
        role="tab"
        aria-selected={activeId === 'all'}
        $active={activeId === 'all'}
        onClick={() => onChange('all')}
      >
        <LabelRow>
          <Tag $kind="all">all</Tag>
          <NameLabel>All accounts</NameLabel>
        </LabelRow>
        <Val>{fmtMoney(allEquity)}</Val>
        <Meta>{accounts.length} account{accounts.length === 1 ? '' : 's'} · {allLive} sim{allLive === 1 ? '' : 's'}</Meta>
      </Cell>
      {accounts.map(account => {
        const kind = brokerKind(account.brokerType);
        const equity = account.equity?.amount ?? account.totalBalance.amount;
        const sims = liveSimsByBrokerType[account.brokerType] ?? 0;
        return (
          <Cell
            key={account.id}
            type="button"
            role="tab"
            aria-selected={activeId === account.id}
            $active={activeId === account.id}
            onClick={() => onChange(account.id)}
          >
            <LabelRow>
              <Tag $kind={kind}>{kind === 'real' ? 'real' : kind}</Tag>
              <NameLabel>{account.name}</NameLabel>
            </LabelRow>
            <Val>{fmtMoney(equity)}</Val>
            <Meta>{sims} sim{sims === 1 ? '' : 's'} · {account.brokerType === 'ALPACA_LIVE' ? 'real' : account.brokerType === 'ALPACA_PAPER' ? 'paper' : 'mock'}</Meta>
          </Cell>
        );
      })}
      <Connect href="/settings/broker-accounts">+ Connect account</Connect>
    </Row>
  );
}
