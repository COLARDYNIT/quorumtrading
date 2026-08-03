'use client';

import styled from 'styled-components';
import { textShadows } from '@/app/styles/animations';

type TitleColor = 'primary' | 'cyan' | 'amber' | 'default';

interface SectionTitleProps {
  /** Color variant with matching text-shadow */
  $color?: TitleColor;
  /** Center align text */
  $centered?: boolean;
  /** Size variant */
  $size?: 'sm' | 'md' | 'lg' | 'xl';
}

const colorMap = {
  primary: (theme: { colors: { terminalGreen: string } }) => ({
    color: theme.colors.terminalGreen,
    shadow: textShadows.primary,
  }),
  cyan: (theme: { colors: { terminalBlue: string } }) => ({
    color: theme.colors.terminalBlue,
    shadow: textShadows.cyan,
  }),
  amber: (theme: { colors: { terminalAmber: string } }) => ({
    color: theme.colors.terminalAmber,
    shadow: textShadows.amber,
  }),
  default: (theme: { colors: { primary: string } }) => ({
    color: theme.colors.primary,
    shadow: textShadows.primary,
  }),
};

/**
 * Professional section title with landing-page-style text-shadow.
 *
 * Matches the refined typography from CouncilSection and HeroStrategyEditor.
 *
 * Usage:
 *   <SectionTitle $color="cyan" $centered>The Council Decides</SectionTitle>
 *   <SectionTitle $size="md">Active Simulations</SectionTitle>
 */
export const SectionTitle = styled.h2<SectionTitleProps>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => {
    const sizes = {
      sm: p.theme.typography.fontSize.lg,
      md: p.theme.typography.fontSize.xl,
      lg: p.theme.typography.fontSize['2xl'],
      xl: p.theme.typography.fontSize['3xl'],
    };
    return sizes[p.$size || 'lg'];
  }};
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0;
  margin-bottom: ${p => p.theme.spacing.lg};

  color: ${p => colorMap[p.$color || 'default'](p.theme).color};
  text-shadow: ${p => colorMap[p.$color || 'default'](p.theme).shadow};
  text-align: ${p => p.$centered ? 'center' : 'left'};

  @media (max-width: 768px) {
    font-size: ${p => {
      const sizes = {
        sm: p.theme.typography.fontSize.base,
        md: p.theme.typography.fontSize.lg,
        lg: p.theme.typography.fontSize.xl,
        xl: p.theme.typography.fontSize['2xl'],
      };
      return sizes[p.$size || 'lg'];
    }};
    margin-bottom: ${p => p.theme.spacing.md};
  }
`;

/**
 * Section subtitle with muted styling.
 */
export const SectionSubtitle = styled.p<{ $centered?: boolean }>`
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.6;
  max-width: 600px;
  margin: ${p => p.theme.spacing.lg} ${p => p.$centered ? 'auto' : '0'} 0;
  text-align: ${p => p.$centered ? 'center' : 'left'};

  @media (max-width: 768px) {
    font-size: ${p => p.theme.typography.fontSize.base};
  }
`;
