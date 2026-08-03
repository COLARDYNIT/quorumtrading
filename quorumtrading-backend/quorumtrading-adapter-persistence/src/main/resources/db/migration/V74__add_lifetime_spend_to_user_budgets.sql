ALTER TABLE user_budgets ADD COLUMN lifetime_spend_amount DECIMAL(10,6) NOT NULL DEFAULT 0;

-- Seed lifetime_spend from current monthly spend for existing users
UPDATE user_budgets SET lifetime_spend_amount = current_spend_amount;
