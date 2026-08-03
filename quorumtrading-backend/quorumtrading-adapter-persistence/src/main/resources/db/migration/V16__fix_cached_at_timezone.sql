-- V16__fix_cached_at_timezone.sql
-- Change cached_at to TIMESTAMPTZ for consistency with other temporal columns
-- Note: V15 was updated to create cached_at as TIMESTAMPTZ directly
-- This migration only runs for existing databases that had the old TIMESTAMP version

DO $$
BEGIN
    -- Only alter if column exists with old TIMESTAMP type (for existing databases)
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'market_events'
          AND column_name = 'cached_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE market_events
            ALTER COLUMN cached_at TYPE TIMESTAMPTZ USING cached_at AT TIME ZONE 'UTC';
    END IF;
END $$;
