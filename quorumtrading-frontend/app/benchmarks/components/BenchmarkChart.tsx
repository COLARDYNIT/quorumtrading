'use client';

import React from 'react';
import styled, { useTheme } from 'styled-components';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { BenchmarkSimulation, ChartDataPoint, OverlayResponse } from '@/app/lib/types/benchmark';
import { getOverlayColor } from '@/app/lib/utils/colors';

interface BenchmarkChartProps {
  chartData: ChartDataPoint[];
  simulations: BenchmarkSimulation[];
  timelineBounds: { start: Date; end: Date };
  overlays?: OverlayResponse[];
}

const ChartContainer = styled.div`
  width: 100%;
  height: 100%;
  background-color: transparent;
  padding: 20px;
  font-family: 'IBM Plex Mono', monospace;
  position: relative;
`;

const ChartTitle = styled.h2`
  font-size: 17px;
  font-weight: bold;
  color: ${p => p.theme.colors.terminalBlue};
  margin: 0 0 20px 0;
  text-transform: uppercase;
  letter-spacing: 2px;
`;

const CustomTooltipContainer = styled.div`
  background-color: ${p => p.theme.colors.chart.tooltip.background};
  border: 2px solid ${p => p.theme.colors.chart.tooltip.border};
  padding: 12px;
  font-size: 12px;
  color: ${p => p.theme.colors.chart.tooltip.text};
  box-shadow: 0 4px 12px rgba(0, 217, 255, 0.3);
`;

const TooltipTitle = styled.div`
  color: ${p => p.theme.colors.chart.tooltip.label};
  margin-bottom: 8px;
  font-weight: bold;
`;

const TooltipItem = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
`;

const TooltipDot = styled.div<{ $color: string }>`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: ${props => props.$color};
`;

const TooltipLabel = styled.span`
  flex: 1;
`;

const TooltipValue = styled.span`
  font-variant-numeric: tabular-nums;
`;

interface CustomTooltipProps {
  active?: boolean;
  payload?: Array<{
    name: string;
    value: number;
    color: string;
    dataKey: string;
  }>;
  label?: number;
  simulations: BenchmarkSimulation[];
  overlays?: OverlayResponse[];
}

const CustomTooltip: React.FC<CustomTooltipProps> = ({ active, payload, label, simulations, overlays = [] }) => {
  if (!active || !payload || !label) return null;

  const formatDate = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  };

  // Filter out null values and sort by portfolio value (descending)
  const activePayload = payload
    .filter(p => p.value !== null)
    .sort((a, b) => b.value - a.value);

  return (
    <CustomTooltipContainer>
      <TooltipTitle>{formatDate(label)}</TooltipTitle>
      {activePayload.map((entry) => {
        const isOverlay = entry.dataKey.startsWith('overlay_');
        const sim = !isOverlay ? simulations.find(s => s.id === entry.dataKey) : undefined;
        const overlaySymbol = isOverlay ? entry.dataKey.replace('overlay_', '') : undefined;
        const displayName = sim?.strategyName || overlaySymbol || entry.name;

        return (
          <TooltipItem key={entry.dataKey}>
            <TooltipDot $color={entry.color} />
            <TooltipLabel>{displayName}{isOverlay ? ' (B&H)' : ''}</TooltipLabel>
            <TooltipValue>{formatCurrency(entry.value)}</TooltipValue>
          </TooltipItem>
        );
      })}
    </CustomTooltipContainer>
  );
};

export function BenchmarkChart({ chartData, simulations, timelineBounds, overlays = [] }: BenchmarkChartProps) {
  const theme = useTheme();
  const colors = theme.colors;
  const readyOverlays = overlays.filter(o => o.status === 'READY');

  // Detect long-range benchmarks (> 1 year)
  const spanMs = timelineBounds.end.getTime() - timelineBounds.start.getTime();
  const spanYears = spanMs / (1000 * 60 * 60 * 24 * 365.25);
  const isLongRange = spanYears > 1;

  const formatDate = (timestamp: number) => {
    const date = new Date(timestamp);
    if (isLongRange) {
      // Long range: show "2022", "2023", etc.
      return String(date.getFullYear());
    }
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
    });
  };

  // For long-range charts: generate month-start ticks and render years + subtle month marks
  const xAxisTicks = React.useMemo(() => {
    if (!isLongRange) return undefined; // Let Recharts auto-generate
    const ticks: number[] = [];
    const d = new Date(timelineBounds.start);
    d.setUTCDate(1);
    d.setUTCHours(0, 0, 0, 0);
    d.setUTCMonth(d.getUTCMonth() + 1);
    while (d.getTime() < timelineBounds.end.getTime()) {
      ticks.push(d.getTime());
      d.setUTCMonth(d.getUTCMonth() + 1);
    }
    return ticks;
  }, [isLongRange, timelineBounds]);

  // Custom tick renderer: year label at January, small tick mark at other months
  const renderXTick = React.useCallback((props: any) => {
    const { x, y, payload } = props;
    const date = new Date(payload.value);
    const isJanuary = date.getUTCMonth() === 0;
    if (isJanuary) {
      return (
        <g transform={`translate(${x},${y})`}>
          <line x1={0} y1={0} x2={0} y2={4} stroke={colors.chart.axis} />
          <text x={0} y={16} textAnchor="middle" fill={colors.textMuted}
            style={{ fontSize: '10px', fontFamily: 'IBM Plex Mono, monospace' }}>
            {date.getFullYear()}
          </text>
        </g>
      );
    }
    // Non-January: subtle tick mark only
    return (
      <g transform={`translate(${x},${y})`}>
        <line x1={0} y1={0} x2={0} y2={3} stroke={colors.chart.axis} strokeOpacity={0.3} />
      </g>
    );
  }, []);

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(value);
  };

  // Calculate smart Y-axis domain based on data (including overlays)
  // Memoized to prevent infinite re-renders (array reference changes on every render)
  const yDomain = React.useMemo((): [number, number] => {
    if (chartData.length === 0) return [0, 100000];

    let minValue = Infinity;
    let maxValue = -Infinity;

    // Find min and max across all simulation data points
    chartData.forEach(dataPoint => {
      simulations.forEach(sim => {
        const value = dataPoint[sim.id];
        if (value !== null && value !== undefined) {
          minValue = Math.min(minValue, value);
          maxValue = Math.max(maxValue, value);
        }
      });
      // Include overlay values in domain calculation
      readyOverlays.forEach(overlay => {
        const value = dataPoint[`overlay_${overlay.symbol}`];
        if (value !== null && value !== undefined) {
          minValue = Math.min(minValue, value);
          maxValue = Math.max(maxValue, value);
        }
      });
    });

    // If no valid data found, use defaults
    if (minValue === Infinity || maxValue === -Infinity) {
      return [0, 100000];
    }

    // Percentage-based padding relative to data range
    const range = maxValue - minValue;
    const effectiveRange = range < 100 ? 500 : range; // Guard for tiny ranges
    const paddedMin = Math.max(0, minValue - 0.20 * effectiveRange);
    const paddedMax = maxValue + 0.10 * effectiveRange;

    return [paddedMin, paddedMax];
  }, [chartData, simulations, readyOverlays]);

  return (
    <ChartContainer>
      <ChartTitle>Total Account Value</ChartTitle>
      <ResponsiveContainer width="100%" height="95%">
        <LineChart
          data={chartData}
          margin={{ top: 10, right: 30, left: 10, bottom: 10 }}
          style={{ backgroundColor: 'transparent' }}
        >
          <CartesianGrid
            strokeDasharray="3 3"
            stroke={colors.chart.grid}
            opacity={0.5}
          />

          <XAxis
            dataKey="timestamp"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            stroke={colors.chart.axis}
            {...(isLongRange
              ? { ticks: xAxisTicks, tick: renderXTick, tickLine: false }
              : {
                  tickFormatter: formatDate,
                  tick: { fill: colors.textMuted },
                  style: { fontSize: '10px', fontFamily: 'IBM Plex Mono, monospace' },
                }
            )}
          />

          <YAxis
            domain={yDomain}
            tickFormatter={formatCurrency}
            stroke={colors.chart.axis}
            tick={{ fill: colors.textMuted }}
            style={{
              fontSize: '10px',
              fontFamily: 'IBM Plex Mono, monospace',
            }}
          />

          <Tooltip
            content={<CustomTooltip simulations={simulations} overlays={readyOverlays} />}
            cursor={{ stroke: colors.info, strokeWidth: 1, strokeDasharray: '5 5' }}
          />

          {/* Legend hidden - sidebar shows all strategies with colors */}

          {simulations.map(sim => (
            <Line
              key={sim.id}
              type="monotone"
              dataKey={sim.id}
              name={sim.strategyName}
              stroke={sim.color}
              strokeWidth={2.5}
              dot={false}
              activeDot={{ r: 6, fill: sim.color, stroke: '#fff', strokeWidth: 2 }}
              connectNulls={true}
              isAnimationActive={false}
            />
          ))}

          {/* Overlay lines (dashed, no dots) */}
          {readyOverlays.map(overlay => (
            <Line
              key={`overlay_${overlay.symbol}`}
              type="monotone"
              dataKey={`overlay_${overlay.symbol}`}
              name={overlay.symbol}
              stroke={getOverlayColor(overlay.symbol)}
              strokeWidth={2}
              strokeDasharray="8 4"
              dot={false}
              activeDot={{ r: 4, fill: getOverlayColor(overlay.symbol), stroke: '#fff', strokeWidth: 1 }}
              connectNulls={true}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </ChartContainer>
  );
}
