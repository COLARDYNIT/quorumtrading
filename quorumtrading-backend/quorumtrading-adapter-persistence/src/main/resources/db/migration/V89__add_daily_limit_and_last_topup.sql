-- Add daily spend limit and last top-up tracking to user_budgets
ALTER TABLE user_budgets ADD COLUMN daily_spend_limit DECIMAL(10,2);
ALTER TABLE user_budgets ADD COLUMN last_top_up_at TIMESTAMP WITH TIME ZONE;
