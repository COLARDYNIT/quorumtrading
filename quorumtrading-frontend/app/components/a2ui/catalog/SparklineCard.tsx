'use client';

import styled from 'styled-components';
import { ResponsiveContainer, LineChart, Line, YAxis } from 'recharts';

interface SparklineCardProps {
  label?: unknown;
  value?: unknown;
  change?: unknown;
  changePercent?: unknown;
  /** JSON array of numbers or [{value: number}] for the sparkline */
  data?: unknown;
  color?: unknown;
  [key: string]: unknown;
}

export function SparklineCard({ label, value, change, changePercent, data, color }: SparklineCardProps) {
  const lineColor = typeof color === 'string' ? color : 'var(--qt-agent-technical)';
  const changeNum = Number(change);
  const isPositive = changeNum > 0;
  const isNegative = changeNum < 0;

  let chartData: Array<{ index: number; value: number }> = [];
  if (typeof data === 'string') {
    try {
      const parsed = JSON.parse(data);
      if (Array.isArray(parsed)) {
        chartData = parsed.map((d: number | { value: number }, i: number) => ({
          index: i,
          value: typeof d === 'number' ? d : d.value,
        }));
      }
    } catch { /* ignore */ }
  } else if (Array.isArray(data)) {
    chartData = (data as Array<number | { value: number }>).map((d, i) => ({
      index: i,
      value: typeof d === 'number' ? d : d.value,
    }));
  }

  return (
    <Card>
      <Info>
        {label ? <Label>{String(label)}</Label> : null}
        <ValueRow>
          <Value>{String(value ?? '—')}</Value>
          {(change !== undefined || changePercent !== undefined) && (
            <Change $positive={isPositive} $negative={isNegative}>
              {isPositive ? '▲' : isNegative ? '▼' : ''}
              {change !== undefined && ` ${String(change)}`}
              {changePercent !== undefined && ` (${String(changePercent)}%)`}
            </Change>
          )}
        </ValueRow>
      </Info>
      {chartData.length > 1 && (
        <Sparkline>
          <ResponsiveContainer width="100%" height={40}>
            <LineChart data={chartData}>
              <YAxis hide domain={['auto', 'auto']} />
              <Line
                type="monotone"
                dataKey="value"
                stroke={isNegative ? 'var(--qt-action-sell)' : isPositive ? 'var(--qt-action-buy)' : lineColor}
                strokeWidth={1.5}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </Sparkline>
      )}
    </Card>
  );
}

const Card = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
`;

const Info = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
`;

const Label = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const ValueRow = styled.div`
  display: flex;
  align-items: baseline;
  gap: ${p => p.theme.spacing.sm};
`;

const Value = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.textPrimary};
`;

const Change = styled.span<{ $positive: boolean; $negative: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$positive ? p.theme.colors.success : p.$negative ? p.theme.colors.error : p.theme.colors.textMuted};
`;

const Sparkline = styled.div`
  width: 100px;
  flex-shrink: 0;
`;
