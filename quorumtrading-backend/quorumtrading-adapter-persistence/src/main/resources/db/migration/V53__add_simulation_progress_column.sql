-- Add progress column to simulations table
-- Progress is tracked as actual evaluations completed vs total, not based on timestamps
-- This fixes backtest progress which runs faster than wall-clock time

ALTER TABLE simulations ADD COLUMN progress INTEGER NOT NULL DEFAULT 0;

-- Add constraint to ensure progress is between 0 and 100
ALTER TABLE simulations ADD CONSTRAINT simulations_progress_range CHECK (progress >= 0 AND progress <= 100);
