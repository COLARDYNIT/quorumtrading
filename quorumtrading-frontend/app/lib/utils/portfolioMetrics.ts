import type { ChartDataPoint } from '@/app/lib/types/api';

export interface PortfolioMetrics {
  currentValue: number;
  initialValue: number;
  totalReturn: number;      // percentage
  todayPnL: number;         // absolute dollar
  todayPnLPct: number;      // percentage
}

/**
 * Calculate portfolio metrics from chart data points.
 * First point = initial value, last point = current value.
 * Today's P&L is calculated from the data point closest to 24h ago.
 */
export function calculatePortfolioMetrics(chartData: ChartDataPoint[]): PortfolioMetrics {
  if (chartData.length === 0) {
    return {
      currentValue: 0,
      initialValue: 0,
      totalReturn: 0,
      todayPnL: 0,
      todayPnLPct: 0,
    };
  }

  const initialValue = chartData[0].portfolioValue;
  const currentValue = chartData[chartData.length - 1].portfolioValue;
  const totalReturn = initialValue > 0
    ? ((currentValue - initialValue) / initialValue) * 100
    : 0;

  // Find data point closest to 24 hours ago for Today's P&L
  const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;
  let dayAgoValue = initialValue;

  for (let i = chartData.length - 1; i >= 0; i--) {
    const pointTime = new Date(chartData[i].timestamp).getTime();
    if (pointTime <= oneDayAgo) {
      dayAgoValue = chartData[i].portfolioValue;
      break;
    }
  }

  const todayPnL = currentValue - dayAgoValue;
  const todayPnLPct = dayAgoValue > 0 ? (todayPnL / dayAgoValue) * 100 : 0;

  return {
    currentValue,
    initialValue,
    totalReturn,
    todayPnL,
    todayPnLPct,
  };
}
