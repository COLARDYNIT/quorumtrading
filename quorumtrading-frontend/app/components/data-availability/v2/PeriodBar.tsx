'use client';

import styled from 'styled-components';
import { format, parseISO } from 'date-fns';

interface PeriodBarProps {
  selectedMonth: Date;
  onMonthChange: (date: Date) => void;
  /** `/horizon` map (symbol → earliest ISO date) for the selected symbols. */
  horizon?: Record<string, string>;
}

const PENDING_C = '#9d8cff';

const MONTH_ABBR = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// Real year range: from a sensible floor up to next year. Selection is real
// (sets the query range). No fabricated coverage hints / gap-dots are rendered —
// the month buttons carry no dots (per-symbol trading-calendar signal not built).
function buildYears(): number[] {
  const current = new Date().getFullYear();
  const start = 2020;
  const years: number[] = [];
  for (let y = start; y <= current + 1; y++) years.push(y);
  return years;
}

const Bar = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 9px 16px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
`;

const Left = styled.div`
  display: flex;
  align-items: baseline;
  gap: 9px;
  flex-shrink: 0;
`;

const Label = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const Current = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 14px;
  color: ${p => p.theme.colors.fgStrong};
  font-variant-numeric: tabular-nums;
`;

const Group = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
`;

const GrpLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgSubtle};
  margin-right: 2px;
`;

const Div = styled.div`
  width: 1px;
  height: 22px;
  background: ${p => p.theme.colors.borderSubtle};
  flex-shrink: 0;
`;

const YrBtn = styled.button<{ $on: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  padding: 3px 8px;
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid ${p => (p.$on ? p.theme.colors.info : p.theme.colors.border)};
  background: ${p => (p.$on ? 'rgba(0,217,255,0.06)' : 'transparent')};
  color: ${p => (p.$on ? p.theme.colors.info : p.theme.colors.fgMuted)};
  cursor: pointer;
  transition: ${p => p.theme.transition.fast};

  &:hover {
    ${p => (!p.$on ? `color: ${p.theme.colors.foreground}; border-color: ${p.theme.colors.fgMuted};` : '')}
  }
`;

const MoBtn = styled.button<{ $on: boolean }>`
  position: relative;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  padding: 4px 7px;
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid ${p => (p.$on ? p.theme.colors.info : p.theme.colors.borderSubtle)};
  background: ${p => (p.$on ? 'rgba(0,217,255,0.08)' : p.theme.colors.background)};
  color: ${p => (p.$on ? p.theme.colors.fgStrong : p.theme.colors.fgMuted)};
  cursor: pointer;
  transition: ${p => p.theme.transition.fast};
  min-width: 30px;
  text-align: center;

  &:hover {
    ${p => (!p.$on ? `color: ${p.theme.colors.foreground};` : '')}
  }
`;

const PendingTag = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${PENDING_C};
  border: 1px dashed rgba(157, 140, 255, 0.5);
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: 1px 6px;
  white-space: nowrap;
`;

export function PeriodBar({ selectedMonth, onMonthChange, horizon }: PeriodBarProps) {
  const years = buildYears();
  const year = selectedMonth.getFullYear();
  const mi = selectedMonth.getMonth();

  const setYear = (y: number) => onMonthChange(new Date(y, mi, 1));
  const setMonth = (m: number) => onMonthChange(new Date(year, m, 1));

  // Earliest provider coverage across selected symbols (real /horizon data).
  // Unknown horizons are simply absent — never fabricated.
  const dates = Object.values(horizon ?? {}).map(d => d.slice(0, 10));
  const earliest = dates.length > 0 ? dates.reduce((a, b) => (a < b ? a : b)) : null;
  const earliestLabel = earliest ? format(parseISO(earliest), 'MMM yyyy') : null;

  return (
    <Bar>
      <Left>
        <Label>Period</Label>
        <Current>
          {MONTH_ABBR[mi]} {year}
        </Current>
      </Left>
      <Group>
        <GrpLabel>Year</GrpLabel>
        {years.map(y => (
          <YrBtn key={y} $on={y === year} onClick={() => setYear(y)}>
            {y}
          </YrBtn>
        ))}
      </Group>
      <Div />
      <Group>
        <GrpLabel>Month</GrpLabel>
        {MONTH_ABBR.map((m, i) => (
          <MoBtn key={m} $on={i === mi} onClick={() => setMonth(i)}>
            {m}
          </MoBtn>
        ))}
      </Group>
      {earliestLabel && (
        <Group
          style={{ marginLeft: 'auto' }}
          title="Earliest provider coverage across the selected symbols"
        >
          <PendingTag>coverage from {earliestLabel}</PendingTag>
        </Group>
      )}
    </Bar>
  );
}
