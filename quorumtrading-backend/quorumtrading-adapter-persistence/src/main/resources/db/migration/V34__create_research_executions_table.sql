-- Migration V34: Create research_executions table
-- Purpose: Track individual market research execution results and outcomes
-- Date: 2025-11-17
-- Feature: Market Research (Phase 3 - Database Migrations, Task 3.2)

CREATE TABLE research_executions (
    id UUID PRIMARY KEY,
    market_research_id UUID NOT NULL REFERENCES market_research(id) ON DELETE CASCADE,
    outcome VARCHAR(30) NOT NULL CHECK (outcome IN ('STRATEGY_CREATED', 'NO_OPPORTUNITY', 'FAILED')),
    outcome_reasoning TEXT NOT NULL,
    viability_score NUMERIC(5,2) CHECK (viability_score >= 0 AND viability_score <= 100),
    created_strategy_id UUID REFERENCES orchestrator_strategies(id) ON DELETE SET NULL,
    simulation_id UUID,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index for efficient lookups by market research configuration
CREATE INDEX idx_research_executions_market_research_id
    ON research_executions(market_research_id);

-- Add table and column comments for clarity
COMMENT ON TABLE research_executions IS 'Individual executions of market research configurations with agent consensus outcomes';
COMMENT ON COLUMN research_executions.id IS 'Unique identifier for the research execution';
COMMENT ON COLUMN research_executions.market_research_id IS 'Foreign key to the market research configuration that triggered this execution';
COMMENT ON COLUMN research_executions.outcome IS 'Result of the research execution (STRATEGY_CREATED, NO_OPPORTUNITY, or FAILED)';
COMMENT ON COLUMN research_executions.outcome_reasoning IS 'Detailed explanation of why this outcome was reached';
COMMENT ON COLUMN research_executions.viability_score IS 'Viability score from 0-100 (null if outcome is not STRATEGY_CREATED)';
COMMENT ON COLUMN research_executions.created_strategy_id IS 'Foreign key to the created strategy (null if outcome is not STRATEGY_CREATED)';
COMMENT ON COLUMN research_executions.simulation_id IS 'ID of the backtest simulation (null if auto_backtest was disabled or outcome is not STRATEGY_CREATED)';
COMMENT ON COLUMN research_executions.executed_at IS 'Timestamp when the research execution completed';
