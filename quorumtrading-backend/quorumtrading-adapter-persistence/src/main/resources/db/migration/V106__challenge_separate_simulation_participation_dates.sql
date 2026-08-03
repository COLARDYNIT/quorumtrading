-- Separate simulation period from participation window in challenges.
-- Allows backtest challenges where simulation dates are historical
-- but participation window is in the present.

ALTER TABLE challenges RENAME COLUMN start_date TO simulation_start_date;
ALTER TABLE challenges RENAME COLUMN end_date TO simulation_end_date;

ALTER TABLE challenges ADD COLUMN participation_open TIMESTAMPTZ;
ALTER TABLE challenges ADD COLUMN participation_close TIMESTAMPTZ;

-- Backfill: existing challenges used simulation dates as participation window
UPDATE challenges SET
  participation_open = simulation_start_date,
  participation_close = COALESCE(join_deadline, simulation_end_date);

ALTER TABLE challenges ALTER COLUMN participation_open SET NOT NULL;
ALTER TABLE challenges ALTER COLUMN participation_close SET NOT NULL;

ALTER TABLE challenges DROP COLUMN join_deadline;

-- Update check constraint
ALTER TABLE challenges DROP CONSTRAINT IF EXISTS chk_challenge_dates;
ALTER TABLE challenges DROP CONSTRAINT IF EXISTS chk_challenge_join;
ALTER TABLE challenges ADD CONSTRAINT chk_challenge_sim_dates CHECK (simulation_end_date > simulation_start_date);
ALTER TABLE challenges ADD CONSTRAINT chk_challenge_part_dates CHECK (participation_close > participation_open);
