'use client';

import styled from 'styled-components';
import { EventCategory, CATEGORY_CONFIG } from '@/app/lib/constants/historic-events';
import type { CategoryBadgeProps } from './types';

const StyledBadge = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== '$category',
})<{ $category: EventCategory }>`
  display: inline-block;
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid;

  ${p => {
    const colorVar = CATEGORY_CONFIG[p.$category].colorVar;
    const color = p.theme.colors[colorVar as keyof typeof p.theme.colors] || p.theme.colors.textMuted;
    return `
      border-color: ${color};
      background-color: ${color}22;
      color: ${color};
    `;
  }}
`;

export function CategoryBadge({ category, className }: CategoryBadgeProps) {
  const config = CATEGORY_CONFIG[category];

  return (
    <StyledBadge $category={category} className={className}>
      {config.label}
    </StyledBadge>
  );
}
