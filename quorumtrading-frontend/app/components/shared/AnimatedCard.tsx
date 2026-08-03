'use client';

import styled, { css } from 'styled-components';
import { fadeInUp, shadows } from '@/app/styles/animations';

interface AnimatedCardProps {
  /** Accent color for hover glow */
  $accentColor?: string;
  /** Animation delay in ms for staggered effects */
  $delay?: number;
  /** Disable hover effects */
  $static?: boolean;
  /** Use premium multi-layer shadow */
  $premium?: boolean;
}

/**
 * Premium card component with landing-page-quality polish.
 *
 * Features:
 * - Fade-in-up entrance animation with optional delay
 * - Hover lift effect (translateY -4px)
 * - Multi-layer shadows
 * - Optional accent color glow on hover
 *
 * Usage:
 *   <AnimatedCard $delay={100} $accentColor="#00ff41">
 *     <CardContent>...</CardContent>
 *   </AnimatedCard>
 *
 *   <AnimatedCard $premium $delay={200}>
 *     ...hero-style card...
 *   </AnimatedCard>
 */
export const AnimatedCard = styled.div<AnimatedCardProps>`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  overflow: hidden;

  /* Entrance animation */
  animation: ${fadeInUp} 0.5s ease-out both;
  animation-delay: ${p => p.$delay || 0}ms;

  /* Shadow based on premium flag */
  box-shadow: ${p => p.$premium ? shadows.cardPremium : shadows.card};

  /* Hover effects */
  ${p => !p.$static && css`
    transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;

    &:hover {
      transform: translateY(-4px);
      border-color: ${p.$accentColor || p.theme.colors.primary};
      box-shadow: ${p.$premium
        ? shadows.cardHover
        : `var(--qt-shadow-card-hover), 0 0 20px color-mix(in srgb, ${p.$accentColor || p.theme.colors.primary} 20%, transparent)`
      };
    }
  `}
`;

/**
 * Card header with colored accent.
 */
export const CardHeader = styled.div<{ $color?: string }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.md};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.$color || p.theme.colors.primary};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: 600;
  font-size: ${p => p.theme.typography.fontSize.sm};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

/**
 * Card content area with standard padding.
 */
export const CardContent = styled.div<{ $noPadding?: boolean }>`
  padding: ${p => p.$noPadding ? 0 : p.theme.spacing.md};
`;

/**
 * Card footer with subtle background.
 */
export const CardFooter = styled.div<{ $color?: string }>`
  padding: ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.$color ? `color-mix(in srgb, ${p.$color} 4%, transparent)` : p.theme.colors.surface};
  text-align: center;
`;

/**
 * Grid container for card layouts with staggered animations.
 * Children automatically get staggered animation delays.
 */
export const CardGrid = styled.div<{ $columns?: number; $gap?: string }>`
  display: grid;
  grid-template-columns: repeat(${p => p.$columns || 3}, 1fr);
  gap: ${p => p.$gap || p.theme.spacing.lg};

  @media (max-width: 968px) {
    grid-template-columns: repeat(2, 1fr);
    gap: ${p => p.theme.spacing.md};
  }

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }

  /* Staggered animation for children */
  > * {
    &:nth-child(1) { animation-delay: 0ms; }
    &:nth-child(2) { animation-delay: 100ms; }
    &:nth-child(3) { animation-delay: 200ms; }
    &:nth-child(4) { animation-delay: 300ms; }
    &:nth-child(5) { animation-delay: 400ms; }
    &:nth-child(6) { animation-delay: 500ms; }
    &:nth-child(7) { animation-delay: 600ms; }
    &:nth-child(8) { animation-delay: 700ms; }
    &:nth-child(9) { animation-delay: 800ms; }
  }
`;
