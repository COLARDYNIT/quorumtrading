-- Migration V29: Create leaderboards table and add leaderboard relationship to simulations
-- Purpose: Support backtesting wizard feature where multiple simulations compete within a leaderboard
-- Date: 2025-11-10

-- Create leaderboards table
CREATE TABLE IF NOT EXISTS leaderboards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Add comment to document the table purpose
COMMENT ON TABLE leaderboards IS 'Leaderboards group multiple simulations competing over the same date range';
COMMENT ON COLUMN leaderboards.name IS 'Human-readable name for the leaderboard (e.g., "November 2025 Battle")';
COMMENT ON COLUMN leaderboards.start_date IS 'Common start date for all simulations in this leaderboard';
COMMENT ON COLUMN leaderboards.end_date IS 'Common end date for all simulations in this leaderboard';
COMMENT ON COLUMN leaderboards.created_at IS 'Timestamp when the leaderboard was created';

-- Add leaderboard_id foreign key column to simulations table (nullable)
-- NULL means the simulation is standalone (not part of a leaderboard)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'simulations' AND column_name = 'leaderboard_id'
    ) THEN
        ALTER TABLE simulations
            ADD COLUMN leaderboard_id UUID;
    END IF;
END $$;

-- Add foreign key constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'simulations_leaderboard_id_fkey'
    ) THEN
        ALTER TABLE simulations
            ADD CONSTRAINT simulations_leaderboard_id_fkey
            FOREIGN KEY (leaderboard_id)
            REFERENCES leaderboards(id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- Add comment to document the relationship
COMMENT ON COLUMN simulations.leaderboard_id IS 'Optional foreign key to leaderboards table. NULL for standalone simulations (manual backtests). Non-NULL for wizard-created simulations competing in a leaderboard.';

-- Create index on simulations.leaderboard_id for efficient queries
CREATE INDEX IF NOT EXISTS idx_simulations_leaderboard_id
    ON simulations(leaderboard_id);

-- Note: simulations.created_at already exists from V3 migration (line 16 in V3__decisions_and_simulations.sql)
-- This column is used by domain logic to distinguish backtests (created_at != null) from live trading (created_at = null for future use)
