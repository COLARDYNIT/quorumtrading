-- V40__create_scheduled_simulations.sql
-- Table for simulation scheduling configuration

CREATE TABLE scheduled_simulations (
    id UUID PRIMARY KEY,
    strategy_id UUID NOT NULL REFERENCES orchestrator_strategies(id) ON DELETE CASCADE,
    frequency VARCHAR(50) NOT NULL DEFAULT 'MANUAL_ONLY',
    next_run_at TIMESTAMP WITH TIME ZONE,
    last_run_at TIMESTAMP WITH TIME ZONE,
    enabled BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    CONSTRAINT chk_frequency CHECK (frequency IN ('DAILY', 'WEEKLY', 'MANUAL_ONLY'))
);

-- Index for finding due simulations
CREATE INDEX idx_scheduled_sims_due ON scheduled_simulations(next_run_at)
    WHERE enabled = true AND next_run_at IS NOT NULL;

-- Index for strategy lookup
CREATE INDEX idx_scheduled_sims_strategy ON scheduled_simulations(strategy_id);

-- One schedule per strategy
CREATE UNIQUE INDEX idx_scheduled_sims_unique_strategy ON scheduled_simulations(strategy_id);

COMMENT ON TABLE scheduled_simulations IS 'Configuration for recurring simulation runs';
