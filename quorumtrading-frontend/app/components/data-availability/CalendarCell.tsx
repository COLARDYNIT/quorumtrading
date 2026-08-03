'use client';

import { format, isWeekend } from 'date-fns';
import styled, { useTheme } from 'styled-components';
import { DailyCoverage } from '@/app/lib/types/dataAvailability';

interface Props {
  date: Date;
  coverage?: DailyCoverage;
  onClick: () => void;
}

const CellButton = styled.button<{
  $bgColor: string;
  $textColor: string;
  $isWeekend: boolean;
}>`
  aspect-ratio: 1;
  padding: ${p => p.theme.spacing.xs};
  border: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.$bgColor};
  color: ${p => p.$textColor};
  cursor: pointer;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  transition: ${p => p.theme.transition.fast};
  opacity: ${p => p.$isWeekend ? 0.75 : 1};
  position: relative;

  &:hover {
    border-color: ${p => p.theme.colors.primary};
    box-shadow: 0 0 10px ${p => p.theme.colors.primary}40;
    z-index: 1;
  }

  &:active {
    transform: translateY(1px);
  }
`;

const DayNumber = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
`;

const Percentage = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  margin-top: ${p => p.theme.spacing['2xs']};
  opacity: 0.9;
`;

const TooltipWrapper = styled.div`
  position: relative;

  &:hover .tooltip {
    opacity: 1;
    visibility: visible;
  }
`;

const Tooltip = styled.div`
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.primary};
  border-radius: ${p => p.theme.borderRadius.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  white-space: nowrap;
  z-index: 1000;
  opacity: 0;
  visibility: hidden;
  transition: ${p => p.theme.transition.fast};
  pointer-events: none;
  box-shadow: 0 0 15px ${p => p.theme.colors.primary}40;
`;

const TooltipLine = styled.div`
  margin: ${p => p.theme.spacing['2xs']} 0;
  color: ${p => p.theme.colors.foreground};
`;

const TooltipLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
  margin-right: ${p => p.theme.spacing.xs};
`;

export function CalendarCell({ date, coverage, onClick }: Props) {
  const theme = useTheme();
  const colors = theme.colors;
  const percentage = coverage?.percentage ?? 0;

  const { bgColor, textColor } = getCellColors(percentage, colors);
  const weekend = isWeekend(date);

  return (
    <TooltipWrapper>
      <CellButton
        onClick={onClick}
        $bgColor={bgColor}
        $textColor={textColor}
        $isWeekend={weekend}
        aria-label={`${format(date, 'MMM dd, yyyy')} - ${percentage.toFixed(1)}% coverage`}
      >
        <DayNumber>{format(date, 'd')}</DayNumber>
        {coverage && <Percentage>{percentage.toFixed(0)}%</Percentage>}
      </CellButton>

      {coverage && (
        <Tooltip className="tooltip">
          <TooltipLine>
            <TooltipLabel>Date:</TooltipLabel>
            {format(date, 'MMM dd, yyyy')}
          </TooltipLine>
          <TooltipLine>
            <TooltipLabel>Coverage:</TooltipLabel>
            {percentage.toFixed(1)}%
          </TooltipLine>
          {Object.entries(coverage.perType).map(([type, pct]) => (
            <TooltipLine key={type}>
              <TooltipLabel>{type}:</TooltipLabel>
              {pct.toFixed(1)}%
            </TooltipLine>
          ))}
        </Tooltip>
      )}

      {!coverage && (
        <Tooltip className="tooltip">
          <TooltipLine>No data available</TooltipLine>
        </Tooltip>
      )}
    </TooltipWrapper>
  );
}

function getCellColors(percentage: number, colors: { coverage: { full: string; high: string; medium: string; low: string; none: string }; textInverse: string; textWhite: string; primary: string }): { bgColor: string; textColor: string } {
  if (percentage >= 100) {
    return { bgColor: colors.coverage.full, textColor: colors.textInverse };
  }
  if (percentage >= 75) {
    return { bgColor: colors.coverage.high, textColor: colors.textInverse };
  }
  if (percentage >= 50) {
    return { bgColor: colors.coverage.medium, textColor: colors.textInverse };
  }
  if (percentage > 0) {
    return { bgColor: colors.coverage.low, textColor: colors.textWhite };
  }
  return { bgColor: colors.coverage.none, textColor: colors.primary };
}
