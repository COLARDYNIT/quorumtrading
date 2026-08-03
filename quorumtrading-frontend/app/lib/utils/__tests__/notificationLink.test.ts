import { notificationLink } from '../notificationLink';
import type { Notification } from '@/app/lib/types/notification';

function makeNotification(overrides: Partial<Notification>): Notification {
  return {
    id: 'n-1',
    type: 'INVESTMENT_FAILED',
    channel: 'IN_APP',
    title: 'Investment plan execution failed',
    body: 'Your broker rejected this month\'s contribution.',
    payload: {},
    relatedEntityId: null,
    status: 'DELIVERED',
    createdAt: '2026-05-17T16:36:32Z',
    deliveredAt: '2026-05-17T16:36:32Z',
    readAt: null,
    failureReason: null,
    ...overrides,
  };
}

describe('notificationLink', () => {
  it('prefers payload.planId over relatedEntityId for INVESTMENT_FAILED', () => {
    // Reproduces the original prod bug: relatedEntityId is the allocation UUID,
    // payload.planId is the actual plan UUID. We must link to the plan.
    const n = makeNotification({
      type: 'INVESTMENT_FAILED',
      relatedEntityId: '12fc6f43-14ef-4192-ae2f-50c051947cb0', // allocationId
      payload: { planId: '9f0883f0-c952-4884-b45d-02af7f671051' },
    });
    expect(notificationLink(n)).toBe('/investment-plans/9f0883f0-c952-4884-b45d-02af7f671051');
  });

  it.each(['INVESTMENT_EXECUTED', 'INVESTMENT_SKIPPED', 'INVESTMENT_FAILED'] as const)(
    'uses payload.planId for %s',
    (type) => {
      const n = makeNotification({
        type,
        relatedEntityId: 'allocation-uuid',
        payload: { planId: 'plan-uuid' },
      });
      expect(notificationLink(n)).toBe('/investment-plans/plan-uuid');
    }
  );

  it('falls back to relatedEntityId when planId is missing (PLAN_PAUSED contract)', () => {
    const n = makeNotification({
      type: 'PLAN_PAUSED',
      relatedEntityId: 'plan-uuid',
      payload: {},
    });
    expect(notificationLink(n)).toBe('/investment-plans/plan-uuid');
  });

  it('falls back to the index page when neither planId nor relatedEntityId is set', () => {
    const n = makeNotification({
      type: 'INVESTMENT_FAILED',
      relatedEntityId: null,
      payload: {},
    });
    expect(notificationLink(n)).toBe('/investment-plans');
  });

  it('returns null for non-plan notification types', () => {
    const n = makeNotification({
      type: 'GENERIC',
      relatedEntityId: 'something',
      payload: { planId: 'also-something' },
    });
    expect(notificationLink(n)).toBeNull();
  });

  it('links a research-created strategy to the strategy page (payload.strategyId)', () => {
    const n = makeNotification({
      type: 'MARKET_RESEARCH_STRATEGY_CREATED',
      relatedEntityId: '68f0a507-8e67-42f9-aed1-949432ecd31d',
      payload: { strategyId: '68f0a507-8e67-42f9-aed1-949432ecd31d', strategyName: 'Defense Primes' },
    });
    expect(notificationLink(n)).toBe('/strategies/68f0a507-8e67-42f9-aed1-949432ecd31d');
  });

  it('falls back to relatedEntityId for a research strategy when payload.strategyId is missing', () => {
    const n = makeNotification({
      type: 'MARKET_RESEARCH_STRATEGY_CREATED',
      relatedEntityId: 'strat-uuid',
      payload: {},
    });
    expect(notificationLink(n)).toBe('/strategies/strat-uuid');
  });

  it('ignores non-string payload.planId values', () => {
    const n = makeNotification({
      type: 'INVESTMENT_FAILED',
      relatedEntityId: 'fallback-uuid',
      payload: { planId: 42 as unknown as string },
    });
    expect(notificationLink(n)).toBe('/investment-plans/fallback-uuid');
  });
});
