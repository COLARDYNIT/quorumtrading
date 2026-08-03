'use client';

import styled from 'styled-components';

interface Stat {
  label: string;
  value: string;
  delta?: string;
  trend?: 'up' | 'down' | 'neutral';
}

interface StatGroupProps {
  /** JSON array of stats: [{"label":"Total Value","value":"$100,765","delta":"+0.77%","trend":"up"}] */
  stats?: unknown;
  label?: unknown;
  [key: string]: unknown;
}

export function StatGroup({ stats, label }: StatGroupProps) {
  let data: Stat[] = [];

  if (typeof stats === 'string') {
    try { data = JSON.parse(stats); } catch { return null; }
  } else if (Array.isArray(stats)) {
    data = stats as Stat[];
  } else {
    return null;
  }

  if (data.length === 0) return null;

  return (
    <Container>
      {label ? <GroupLabel>{String(label)}</GroupLabel> : null}
      <Grid $count={data.length}>
        {data.map((stat, i) => {
          const isPositive = stat.trend === 'up' || (stat.delta && !stat.delta.startsWith('-'));
          const isNegative = stat.trend === 'down' || (stat.delta && stat.delta.startsWith('-'));
          return (
            <StatBox key={i}>
              <StatLabel>{stat.label}</StatLabel>
              <StatValue>{stat.value}</StatValue>
              {stat.delta && (
                <StatDelta $positive={!!isPositive} $negative={!!isNegative}>
                  {isPositive ? '▲' : isNegative ? '▼' : ''} {stat.delta}
                </StatDelta>
              )}
            </StatBox>
          );
        })}
      </Grid>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const GroupLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const Grid = styled.div<{ $count: number }>`
  display: grid;
  grid-template-columns: repeat(${p => Math.min(p.$count, 4)}, 1fr);
  gap: 1px;
  background: ${p => p.theme.colors.border};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  overflow: hidden;
`;

const StatBox = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: ${p => p.theme.spacing.sm};
  background: ${p => p.theme.colors.background};
`;

const StatLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const StatValue = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.textPrimary};
`;

const StatDelta = styled.div<{ $positive: boolean; $negative: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$positive ? p.theme.colors.success : p.$negative ? p.theme.colors.error : p.theme.colors.textMuted};
`;
