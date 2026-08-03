'use client';

import { useState, useMemo } from 'react';
import styled from 'styled-components';
import { TerminalButton } from '@/app/components/terminal';
import { CategoryBadge } from '@/app/components/backtest/PeriodSelector/CategoryBadge';
import { HistoricEvent, EventCategory, CATEGORY_CONFIG, EVENT_CATEGORIES } from '@/app/lib/constants/historic-events';
import { useHistoricalEvents, useRequestBackfill } from '@/app/lib/api/hooks';
import { EventType } from '@/app/lib/types/dataAvailability';

const Container = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.lg};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const SectionTitle = styled.h3`
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.terminalBlue};
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  text-shadow: 0 0 2px rgba(0, 217, 255, 0.4);
`;

const Description = styled.p`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  margin: 0;
`;

const FilterRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.xs};
`;

const FilterChip = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== '$active' && prop !== '$colorVar',
})<{ $active: boolean; $colorVar?: string }>`
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
  border-radius: ${p => p.theme.borderRadius.sm};
  cursor: pointer;
  transition: all ${p => p.theme.transition.fast};

  ${p => {
    if (p.$active) {
      const color = p.$colorVar
        ? p.theme.colors[p.$colorVar as keyof typeof p.theme.colors]
        : p.theme.colors.primary;
      return `
        background: ${color}22;
        border: 1px solid ${color};
        color: ${color};
      `;
    }
    return `
      background: transparent;
      border: 1px solid ${p.theme.colors.border};
      color: ${p.theme.colors.textMuted};
    `;
  }}

  &:hover {
    ${p => {
      const color = p.$colorVar
        ? p.theme.colors[p.$colorVar as keyof typeof p.theme.colors]
        : p.theme.colors.primary;
      return `
        border-color: ${color};
        color: ${color};
      `;
    }}
  }
`;

const EventsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
  max-height: 400px;
  overflow-y: auto;
  padding-right: ${p => p.theme.spacing.xs};

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
    border-radius: 3px;
  }

  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.border};
    border-radius: 3px;

    &:hover {
      background: ${p => p.theme.colors.textMuted};
    }
  }
`;

const EventCardStyled = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== '$selected',
})<{ $selected: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: ${p => p.theme.spacing.md};
  background: ${p => p.$selected ? p.theme.colors.primary + '15' : p.theme.colors.background};
  border: 1px solid ${p => p.$selected ? p.theme.colors.primary : p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  cursor: pointer;
  text-align: left;
  transition: all ${p => p.theme.transition.fast};

  &:hover {
    border-color: ${p => p.theme.colors.primary};
    background: ${p => p.theme.colors.primary}10;
  }
`;

const EventHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const EventName = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.terminalBlue};
`;

const DateRange = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const TickersList = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.xs};
  margin-top: ${p => p.theme.spacing.xs};
`;

const TickerBadge = styled.span`
  padding: 2px ${p => p.theme.spacing.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  background: ${p => p.theme.colors.primary}20;
  border: 1px solid ${p => p.theme.colors.primary}40;
  border-radius: ${p => p.theme.borderRadius.sm};
  color: ${p => p.theme.colors.primary};
`;

const SelectedEventSummary = styled.div`
  padding: ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.primary}10;
  border: 1px solid ${p => p.theme.colors.primary}40;
  border-radius: ${p => p.theme.borderRadius.md};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const SummaryRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const SummaryLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
`;

const SummaryValue = styled.span`
  color: ${p => p.theme.colors.primary};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const BackfillStatusStyled = styled.div<{ $status: 'idle' | 'pending' | 'success' | 'error' }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => {
    if (p.$status === 'success') return p.theme.colors.success;
    if (p.$status === 'error') return p.theme.colors.error;
    if (p.$status === 'pending') return p.theme.colors.warning;
    return p.theme.colors.textMuted;
  }};
  text-align: center;
  padding: ${p => p.theme.spacing.sm};
`;

type FilterValue = 'all' | EventCategory;

function mapCategory(apiCategory: string): EventCategory {
  const mapping: Record<string, EventCategory> = {
    'CRISIS': 'crisis',
    'BULL_RUN': 'bull-run',
    'VOLATILITY': 'volatility',
    'CATALYST': 'catalyst',
    'BEAR_MARKET': 'bear-market',
  };
  return mapping[apiCategory] || 'crisis';
}

function formatDateRange(startDate: string, endDate: string): string {
  const start = new Date(startDate);
  const end = new Date(endDate);

  const startMonth = start.toLocaleDateString('en-US', { month: 'short' });
  const endMonth = end.toLocaleDateString('en-US', { month: 'short' });
  const startYear = start.getFullYear();
  const endYear = end.getFullYear();

  if (startYear === endYear) {
    if (startMonth === endMonth) {
      return `${startMonth} ${startYear}`;
    }
    return `${startMonth} - ${endMonth} ${startYear}`;
  }
  return `${startMonth} ${startYear} - ${endMonth} ${endYear}`;
}

export function HistoricEventBackfillSelector() {
  const [filter, setFilter] = useState<FilterValue>('all');
  const [selectedEvent, setSelectedEvent] = useState<HistoricEvent | null>(null);
  const { data: apiEvents, isLoading } = useHistoricalEvents();
  const { mutate: requestBackfill, isPending, isSuccess, isError, reset } = useRequestBackfill();

  const events: HistoricEvent[] = useMemo(() =>
    (apiEvents ?? []).map(e => ({
      id: e.slug,
      name: e.name,
      startDate: e.startDate,
      endDate: e.endDate,
      category: mapCategory(e.category),
      description: e.description,
      impactSummary: e.impactSummary,
      suggestedAssets: e.suggestedAssets,
    })),
    [apiEvents]
  );

  const filteredEvents = filter === 'all'
    ? events
    : events.filter(event => event.category === filter);

  const handleEventSelect = (event: HistoricEvent) => {
    setSelectedEvent(event);
    reset();
  };

  const handleBackfill = () => {
    if (!selectedEvent) return;

    requestBackfill({
      symbols: selectedEvent.suggestedAssets,
      dataTypes: [EventType.PRICE],
      from: selectedEvent.startDate,
      to: selectedEvent.endDate,
      forceBackfill: false,
    });
  };

  const getStatus = (): 'idle' | 'pending' | 'success' | 'error' => {
    if (isPending) return 'pending';
    if (isSuccess) return 'success';
    if (isError) return 'error';
    return 'idle';
  };

  return (
    <Container>
      <SectionTitle>Historic Event Backfill</SectionTitle>
      <Description>
        Select a historic market event to backfill price data for the suggested assets.
      </Description>

      <FilterRow>
        <FilterChip
          type="button"
          $active={filter === 'all'}
          onClick={() => setFilter('all')}
        >
          All
        </FilterChip>
        {EVENT_CATEGORIES.map(category => (
          <FilterChip
            key={category}
            type="button"
            $active={filter === category}
            $colorVar={CATEGORY_CONFIG[category].colorVar}
            onClick={() => setFilter(category)}
          >
            {CATEGORY_CONFIG[category].label}
          </FilterChip>
        ))}
      </FilterRow>

      <EventsList>
        {isLoading ? (
          <Description>Loading events...</Description>
        ) : (
          filteredEvents.map(event => (
            <EventCardStyled
              key={event.id}
              type="button"
              $selected={selectedEvent?.id === event.id}
              onClick={() => handleEventSelect(event)}
            >
              <EventHeader>
                <CategoryBadge category={event.category} />
                <DateRange>{formatDateRange(event.startDate, event.endDate)}</DateRange>
              </EventHeader>
              <EventName>{event.name}</EventName>
              <TickersList>
                {event.suggestedAssets.map(ticker => (
                  <TickerBadge key={ticker}>{ticker}</TickerBadge>
                ))}
              </TickersList>
            </EventCardStyled>
          ))
        )}
      </EventsList>

      {selectedEvent && (
        <>
          <SelectedEventSummary>
            <SummaryRow>
              <SummaryLabel>Event:</SummaryLabel>
              <SummaryValue>{selectedEvent.name}</SummaryValue>
            </SummaryRow>
            <SummaryRow>
              <SummaryLabel>Period:</SummaryLabel>
              <SummaryValue>{selectedEvent.startDate} to {selectedEvent.endDate}</SummaryValue>
            </SummaryRow>
            <SummaryRow>
              <SummaryLabel>Symbols:</SummaryLabel>
              <SummaryValue>{selectedEvent.suggestedAssets.join(', ')}</SummaryValue>
            </SummaryRow>
            <SummaryRow>
              <SummaryLabel>Data Type:</SummaryLabel>
              <SummaryValue>PRICE</SummaryValue>
            </SummaryRow>
          </SelectedEventSummary>

          <TerminalButton
            onClick={handleBackfill}
            disabled={isPending}
            size="md"
          >
            {isPending ? 'Starting Backfill...' : `Backfill ${selectedEvent.name}`}
          </TerminalButton>

          {getStatus() !== 'idle' && (
            <BackfillStatusStyled $status={getStatus()}>
              {isPending && 'Backfill job submitted...'}
              {isSuccess && 'Backfill job started! Check the jobs panel for progress.'}
              {isError && 'Failed to start backfill. Please try again.'}
            </BackfillStatusStyled>
          )}
        </>
      )}
    </Container>
  );
}
