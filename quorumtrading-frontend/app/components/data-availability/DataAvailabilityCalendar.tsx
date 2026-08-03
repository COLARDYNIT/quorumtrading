'use client';

import { useMemo, useState, useEffect, useCallback } from 'react';
import styled, { useTheme } from 'styled-components';
import { format, eachDayOfInterval, startOfMonth, endOfMonth } from 'date-fns';
import { DataAvailabilityResponse, DailyCoverage, DataGap } from '@/app/lib/types/dataAvailability';
import { CalendarCell } from './CalendarCell';
import { TerminalButton } from '@/app/components/terminal';

type SelectionMode = 'day' | 'month';

interface Props {
  coverage?: DataAvailabilityResponse;
  isLoading: boolean;
  error: Error | null;
  selectedMonth: Date;
  onGapSelect: (gaps: DataGap[]) => void;
}

const Container = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.lg};
`;

const CalendarHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${p => p.theme.spacing.lg};
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.md};
`;

const MonthTitle = styled.h2`
  font-size: ${p => p.theme.typography.fontSize.xl};
  color: ${p => p.theme.colors.primary};
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const CoverageInfo = styled.div`
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const CoverageLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
`;

const CoverageValue = styled.span<{ $percentage: number }>`
  color: ${p => {
    if (p.$percentage >= 100) return p.theme.colors.success;
    if (p.$percentage >= 75) return p.theme.colors.warning;
    if (p.$percentage >= 50) return p.theme.colors.error;
    return p.theme.colors.error;
  }};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
`;

const DayHeaders = styled.div`
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: ${p => p.theme.spacing.xs};
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const DayHeader = styled.div`
  text-align: center;
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  padding: ${p => p.theme.spacing.xs};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const CalendarGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: ${p => p.theme.spacing.xs};
`;

const Legend = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.md};
  justify-content: center;
  margin-top: ${p => p.theme.spacing.lg};
  padding-top: ${p => p.theme.spacing.md};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

const LegendItem = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const LegendColor = styled.div<{ $color: string }>`
  width: 12px;
  height: 12px;
  background: ${p => p.$color};
  border: 1px solid ${p => p.theme.colors.border};
`;

const LegendLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
`;

const LoadingMessage = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${p => p.theme.spacing['2xl']};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
`;

const ErrorMessage = styled.div`
  border: 1px solid ${p => p.theme.colors.error};
  background: ${p => p.theme.colors.error}10;
  padding: ${p => p.theme.spacing.md};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const ErrorTitle = styled.p`
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  margin: 0 0 ${p => p.theme.spacing.xs} 0;
`;

const ErrorDetail = styled.p`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  margin: 0;
`;

const DAY_NAMES_SUN_START = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const DAY_NAMES_MON_START = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export function DataAvailabilityCalendar({
  coverage,
  isLoading,
  error,
  selectedMonth,
  onGapSelect,
}: Props) {
  const theme = useTheme();
  const colors = theme.colors;
  // Week start preference: true = Monday (European), false = Sunday (US)
  const [weekStartsMonday, setWeekStartsMonday] = useState(true);
  // Selection mode: day or month (default to month)
  const [selectionMode, setSelectionMode] = useState<SelectionMode>('month');

  const dayNames = weekStartsMonday ? DAY_NAMES_MON_START : DAY_NAMES_SUN_START;

  const days = useMemo(() => {
    return eachDayOfInterval({
      start: startOfMonth(selectedMonth),
      end: endOfMonth(selectedMonth),
    });
  }, [selectedMonth]);

  const coverageByDate = useMemo(() => {
    if (!coverage) return new Map();

    const map = new Map<string, DailyCoverage>();
    coverage.dailyCoverage.forEach(day => {
      map.set(day.date, day);
    });
    return map;
  }, [coverage]);

  // Calculate offset for first day of month based on week start preference
  const firstDayOffset = useMemo(() => {
    if (days.length === 0) return 0;
    const firstDayOfWeek = days[0].getDay();
    if (weekStartsMonday) {
      // Monday = 0, Sunday = 6
      return firstDayOfWeek === 0 ? 6 : firstDayOfWeek - 1;
    }
    // Sunday = 0, Saturday = 6
    return firstDayOfWeek;
  }, [days, weekStartsMonday]);

  // Get gaps for a date range
  const getGapsForRange = useCallback((fromDate: Date, toDate: Date, gaps: DataGap[]) => {
    const fromStr = format(fromDate, 'yyyy-MM-dd');
    const toStr = format(toDate, 'yyyy-MM-dd');

    // Find all gaps that overlap with the selected date range
    return gaps.filter(gap => {
      // Gap overlaps if it starts before range ends AND ends after range starts
      return gap.from <= toStr && gap.to >= fromStr;
    });
  }, []);

  // Handle day/month selection - clicking a day always switches to day mode
  const handleDateSelection = (day: Date) => {
    if (!coverage?.gaps) return;

    // When user clicks a specific day, switch to day mode
    setSelectionMode('day');

    // Select gaps for that specific day
    const selectedGaps = getGapsForRange(day, day, coverage.gaps);
    onGapSelect(selectedGaps);
  };

  // Memoize gaps array to avoid unnecessary effect triggers
  const gaps = coverage?.gaps;

  // Auto-select month's gaps when in month mode and coverage changes
  useEffect(() => {
    if (selectionMode === 'month' && gaps) {
      const fromDate = startOfMonth(selectedMonth);
      const toDate = endOfMonth(selectedMonth);
      const monthGaps = getGapsForRange(fromDate, toDate, gaps);
      onGapSelect(monthGaps);
    }
  }, [selectionMode, gaps, selectedMonth, getGapsForRange, onGapSelect]);

  if (isLoading) {
    return (
      <Container>
        <LoadingMessage>Loading coverage data...</LoadingMessage>
      </Container>
    );
  }

  if (error) {
    return (
      <Container>
        <ErrorMessage>
          <ErrorTitle>Error loading coverage data</ErrorTitle>
          <ErrorDetail>{error.message}</ErrorDetail>
        </ErrorMessage>
      </Container>
    );
  }

  return (
    <Container>
      <CalendarHeader>
        <MonthTitle>{format(selectedMonth, 'MMMM yyyy')}</MonthTitle>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          {/* Selection Mode Toggle */}
          <div style={{ display: 'flex', gap: '4px' }}>
            <TerminalButton
              size="sm"
              variant={selectionMode === 'day' ? 'primary' : 'ghost'}
              onClick={() => setSelectionMode('day')}
              title="Select by day"
            >
              Day
            </TerminalButton>
            <TerminalButton
              size="sm"
              variant={selectionMode === 'month' ? 'primary' : 'ghost'}
              onClick={() => setSelectionMode('month')}
              title="Select by month"
            >
              Month
            </TerminalButton>
          </div>
          {/* Week Start Toggle */}
          <TerminalButton
            size="sm"
            variant="ghost"
            onClick={() => setWeekStartsMonday(!weekStartsMonday)}
            title="Toggle week start day"
          >
            {weekStartsMonday ? 'MON-SUN' : 'SUN-SAT'}
          </TerminalButton>
          {/* Coverage Info */}
          {coverage && (
            <CoverageInfo>
              <CoverageLabel>Coverage: </CoverageLabel>
              <CoverageValue $percentage={coverage.overallCoverage}>
                {coverage.overallCoverage.toFixed(1)}%
              </CoverageValue>
            </CoverageInfo>
          )}
        </div>
      </CalendarHeader>

      <DayHeaders>
        {dayNames.map(day => (
          <DayHeader key={day}>{day}</DayHeader>
        ))}
      </DayHeaders>

      <CalendarGrid>
        {/* Empty cells for alignment */}
        {Array.from({ length: firstDayOffset }).map((_, i) => (
          <div key={`empty-${i}`} />
        ))}

        {/* Day cells */}
        {days.map(day => {
          const dateStr = format(day, 'yyyy-MM-dd');
          const dayCoverage = coverageByDate.get(dateStr);

          return (
            <CalendarCell
              key={dateStr}
              date={day}
              coverage={dayCoverage}
              onClick={() => handleDateSelection(day)}
            />
          );
        })}
      </CalendarGrid>

      <Legend>
        <LegendItem>
          <LegendColor $color={colors.coverage.full} />
          <LegendLabel>100%</LegendLabel>
        </LegendItem>
        <LegendItem>
          <LegendColor $color={colors.coverage.high} />
          <LegendLabel>75-99%</LegendLabel>
        </LegendItem>
        <LegendItem>
          <LegendColor $color={colors.coverage.medium} />
          <LegendLabel>50-74%</LegendLabel>
        </LegendItem>
        <LegendItem>
          <LegendColor $color={colors.coverage.low} />
          <LegendLabel>&lt;50%</LegendLabel>
        </LegendItem>
        <LegendItem>
          <LegendColor $color={colors.coverage.none} />
          <LegendLabel>No Data</LegendLabel>
        </LegendItem>
      </Legend>
    </Container>
  );
}
