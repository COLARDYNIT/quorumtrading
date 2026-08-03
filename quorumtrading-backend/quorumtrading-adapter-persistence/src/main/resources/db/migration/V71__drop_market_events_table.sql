-- Phase 4: Drop the old market_events table.
-- All reads and writes now use typed tables (price_bars, news_events, social_events).
DROP TABLE IF EXISTS market_events;
DROP SEQUENCE IF EXISTS market_events_seq;
