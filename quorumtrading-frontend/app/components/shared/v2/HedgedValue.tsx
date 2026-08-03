'use client';

import styled from 'styled-components';

interface HedgedValueProps {
  /** Visible placeholder. Default em-dash. */
  label?: string;
  /** Native tooltip explaining the hedge. */
  title?: string;
  className?: string;
}

const Root = styled.span`
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  font-variant-numeric: tabular-nums;
`;

const Dash = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.fgSubtle};
`;

const Mark = styled.sup`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9px;
  color: ${p => p.theme.colors.terminalAmber};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  letter-spacing: 0;
  line-height: 1;
`;

export function HedgedValue({
  label = '—',
  title = 'Δ derived — field not on list endpoint',
  className,
}: HedgedValueProps) {
  return (
    <Root className={className} title={title}>
      <Dash>{label}</Dash>
      <Mark>Δ</Mark>
    </Root>
  );
}
