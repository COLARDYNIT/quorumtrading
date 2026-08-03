'use client';

import styled from 'styled-components';
import { textGlow } from '@/app/styles/theme/effects';

interface TerminalBadgeProps {
  variant?: 'success' | 'warning' | 'error' | 'info' | 'neutral';
  children: React.ReactNode;
  className?: string;
}

const StyledBadge = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== 'variant',
})<TerminalBadgeProps>`
  display: inline-block;
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
  border-radius: ${p => p.theme.borderRadius.sm};

  border: 1px solid;
  border-color: ${p => {
    switch (p.variant) {
      case 'success': return p.theme.colors.success;
      case 'warning': return p.theme.colors.warning;
      case 'error': return p.theme.colors.error;
      case 'info': return p.theme.colors.info;
      case 'neutral': return p.theme.colors.textMuted;
      default: return p.theme.colors.success;
    }
  }};

  background-color: ${p => {
    switch (p.variant) {
      case 'success': return p.theme.colors.success + '22'; // 13% opacity
      case 'warning': return p.theme.colors.warning + '22';
      case 'error': return p.theme.colors.error + '22';
      case 'info': return p.theme.colors.info + '22';
      case 'neutral': return p.theme.colors.textMuted + '22';
      default: return p.theme.colors.success + '22';
    }
  }};

  color: ${p => {
    switch (p.variant) {
      case 'success': return p.theme.colors.success;
      case 'warning': return p.theme.colors.warning;
      case 'error': return p.theme.colors.error;
      case 'info': return p.theme.colors.info;
      case 'neutral': return p.theme.colors.textMuted;
      default: return p.theme.colors.success;
    }
  }};

  ${textGlow}
`;

export function TerminalBadge({
  variant = 'neutral',
  children,
  className,
}: TerminalBadgeProps) {
  return (
    <StyledBadge variant={variant} className={className}>
      {children}
    </StyledBadge>
  );
}
