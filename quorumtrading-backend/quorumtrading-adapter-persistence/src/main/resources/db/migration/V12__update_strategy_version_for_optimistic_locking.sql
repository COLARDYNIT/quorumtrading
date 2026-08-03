-- V12__update_strategy_version_for_optimistic_locking.sql
-- Refactor Strategy entity to use proper Hibernate @Version for optimistic locking
-- This migration:
-- 1. Renames the 'version' VARCHAR column to 'name' (for strategy naming)
-- 2. Adds a new 'opt_lock_version' INTEGER column for Hibernate @Version optimistic locking
-- 3. Drops the unique constraint on the old version column (now name)
-- 4. Removes the index on version since name doesn't need to be indexed

-- Drop the index on version column first (created in V4)
DROP INDEX IF EXISTS idx_strategies_version;

-- Rename version column to name
ALTER TABLE orchestrator_strategies RENAME COLUMN version TO name;

-- Remove unique constraint from name (strategies can have duplicate names)
ALTER TABLE orchestrator_strategies DROP CONSTRAINT IF EXISTS orchestrator_strategies_version_key;

-- Add new integer version column for Hibernate optimistic locking
ALTER TABLE orchestrator_strategies ADD COLUMN opt_lock_version INTEGER;

-- Set default version to 1 for existing records
UPDATE orchestrator_strategies SET opt_lock_version = 1 WHERE opt_lock_version IS NULL;

-- Make opt_lock_version not null
ALTER TABLE orchestrator_strategies ALTER COLUMN opt_lock_version SET NOT NULL;

-- Make opt_lock_version default to 1 for new inserts
ALTER TABLE orchestrator_strategies ALTER COLUMN opt_lock_version SET DEFAULT 1;
