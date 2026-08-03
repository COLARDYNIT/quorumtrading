import { HistoricEvent, EventCategory } from '@/app/lib/constants/historic-events';

export interface PeriodSelectorProps {
  startDate: string;  // datetime-local format: "YYYY-MM-DDTHH:MM"
  endDate: string;
  onStartDateChange: (date: string) => void;
  onEndDateChange: (date: string) => void;
}

export interface EventCardProps {
  event: HistoricEvent;
  selected: boolean;
  onSelect: () => void;
}

export interface CategoryBadgeProps {
  category: EventCategory;
  className?: string;
}

export interface HistoricEventsTabProps {
  onEventSelect: (event: HistoricEvent) => void;
  selectedEventId: string | null;
}

export interface CustomRangeTabProps {
  startDate: string;
  endDate: string;
  onStartDateChange: (date: string) => void;
  onEndDateChange: (date: string) => void;
}

export type PeriodTab = 'historic' | 'custom';
