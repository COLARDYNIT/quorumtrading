/**
 * User Budget Types
 * Matches backend DTO: UserBudgetResponse
 */

export type BudgetStatus = 'AVAILABLE' | 'WARNING' | 'DEPLETED';

export interface UserBudget {
  id: string;
  /** Total budget limit in USD. Null if unlimited. */
  limitAmount: number | null;
  /** Current month's spend in USD (display only) */
  currentSpend: number;
  /** Total lifetime spend in USD (used for budget availability) */
  lifetimeSpend: number;
  /** Remaining budget in USD. Null if unlimited. */
  remainingBudget: number | null;
  /** Usage as percentage of limit (0-100+). 0 if unlimited. */
  usagePercentage: number;
  /** Current budget status */
  status: BudgetStatus;
  /** Whether the budget is unlimited */
  isUnlimited: boolean;
  /** Daily spend limit in USD. Null if no limit. */
  dailySpendLimit: number | null;
  /** ISO timestamp of last top-up. Null if never topped up. */
  lastTopUpAt: string | null;
  /** Today's spend in USD (billed, with markup) */
  todaySpend: number;
  /** Last 7 days' spend in USD (billed, with markup) */
  weeklySpend: number;
}

export interface TopUpBudgetRequest {
  /** Amount to add in USD */
  amount: number;
}

export interface UpdateDailySpendLimitRequest {
  /** Daily spend limit in USD. Null to clear. */
  dailySpendLimit: number | null;
}
