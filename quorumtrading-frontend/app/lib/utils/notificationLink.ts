import type { Notification, NotificationType } from '@/app/lib/types/notification';

/**
 * Resolve the page a notification should deep-link to.
 *
 * Why this exists: for INVESTMENT_EXECUTED / SKIPPED / FAILED notifications the
 * backend sets {@code relatedEntityId} to the *allocation* UUID — those rows
 * are about a specific execution attempt, not the plan. The plan UUID lives in
 * {@code payload.planId}. Treating {@code relatedEntityId} as the plan ID
 * builds a {@code /investment-plans/<allocationId>} URL that 404s on the API
 * (which is exactly the bug that originally showed up in prod).
 *
 * Prefer {@code payload.planId} for plan-type notifications, fall back to
 * {@code relatedEntityId} (for PLAN_PAUSED, which has no allocation context).
 */
const PLAN_TYPES: ReadonlySet<NotificationType> = new Set([
  'INVESTMENT_EXECUTED',
  'INVESTMENT_SKIPPED',
  'INVESTMENT_FAILED',
  'PLAN_PAUSED',
]);

function planIdFromPayload(n: Notification): string | null {
  const raw = n.payload?.planId;
  return typeof raw === 'string' && raw.length > 0 ? raw : null;
}

function strategyIdFromPayload(n: Notification): string | null {
  const raw = n.payload?.strategyId;
  return typeof raw === 'string' && raw.length > 0 ? raw : null;
}

export function notificationLink(n: Notification): string | null {
  // A research-created strategy deep-links to the strategy itself. The backend
  // sets both payload.strategyId and relatedEntityId to the strategy UUID;
  // prefer the payload, fall back to relatedEntityId, then the strategies index.
  if (n.type === 'MARKET_RESEARCH_STRATEGY_CREATED') {
    const strategyId = strategyIdFromPayload(n) ?? n.relatedEntityId;
    return strategyId ? `/strategies/${strategyId}` : '/strategies';
  }

  if (!PLAN_TYPES.has(n.type)) return null;
  const planId = planIdFromPayload(n) ?? n.relatedEntityId;
  return planId ? `/investment-plans/${planId}` : '/investment-plans';
}
