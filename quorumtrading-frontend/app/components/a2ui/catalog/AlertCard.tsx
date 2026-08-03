'use client';

import styled, { useTheme } from 'styled-components';

interface AlertCardProps {
  title?: unknown;
  message?: unknown;
  severity?: unknown;
  [key: string]: unknown;
}

type ThemeColors = { terminalPink: string; terminalAmber: string; terminalGreen: string; terminalBlue: string };

function getSeverityColor(severity: string, colors: ThemeColors): string {
  switch (severity.toLowerCase()) {
    case 'error':
    case 'critical': return colors.terminalPink;
    case 'warning': return colors.terminalAmber;
    case 'success': return colors.terminalGreen;
    default: return colors.terminalBlue;
  }
}

export function AlertCard({ title, message, severity }: AlertCardProps) {
  const theme = useTheme();
  const severityStr = String(severity || 'info');
  const color = getSeverityColor(severityStr, theme.colors as unknown as ThemeColors);

  return (
    <Card $color={color}>
      {title ? <Title $color={color}>{String(title)}</Title> : null}
      {message ? <Message>{String(message)}</Message> : null}
    </Card>
  );
}

const Card = styled.div<{ $color: string }>`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.$color}10;
  border: 1px solid ${p => p.$color}30;
  border-left: 3px solid ${p => p.$color};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const Title = styled.div<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.$color};
  margin-bottom: 4px;
`;

const Message = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
`;
