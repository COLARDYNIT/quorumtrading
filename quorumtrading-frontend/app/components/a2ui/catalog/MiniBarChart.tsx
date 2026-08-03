'use client';

import styled from 'styled-components';

interface Bar {
  name: string;
  value: number;
  color?: string;
}

interface MiniBarChartProps {
  label?: unknown;
  /** JSON array of bars: [{"name":"Strategy A","value":12.5},{"name":"Strategy B","value":-3.2,"color":"#ff3366"}] */
  bars?: unknown;
  /** Format values as percentages */
  percent?: unknown;
  /** Format values with $ prefix */
  dollar?: unknown;
  [key: string]: unknown;
}

const DEFAULT_COLORS = [
  'var(--qt-action-buy)', 'var(--qt-agent-technical)', 'var(--qt-action-hold)',
  'var(--qt-action-sell)', 'var(--qt-agent-orchestrator)', '#a855f7',
];

export function MiniBarChart({ label, bars, percent, dollar }: MiniBarChartProps) {
  let data: Bar[] = [];

  if (typeof bars === 'string') {
    try { data = JSON.parse(bars); } catch { return null; }
  } else if (Array.isArray(bars)) {
    data = bars as Bar[];
  } else {
    return null;
  }

  if (data.length === 0) return null;

  const maxAbs = Math.max(...data.map(b => Math.abs(b.value)), 0.01);
  const isPercent = percent === true || percent === 'true';
  const isDollar = dollar === true || dollar === 'true';

  function formatValue(v: number) {
    if (isDollar) return `$${v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    if (isPercent) return `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`;
    return v.toLocaleString(undefined, { maximumFractionDigits: 2 });
  }

  return (
    <Container>
      {label ? <ChartLabel>{String(label)}</ChartLabel> : null}
      <BarsContainer>
        {data.map((bar, i) => {
          const color = bar.color || DEFAULT_COLORS[i % DEFAULT_COLORS.length];
          const isNeg = bar.value < 0;
          const width = Math.min(Math.abs(bar.value) / maxAbs * 100, 100);

          return (
            <BarRow key={i}>
              <BarName>{bar.name}</BarName>
              <BarTrack>
                <BarFill $width={width} $color={color} $negative={isNeg} />
              </BarTrack>
              <BarValue $negative={isNeg}>{formatValue(bar.value)}</BarValue>
            </BarRow>
          );
        })}
      </BarsContainer>
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

const BarsContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm};
`;

const BarRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const BarName = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textLight};
  width: 90px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const BarTrack = styled.div`
  flex: 1;
  height: 14px;
  background: ${p => p.theme.colors.surface};
  border-radius: 2px;
  overflow: hidden;
`;

const BarFill = styled.div<{ $width: number; $color: string; $negative: boolean }>`
  height: 100%;
  width: ${p => p.$width}%;
  background: ${p => p.$color};
  opacity: ${p => p.$negative ? 0.7 : 0.85};
  border-radius: 2px;
  transition: width 0.3s ease;
`;

const BarValue = styled.div<{ $negative: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.$negative ? p.theme.colors.error : p.theme.colors.textPrimary};
  width: 70px;
  flex-shrink: 0;
  text-align: right;
`;
