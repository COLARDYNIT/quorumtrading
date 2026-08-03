-- Add retry_after column for RETRY_PENDING backfill jobs
ALTER TABLE backfill_jobs ADD COLUMN retry_after TIMESTAMP;
