-- Allow RETRY_PENDING as a valid backfill job status
ALTER TABLE backfill_jobs DROP CONSTRAINT backfill_jobs_status_check;
ALTER TABLE backfill_jobs ADD CONSTRAINT backfill_jobs_status_check
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'RETRY_PENDING'));
