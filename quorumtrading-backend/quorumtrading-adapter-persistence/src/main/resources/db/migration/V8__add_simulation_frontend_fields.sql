-- V8__add_simulation_frontend_fields.sql
-- Add fields needed for frontend API bridge

ALTER TABLE simulations
    ADD COLUMN notes TEXT,
    ADD COLUMN strategy_version VARCHAR(50);

-- Populate strategy_version from the referenced strategy
UPDATE simulations s
SET strategy_version = (
    SELECT st.version
    FROM orchestrator_strategies st
    WHERE st.id = s.strategy_id
)
WHERE s.strategy_id IS NOT NULL;

-- Add index for strategy_version lookups
CREATE INDEX idx_simulations_strategy_version ON simulations(strategy_version);
