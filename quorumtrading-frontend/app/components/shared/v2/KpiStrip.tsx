'use client';

import styled from 'styled-components';
import type { ReactNode } from 'react';

export interface KpiItem {
  /** ReactNode so a cell can carry an affordance (e.g. an info toggle) beside its text. */
  label: ReactNode;
  value: ReactNode;
  delta?: ReactNode;
  tone?: 'default' | 'positive' | 'negative' | 'hold' | 'dim';
}

interface KpiStripProps {
  items: KpiItem[];
  dense?: boolean;
  /**
   * Operator glyph drawn on the seam to the LEFT of each cell, so a strip can
   * read as an equation (seed + contributed = invested → value). One entry per
   * item; null leaves that seam bare. Index 0 is ignored — there is no seam
   * before the first cell.
   */
  ops?: Array<string | null>;
  /** Squares off the bottom corners when something is docked directly beneath. */
  flatBottom?: boolean;
}

const ChainRoot = styled.div`
  position: relative;
`;

const Op = styled.span<{ $left: number }>`
  position: absolute;
  top: 50%;
  left: ${p => p.$left}%;
  transform: translate(-50%, -50%);
  z-index: 2;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 50%;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.fgMuted};
  pointer-events: none;

  /* The strip collapses to two columns on narrow screens, where seams computed
     from the full column count no longer line up. */
  @media (max-width: 640px) {
    display: none;
  }
`;

const Strip = styled.div<{ $cols: number; $flatBottom?: boolean }>`
  display: grid;
  grid-template-columns: repeat(${p => p.$cols}, minmax(0, 1fr));
  gap: 1px;
  background: ${p => p.theme.colors.borderSubtle};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: ${p => (p.$flatBottom ? '4px 4px 0 0' : '4px')};
  overflow: hidden;

  @media (max-width: 640px) {
    grid-template-columns: ${p => p.$cols >= 3 ? 'repeat(2, minmax(0, 1fr))' : `repeat(${p.$cols}, minmax(0, 1fr))`};
  }
`;

const KpiCell = styled.div<{ $dense: boolean }>`
  background: ${p => p.theme.colors.surface};
  padding: ${p => (p.$dense ? '9px 12px' : '12px 16px')};
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
`;

const Label = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Value = styled.div<{ $tone: NonNullable<KpiItem['tone']>; $dense: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: ${p => (p.$dense ? '16px' : '19px')};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  color: ${p => {
    switch (p.$tone) {
      case 'positive': return p.theme.colors.positive;
      case 'negative': return p.theme.colors.negative;
      case 'hold': return p.theme.colors.action.hold;
      case 'dim': return p.theme.colors.fgMuted;
      default: return p.theme.colors.fgStrong;
    }
  }};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Delta = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  color: ${p => p.theme.colors.fgMuted};
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

export function KpiStrip({ items, dense = false, ops, flatBottom = false }: KpiStripProps) {
  const strip = (
    <Strip $cols={items.length} $flatBottom={flatBottom}>
      {items.map((item, idx) => (
        <KpiCell key={idx} $dense={dense}>
          <Label>{item.label}</Label>
          <Value $tone={item.tone ?? 'default'} $dense={dense}>{item.value}</Value>
          {item.delta != null && <Delta>{item.delta}</Delta>}
        </KpiCell>
      ))}
    </Strip>
  );

  if (!ops || ops.length === 0) return strip;

  return (
    <ChainRoot>
      {strip}
      {ops.map((op, idx) =>
        op && idx > 0 ? (
          <Op key={idx} $left={(idx * 100) / items.length} aria-hidden="true">{op}</Op>
        ) : null
      )}
    </ChainRoot>
  );
}
