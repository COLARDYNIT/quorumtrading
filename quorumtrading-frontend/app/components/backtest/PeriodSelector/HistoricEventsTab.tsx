'use client';

import { useState, useMemo } from 'react';
import styled from 'styled-components';
import { EVENT_CATEGORIES, CATEGORY_CONFIG, EventCategory } from '@/app/lib/constants/historic-events';
import { useHistoricalEvents } from '@/app/lib/api/hooks';
import { EventCard } from './EventCard';
import type { HistoricEventsTabProps } from './types';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const SectionLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  min-width: 90px;
`;

const FilterRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
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

  &:focus {
    outline: none;
    box-shadow: 0 0 0 2px ${p => p.theme.colors.primary}44;
  }
`;

const EventsGrid = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
  max-height: 400px;
  overflow-y: auto;
  padding-right: ${p => p.theme.spacing.xs};

  /* Custom scrollbar */
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

const NoResults = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  text-align: center;
  padding: ${p => p.theme.spacing.xl};
`;

type FilterValue = 'all' | EventCategory;

/**
 * Map backend UPPER_SNAKE category to frontend kebab-case EventCategory.
 */
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

export function HistoricEventsTab({ onEventSelect, selectedEventId }: HistoricEventsTabProps) {
  const [filter, setFilter] = useState<FilterValue>('all');
  const { data: apiEvents, isLoading } = useHistoricalEvents();

  const events = useMemo(() =>
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

  return (
    <Container>
      <FilterRow>
        <SectionLabel>Category:</SectionLabel>
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

      <EventsGrid>
        {isLoading ? (
          <NoResults>Loading events...</NoResults>
        ) : filteredEvents.length === 0 ? (
          <NoResults>No events match the selected filter.</NoResults>
        ) : (
          filteredEvents.map(event => (
            <EventCard
              key={event.id}
              event={event}
              selected={selectedEventId === event.id}
              onSelect={() => onEventSelect(event)}
            />
          ))
        )}
      </EventsGrid>
    </Container>
  );
}
