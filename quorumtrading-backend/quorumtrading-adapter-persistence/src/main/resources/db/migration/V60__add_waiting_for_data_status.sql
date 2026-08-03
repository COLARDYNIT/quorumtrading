-- V60__add_waiting_for_data_status.sql
-- Add WAITING_FOR_DATA to simulation status constraint
-- This status is used when a simulation is waiting for market data backfill to complete

-- Drop the existing constraint
ALTER TABLE simulations DROP CONSTRAINT IF EXISTS simulations_status_check;

-- Recreate with WAITING_FOR_DATA included
ALTER TABLE simulations
    ADD CONSTRAINT simulations_status_check
    CHECK (status IN ('PENDING', 'WAITING_FOR_DATA', 'SIMULATING', 'LIVE', 'COMPLETED', 'FAILED'));

-- Update column comment to document all valid statuses
COMMENT ON COLUMN simulations.status IS 'Simulation status: PENDING (queued), WAITING_FOR_DATA (backfill in progress), SIMULATING (historical catchup), LIVE (real-time), COMPLETED, FAILED';
