'use client';

import styled from 'styled-components';
import { textGlow, boxGlow } from '@/app/styles/theme/effects';

interface TerminalButtonProps {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  onClick?: (e?: React.MouseEvent<HTMLButtonElement>) => void;
  children: React.ReactNode;
  className?: string;
  type?: 'button' | 'submit' | 'reset';
  style?: React.CSSProperties;
  title?: string;
}

const StyledButton = styled.button.withConfig({
  shouldForwardProp: (prop) => !['variant', 'size'].includes(prop),
})<TerminalButtonProps>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => {
    switch (p.size) {
      case 'sm': return p.theme.typography.fontSize.sm;
      case 'lg': return p.theme.typography.fontSize.lg;
      default: return p.theme.typography.fontSize.base;
    }
  }};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  padding: ${p => {
    switch (p.size) {
      case 'sm': return `${p.theme.spacing.xs} ${p.theme.spacing.sm}`;
      case 'lg': return `${p.theme.spacing.md} ${p.theme.spacing.lg}`;
      default: return `${p.theme.spacing.sm} ${p.theme.spacing.md}`;
    }
  }};

  border: 1px solid;
  border-color: ${p => {
    if (p.disabled) return p.theme.colors.disabled;
    switch (p.variant) {
      case 'primary': return p.theme.colors.primary;
      case 'secondary': return p.theme.colors.secondary;
      case 'danger': return p.theme.colors.error;
      case 'ghost': return 'transparent';
      default: return p.theme.colors.primary;
    }
  }};

  background-color: ${p => {
    if (p.disabled) return 'transparent';
    switch (p.variant) {
      case 'primary': return p.theme.colors.primary + '22'; // 13% opacity
      case 'secondary': return p.theme.colors.secondary + '22';
      case 'danger': return p.theme.colors.error + '22';
      case 'ghost': return 'transparent';
      default: return p.theme.colors.primary + '22';
    }
  }};

  color: ${p => {
    if (p.disabled) return p.theme.colors.disabled;
    switch (p.variant) {
      case 'primary': return p.theme.colors.primary;
      case 'secondary': return p.theme.colors.foreground;
      case 'danger': return p.theme.colors.error;
      case 'ghost': return p.theme.colors.foreground;
      default: return p.theme.colors.primary;
    }
  }};

  border-radius: ${p => p.theme.borderRadius.sm};
  cursor: ${p => p.disabled ? 'not-allowed' : 'pointer'};
  transition: ${p => p.theme.transition.fast};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};

  ${textGlow}
  ${boxGlow}

  &:hover:not(:disabled) {
    background-color: ${p => {
      switch (p.variant) {
        case 'primary': return p.theme.colors.primary + '33'; // 20% opacity
        case 'secondary': return p.theme.colors.secondary + '33';
        case 'danger': return p.theme.colors.error + '33';
        case 'ghost': return p.theme.colors.hover;
        default: return p.theme.colors.primary + '33';
      }
    }};
    border-color: ${p => {
      switch (p.variant) {
        case 'primary': return p.theme.colors.primary;
        case 'danger': return p.theme.colors.error;
        case 'ghost': return p.theme.colors.primary + '44';
        default: return p.theme.colors.primary;
      }
    }};
  }

  &:active:not(:disabled) {
    transform: translateY(1px);
  }
`;

export function TerminalButton({
  variant = 'primary',
  size = 'md',
  disabled = false,
  onClick,
  children,
  className,
  type = 'button',
  style,
  title,
}: TerminalButtonProps) {
  return (
    <StyledButton
      variant={variant}
      size={size}
      disabled={disabled}
      onClick={onClick}
      className={className}
      type={type}
      style={style}
      title={title}
    >
      {children}
    </StyledButton>
  );
}
