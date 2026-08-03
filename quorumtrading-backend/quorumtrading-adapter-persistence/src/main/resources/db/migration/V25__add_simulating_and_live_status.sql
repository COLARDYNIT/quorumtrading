-- V25__add_simulating_and_live_status.sql
-- Add SIMULATING and LIVE status values to support historical simulation backfill
--
-- Background:
-- - SIMULATING: Simulation is catching up with historical data (scheduler disabled)
-- - LIVE: Simulation has reached current time and is running in real-time (scheduler enabled)
--
-- Migration Strategy:
-- 1. Add comment to document new status values (status column is VARCHAR, no enum to modify)
-- 2. Update existing RUNNING simulations to appropriate status based on end_date
-- 3. Add check constraint to validate status values

-- Add check constraint to validate status values
-- Drop existing constraint if it exists (from earlier migrations)
ALTER TABLE simulations DROP CONSTRAINT IF EXISTS simulations_status_check;

-- Update existing RUNNING simulations:
-- - If end_date is in the future and start_date is in the past -> SIMULATING (catching up)
-- - If end_date is in the future and start_date >= now -> LIVE (real-time)
-- - If end_date is in the past -> keep as is (will be handled by application logic)
UPDATE simulations
SET status = 'SIMULATING'
WHERE status = 'RUNNING'
  AND start_date < NOW()
  AND end_date > NOW();

UPDATE simulations
SET status = 'LIVE'
WHERE status = 'RUNNING'
  AND start_date >= NOW()
  AND end_date > NOW();

-- Add new constraint with SIMULATING and LIVE
ALTER TABLE simulations
    ADD CONSTRAINT simulations_status_check
    CHECK (status IN ('PENDING', 'SIMULATING', 'LIVE', 'COMPLETED', 'FAILED'));

-- Add comment to status column documenting the new values
COMMENT ON COLUMN simulations.status IS 'Simulation execution status: PENDING (not started), SIMULATING (catching up with historical data, scheduler disabled), LIVE (running in real-time, scheduler enabled), COMPLETED (finished successfully), FAILED (encountered error)';
