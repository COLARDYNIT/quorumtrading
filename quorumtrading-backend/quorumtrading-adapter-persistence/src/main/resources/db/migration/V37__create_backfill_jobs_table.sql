CREATE TABLE backfill_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbols JSONB NOT NULL,
    data_types JSONB NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    progress_percentage INTEGER DEFAULT 0 CHECK (progress_percentage >= 0 AND progress_percentage <= 100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    total_records_expected INTEGER,
    records_processed INTEGER DEFAULT 0
);

CREATE INDEX idx_backfill_jobs_status ON backfill_jobs(status);
CREATE INDEX idx_backfill_jobs_created_at ON backfill_jobs(created_at DESC);
