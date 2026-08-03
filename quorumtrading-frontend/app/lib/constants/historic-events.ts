/**
 * Historic market events types and UI configuration.
 * Event data is now fetched from the API — see useHistoricalEvents() hook.
 */

export type EventCategory = 'crisis' | 'bull-run' | 'volatility' | 'catalyst' | 'bear-market';

export interface HistoricEvent {
  id: string;
  name: string;
  startDate: string;        // YYYY-MM-DD format
  endDate: string;          // YYYY-MM-DD format
  category: EventCategory;
  description: string;
  impactSummary: string;
  suggestedAssets: string[];
}

/**
 * Category display configuration for UI
 */
export const CATEGORY_CONFIG: Record<EventCategory, { label: string; colorVar: string }> = {
  'crisis': { label: 'Crisis', colorVar: 'terminalPink' },
  'bull-run': { label: 'Bull Run', colorVar: 'terminalGreen' },
  'volatility': { label: 'Volatility', colorVar: 'terminalAmber' },
  'catalyst': { label: 'Catalyst', colorVar: 'terminalBlue' },
  'bear-market': { label: 'Bear Market', colorVar: 'textMuted' },
};

/**
 * Get all unique categories from events
 */
export const EVENT_CATEGORIES: EventCategory[] = ['crisis', 'bull-run', 'volatility', 'catalyst', 'bear-market'];
