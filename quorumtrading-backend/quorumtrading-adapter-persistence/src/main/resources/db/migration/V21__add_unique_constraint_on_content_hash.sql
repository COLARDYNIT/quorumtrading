-- V21__add_unique_constraint_on_content_hash.sql
-- Add UNIQUE constraint on content_hash to prevent duplicate market events
--
-- This constraint ensures deduplication works as intended:
-- - RedditAdapter generates content_hash from post content
-- - NewsAPIAdapter generates content_hash from article content
-- - Repository catches ConstraintViolationException on duplicate inserts
--
-- NOTE: Must clean up existing duplicates first before adding constraint

-- Step 1: Remove duplicate rows, keeping only the oldest instance of each unique content_hash
-- For rows with NULL content_hash, keep all (NULL != NULL in SQL)
DELETE FROM market_events a
USING market_events b
WHERE a.id > b.id
  AND a.content_hash = b.content_hash
  AND a.content_hash IS NOT NULL;

-- Step 2: Add UNIQUE constraint on content_hash
-- NULL values are allowed (multiple NULLs don't violate uniqueness in PostgreSQL)
ALTER TABLE market_events
    ADD CONSTRAINT market_events_content_hash_unique UNIQUE (content_hash);

-- Add comment explaining the constraint
COMMENT ON CONSTRAINT market_events_content_hash_unique ON market_events IS
'Ensures deduplication of market events based on content hash. NULL values allowed for events without hash.';
