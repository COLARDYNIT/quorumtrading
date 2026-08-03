-- Add created_at column to consensus_decisions to track when the LLM call was actually made (wall-clock time).
-- This is distinct from decision_timestamp, which stores the simulated analysis time for backtests.
-- The budget/usage queries must use created_at to correctly attribute costs to the day the API call happened,
-- not the simulated historical date.

ALTER TABLE consensus_decisions
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- Backfill existing rows: use decision_timestamp as best approximation since we don't have the original wall-clock time.
-- For live/paper simulations this is accurate; for historical backtests this is approximate but acceptable for past data.
UPDATE consensus_decisions SET created_at = decision_timestamp WHERE created_at = NOW();

-- Add index for efficient date-range queries on created_at (used by budget usage aggregation)
CREATE INDEX idx_consensus_decisions_created_at ON consensus_decisions (created_at);
