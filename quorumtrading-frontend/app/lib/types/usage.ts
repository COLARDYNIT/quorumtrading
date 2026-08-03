/**
 * LLM Usage Statistics Types
 * Matches backend DTO: LLMUsageStatisticsResponse
 */

export interface DailyUsage {
  date: string;
  rawCostUsd: number;
  billedCostUsd: number;
  totalTokens: number;
}

export interface LLMUsageStatistics {
  dailyUsage: DailyUsage[];
  weeklyTotalUsd: number;
  monthlyTotalUsd: number;
  allTimeTotalUsd: number;
}
