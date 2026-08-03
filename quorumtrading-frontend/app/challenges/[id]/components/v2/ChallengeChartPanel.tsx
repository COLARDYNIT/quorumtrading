'use client';

import { useMemo } from 'react';
import styled, { keyframes } from 'styled-components';
import { BenchmarkChart } from '@/app/benchmarks/components/BenchmarkChart';
import { transformToChartData, mergeOverlaysIntoChartData } from '@/app/lib/utils/chartData';
import type { ChallengeResponse, ChallengeChartData } from '@/app/lib/types/challenge';
import type { BenchmarkSimulation, OverlayResponse } from '@/app/lib/types/benchmark';
import { fmtDate } from './helpers';

interface Props {
  ch: ChallengeResponse;
  chart: ChallengeChartData;
  height?: number;
}

const ENTRY_COLORS = [
  '#00d9ff', '#ff6b6b', '#51cf66', '#fcc419', '#cc5de8',
  '#ff922b', '#22b8cf', '#20c997', '#845ef7', '#74c0fc',
];

/**
 * Wraps the production BenchmarkChart with v2 chrome: title strip on top
 * (range + polling indicator + buy-and-hold legend cue).
 */
export function ChallengeChartPanel({ ch, chart, height = 360 }: Props) {
  const { chartData, simulations, overlays } = useMemo(() => {
    if (!chart?.simulations?.length) {
      return { chartData: [], simulations: [] as BenchmarkSimulation[], overlays: [] as OverlayResponse[] };
    }
    const simCount = chart.simulations.length;
    const sims: BenchmarkSimulation[] = chart.simulations.map((s, i) => ({
      id: s.id,
      name: s.name,
      strategyName: s.strategyName,
      strategyId: '',
      color: ENTRY_COLORS[i % ENTRY_COLORS.length],
      currentValue: 0,
      startValue: s.startValue,
      returnPercent: 0,
      dataPoints: [],
      startDate: new Date(),
      endDate: new Date(),
      status: s.status as BenchmarkSimulation['status'],
    }));

    if (chart.points.length > 0) {
      const timestamps = chart.points[0] as number[];
      sims.forEach((sim, i) => {
        const values = chart.points[i + 1] ?? [];
        sim.dataPoints = timestamps.map((ts, j) => ({
          timestamp: new Date(ts),
          portfolioValue: values[j] ?? 0,
        }));
      });
    }

    let overlay: OverlayResponse[] = [];
    if (chart.buyAndHold && chart.points.length > 0) {
      const timestamps = chart.points[0] as number[];
      const bhValues = chart.points[simCount + 1];
      if (bhValues) {
        const dataPoints = timestamps
          .map((ts, i) => ({
            timestamp: new Date(ts).toISOString(),
            portfolioValue: bhValues[i] ?? 0,
          }))
          .filter((d) => d.portfolioValue > 0);
        overlay = [{
          symbol: chart.buyAndHold.ticker,
          assetType: ch.tickerType,
          status: 'READY' as const,
          dataPoints,
          returnPercent: chart.buyAndHold.returnPercent,
          errorMessage: null,
        }];
      }
    }

    return {
      chartData: transformToChartData(sims),
      simulations: sims,
      overlays: overlay,
    };
  }, [chart, ch.tickerType]);

  const polling =
    ch.status === 'ACTIVE' &&
    ch.entries.some((e) => e.status === 'SIMULATING' || e.status === 'LIVE');

  return (
    <Root>
      <Head>
        <Left>
          <Eye>EQUITY CURVES</Eye>
          <Range>
            {fmtDate(ch.simulationStartDate, false)} → {fmtDate(ch.simulationEndDate)}
          </Range>
        </Left>
        <Right>
          {polling && (
            <PollPip>
              <PollDot />
              polling · 15s
            </PollPip>
          )}
          <Legend>
            {simulations.length} {simulations.length === 1 ? 'entry' : 'entries'}
            {chart.buyAndHold && <> · buy-and-hold dashed</>}
          </Legend>
        </Right>
      </Head>
      <ChartWrap $h={height}>
        {chartData.length > 0 ? (
          <BenchmarkChart
            chartData={overlays.length > 0 ? mergeOverlaysIntoChartData(chartData, overlays) : chartData}
            simulations={simulations}
            timelineBounds={{
              start: new Date(ch.simulationStartDate),
              end: new Date(ch.simulationEndDate),
            }}
            overlays={overlays}
          />
        ) : (
          <EmptyChart>
            No chart data yet — waiting for the first simulation tick to land.
          </EmptyChart>
        )}
      </ChartWrap>
    </Root>
  );
}

const Root = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
`;

const Head = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  flex-wrap: wrap;
`;

const Left = styled.div`
  display: inline-flex;
  align-items: baseline;
  gap: 12px;
`;

const Right = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 12px;
`;

const Eye = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const Range = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Legend = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11.5px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const pulse = keyframes`
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
`;

const PollPip = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 2px;
  border: 1px solid rgba(0, 217, 255, 0.4);
  background: rgba(0, 217, 255, 0.05);
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.info};
  white-space: nowrap;
`;

const PollDot = styled.span`
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: ${p => p.theme.colors.info};
  box-shadow: 0 0 4px rgba(0, 217, 255, 0.6);
  animation: ${pulse} 1.4s ease-in-out infinite;
`;

const ChartWrap = styled.div<{ $h: number }>`
  padding: 12px 14px 16px;
  min-height: ${p => p.$h}px;
  display: flex;
  flex-direction: column;
`;

const EmptyChart = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};
`;
