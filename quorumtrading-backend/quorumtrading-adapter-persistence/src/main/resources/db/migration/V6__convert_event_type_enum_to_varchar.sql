-- V6__convert_event_type_enum_to_varchar.sql
-- Convert enum types to VARCHAR to support Hibernate's @Enumerated(EnumType.STRING)

-- Convert event_type column from custom enum type to VARCHAR
ALTER TABLE market_events ALTER COLUMN event_type TYPE VARCHAR(50) USING event_type::text;

-- Convert consensus_method column from custom enum type to VARCHAR
ALTER TABLE consensus_decisions ALTER COLUMN consensus_method TYPE VARCHAR(50) USING consensus_method::text;

-- Drop the custom enum types (must be done after column conversion)
DROP TYPE IF EXISTS event_type CASCADE;
DROP TYPE IF EXISTS consensus_method_type CASCADE;
