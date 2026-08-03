'use client';

import styled, { css } from 'styled-components';
import type { ReactNode } from 'react';

export type PanelKind = 'default' | 'negative';

interface PanelProps {
  title: ReactNode;
  count?: ReactNode;
  headerActions?: ReactNode;
  children: ReactNode;
  /** Remove panel-body padding so the inner content (table, chart) controls its own gutters. */
  noBodyPadding?: boolean;
  /** Cap height + scroll the body. */
  bodyMaxHeight?: number;
  /**
   * Optional accent. `negative` paints a pink-tinted border + pink panel title for
   * destructive sections (e.g. /settings/account danger zone, terminate-plan rows).
   */
  kind?: PanelKind;
  className?: string;
}

const PanelRoot = styled.div<{ $kind: PanelKind }>`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  min-width: 0;

  ${p => p.$kind === 'negative' && css`
    border-color: rgba(255, 51, 102, 0.35);
  `}
`;

const PanelHead = styled.div<{ $kind: PanelKind }>`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  padding: 8px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  gap: 10px;

  ${p => p.$kind === 'negative' && css`
    border-bottom-color: rgba(255, 51, 102, 0.22);
  `}
`;

const TitleRow = styled.div<{ $kind: PanelKind }>`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;

  h3 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.$kind === 'negative' ? p.theme.colors.negative : p.theme.colors.fgStrong};
    letter-spacing: 0;
    margin: 0;
  }
`;

const Count = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const PanelBody = styled.div<{ $noPad: boolean; $maxH?: number }>`
  padding: ${p => (p.$noPad ? '0' : '10px')};
  flex: 1;
  min-height: 0;
  ${p => p.$maxH ? `max-height: ${p.$maxH}px; overflow-y: auto; scrollbar-gutter: stable;` : ''}
`;

export function Panel({ title, count, headerActions, children, noBodyPadding, bodyMaxHeight, kind = 'default', className }: PanelProps) {
  return (
    <PanelRoot className={className} $kind={kind}>
      <PanelHead $kind={kind}>
        <TitleRow $kind={kind}>
          <h3>{title}</h3>
          {count != null && <Count className="mono">{count}</Count>}
        </TitleRow>
        {headerActions}
      </PanelHead>
      <PanelBody $noPad={!!noBodyPadding} $maxH={bodyMaxHeight}>
        {children}
      </PanelBody>
    </PanelRoot>
  );
}
