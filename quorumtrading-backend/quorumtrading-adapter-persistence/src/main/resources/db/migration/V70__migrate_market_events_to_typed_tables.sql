-- Phase 2: Migrate existing data from market_events to typed tables.
-- Uses ON CONFLICT to skip rows already inserted by dual-write.

-- PRICE → price_bars
INSERT INTO price_bars (symbol, timestamp, source, open, high, low, close, volume, ingested_at, content_hash)
SELECT symbol, timestamp, source,
    (raw_data->>'open')::NUMERIC(20,8),
    (raw_data->>'high')::NUMERIC(20,8),
    (raw_data->>'low')::NUMERIC(20,8),
    COALESCE((raw_data->>'close')::NUMERIC(20,8), (raw_data->>'price')::NUMERIC(20,8)),
    (raw_data->>'volume')::NUMERIC(20,4),
    ingested_at, content_hash
FROM market_events
WHERE event_type = 'PRICE' AND content_hash IS NOT NULL
ON CONFLICT (content_hash) DO NOTHING;

-- NEWS → news_events
INSERT INTO news_events (symbol, timestamp, source, headline, summary, url, category, raw_data, ingested_at, content_hash)
SELECT symbol, timestamp, source,
    raw_data->>'headline',
    raw_data->>'summary',
    COALESCE(raw_data->>'url', raw_data->>'link'),
    raw_data->>'category',
    raw_data,
    ingested_at, content_hash
FROM market_events
WHERE event_type = 'NEWS' AND content_hash IS NOT NULL
ON CONFLICT (content_hash) DO NOTHING;

-- SOCIAL → social_events
INSERT INTO social_events (symbol, timestamp, source, title, author, score, num_comments, permalink, subreddit, raw_data, ingested_at, content_hash)
SELECT symbol, timestamp, source,
    raw_data->>'title',
    raw_data->>'author',
    (raw_data->>'score')::INTEGER,
    (raw_data->>'num_comments')::INTEGER,
    raw_data->>'permalink',
    raw_data->>'subreddit',
    raw_data,
    ingested_at, content_hash
FROM market_events
WHERE event_type = 'SOCIAL' AND content_hash IS NOT NULL
ON CONFLICT (content_hash) DO NOTHING;
