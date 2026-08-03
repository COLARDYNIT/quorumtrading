-- Migration V36: Add market_research_id to orchestrator_strategies table
-- Purpose: Link strategies back to the market research that created them
-- Date: 2025-11-17
-- Feature: Market Research (Phase 3 - Database Migrations, Task 3.4)

-- Add the foreign key column
ALTER TABLE orchestrator_strategies
    ADD COLUMN market_research_id UUID REFERENCES market_research(id) ON DELETE SET NULL;

-- Create index for efficient lookups by market research
CREATE INDEX idx_strategies_market_research_id
    ON orchestrator_strategies(market_research_id);

-- Add column comment for clarity
COMMENT ON COLUMN orchestrator_strategies.market_research_id IS 'Foreign key to market_research table. NULL for manually created strategies. Non-NULL for strategies automatically created by market research.';
