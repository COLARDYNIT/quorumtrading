'use client';

import { useState, useCallback } from 'react';
import styled from 'styled-components';
import { HistoricEvent } from '@/app/lib/constants/historic-events';
import { HistoricEventsTab } from './HistoricEventsTab';
import { CustomRangeTab } from './CustomRangeTab';
import type { PeriodSelectorProps, PeriodTab } from './types';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
  margin-top: ${p => p.theme.spacing.sm};
`;

const SectionLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  min-width: 90px;
`;

const TabRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
`;

const TabButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== '$active',
})<{ $active: boolean }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.normal};
  background: ${p => p.$active ? p.theme.colors.primary + '15' : 'transparent'};
  border: 1px solid ${p => p.$active ? p.theme.colors.primary : p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  color: ${p => p.$active ? p.theme.colors.primary : p.theme.colors.foreground};
  cursor: pointer;
  transition: all ${p => p.theme.transition.fast};

  &:hover {
    border-color: ${p => p.theme.colors.primary};
    color: ${p => p.theme.colors.primary};
  }

  &:focus {
    outline: none;
    box-shadow: 0 0 0 2px ${p => p.theme.colors.primary}44;
  }
`;

const TabIcon = styled.span`
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const TabContent = styled.div`
  min-height: 200px;
`;

/**
 * Converts an event's date (YYYY-MM-DD) to datetime-local format (YYYY-MM-DDTHH:MM)
 */
function eventDateToDatetimeLocal(dateStr: string, isEnd: boolean): string {
  // For start dates, use 00:00; for end dates, use 23:59
  const time = isEnd ? 'T23:59' : 'T00:00';
  return `${dateStr}${time}`;
}

export function PeriodSelector({
  startDate,
  endDate,
  onStartDateChange,
  onEndDateChange,
}: PeriodSelectorProps) {
  const [activeTab, setActiveTab] = useState<PeriodTab>('historic');
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);

  const handleEventSelect = useCallback((event: HistoricEvent) => {
    setSelectedEventId(event.id);
    onStartDateChange(eventDateToDatetimeLocal(event.startDate, false));
    onEndDateChange(eventDateToDatetimeLocal(event.endDate, true));
  }, [onStartDateChange, onEndDateChange]);

  const handleCustomDateChange = useCallback((
    setter: (date: string) => void
  ) => (date: string) => {
    // When user manually changes dates, clear the selected event
    setSelectedEventId(null);
    setter(date);
  }, []);

  return (
    <Container>
      <TabRow>
        <SectionLabel>Period:</SectionLabel>
        <TabButton
          type="button"
          $active={activeTab === 'historic'}
          onClick={() => setActiveTab('historic')}
          aria-selected={activeTab === 'historic'}
        >
          <TabIcon>&#x1F4C5;</TabIcon>
          Historic Events
        </TabButton>
        <TabButton
          type="button"
          $active={activeTab === 'custom'}
          onClick={() => setActiveTab('custom')}
          aria-selected={activeTab === 'custom'}
        >
          <TabIcon>&#x270F;&#xFE0F;</TabIcon>
          Custom Range
        </TabButton>
      </TabRow>

      <TabContent>
        {activeTab === 'historic' ? (
          <HistoricEventsTab
            onEventSelect={handleEventSelect}
            selectedEventId={selectedEventId}
          />
        ) : (
          <CustomRangeTab
            startDate={startDate}
            endDate={endDate}
            onStartDateChange={handleCustomDateChange(onStartDateChange)}
            onEndDateChange={handleCustomDateChange(onEndDateChange)}
          />
        )}
      </TabContent>
    </Container>
  );
}
