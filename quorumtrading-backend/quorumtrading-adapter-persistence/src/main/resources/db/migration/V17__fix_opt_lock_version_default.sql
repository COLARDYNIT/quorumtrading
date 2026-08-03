-- V15__fix_opt_lock_version_default.sql
-- Change opt_lock_version default from 1 to 0 to match Hibernate's @Version behavior
-- Hibernate starts version at 0 and increments, so DB default should be 0 (or no default)

-- Remove the default entirely - let Hibernate manage the version
ALTER TABLE orchestrator_strategies ALTER COLUMN opt_lock_version DROP DEFAULT;

-- Note: Existing rows keep their current version values (likely 1)
-- New rows inserted by Hibernate will start at 0 (or 1 after first persist)
