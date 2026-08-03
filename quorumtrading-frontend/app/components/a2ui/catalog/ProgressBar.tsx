'use client';

import styled from 'styled-components';

interface ProgressBarProps {
  label?: unknown;
  /** Progress value 0–100 */
  value?: unknown;
  /** Optional detail text shown after the percentage, e.g. "Day 45/120" */
  detail?: unknown;
  [key: string]: unknown;
}

export function ProgressBar({ label, value, detail }: ProgressBarProps) {
  const numValue = typeof value === 'number' ? Math.min(100, Math.max(0, value)) : 0;

  return (
    <Container>
      {label ? <Label>{String(label)}</Label> : null}
      <BarTrack>
        <BarFill $width={numValue} />
      </BarTrack>
      <ValueLabel>
        {numValue.toFixed(0)}%
        {detail ? <Detail> {String(detail)}</Detail> : null}
      </ValueLabel>
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

const BarFill = styled.div<{ $width: number }>`
  height: 100%;
  width: ${p => p.$width}%;
  background: ${p => p.theme.colors.terminalBlue};
  border-radius: 3px;
  transition: width 0.3s ease;
`;

const ValueLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalBlue};
  min-width: 40px;
  text-align: right;
  white-space: nowrap;
`;

const Detail = styled.span`
  font-weight: ${p => p.theme.typography.fontWeight.normal};
  color: ${p => p.theme.colors.textDimmed};
`;
