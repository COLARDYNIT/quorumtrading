-- Migration V35: Create research_agent_decisions table
-- Purpose: Store individual agent advice during market research consensus process
-- Date: 2025-11-17
-- Feature: Market Research (Phase 3 - Database Migrations, Task 3.3)

CREATE TABLE research_agent_decisions (
    id UUID PRIMARY KEY,
    research_execution_id UUID NOT NULL REFERENCES research_executions(id) ON DELETE CASCADE,
    agent_type VARCHAR(50) NOT NULL CHECK (agent_type IN ('NEWS_SENTIMENT_ANALYST', 'STRATEGY_DESIGNER', 'VIABILITY_ASSESSOR')),
    advice TEXT NOT NULL,
    reasoning TEXT NOT NULL,
    decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index for efficient lookups by research execution
CREATE INDEX idx_research_agent_decisions_execution_id
    ON research_agent_decisions(research_execution_id);

-- Add table and column comments for clarity
COMMENT ON TABLE research_agent_decisions IS 'Individual agent decisions during market research execution consensus process';
COMMENT ON COLUMN research_agent_decisions.id IS 'Unique identifier for the agent decision';
COMMENT ON COLUMN research_agent_decisions.research_execution_id IS 'Foreign key to the research execution this decision belongs to';
COMMENT ON COLUMN research_agent_decisions.agent_type IS 'Type of agent that made this decision (NEWS_SENTIMENT_ANALYST, STRATEGY_DESIGNER, or VIABILITY_ASSESSOR)';
COMMENT ON COLUMN research_agent_decisions.advice IS 'The agent''s advice or recommendation';
COMMENT ON COLUMN research_agent_decisions.reasoning IS 'The agent''s reasoning and analysis supporting the advice';
COMMENT ON COLUMN research_agent_decisions.decided_at IS 'Timestamp when the agent made this decision';
