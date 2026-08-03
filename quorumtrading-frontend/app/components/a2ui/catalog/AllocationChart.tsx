'use client';

import styled from 'styled-components';
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip } from 'recharts';

interface AllocationChartProps {
  label?: unknown;
  /** JSON array of segments: [{"name":"Cash","value":93870.77},{"name":"EQIX","value":6073.28}] */
  segments?: unknown;
  [key: string]: unknown;
}

const COLORS = [
  'var(--qt-action-buy)', 'var(--qt-agent-technical)', 'var(--qt-action-hold)',
  'var(--qt-action-sell)', 'var(--qt-agent-orchestrator)', '#a855f7', '#f97316',
  'var(--qt-action-neutral)',
];

export function AllocationChart({ label, segments }: AllocationChartProps) {
  let data: Array<{ name: string; value: number }> = [];

  if (typeof segments === 'string') {
    try { data = JSON.parse(segments); } catch { return null; }
  } else if (Array.isArray(segments)) {
    data = segments as Array<{ name: string; value: number }>;
  } else {
    return null;
  }

  if (data.length === 0) return null;

  const total = data.reduce((sum, d) => sum + d.value, 0);

  return (
    <Container>
      {label ? <ChartLabel>{String(label)}</ChartLabel> : null}
      <ChartRow>
        <ChartWrapper>
          <ResponsiveContainer width="100%" height={120}>
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={30}
                outerRadius={50}
                dataKey="value"
                stroke="none"
              >
                {data.map((_, i) => (
                  <Cell key={i} fill={COLORS[i % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  background: 'var(--qt-surface)',
                  border: '1px solid var(--qt-agent-technical)',
                  borderRadius: '4px',
                  fontFamily: 'monospace',
                  fontSize: '11px',
                }}
                formatter={(value, name) => [
                  typeof value === 'number'
                    ? `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                    : '',
                  String(name ?? ''),
                ]}
              />
            </PieChart>
          </ResponsiveContainer>
        </ChartWrapper>
        <Legend>
          {data.map((d, i) => (
            <LegendItem key={d.name}>
              <LegendDot $color={COLORS[i % COLORS.length]} />
              <LegendName>{d.name}</LegendName>
              <LegendValue>
                {total > 0 ? `${((d.value / total) * 100).toFixed(1)}%` : '0%'}
              </LegendValue>
            </LegendItem>
          ))}
        </Legend>
      </ChartRow>
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

const ChartRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm};
`;

const ChartWrapper = styled.div`
  flex-shrink: 0;
  width: 120px;
`;

const Legend = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
`;

const LegendItem = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
`;

const LegendDot = styled.span<{ $color: string }>`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${p => p.$color};
  flex-shrink: 0;
`;

const LegendName = styled.span`
  color: ${p => p.theme.colors.textLight};
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const LegendValue = styled.span`
  color: ${p => p.theme.colors.textMuted};
  flex-shrink: 0;
`;
