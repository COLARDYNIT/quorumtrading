-- Migration V65: Rename simulation_runs table to benchmarks
-- Purpose: Align database naming with domain model refactoring (SimulationRun → Benchmark)
-- Date: 2026-02-02
-- Reference: Full rename of "group of simulations for comparison" concept to Benchmark

-- Rename table
ALTER TABLE simulation_runs RENAME TO benchmarks;

-- Rename foreign key column in simulations table
ALTER TABLE simulations RENAME COLUMN simulation_run_id TO benchmark_id;

-- Drop old foreign key constraint
ALTER TABLE simulations DROP CONSTRAINT IF EXISTS simulations_simulation_run_id_fkey;

-- Add new foreign key constraint with updated name
ALTER TABLE simulations
    ADD CONSTRAINT simulations_benchmark_id_fkey
    FOREIGN KEY (benchmark_id)
    REFERENCES benchmarks(id)
    ON DELETE CASCADE;

-- Drop old index
DROP INDEX IF EXISTS idx_simulations_simulation_run_id;

-- Create new index with updated name
CREATE INDEX idx_simulations_benchmark_id
    ON simulations(benchmark_id);

-- Update table and column comments
COMMENT ON TABLE benchmarks IS 'Benchmarks group multiple simulations competing over the same date range for strategy comparison';
COMMENT ON COLUMN benchmarks.name IS 'Human-readable name for the benchmark (e.g., "November 2025 Battle")';
COMMENT ON COLUMN benchmarks.start_date IS 'Common start date for all simulations in this benchmark';
COMMENT ON COLUMN benchmarks.end_date IS 'Common end date for all simulations in this benchmark (NULL for live/ongoing competitions)';
COMMENT ON COLUMN benchmarks.created_at IS 'Timestamp when the benchmark was created';
COMMENT ON COLUMN simulations.benchmark_id IS 'Optional foreign key to benchmarks table. NULL for standalone simulations (manual backtests). Non-NULL for wizard-created simulations competing in a benchmark.';
