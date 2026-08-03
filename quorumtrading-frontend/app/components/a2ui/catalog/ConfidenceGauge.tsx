'use client';

import styled, { useTheme } from 'styled-components';

interface ConfidenceGaugeProps {
  label?: unknown;
  value?: unknown;
  [key: string]: unknown;
}

export function ConfidenceGauge({ label, value }: ConfidenceGaugeProps) {
  const theme = useTheme();
  const numValue = typeof value === 'number' ? Math.min(100, Math.max(0, value)) : 0;
  const color = numValue >= 70 ? theme.colors.terminalGreen : numValue >= 40 ? theme.colors.terminalAmber : theme.colors.terminalPink;

  return (
    <Container>
      {label ? <Label>{String(label)}</Label> : null}
      <BarTrack>
        <BarFill $width={numValue} $color={color} />
      </BarTrack>
      <ValueLabel $color={color}>{numValue.toFixed(0)}%</ValueLabel>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
`;

const Label = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  min-width: 80px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const BarTrack = styled.div`
  flex: 1;
  height: 8px;
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  overflow: hidden;
`;

const BarFill = styled.div<{ $width: number; $color: string }>`
  height: 100%;
  width: ${p => p.$width}%;
  background: ${p => p.$color};
  border-radius: 3px;
  transition: width 0.3s ease;
`;

const ValueLabel = styled.span<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$color};
  min-width: 40px;
  text-align: right;
`;
