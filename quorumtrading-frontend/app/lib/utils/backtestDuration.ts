export type WarningLevel = 'none' | 'mild' | 'moderate' | 'severe';

export interface DurationWarning {
  level: WarningLevel;
  days: number;
  title: string;
  message: string;
}

/**
 * Calculate the number of calendar days between two date strings.
 * Returns 0 if either date is missing or invalid, or if end <= start.
 */
export function calculateDurationDays(startDate: string, endDate: string): number {
  if (!startDate || !endDate) return 0;
  const start = new Date(startDate);
  const end = new Date(endDate);
  if (isNaN(start.getTime()) || isNaN(end.getTime())) return 0;
  const diffMs = end.getTime() - start.getTime();
  if (diffMs <= 0) return 0;
  return Math.floor(diffMs / (1000 * 60 * 60 * 24));
}

/**
 * Return a warning level and message based on the backtest duration.
 *
 * Thresholds:
 *   < 90 days  → none
 *   90–179     → mild   (informational)
 *   180–364    → moderate (caution)
 *   >= 365     → severe (strong)
 */
export function getDurationWarning(startDate: string, endDate: string): DurationWarning {
  const days = calculateDurationDays(startDate, endDate);

  if (days < 90) {
    return { level: 'none', days, title: '', message: '' };
  }

  if (days < 180) {
    return {
      level: 'mild',
      days,
      title: 'Backtest Duration Notice',
      message: `This backtest spans ${days} days. Each trading day triggers 4+ AI agent consensus calls, which consumes AI budget. Consider starting with a shorter period to validate your strategy before running longer backtests.`,
    };
  }

  if (days < 365) {
    return {
      level: 'moderate',
      days,
      title: 'Long Backtest Duration',
      message: `This backtest spans ${days} days — that's substantial AI budget usage. Each trading day triggers 4+ AI agent consensus calls. Consider starting with a 1–2 month backtest to validate your strategy first.`,
    };
  }

  return {
    level: 'severe',
    days,
    title: 'Extended Backtest Duration',
    message: `This backtest spans ${days} days (${Math.round(days / 30)} months). This will consume significant AI budget and take considerable time to complete. Each trading day triggers 4+ AI agent consensus calls. We strongly recommend starting with a shorter period (1–3 months) first.`,
  };
}
