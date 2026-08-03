'use client';

import { useMemo } from 'react';
import styled from 'styled-components';
import { KpiStrip, type KpiItem } from '@/app/components/shared/v2';
import { useQueries } from '@tanstack/react-query';
import { apiClient } from '@/app/lib/api/client';
import { queryKeys } from '@/app/lib/api/hooks';
import type { SimulationResponse, PortfolioSnapshot, ChartDataPoint } from '@/app/lib/types/api';
import type { BrokerAccount } from '@/app/lib/types/brokerAccount';

interface ControlKpisProps {
  liveSims: SimulationResponse[];
  accounts: BrokerAccount[];
  unreadNotificationCount: number;
  strategyCount: number;
}

const Mono = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
`;

const Dim2 = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  font-size: 12px;
`;

function fmtMoney(n: number, dp = 2): string {
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: dp, maximumFractionDigits: dp });
}

function fmtPct(n: number, dp = 2): string {
  const sign = n > 0 ? '+' : n < 0 ? '−' : '';
  return `${sign}${Math.abs(n).toFixed(dp)}%`;
}

export function ControlKpis({ liveSims, accounts, unreadNotificationCount, strategyCount }: ControlKpisProps) {
  // Per-sim portfolio (latest) — drives capital + allocations
  const portfolioQueries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.portfolioLatest(s.id),
      queryFn: () => apiClient.portfolio.latest(s.id),
      enabled: !!s.id,
      staleTime: 10_000,
    })),
  });

  // Per-sim chart data — drives today's P&L (last vs first point in window)
  const chartQueries = useQueries({
    queries: liveSims.map(s => ({
      queryKey: queryKeys.simulationChartData(s.id, 24),
      queryFn: () => apiClient.simulations.chartData(s.id, 24),
      enabled: !!s.id,
      staleTime: 30_000,
    })),
  });

  const kpis = useMemo<KpiItem[]>(() => {
    const portfolioRows = portfolioQueries.map(q => q.data as PortfolioSnapshot | undefined);

    // Total live capital — sum totalValue across LIVE sims when we have it,
    // else fall back to broker-account equity. Sims are the truth for "live capital".
    const simTotal = portfolioRows.reduce((sum, p) => sum + (p?.totalValue ?? 0), 0);
    const accountsEquity = accounts.reduce((sum, a) => sum + (a.equity?.amount ?? a.totalBalance.amount), 0);
    const liveCapital = simTotal > 0 ? simTotal : accountsEquity;

    // Total cash across live sims → allocations %
    const cash = portfolioRows.reduce((sum, p) => sum + (p?.cashBalance ?? 0), 0);
    const invested = Math.max(simTotal - cash, 0);
    const allocationsPct = simTotal > 0 ? (invested / simTotal) * 100 : null;

    // Today's P&L — last chart point vs earliest within ~24h
    let plUsd = 0;
    let plPct = 0;
    let havePnl = false;
    chartQueries.forEach((q, idx) => {
      const series = (q.data as ChartDataPoint[] | undefined) ?? [];
      if (series.length < 2) return;
      const last = series[series.length - 1].portfolioValue;
      const first = series[0].portfolioValue;
      if (first <= 0) return;
      plUsd += last - first;
      // Weight pct by sim size
      const simWeight = portfolioRows[idx]?.totalValue ?? last;
      plPct += ((last - first) / first) * 100 * simWeight;
      havePnl = true;
    });
    const totalWeight = portfolioRows.reduce((s, p) => s + (p?.totalValue ?? 0), 0);
    const plPctNorm = havePnl && totalWeight > 0 ? plPct / totalWeight : 0;

    const activeSims = liveSims.length;

    return [
      {
        label: 'Live capital',
        value: <Mono>{fmtMoney(liveCapital, 2)}</Mono>,
        delta: <Dim2>{activeSims} sim{activeSims === 1 ? '' : 's'} · {accounts.length} account{accounts.length === 1 ? '' : 's'}</Dim2>,
      },
      {
        label: "Today's P&L",
        value: havePnl
          ? <Mono>{plUsd >= 0 ? '+' : '−'}{fmtMoney(Math.abs(plUsd), 2)}</Mono>
          : <Mono>—</Mono>,
        delta: havePnl
          ? <Dim2>{fmtPct(plPctNorm)} weighted</Dim2>
          : <Dim2>no data yet</Dim2>,
        tone: havePnl ? (plUsd >= 0 ? 'positive' : 'negative') : 'dim',
      },
      {
        label: 'Allocations',
        value: allocationsPct != null
          ? <Mono>{allocationsPct.toFixed(1)}%</Mono>
          : <Mono>—</Mono>,
        delta: allocationsPct != null
          ? <Dim2>{fmtMoney(invested, 0)} invested · {fmtMoney(cash, 0)} cash</Dim2>
          : <Dim2>no live capital</Dim2>,
        tone: allocationsPct == null ? 'dim' : 'default',
      },
      {
        label: 'Active sims',
        value: <Mono>{activeSims}</Mono>,
        delta: <Dim2>{liveSims.filter(s => s.status === 'LIVE').length} live · {liveSims.filter(s => s.status === 'SIMULATING').length} backtest</Dim2>,
        tone: activeSims === 0 ? 'dim' : 'default',
      },
      {
        label: 'Open alerts',
        value: <Mono>{unreadNotificationCount}</Mono>,
        delta: <Dim2>{unreadNotificationCount === 0 ? 'all clear' : 'unread'}</Dim2>,
        tone: unreadNotificationCount === 0 ? 'dim' : (unreadNotificationCount > 0 ? 'hold' : 'default'),
      },
      {
        label: 'Strategies',
        value: <Mono>{strategyCount}</Mono>,
        delta: <Dim2>authored</Dim2>,
        tone: strategyCount === 0 ? 'dim' : 'default',
      },
    ];
  }, [portfolioQueries, chartQueries, liveSims, accounts, unreadNotificationCount, strategyCount]);

  return <KpiStrip items={kpis} dense />;
}
