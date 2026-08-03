-- V27: Drop unused tables and columns identified in database cleanup analysis
-- Date: 2025-11-07
-- Removes:
--   - portfolios table (0 records, replaced by portfolio_snapshots)
--   - llm_performance table (0 records, never implemented)
--   - simulations.results (always NULL)
--   - simulations.notes (always NULL)
--   - simulations.strategy_version (always NULL)
--   - llm_decisions.ticker (always NULL, replaced by consensus_decisions.symbol)
--   - llm_decisions.ticker_quantity (always NULL, replaced by consensus_decisions.quantity)
--   - consensus_decisions.trigger_event_id (always NULL, never used)

-- Drop unused tables
DROP TABLE IF EXISTS portfolios CASCADE;
DROP TABLE IF EXISTS llm_performance CASCADE;

-- Drop unused columns from simulations
ALTER TABLE simulations DROP COLUMN IF EXISTS results;
ALTER TABLE simulations DROP COLUMN IF EXISTS notes;
ALTER TABLE simulations DROP COLUMN IF EXISTS strategy_version;

-- Drop index on strategy_version (if it exists)
DROP INDEX IF EXISTS idx_simulations_strategy_version;

-- Drop unused columns from llm_decisions
ALTER TABLE llm_decisions DROP COLUMN IF EXISTS ticker;
ALTER TABLE llm_decisions DROP COLUMN IF EXISTS ticker_quantity;

-- Drop indexes related to ticker (if they exist)
DROP INDEX IF EXISTS idx_llm_decisions_ticker;
DROP INDEX IF EXISTS idx_llm_decisions_agent_ticker;

-- Drop unused column from consensus_decisions (and its FK constraint)
ALTER TABLE consensus_decisions DROP CONSTRAINT IF EXISTS consensus_decisions_trigger_event_id_fkey;
ALTER TABLE consensus_decisions DROP COLUMN IF EXISTS trigger_event_id;
