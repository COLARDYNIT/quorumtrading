-- Migration V54: Add LLM cost tracking columns
-- Purpose: Track token usage and costs for billing (OpenRouter cost + 30% markup)
-- Date: 2026-01-13
-- Feature: Per-user LLM cost tracking for billing

-- Add cost columns to research_executions (market research costs)
ALTER TABLE research_executions
    ADD COLUMN prompt_tokens INTEGER DEFAULT 0,
    ADD COLUMN completion_tokens INTEGER DEFAULT 0,
    ADD COLUMN total_tokens INTEGER DEFAULT 0,
    ADD COLUMN raw_cost_usd NUMERIC(18,8) DEFAULT 0;

-- Add cost columns to consensus_decisions (simulation consensus costs)
ALTER TABLE consensus_decisions
    ADD COLUMN prompt_tokens INTEGER DEFAULT 0,
    ADD COLUMN completion_tokens INTEGER DEFAULT 0,
    ADD COLUMN total_tokens INTEGER DEFAULT 0,
    ADD COLUMN raw_cost_usd NUMERIC(18,8) DEFAULT 0;

-- Add cost columns to llm_decisions (individual agent decision costs)
ALTER TABLE llm_decisions
    ADD COLUMN prompt_tokens INTEGER DEFAULT 0,
    ADD COLUMN completion_tokens INTEGER DEFAULT 0,
    ADD COLUMN total_tokens INTEGER DEFAULT 0,
    ADD COLUMN raw_cost_usd NUMERIC(18,8) DEFAULT 0;

-- Add table comments for the new columns
COMMENT ON COLUMN research_executions.prompt_tokens IS 'Total prompt tokens used across all agent calls for this research execution';
COMMENT ON COLUMN research_executions.completion_tokens IS 'Total completion tokens generated across all agent calls for this research execution';
COMMENT ON COLUMN research_executions.total_tokens IS 'Total tokens (prompt + completion) for this research execution';
COMMENT ON COLUMN research_executions.raw_cost_usd IS 'Raw OpenRouter cost in USD (billed cost = raw_cost_usd * 1.30)';

COMMENT ON COLUMN consensus_decisions.prompt_tokens IS 'Total prompt tokens used for this consensus decision';
COMMENT ON COLUMN consensus_decisions.completion_tokens IS 'Total completion tokens generated for this consensus decision';
COMMENT ON COLUMN consensus_decisions.total_tokens IS 'Total tokens (prompt + completion) for this consensus decision';
COMMENT ON COLUMN consensus_decisions.raw_cost_usd IS 'Raw OpenRouter cost in USD (billed cost = raw_cost_usd * 1.30)';

COMMENT ON COLUMN llm_decisions.prompt_tokens IS 'Prompt tokens used for this individual agent decision';
COMMENT ON COLUMN llm_decisions.completion_tokens IS 'Completion tokens generated for this individual agent decision';
COMMENT ON COLUMN llm_decisions.total_tokens IS 'Total tokens (prompt + completion) for this agent decision';
COMMENT ON COLUMN llm_decisions.raw_cost_usd IS 'Raw OpenRouter cost in USD (billed cost = raw_cost_usd * 1.30)';
