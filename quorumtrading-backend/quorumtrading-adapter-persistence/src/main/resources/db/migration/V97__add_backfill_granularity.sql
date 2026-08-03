-- Add granularity column to backfill_jobs table.
-- Backtests should use daily bars (ONE_DAY) instead of 5-minute bars
-- to avoid fetching millions of intraday records for long date ranges.
ALTER TABLE backfill_jobs ADD COLUMN granularity VARCHAR(20) DEFAULT 'ONE_DAY';
