-- V19__add_symbol_and_investment_type_to_consensus_decisions.sql
-- Add symbol and investment_type columns to consensus_decisions table

-- Add symbol column (trading symbol like BTC, AAPL, etc.)
ALTER TABLE consensus_decisions
    ADD COLUMN symbol VARCHAR(10);

-- Add investment_type column (asset class: CRYPTO or STOCK)
ALTER TABLE consensus_decisions
    ADD COLUMN investment_type VARCHAR(20);

-- Add comments for documentation
COMMENT ON COLUMN consensus_decisions.symbol IS 'Trading symbol (e.g., BTC, AAPL)';
COMMENT ON COLUMN consensus_decisions.investment_type IS 'Asset type: CRYPTO or STOCK';
