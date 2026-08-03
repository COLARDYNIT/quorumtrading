-- V7__fix_consensus_method_type.sql
-- Fix consensus_method column that wasn't converted in V6

-- Convert consensus_method column from custom enum type to VARCHAR
ALTER TABLE consensus_decisions ALTER COLUMN consensus_method TYPE VARCHAR(50) USING consensus_method::text;

-- Drop the custom enum type (must be done after column conversion)
DROP TYPE IF EXISTS consensus_method_type CASCADE;
