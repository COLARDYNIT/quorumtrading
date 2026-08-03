-- Migration V32: Rename leaderboards table to simulation_runs
-- Purpose: Align database naming with domain model refactoring (Leaderboard → SimulationRun)
-- Date: 2025-11-16
-- Reference: commit 85e4b54 - DDD refactoring for clearer ubiquitous language

-- Rename table
ALTER TABLE leaderboards RENAME TO simulation_runs;

-- Rename foreign key column in simulations table
ALTER TABLE simulations RENAME COLUMN leaderboard_id TO simulation_run_id;

-- Drop old foreign key constraint
ALTER TABLE simulations DROP CONSTRAINT IF EXISTS simulations_leaderboard_id_fkey;

-- Add new foreign key constraint with updated name
ALTER TABLE simulations
    ADD CONSTRAINT simulations_simulation_run_id_fkey
    FOREIGN KEY (simulation_run_id)
    REFERENCES simulation_runs(id)
    ON DELETE CASCADE;

-- Drop old index
DROP INDEX IF EXISTS idx_simulations_leaderboard_id;

-- Create new index with updated name
CREATE INDEX idx_simulations_simulation_run_id
    ON simulations(simulation_run_id);

-- Update table and column comments
COMMENT ON TABLE simulation_runs IS 'Simulation runs group multiple simulations competing over the same date range';
COMMENT ON COLUMN simulation_runs.name IS 'Human-readable name for the simulation run (e.g., "November 2025 Battle")';
COMMENT ON COLUMN simulation_runs.start_date IS 'Common start date for all simulations in this simulation run';
COMMENT ON COLUMN simulation_runs.end_date IS 'Common end date for all simulations in this simulation run (NULL for live/ongoing competitions)';
COMMENT ON COLUMN simulation_runs.created_at IS 'Timestamp when the simulation run was created';
COMMENT ON COLUMN simulations.simulation_run_id IS 'Optional foreign key to simulation_runs table. NULL for standalone simulations (manual backtests). Non-NULL for wizard-created simulations competing in a simulation run.';
