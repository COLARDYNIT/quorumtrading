-- V10__add_ticker_to_llm_decisions.sql
-- Add ticker column to llm_decisions table to track which asset the decision was for

ALTER TABLE llm_decisions
ADD COLUMN ticker VARCHAR(20);

-- Create index for efficient ticker-based queries
CREATE INDEX idx_llm_decisions_ticker ON llm_decisions(ticker);

-- Create composite index for agent + ticker queries
CREATE INDEX idx_llm_decisions_agent_ticker ON llm_decisions(agent_id, ticker);
