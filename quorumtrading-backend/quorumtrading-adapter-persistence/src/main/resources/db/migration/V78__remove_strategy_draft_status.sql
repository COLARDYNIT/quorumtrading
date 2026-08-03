-- Migration V72: Remove DRAFT strategy status
-- Purpose: DRAFT status is dead code — strategies are auto-activated on creation.
-- This migration promotes any remaining DRAFT rows to ACTIVE and tightens the check constraint.

-- 1. Promote any lingering DRAFT strategies to ACTIVE
UPDATE orchestrator_strategies SET status = 'ACTIVE' WHERE status = 'DRAFT';

-- 2. Replace check constraint to only allow ACTIVE and ARCHIVED
ALTER TABLE orchestrator_strategies DROP CONSTRAINT IF EXISTS orchestrator_strategies_status_check;
ALTER TABLE orchestrator_strategies
    ADD CONSTRAINT orchestrator_strategies_status_check
    CHECK (status IN ('ACTIVE', 'ARCHIVED'));

-- 3. Update column comment
COMMENT ON COLUMN orchestrator_strategies.status IS 'Strategy lifecycle status: ACTIVE (ready for use), ARCHIVED (historical)';
