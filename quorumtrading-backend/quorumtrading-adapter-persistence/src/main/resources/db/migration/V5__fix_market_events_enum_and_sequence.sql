-- V5__fix_market_events_enum_and_sequence.sql
-- Fix enum handling and sequence naming after merge

-- Convert event_type column from custom enum type to VARCHAR to support @Enumerated(EnumType.STRING)
-- First, alter the column type (PostgreSQL will handle data conversion automatically)
ALTER TABLE market_events ALTER COLUMN event_type TYPE VARCHAR(50) USING event_type::text;

-- Drop the custom enum type if it exists (must be done after column conversion)
DROP TYPE IF EXISTS event_type CASCADE;

-- Drop the CHECK constraint if it exists
ALTER TABLE market_events DROP CONSTRAINT IF EXISTS market_events_event_type_check;

-- Drop old sequence with uppercase name if it exists
DROP SEQUENCE IF EXISTS market_events_SEQ CASCADE;

-- Create sequence with lowercase name (PostgreSQL convention)
CREATE SEQUENCE IF NOT EXISTS market_events_seq START 1 INCREMENT 1;

-- Ensure the sequence is owned by the table column
ALTER SEQUENCE market_events_seq OWNED BY market_events.id;

-- Set the sequence to continue from current max id
SELECT setval('market_events_seq', COALESCE((SELECT MAX(id) FROM market_events), 0) + 1, false);
