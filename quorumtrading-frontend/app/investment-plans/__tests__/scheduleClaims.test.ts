/**
 * What the plan detail page claims about the next execution.
 *
 * The caption under the next-execution date read 'on schedule' for every ACTIVE plan, including on
 * the installs where the DCA scheduler is off — which is all of them by default, because the
 * scheduler moves real money and has to be enabled deliberately. So a user opened the page to
 * check whether their money had moved and was told everything was fine.
 *
 * The date itself is real: it is the schedule the plan would follow. Whether anything will act on
 * it is a separate question, and this is the screen where it has to be answered.
 */
import { nextExecutionDelta } from '../[id]/page';
import type { InvestmentPlan } from '@/app/lib/types/investmentPlan';

function plan(overrides: Partial<InvestmentPlan> = {}): InvestmentPlan {
  return {
    id: 'plan-1',
    userId: 'user-1',
    brokerAccountId: 'broker-1',
    budgetMode: 'FIXED_AMOUNT',
    amount: 500,
    currency: 'USD',
    dayOfMonth: 1,
    pickerPolicy: 'AI_PICK',
    preferences: {
      riskTolerance: null,
      allowedTickers: [],
      blockedTickers: [],
      maxStrategiesActive: null,
      preferExistingHoldings: true,
    },
    status: 'ACTIVE',
    nextExecutionAt: '2026-08-01T06:00:00Z',
    lastExecutionAt: null,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
    automaticExecutionEnabled: false,
    ...overrides,
  } as InvestmentPlan;
}

describe('nextExecutionDelta', () => {
  it('does not claim an ACTIVE plan is on schedule when nothing will run it', () => {
    expect(nextExecutionDelta(plan({ automaticExecutionEnabled: false })))
      .toBe('automatic execution off — run manually');
  });

  it('says on schedule only when the scheduler is actually enabled', () => {
    expect(nextExecutionDelta(plan({ automaticExecutionEnabled: true })))
      .toBe('on schedule');
  });

  it('reports a paused plan as paused regardless of the scheduler', () => {
    expect(nextExecutionDelta(plan({ status: 'PAUSED', automaticExecutionEnabled: true })))
      .toBe('plan paused');
    expect(nextExecutionDelta(plan({ status: 'PAUSED', automaticExecutionEnabled: false })))
      .toBe('plan paused');
  });

  it('reports a terminated plan as terminated regardless of the scheduler', () => {
    expect(nextExecutionDelta(plan({ status: 'TERMINATED', automaticExecutionEnabled: true })))
      .toBe('plan terminated');
  });
});
