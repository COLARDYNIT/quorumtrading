'use client';

import styled, { useTheme } from 'styled-components';

interface SignalCardProps {
  name?: unknown;
  value?: unknown;
  interpretation?: unknown;
  [key: string]: unknown;
}

export function SignalCard({ name, value, interpretation }: SignalCardProps) {
  const theme = useTheme();
  const interpStr = typeof interpretation === 'string' ? interpretation : '';
  const lower = interpStr.toLowerCase();
  const color = !interpStr
    ? theme.colors.textDimmed
    : (lower === 'bullish' || lower === 'oversold' || lower === 'positive')
      ? theme.colors.terminalGreen
      : (lower === 'bearish' || lower === 'overbought' || lower === 'negative')
        ? theme.colors.terminalPink
        : theme.colors.terminalAmber;

  return (
    <Container>
      <SignalName>{String(name || 'Signal')}</SignalName>
      <SignalValue>{typeof value === 'number' ? value.toFixed(2) : String(value ?? '-')}</SignalValue>
      {interpStr && <Interpretation $color={color}>{interpStr}</Interpretation>}
    </Container>
  );
}

const Container = styled.div`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const SignalName = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textDimmed};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const SignalValue = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.textLight};
`;

const Interpretation = styled.span<{ $color: string }>`
  display: inline-block;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$color};
  background: ${p => p.$color}15;
  padding: 2px 8px;
  border-radius: 3px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  align-self: flex-start;
`;
