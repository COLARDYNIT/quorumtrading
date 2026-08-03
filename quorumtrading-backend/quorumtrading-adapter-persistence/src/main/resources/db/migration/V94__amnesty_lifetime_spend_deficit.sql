-- Amnesty: align budget limits with lifetime spend for users in deficit.
-- The lifetime_spend_amount tracking was broken until V90/V91 corrected it.
-- Users accumulated real spend that was never enforced, so their corrected
-- lifetime_spend now exceeds their budget_limit. This migration forgives the
-- deficit by raising the limit to match spend. Future top-ups will give real
-- remaining budget.
UPDATE user_budgets
SET budget_limit_amount = lifetime_spend_amount
WHERE lifetime_spend_amount > budget_limit_amount;
