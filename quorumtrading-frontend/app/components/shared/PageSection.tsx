'use client';

import styled from 'styled-components';

interface PageSectionProps {
  /** Use gradient background like landing page hero */
  $gradient?: boolean;
  /** Include bottom border */
  $bordered?: boolean;
  /** Padding size variant */
  $size?: 'sm' | 'md' | 'lg';
}

/**
 * Consistent page section container matching landing page polish.
 *
 * Features:
 * - Optional gradient background (hero-style)
 * - Responsive padding
 * - Bottom border option
 *
 * Usage:
 *   <PageSection $gradient $bordered>
 *     <SectionTitle>My Section</SectionTitle>
 *     ...content...
 *   </PageSection>
 */
export const PageSection = styled.section<PageSectionProps>`
  padding: ${p => {
    const sizes = {
      sm: `${p.theme.spacing.xl} ${p.theme.spacing.lg}`,
      md: `${p.theme.spacing['2xl']} ${p.theme.spacing.xl}`,
      lg: `${p.theme.spacing['3xl']} ${p.theme.spacing.xl}`,
    };
    return sizes[p.$size || 'lg'];
  }};

  background: ${p => p.$gradient
    ? `linear-gradient(180deg, ${p.theme.colors.background} 0%, ${p.theme.colors.surface} 100%)`
    : p.theme.colors.background
  };

  border-bottom: ${p => p.$bordered
    ? `1px solid ${p.theme.colors.border}`
    : 'none'
  };

  @media (max-width: 768px) {
    padding: ${p => {
      const sizes = {
        sm: `${p.theme.spacing.md} ${p.theme.spacing.sm}`,
        md: `${p.theme.spacing.xl} ${p.theme.spacing.md}`,
        lg: `${p.theme.spacing['2xl']} ${p.theme.spacing.md}`,
      };
      return sizes[p.$size || 'lg'];
    }};
  }
`;

/**
 * Centered content container with max-width constraint.
 */
export const SectionContainer = styled.div<{ $maxWidth?: string }>`
  max-width: ${p => p.$maxWidth || '1100px'};
  margin: 0 auto;
`;
