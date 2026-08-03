-- Migration V30: Make leaderboards.end_date nullable
-- Purpose: Support live leaderboards without an end date (ongoing competitions)
-- Date: 2025-11-10
-- Fixes: V29 incorrectly made end_date NOT NULL, but domain model allows null for live leaderboards

-- Make end_date nullable to support live leaderboards
ALTER TABLE leaderboards
    ALTER COLUMN end_date DROP NOT NULL;

-- Update table comment to clarify backtest vs live distinction
COMMENT ON COLUMN leaderboards.end_date IS 'End date for backtest leaderboards. NULL for live/ongoing competitions (no end date).';
