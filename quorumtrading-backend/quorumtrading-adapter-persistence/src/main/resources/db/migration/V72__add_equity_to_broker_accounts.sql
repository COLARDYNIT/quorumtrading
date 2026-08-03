-- Add equity column to broker_accounts
-- Equity = cash + positions market value (from Alpaca API)
-- Nullable because MOCK accounts don't have an equity concept
ALTER TABLE broker_accounts
    ADD COLUMN equity_amount DECIMAL(20, 8);
