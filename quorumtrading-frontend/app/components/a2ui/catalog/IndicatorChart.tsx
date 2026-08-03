'use client';

import styled from 'styled-components';
import { ResponsiveContainer, LineChart, Line, YAxis, Tooltip } from 'recharts';

interface DataPoint {
  value: number;
  label?: string;
}

interface IndicatorChartProps {
  label?: unknown;
  data?: unknown;
  color?: unknown;
  [key: string]: unknown;
}

export function IndicatorChart({ label, data, color }: IndicatorChartProps) {
  if (!Array.isArray(data) || data.length === 0) return null;

  const chartData = (data as DataPoint[]).map((d, i) => ({
    index: i,
    value: typeof d === 'number' ? d : d.value,
    label: typeof d === 'object' && d !== null ? d.label : undefined,
  }));

  const lineColor = typeof color === 'string' ? color : 'var(--qt-agent-technical)';

  return (
    <Container>
      {label ? <ChartLabel>{String(label)}</ChartLabel> : null}
      <ChartWrapper>
        <ResponsiveContainer width="100%" height={60}>
          <LineChart data={chartData}>
            <YAxis hide domain={['auto', 'auto']} />
            <Tooltip
              contentStyle={{
                background: 'var(--qt-surface)',
                border: '1px solid var(--qt-agent-technical)',
                borderRadius: '4px',
                fontFamily: 'monospace',
                fontSize: '11px',
              }}
              labelStyle={{ color: 'var(--qt-agent-technical)' }}
              itemStyle={{ color: 'var(--qt-fg)' }}
              formatter={(value) => [typeof value === 'number' ? value.toFixed(2) : '', '']}
            />
            <Line
              type="monotone"
              dataKey="value"
              stroke={lineColor}
              strokeWidth={1.5}
              dot={false}
              activeDot={{ r: 3, fill: lineColor }}
            />
          </LineChart>
        </ResponsiveContainer>
      </ChartWrapper>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const ChartLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const ChartWrapper = styled.div`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.xs};
`;
