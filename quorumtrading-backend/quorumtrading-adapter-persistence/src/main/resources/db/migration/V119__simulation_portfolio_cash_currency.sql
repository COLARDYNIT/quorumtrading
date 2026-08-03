-- V119__simulation_portfolio_cash_currency.sql
-- CUR1 (Saxo/EU currency thread): persist the settlement currency of a
-- SimulationPortfolio's cash so a EUR (Belgian/Saxo) portfolio round-trips as EUR
-- instead of being reconstituted as USD by the mapper's hard-coded default.
--
-- Backfill is byte-identical for existing rows: every portfolio created before EU
-- support settled in USD, so the column defaults to 'USD' and existing rows are
-- stamped 'USD'. New EUR portfolios persist 'EUR'.

ALTER TABLE simulation_portfolios
    ADD COLUMN cash_currency VARCHAR(10) NOT NULL DEFAULT 'USD';

COMMENT ON COLUMN simulation_portfolios.cash_currency IS
    'Settlement currency for cash_available / cash_reserved / realized_pnl (USD for Alpaca, EUR for Saxo/EU)';
