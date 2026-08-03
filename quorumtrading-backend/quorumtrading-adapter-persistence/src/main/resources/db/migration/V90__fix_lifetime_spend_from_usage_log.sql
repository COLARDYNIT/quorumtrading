-- V90: Fix lifetime_spend_amount which was never persisted on updates (repository bug).
-- Recalculate from llm_usage_log (source of truth) with 1.50x markup applied.

UPDATE user_budgets ub
SET lifetime_spend_amount = COALESCE(
    (SELECT SUM(l.raw_cost_usd) * 1.50
     FROM llm_usage_log l
     WHERE l.user_id = ub.user_id),
    0
);
