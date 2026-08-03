-- V41__add_backfill_simulation_link.sql
-- Link backfill jobs to triggering simulation for completion callback

ALTER TABLE backfill_jobs
    ADD COLUMN linked_simulation_id UUID REFERENCES simulations(id) ON DELETE SET NULL;

CREATE INDEX idx_backfill_jobs_simulation ON backfill_jobs(linked_simulation_id);

COMMENT ON COLUMN backfill_jobs.linked_simulation_id IS 'Simulation waiting for this backfill to complete';
