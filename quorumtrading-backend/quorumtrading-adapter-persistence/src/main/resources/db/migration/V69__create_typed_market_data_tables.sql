-- Split market_events into typed tables for better performance and type safety.
-- Phase 1: Create new tables (DDL only, no data movement).
-- Dual-write from application code ensures new data goes to both old and new tables.

-- price_bars: Native OHLCV columns, no JSONB
CREATE TABLE price_bars (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20) NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL,
    source      VARCHAR(100) NOT NULL,
    open        NUMERIC(20,8),
    high        NUMERIC(20,8),
    low         NUMERIC(20,8),
    close       NUMERIC(20,8) NOT NULL,
    volume      NUMERIC(20,4),
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64),
    CONSTRAINT price_bars_content_hash_unique UNIQUE (content_hash)
);
CREATE INDEX idx_price_bars_symbol_timestamp ON price_bars(symbol, timestamp);
CREATE INDEX idx_price_bars_ingested_at ON price_bars(ingested_at);

-- news_events: Promoted headline/url + JSONB overflow
CREATE TABLE news_events (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20),
    timestamp   TIMESTAMPTZ NOT NULL,
    source      VARCHAR(100) NOT NULL,
    headline    TEXT,
    summary     TEXT,
    url         TEXT,
    category    VARCHAR(100),
    raw_data    JSONB,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64),
    CONSTRAINT news_events_content_hash_unique UNIQUE (content_hash)
);
CREATE INDEX idx_news_events_symbol_timestamp ON news_events(symbol, timestamp DESC);
CREATE INDEX idx_news_events_source_symbol_timestamp ON news_events(source, symbol, timestamp);
CREATE INDEX idx_news_events_ingested_at ON news_events(ingested_at);

-- social_events: Promoted title/score/subreddit + JSONB overflow
CREATE TABLE social_events (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20),
    timestamp   TIMESTAMPTZ NOT NULL,
    source      VARCHAR(100) NOT NULL,
    title       TEXT,
    author      VARCHAR(200),
    score       INTEGER,
    num_comments INTEGER,
    permalink   TEXT,
    subreddit   VARCHAR(100),
    raw_data    JSONB,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64),
    CONSTRAINT social_events_content_hash_unique UNIQUE (content_hash)
);
CREATE INDEX idx_social_events_symbol_timestamp ON social_events(symbol, timestamp DESC);
CREATE INDEX idx_social_events_source_symbol_timestamp ON social_events(source, symbol, timestamp);
CREATE INDEX idx_social_events_ingested_at ON social_events(ingested_at);
