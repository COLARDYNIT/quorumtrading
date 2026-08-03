-- Add quantity field to consensus_decisions table
ALTER TABLE consensus_decisions
ADD COLUMN quantity DECIMAL(20, 8);

-- Add ticker_quantity field to llm_decisions table
ALTER TABLE llm_decisions
ADD COLUMN ticker_quantity DECIMAL(20, 8);

-- Add comments for documentation
COMMENT ON COLUMN consensus_decisions.quantity IS 'Quantity to trade as determined by orchestrator (null means use default)';
COMMENT ON COLUMN llm_decisions.ticker_quantity IS 'Quantity recommended by individual agent';
