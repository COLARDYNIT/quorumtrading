-- Migration V55: Add LLM cost tracking columns to research_agent_decisions
-- Purpose: Track token usage and costs for individual research agent decisions
-- Date: 2026-01-13
-- Feature: Per-agent LLM cost tracking for market research

-- Add cost columns to research_agent_decisions (individual research agent decision costs)
ALTER TABLE research_agent_decisions
    ADD COLUMN model_used VARCHAR(100),
    ADD COLUMN prompt_tokens INTEGER DEFAULT 0,
    ADD COLUMN completion_tokens INTEGER DEFAULT 0,
    ADD COLUMN total_tokens INTEGER DEFAULT 0,
    ADD COLUMN raw_cost_usd NUMERIC(18,8) DEFAULT 0;

-- Add table comments for the new columns
COMMENT ON COLUMN research_agent_decisions.model_used IS 'The LLM model used for this agent decision (e.g., openai/gpt-4o)';
COMMENT ON COLUMN research_agent_decisions.prompt_tokens IS 'Prompt tokens used for this research agent decision';
COMMENT ON COLUMN research_agent_decisions.completion_tokens IS 'Completion tokens generated for this research agent decision';
COMMENT ON COLUMN research_agent_decisions.total_tokens IS 'Total tokens (prompt + completion) for this research agent decision';
COMMENT ON COLUMN research_agent_decisions.raw_cost_usd IS 'Raw OpenRouter cost in USD (billed cost = raw_cost_usd * 1.30)';
