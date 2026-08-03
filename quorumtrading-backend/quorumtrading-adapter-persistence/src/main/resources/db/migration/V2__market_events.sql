-- V2__market_events.sql

-- Create sequence with Hibernate's expected naming convention
CREATE SEQUENCE market_events_SEQ START 1 INCREMENT 1;

CREATE TABLE market_events (
    id BIGINT PRIMARY KEY DEFAULT nextval('market_events_SEQ'),
    timestamp TIMESTAMPTZ NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('PRICE_UPDATE', 'NEWS', 'REDDIT_POST')),
    source VARCHAR(100) NOT NULL,
    symbol VARCHAR(20),
    raw_data JSONB NOT NULL,
    ingested_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_events_timestamp ON market_events(timestamp);
CREATE INDEX idx_events_type_symbol ON market_events(event_type, symbol);
CREATE INDEX idx_events_source ON market_events(source);
