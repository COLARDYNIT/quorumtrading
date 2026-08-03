-- Migration V28: Add status column to orchestrator_strategies table
-- Purpose: Track strategy lifecycle (DRAFT, ACTIVE, ARCHIVED) as part of DDD domain model enrichment
-- IMPORTANT: Safe migration with default value for existing records

-- Add status column with default value for existing rows
ALTER TABLE orchestrator_strategies
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Update comment to document the column
COMMENT ON COLUMN orchestrator_strategies.status IS 'Strategy lifecycle status: DRAFT (being edited), ACTIVE (ready for use), ARCHIVED (historical)';

-- Add check constraint to enforce valid status values
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'orchestrator_strategies_status_check'
    ) THEN
        ALTER TABLE orchestrator_strategies
            ADD CONSTRAINT orchestrator_strategies_status_check
            CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'));
    END IF;
END $$;

-- Create index on status for efficient filtering by lifecycle state
CREATE INDEX IF NOT EXISTS idx_orchestrator_strategies_status
    ON orchestrator_strategies(status);
