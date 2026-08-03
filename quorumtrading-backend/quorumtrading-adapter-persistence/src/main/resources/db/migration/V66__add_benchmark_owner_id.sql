-- V66: Add owner_id to benchmarks table for authorization
-- Benchmarks need an owner for access control checks.

-- Step 1: Add nullable owner_id column
ALTER TABLE benchmarks ADD COLUMN owner_id UUID;

-- Step 2: Backfill owner_id from the strategy owner of the first simulation in each benchmark.
-- For each benchmark, find any simulation linked to it, then look up the strategy's owner.
UPDATE benchmarks b
SET owner_id = (
    SELECT s2.owner_id
    FROM simulations sim
    JOIN orchestrator_strategies s2 ON sim.strategy_id = s2.id
    WHERE sim.benchmark_id = b.id
    LIMIT 1
)
WHERE b.owner_id IS NULL;

-- Step 3: For any benchmarks that still have no owner (orphaned - no simulations),
-- assign to system user as fallback
UPDATE benchmarks
SET owner_id = '00000000-0000-0000-0000-000000000000'
WHERE owner_id IS NULL;

-- Step 4: Make NOT NULL now that all rows have a value
ALTER TABLE benchmarks ALTER COLUMN owner_id SET NOT NULL;

-- Step 5: Add index for efficient owner-based queries
CREATE INDEX idx_benchmarks_owner_id ON benchmarks (owner_id);

-- Add column comment
COMMENT ON COLUMN benchmarks.owner_id IS 'UUID of the user who owns this benchmark';
