-- V59: Add backfill tracking columns to simulations table
-- Supports robust backtest execution with:
--   1. WAITING_FOR_DATA status (now valid in status column)
--   2. Link to backfill job for tracking
--   3. Circuit breaker counters for data gap detection

-- Add backfill job reference
ALTER TABLE simulations
    ADD COLUMN backfill_job_id UUID;

-- Add circuit breaker counters
ALTER TABLE simulations
    ADD COLUMN skipped_evaluation_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_skipped_evaluation_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_evaluation_count INTEGER NOT NULL DEFAULT 0;

-- Add index for reverse lookup (find simulation by backfill job)
CREATE INDEX idx_simulations_backfill_job_id ON simulations(backfill_job_id)
    WHERE backfill_job_id IS NOT NULL;

-- Add constraints for counter validity
ALTER TABLE simulations
    ADD CONSTRAINT simulations_skipped_evaluation_count_non_negative
        CHECK (skipped_evaluation_count >= 0),
    ADD CONSTRAINT simulations_total_skipped_evaluation_count_non_negative
        CHECK (total_skipped_evaluation_count >= 0),
    ADD CONSTRAINT simulations_total_evaluation_count_non_negative
        CHECK (total_evaluation_count >= 0);

COMMENT ON COLUMN simulations.backfill_job_id IS 'Reference to currently running backfill job (NULL when no backfill pending)';
COMMENT ON COLUMN simulations.skipped_evaluation_count IS 'Consecutive skipped evaluations (resets on successful evaluation)';
COMMENT ON COLUMN simulations.total_skipped_evaluation_count IS 'Total skipped evaluations (for percentage calculation)';
COMMENT ON COLUMN simulations.total_evaluation_count IS 'Total evaluations attempted (for percentage calculation)';
