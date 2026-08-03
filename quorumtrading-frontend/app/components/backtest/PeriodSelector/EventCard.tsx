'use client';

import styled from 'styled-components';
import { CategoryBadge } from './CategoryBadge';
import type { EventCardProps } from './types';

const Card = styled.button.withConfig({
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

  &:focus {
    outline: none;
    box-shadow: 0 0 0 2px ${p => p.theme.colors.primary}44;
  }
`;

const CardHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const EventName = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.lg};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.terminalBlue};
`;

const DateRange = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  margin-bottom: ${p => p.theme.spacing.sm};
`;

const Description = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textLight};
  line-height: 1.5;
  margin: 0 0 ${p => p.theme.spacing.sm} 0;
`;

const ImpactSummary = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const SelectionIndicator = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== '$selected',
})<{ $selected: boolean }>`
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid ${p => p.$selected ? p.theme.colors.primary : p.theme.colors.textMuted};
  background: ${p => p.$selected ? p.theme.colors.primary : 'transparent'};
  flex-shrink: 0;
  position: relative;

  ${p => p.$selected && `
    &::after {
      content: '';
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: ${p.theme.colors.background};
    }
  `}
`;

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

export function EventCard({ event, selected, onSelect }: EventCardProps) {
  return (
    <Card
      type="button"
      $selected={selected}
      onClick={onSelect}
      aria-pressed={selected}
    >
      <CardHeader>
        <CategoryBadge category={event.category} />
        <SelectionIndicator $selected={selected} />
      </CardHeader>
      <EventName>{event.name}</EventName>
      <DateRange>{formatDateRange(event.startDate, event.endDate)}</DateRange>
      <Description>{event.description}</Description>
      <ImpactSummary>{event.impactSummary}</ImpactSummary>
    </Card>
  );
}
