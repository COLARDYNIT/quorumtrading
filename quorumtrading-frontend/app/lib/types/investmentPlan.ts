/**
 * Recurring investment plan types. Match backend DTOs:
 *   InvestmentPlanResponse, InvestmentAllocationResponse,
 *   CreateInvestmentPlanRequest, UpdateInvestmentPlanRequest.
 */

export type BudgetMode = 'FIXED_AMOUNT' | 'FIXED_OR_PARTIAL' | 'ALL_AVAILABLE';
export type PickerPolicy = 'AI_PICK';
export type InvestmentPlanStatus = 'ACTIVE' | 'PAUSED' | 'TERMINATED';

export type AllocationStatus =
  | 'EXECUTED'
  | 'SKIPPED_INSUFFICIENT_FUNDS'
  | 'SKIPPED_PICKER_FAILED'
  | 'SKIPPED_NO_ELIGIBLE_STRATEGY'
  | 'SKIPPED_TICKER_POLICY_VIOLATION'
  | 'FAILED_BROKER_ERROR';

export type AllocationAction = 'TOP_UP_EXISTING' | 'NEW_SIMULATION';

export interface InvestmentPlanPreferences {
  riskTolerance: string | null;
  allowedTickers: string[];
  blockedTickers: string[];
  maxStrategiesActive: number | null;
  preferExistingHoldings: boolean;
}

export interface InvestmentPlan {
  id: string;
  userId: string;
  brokerAccountId: string;
  budgetMode: BudgetMode;
  amount: number | null;
  currency: string;
  dayOfMonth: number;
  pickerPolicy: PickerPolicy;
  preferences: InvestmentPlanPreferences;
  status: InvestmentPlanStatus;
  nextExecutionAt: string | null;
  lastExecutionAt: string | null;
  createdAt: string;
  updatedAt: string;
  /**
   * Whether the DCA scheduler is running on this instance and will act on `nextExecutionAt`.
   *
   * False by default. When false, `nextExecutionAt` describes the schedule the plan *would*
   * follow and nothing will execute it — only "Execute now" will fund the plan. Render the
   * difference: an ACTIVE plan that silently never invests looks exactly like a working one.
   */
  automaticExecutionEnabled: boolean;
}

export interface InvestmentAllocation {
  id: string;
  planId: string;
  userId: string;
  executionDate: string;
  status: AllocationStatus;
  intendedAmount: number;
  actualAmount: number | null;
  currency: string;
  chosenStrategyId: string | null;
  pickerReasoning: string | null;
  pickerLlmCostUsd: number;
  action: AllocationAction | null;
  targetSimulationId: string | null;
  contributionId: string | null;
  brokerOrderRefs: string[];
  failureReason: string | null;
  createdAt: string;
}

export interface CreateInvestmentPlanRequest {
  brokerAccountId: string;
  budgetMode: BudgetMode;
  amount?: number | null;
  currency?: string;
  dayOfMonth: number;
  pickerPolicy: PickerPolicy;
  preferences?: Partial<InvestmentPlanPreferences>;
  confirmLive: boolean;
}

export interface UpdateInvestmentPlanRequest {
  brokerAccountId?: string;
  budgetMode?: BudgetMode;
  amount?: number;
  currency?: string;
  dayOfMonth?: number;
  preferences?: Partial<InvestmentPlanPreferences>;
}

export const BUDGET_MODE_LABELS: Record<BudgetMode, string> = {
  FIXED_AMOUNT: 'Fixed amount (skip if short)',
  FIXED_OR_PARTIAL: 'Fixed amount (accept partial)',
  ALL_AVAILABLE: 'All available balance',
};

export const ALLOCATION_STATUS_LABELS: Record<AllocationStatus, string> = {
  EXECUTED: 'Executed',
  SKIPPED_INSUFFICIENT_FUNDS: 'Skipped — insufficient funds',
  SKIPPED_PICKER_FAILED: 'Skipped — AI picker failed',
  SKIPPED_NO_ELIGIBLE_STRATEGY: 'Skipped — no eligible strategy',
  SKIPPED_TICKER_POLICY_VIOLATION: 'Skipped — strategy violates ticker policy',
  FAILED_BROKER_ERROR: 'Failed — broker error',
};
