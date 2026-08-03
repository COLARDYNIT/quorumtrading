-- V15__add_caching_columns_to_market_events.sql
-- Add columns to support caching of market events
-- Use TIMESTAMPTZ for consistency with other temporal columns (timestamp, ingested_at)

ALTER TABLE market_events
    ADD COLUMN IF NOT EXISTS cached_at TIMESTAMPTZ DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- Index for efficient cache lookups by source, symbol, and time range
CREATE INDEX idx_market_events_cache_lookup
    ON market_events(source, symbol, timestamp);

-- Index for cleanup jobs that delete old cached data
CREATE INDEX idx_market_events_cached_at
    ON market_events(cached_at);

-- Add comment explaining content_hash usage
COMMENT ON COLUMN market_events.content_hash IS 'SHA-256 hash of content for deduplication';
