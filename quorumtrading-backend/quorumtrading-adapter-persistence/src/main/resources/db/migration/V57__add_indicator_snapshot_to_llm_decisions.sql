-- V57: Add indicator_snapshot column to llm_decisions table
-- Stores technical indicator values (RSI, MACD, SMA, etc.) that were used at decision time
-- This enables audit trails and traceability of what indicator values influenced each decision

ALTER TABLE llm_decisions
ADD COLUMN indicator_snapshot JSONB;

-- Add index for querying decisions by indicator presence
CREATE INDEX idx_llm_decisions_has_indicators
ON llm_decisions ((indicator_snapshot IS NOT NULL));

COMMENT ON COLUMN llm_decisions.indicator_snapshot IS 'Technical indicator values at decision time (RSI, MACD, SMA, etc.) as JSON';
