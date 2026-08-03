-- V56: Add user budgets table for AI budget management
-- Part of the budget/model selection feature

-- User budget aggregate table
CREATE TABLE user_budgets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,

    -- Budget limit (null = unlimited)
    budget_limit_amount DECIMAL(10, 2),
    budget_limit_currency VARCHAR(3) DEFAULT 'USD',

    -- Current period spend
    current_spend_amount DECIMAL(10, 6) NOT NULL DEFAULT 0,
    current_period VARCHAR(7) NOT NULL,  -- Format: "2026-01"

    -- Model preferences stored as JSONB
    -- Format: {"LIVE_TRADING": "anthropic/claude-3.5-sonnet", "BACKTESTING": "qwen/qwen-2.5-72b:free"}
    model_preferences JSONB DEFAULT '{}',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_budget_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_budgets_user_id ON user_budgets(user_id);

-- Add external pause fields to market_research table
-- This allows the budget context to pause research without knowing about ResearchStatus internals
ALTER TABLE market_research
    ADD COLUMN paused_for_external_reason BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN external_pause_reason VARCHAR(100);

-- Add external pause fields to simulations table
ALTER TABLE simulations
    ADD COLUMN paused_for_external_reason BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN external_pause_reason VARCHAR(100);

-- Add comments for documentation
COMMENT ON TABLE user_budgets IS 'Stores user AI budget configuration and current spend';
COMMENT ON COLUMN user_budgets.budget_limit_amount IS 'Monthly budget limit in USD, null means unlimited';
COMMENT ON COLUMN user_budgets.current_spend_amount IS 'Current month spend in USD';
COMMENT ON COLUMN user_budgets.current_period IS 'Current billing period in YYYY-MM format';
COMMENT ON COLUMN user_budgets.model_preferences IS 'JSON map of TradingContext to preferred model ID';

COMMENT ON COLUMN market_research.paused_for_external_reason IS 'True if paused by external system (e.g., budget depleted)';
COMMENT ON COLUMN market_research.external_pause_reason IS 'Reason for external pause (e.g., "AI budget depleted")';

COMMENT ON COLUMN simulations.paused_for_external_reason IS 'True if paused by external system (e.g., budget depleted)';
COMMENT ON COLUMN simulations.external_pause_reason IS 'Reason for external pause (e.g., "AI budget depleted")';
