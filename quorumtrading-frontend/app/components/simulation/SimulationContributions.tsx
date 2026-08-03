'use client';

import styled from 'styled-components';
import type { ContributionResponse, SimulationResponse } from '@/app/lib/types/api';

interface Props {
  simulation: SimulationResponse;
}

const Wrapper = styled.section`
  margin-top: ${(p) => p.theme.spacing.lg};
  padding: ${(p) => p.theme.spacing.lg};
  background: ${(p) => p.theme.colors.surface};
  border: 1px solid ${(p) => p.theme.colors.border};
  border-radius: ${(p) => p.theme.borderRadius.md};
`;

const Title = styled.h3`
  font-family: ${(p) => p.theme.typography.fontFamily.mono};
  font-size: ${(p) => p.theme.typography.fontSize.base};
  color: ${(p) => p.theme.colors.terminalGreen};
  margin: 0 0 ${(p) => p.theme.spacing.md};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const StatGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: ${(p) => p.theme.spacing.md};
  margin-bottom: ${(p) => p.theme.spacing.md};
`;

const Stat = styled.div`
  padding: ${(p) => p.theme.spacing.sm} ${(p) => p.theme.spacing.md};
  background: ${(p) => p.theme.colors.background};
  border: 1px solid ${(p) => p.theme.colors.border};
  border-radius: ${(p) => p.theme.borderRadius.sm};
`;

const StatLabel = styled.div`
  font-family: ${(p) => p.theme.typography.fontFamily.mono};
  font-size: ${(p) => p.theme.typography.fontSize.xs};
  color: ${(p) => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const StatValue = styled.div<{ $tone?: 'green' | 'amber' | 'pink' | 'default' }>`
  font-family: ${(p) => p.theme.typography.fontFamily.mono};
  font-size: ${(p) => p.theme.typography.fontSize.lg};
  font-weight: ${(p) => p.theme.typography.fontWeight.semibold};
  color: ${(p) => {
    switch (p.$tone) {
      case 'green':
        return p.theme.colors.terminalGreen;
      case 'amber':
        return p.theme.colors.terminalAmber;
      case 'pink':
        return p.theme.colors.terminalPink;
      default:
        return p.theme.colors.text;
    }
  }};
  margin-top: 4px;
`;

const StatHint = styled.div`
  font-family: ${(p) => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  color: ${(p) => p.theme.colors.textMuted};
  margin-top: 2px;
`;

const Table = styled.div`
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: ${(p) => p.theme.spacing.xs} ${(p) => p.theme.spacing.md};
  margin-top: ${(p) => p.theme.spacing.md};
`;

const HeaderCell = styled.div`
  font-family: ${(p) => p.theme.typography.fontFamily.mono};
  font-size: ${(p) => p.theme.typography.fontSize.xs};
  color: ${(p) => p.theme.colors.terminalGreen};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding-bottom: ${(p) => p.theme.spacing.xs};
  border-bottom: 1px solid ${(p) => p.theme.colors.border};
`;

const Cell = styled.div`
  padding: ${(p) => p.theme.spacing.xs} 0;
  font-family: ${(p) => p.theme.typography.fontFamily.mono};
  font-size: ${(p) => p.theme.typography.fontSize.sm};
  color: ${(p) => p.theme.colors.text};
  border-bottom: 1px solid ${(p) => p.theme.colors.border}30;
`;

const Muted = styled(Cell)`
  color: ${(p) => p.theme.colors.textMuted};
`;

const Empty = styled.div`
  font-family: ${(p) => p.theme.typography.fontFamily.mono};
  font-size: ${(p) => p.theme.typography.fontSize.sm};
  color: ${(p) => p.theme.colors.textMuted};
  padding: ${(p) => p.theme.spacing.sm} 0;
`;

function formatMoney(amount: number | null | undefined, currency = 'USD'): string {
  if (amount == null) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

function formatPercent(value: number | null | undefined): string {
  if (value == null) return '—';
  const sign = value >= 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

function percentTone(value: number | null | undefined): 'green' | 'pink' | 'default' {
  if (value == null) return 'default';
  return value >= 0 ? 'green' : 'pink';
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString();
}

export function SimulationContributions({ simulation }: Props) {
  const contributions: ContributionResponse[] = simulation.contributions ?? [];
  const initial = simulation.initialCash;
  const totalInvested = simulation.totalInvested;
  const twr = simulation.twrPercent;
  const mwr = simulation.mwrPercent;

  // Hide the panel entirely when nothing recurring-related is going on AND
  // it's not a LIVE sim — keeps the page clean for vanilla backtests.
  const showPanel = (contributions.length > 0)
      || totalInvested != null
      || twr != null
      || mwr != null;
  if (!showPanel) {
    return null;
  }

  return (
    <Wrapper>
      <Title>Invested capital & performance</Title>
      <StatGrid>
        <Stat>
          <StatLabel>Initial cash</StatLabel>
          <StatValue>{formatMoney(initial)}</StatValue>
        </Stat>
        <Stat>
          <StatLabel>Total invested</StatLabel>
          <StatValue>{formatMoney(totalInvested ?? initial)}</StatValue>
          <StatHint>initial + contributions</StatHint>
        </Stat>
        <Stat>
          <StatLabel>TWR</StatLabel>
          <StatValue $tone={percentTone(twr)}>{formatPercent(twr)}</StatValue>
          <StatHint>strategy skill, ignores cashflow timing</StatHint>
        </Stat>
        <Stat>
          <StatLabel>MWR (IRR)</StatLabel>
          <StatValue $tone={percentTone(mwr)}>{formatPercent(mwr)}</StatValue>
          <StatHint>your actual return, annualized</StatHint>
        </Stat>
      </StatGrid>

      {contributions.length > 0 ? (
        <Table>
          <HeaderCell>Date</HeaderCell>
          <HeaderCell>Source</HeaderCell>
          <HeaderCell style={{ textAlign: 'right' }}>Amount</HeaderCell>
          {contributions.map((c) => (
            <ContributionRow key={c.id} contribution={c} />
          ))}
        </Table>
      ) : (
        <Empty>No contributions yet — initial cash is the only basis.</Empty>
      )}
    </Wrapper>
  );
}

function ContributionRow({ contribution }: { contribution: ContributionResponse }) {
  return (
    <>
      <Cell>{formatDate(contribution.contributionDate)}</Cell>
      <Muted>
        {contribution.sourcePlanId
          ? `Plan ${contribution.sourcePlanId.slice(0, 8)}…`
          : 'Manual'}
      </Muted>
      <Cell style={{ textAlign: 'right' }}>
        {formatMoney(contribution.amount, contribution.currency)}
      </Cell>
    </>
  );
}
