'use client';

import styled from 'styled-components';
import { TerminalInput } from '@/app/components/terminal/TerminalInput';
import type { CustomRangeTabProps } from './types';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const DateInputsRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
`;

const QuickSetSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const QuickSetLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
`;

const QuickSetButtons = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.xs};
`;

const QuickSetButton = styled.button`
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  color: ${p => p.theme.colors.textMuted};
  cursor: pointer;
  transition: all ${p => p.theme.transition.fast};

  &:hover {
    border-color: ${p => p.theme.colors.primary};
    color: ${p => p.theme.colors.primary};
    background: ${p => p.theme.colors.primary}10;
  }

  &:focus {
    outline: none;
    box-shadow: 0 0 0 2px ${p => p.theme.colors.primary}44;
  }
`;

const DurationDisplay = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  padding: ${p => p.theme.spacing.sm};
  background: ${p => p.theme.colors.background};
  border-radius: ${p => p.theme.borderRadius.sm};
  text-align: center;
`;

interface QuickSetPeriod {
  label: string;
  getRange: () => { start: string; end: string };
}

function toDatetimeLocal(date: Date): string {
  // Format as YYYY-MM-DDTHH:MM for datetime-local input
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

function getQuickSetPeriods(): QuickSetPeriod[] {
  const now = new Date();
  const currentYear = now.getFullYear();

  return [
    {
      label: 'YTD',
      getRange: () => ({
        start: toDatetimeLocal(new Date(currentYear, 0, 1, 0, 0)),
        end: toDatetimeLocal(now),
      }),
    },
    {
      label: `Full ${currentYear}`,
      getRange: () => ({
        start: toDatetimeLocal(new Date(currentYear, 0, 1, 0, 0)),
        end: toDatetimeLocal(new Date(currentYear, 11, 31, 23, 59)),
      }),
    },
    {
      label: `Full ${currentYear - 1}`,
      getRange: () => ({
        start: toDatetimeLocal(new Date(currentYear - 1, 0, 1, 0, 0)),
        end: toDatetimeLocal(new Date(currentYear - 1, 11, 31, 23, 59)),
      }),
    },
    {
      label: `Q4 ${currentYear - 1}`,
      getRange: () => ({
        start: toDatetimeLocal(new Date(currentYear - 1, 9, 1, 0, 0)),
        end: toDatetimeLocal(new Date(currentYear - 1, 11, 31, 23, 59)),
      }),
    },
    {
      label: `Q3 ${currentYear - 1}`,
      getRange: () => ({
        start: toDatetimeLocal(new Date(currentYear - 1, 6, 1, 0, 0)),
        end: toDatetimeLocal(new Date(currentYear - 1, 8, 30, 23, 59)),
      }),
    },
  ];
}

function calculateDuration(startDate: string, endDate: string): string {
  if (!startDate || !endDate) {
    return 'Select a date range';
  }

  const start = new Date(startDate);
  const end = new Date(endDate);

  if (isNaN(start.getTime()) || isNaN(end.getTime())) {
    return 'Invalid dates';
  }

  if (end <= start) {
    return 'End date must be after start date';
  }

  const diffMs = end.getTime() - start.getTime();
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));

  if (diffDays === 1) {
    return '1 day';
  } else if (diffDays < 30) {
    return `${diffDays} days`;
  } else if (diffDays < 365) {
    const months = Math.round(diffDays / 30);
    return `~${months} month${months > 1 ? 's' : ''} (${diffDays} days)`;
  } else {
    const years = (diffDays / 365).toFixed(1);
    return `~${years} years (${diffDays} days)`;
  }
}

export function CustomRangeTab({
  startDate,
  endDate,
  onStartDateChange,
  onEndDateChange,
}: CustomRangeTabProps) {
  const quickSetPeriods = getQuickSetPeriods();

  const handleQuickSet = (period: QuickSetPeriod) => {
    const range = period.getRange();
    onStartDateChange(range.start);
    onEndDateChange(range.end);
  };

  return (
    <Container>
      <DateInputsRow>
        <TerminalInput
          type="datetime-local"
          label="Start Date"
          value={startDate}
          onChange={(e) => onStartDateChange(e.target.value)}
          min="1993-01-29T00:00"
          required
          helperText="Earliest: Jan 29, 1993"
        />
        <TerminalInput
          type="datetime-local"
          label="End Date"
          value={endDate}
          onChange={(e) => onEndDateChange(e.target.value)}
          required
          helperText="End of backtest period"
        />
      </DateInputsRow>

      <QuickSetSection>
        <QuickSetLabel>Quick Set</QuickSetLabel>
        <QuickSetButtons>
          {quickSetPeriods.map(period => (
            <QuickSetButton
              key={period.label}
              type="button"
              onClick={() => handleQuickSet(period)}
            >
              {period.label}
            </QuickSetButton>
          ))}
        </QuickSetButtons>
      </QuickSetSection>

      <DurationDisplay>
        Duration: {calculateDuration(startDate, endDate)}
      </DurationDisplay>
    </Container>
  );
}
