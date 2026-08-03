'use client';

import styled from 'styled-components';
import { textGlow } from '@/app/styles/theme/effects';

interface TerminalTextProps {
  variant?: 'primary' | 'secondary' | 'muted' | 'success' | 'warning' | 'error' | 'info';
  size?: 'xs' | 'sm' | 'base' | 'lg' | 'xl';
  weight?: 'normal' | 'medium' | 'semibold' | 'bold';
  mono?: boolean;
  children: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
}

const StyledText = styled.span.withConfig({
  shouldForwardProp: (prop) => !['variant', 'size', 'weight', 'mono'].includes(prop),
})<TerminalTextProps>`
  font-family: ${p => p.mono ? p.theme.typography.fontFamily.mono : 'inherit'};
  font-size: ${p => p.theme.typography.fontSize[p.size || 'base']};
  font-weight: ${p => p.theme.typography.fontWeight[p.weight || 'normal']};
  color: ${p => {
    const variant = p.variant || 'primary';
    switch (variant) {
      case 'primary': return p.theme.colors.textPrimary;
      case 'secondary': return p.theme.colors.textSecondary;
      case 'muted': return p.theme.colors.textMuted;
      case 'success': return p.theme.colors.success;
      case 'warning': return p.theme.colors.warning;
      case 'error': return p.theme.colors.error;
      case 'info': return p.theme.colors.info;
      default: return p.theme.colors.textPrimary;
    }
  }};

  /* Only apply green glow to primary/secondary/muted variants, not to status colors */
  ${p => {
    const variant = p.variant || 'primary';
    const shouldGlow = ['primary', 'secondary', 'muted'].includes(variant);
    return shouldGlow ? textGlow : '';
  }}
`;

export function TerminalText({
  variant = 'primary',
  size = 'base',
  weight = 'normal',
  mono = true,
  children,
  className,
  style,
}: TerminalTextProps) {
  return (
    <StyledText
      variant={variant}
      size={size}
      weight={weight}
      mono={mono}
      className={className}
      style={style}
    >
      {children}
    </StyledText>
  );
}
