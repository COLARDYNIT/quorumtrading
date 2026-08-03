-- V14__convert_simulation_strategy_id_to_uuid.sql
-- Update Simulation table to use UUID for strategy_id foreign key to match refactored Strategy entity

-- Since the strategy table now uses UUID primary key (from V13),
-- we need to update the simulations.strategy_id column to be UUID as well

-- For MVP: Clear existing simulation data since we cannot map old Long foreign keys to new UUIDs
TRUNCATE TABLE simulations CASCADE;

-- Change strategy_id column type from BIGINT to UUID
ALTER TABLE simulations
    ALTER COLUMN strategy_id TYPE UUID USING NULL;

-- Note: The USING NULL clause means all existing rows (if any remained) would get NULL strategy_id
-- But since we truncated, there are no existing rows
