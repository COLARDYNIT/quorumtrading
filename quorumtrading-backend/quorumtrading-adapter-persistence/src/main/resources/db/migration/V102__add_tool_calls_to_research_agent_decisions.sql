-- Migration V102: Add tool_calls column to research_agent_decisions table
-- Purpose: Store tool call details (JSON) from agent reasoning for display in UI
-- Date: 2026-02-25

ALTER TABLE research_agent_decisions
    ADD COLUMN tool_calls TEXT;

COMMENT ON COLUMN research_agent_decisions.tool_calls IS 'JSON array of tool calls made during agent reasoning, e.g., [{"tool":"get_indicators","args":{...},"result":"..."}]';
