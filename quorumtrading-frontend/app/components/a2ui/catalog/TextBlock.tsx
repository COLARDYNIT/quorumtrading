'use client';

import styled from 'styled-components';

interface TextBlockProps {
  text?: unknown;
  variant?: unknown;
  [key: string]: unknown;
}

export function TextBlock({ text, variant }: TextBlockProps) {
  if (!text) return null;

  return (
    <Text $variant={String(variant || 'default')}>
      {String(text)}
    </Text>
  );
}

const Text = styled.p<{ $variant: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: ${p => {
    switch (p.$variant) {
      case 'heading': return p.theme.colors.terminalBlue;
      case 'highlight': return p.theme.colors.terminalGreen;
      case 'warning': return p.theme.colors.warning;
      case 'error': return p.theme.colors.error;
      default: return p.theme.colors.textMuted;
    }
  }};
  font-weight: ${p => p.$variant === 'heading' ? p.theme.typography.fontWeight.bold : 'normal'};
`;
