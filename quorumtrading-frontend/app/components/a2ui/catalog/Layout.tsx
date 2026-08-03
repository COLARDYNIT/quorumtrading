'use client';

import styled from 'styled-components';
import type { ReactNode } from 'react';

interface LayoutProps {
  gap?: unknown;
  align?: unknown;
  justify?: unknown;
  wrap?: unknown;
  children?: ReactNode;
  [key: string]: unknown;
}

export function Row({ gap, align, justify, wrap, children }: LayoutProps) {
  return (
    <FlexRow
      $gap={typeof gap === 'number' ? `${gap}px` : String(gap || '8px')}
      $align={String(align || 'center')}
      $justify={String(justify || 'flex-start')}
      $wrap={wrap === true || wrap === 'true' ? 'wrap' : 'nowrap'}
    >
      {children}
    </FlexRow>
  );
}

export function Column({ gap, align, justify, children }: LayoutProps) {
  return (
    <FlexColumn
      $gap={typeof gap === 'number' ? `${gap}px` : String(gap || '8px')}
      $align={String(align || 'stretch')}
      $justify={String(justify || 'flex-start')}
    >
      {children}
    </FlexColumn>
  );
}

const FlexRow = styled.div<{ $gap: string; $align: string; $justify: string; $wrap: string }>`
  display: flex;
  flex-direction: row;
  gap: ${p => p.$gap};
  align-items: ${p => p.$align};
  justify-content: ${p => p.$justify};
  flex-wrap: ${p => p.$wrap};
`;

const FlexColumn = styled.div<{ $gap: string; $align: string; $justify: string }>`
  display: flex;
  flex-direction: column;
  gap: ${p => p.$gap};
  align-items: ${p => p.$align};
  justify-content: ${p => p.$justify};
`;
