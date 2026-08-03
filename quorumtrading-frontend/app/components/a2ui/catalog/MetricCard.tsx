'use client';

import styled from 'styled-components';

interface MetricCardProps {
  label?: unknown;
  value?: unknown;
  delta?: unknown;
  deltaPercent?: unknown;
  trend?: unknown; // 'up' | 'down' | 'neutral'
  prefix?: unknown; // e.g. '$'
  suffix?: unknown; // e.g. '%'
  [key: string]: unknown;
}

export function MetricCard({ label, value, delta, deltaPercent, trend, prefix, suffix }: MetricCardProps) {
  const trendDir = String(trend ?? 'neutral');
  const deltaNum = Number(delta);
  const isPositive = trendDir === 'up' || deltaNum > 0;
  const isNegative = trendDir === 'down' || deltaNum < 0;

  return (
    <Card>
      {label ? <Label>{String(label)}</Label> : null}
      <Value>
        {prefix ? String(prefix) : ''}{String(value ?? '—')}{suffix ? String(suffix) : ''}
      </Value>
      {(delta !== undefined || deltaPercent !== undefined) && (
        <Delta $positive={isPositive} $negative={isNegative}>
          {isPositive ? '▲' : isNegative ? '▼' : '—'}{' '}
          {delta !== undefined && String(delta)}
          {deltaPercent !== undefined && ` (${String(deltaPercent)}%)`}
        </Delta>
      )}
    </Card>
  );
}

const Card = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  min-width: 120px;
`;

const Label = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const Value = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.textPrimary};
`;

const Delta = styled.div<{ $positive: boolean; $negative: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$positive ? p.theme.colors.success : p.$negative ? p.theme.colors.error : p.theme.colors.textMuted};
`;
