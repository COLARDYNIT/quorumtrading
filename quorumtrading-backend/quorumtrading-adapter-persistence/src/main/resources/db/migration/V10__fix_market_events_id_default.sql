-- V10__fix_market_events_id_default.sql
-- Fix the DEFAULT value for id column to use the lowercase sequence name

-- Update the DEFAULT value to use the correct sequence name
ALTER TABLE market_events ALTER COLUMN id SET DEFAULT nextval('market_events_seq');
