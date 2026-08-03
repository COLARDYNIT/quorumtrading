-- Migration V33: Create market_research table
-- Purpose: Enable scheduled market research automation with agent consensus
-- Date: 2025-11-17
-- Feature: Market Research (Phase 3 - Database Migrations, Task 3.1)

CREATE TABLE market_research (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_assets TEXT NOT NULL,
    interval_amount INTEGER NOT NULL,
    interval_unit VARCHAR(20) NOT NULL CHECK (interval_unit IN ('DAYS', 'WEEKS', 'MONTHS')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED')),
    auto_backtest BOOLEAN NOT NULL DEFAULT false,
    backtest_length_seconds BIGINT,
    backtest_period_start TIMESTAMP,
    backtest_period_end TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_executed_at TIMESTAMP,
    next_scheduled_run TIMESTAMP
);

-- Add table and column comments for clarity
COMMENT ON TABLE market_research IS 'Market research configurations that run on scheduled intervals to discover trading opportunities';
COMMENT ON COLUMN market_research.id IS 'Unique identifier for the market research configuration';
COMMENT ON COLUMN market_research.name IS 'Human-readable name for the research configuration';
COMMENT ON COLUMN market_research.description IS 'Optional detailed description of the research purpose';
COMMENT ON COLUMN market_research.target_assets IS 'Comma-separated list of symbols to research (e.g., "BTC-USD,ETH-USD")';
COMMENT ON COLUMN market_research.interval_amount IS 'Numeric value for the interval (e.g., 7 for "7 days")';
COMMENT ON COLUMN market_research.interval_unit IS 'Unit of time for the interval (DAYS, WEEKS, or MONTHS)';
COMMENT ON COLUMN market_research.status IS 'Current status (ACTIVE, PAUSED, or COMPLETED)';
COMMENT ON COLUMN market_research.auto_backtest IS 'Whether to automatically run backtests on created strategies';
COMMENT ON COLUMN market_research.backtest_length_seconds IS 'Duration of backtests in seconds (null if auto_backtest is false)';
COMMENT ON COLUMN market_research.backtest_period_start IS 'Historical start date for backtests (null if auto_backtest is false)';
COMMENT ON COLUMN market_research.backtest_period_end IS 'Historical end date for backtests (null if auto_backtest is false)';
COMMENT ON COLUMN market_research.created_at IS 'Timestamp when the research configuration was created';
COMMENT ON COLUMN market_research.last_executed_at IS 'Timestamp of the most recent execution (null if never executed)';
COMMENT ON COLUMN market_research.next_scheduled_run IS 'Timestamp when the next execution is scheduled';
