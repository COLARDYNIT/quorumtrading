'use client';

import styled from 'styled-components';

/**
 * Count badge — the DS v0.4.0 `badge` species.
 *
 * Deliberately neutral: a badge reports how many, never how urgent. Colour on a count
 * makes readers infer severity that the number alone does not carry. If a count needs a
 * status, put the status on a StatusPill beside it.
 */
const Count = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  padding: 2px 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.labelSm};
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: ${p => p.theme.colors.fgMuted};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.surfaceElevated};
  border-radius: ${p => p.theme.borderRadius.label};
`;

interface BadgeProps {
  children: React.ReactNode;
  className?: string;
}

export function Badge({ children, className }: BadgeProps) {
  return <Count className={className}>{children}</Count>;
}
