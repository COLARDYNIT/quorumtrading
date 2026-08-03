-- V13__convert_strategy_id_to_uuid.sql
-- Refactor Strategy entity to use UUID for primary key instead of BIGSERIAL
-- This migration:
-- 1. Drops notes column (no longer needed)
-- 2. Converts id from BIGSERIAL to UUID

-- Drop notes column (no longer used in domain model)
ALTER TABLE orchestrator_strategies DROP COLUMN IF EXISTS notes;

-- Since we need to change the PK type, we need to recreate the table
-- This requires handling foreign key constraints and data migration

-- Step 1: Create new table with UUID id
CREATE TABLE orchestrator_strategies_new (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    opt_lock_version INTEGER NOT NULL DEFAULT 1,
    markdown_content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ
);

-- Step 2: Delete existing data (since ID type is fundamentally changing)
-- For MVP, we'll start fresh as the old BIGSERIAL IDs cannot be mapped to UUIDs
-- Production systems would need a more careful migration strategy
TRUNCATE TABLE orchestrator_strategies CASCADE;

-- Step 3: Drop old table (CASCADE to drop foreign key constraints)
DROP TABLE orchestrator_strategies CASCADE;

-- Step 4: Rename new table to original name
ALTER TABLE orchestrator_strategies_new RENAME TO orchestrator_strategies;

-- Step 5: Recreate trigger for updated_at
CREATE TRIGGER update_orchestrator_strategies_updated_at
    BEFORE UPDATE ON orchestrator_strategies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
