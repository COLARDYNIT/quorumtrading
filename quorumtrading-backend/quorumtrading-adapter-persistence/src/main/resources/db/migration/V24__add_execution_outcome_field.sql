-- Add execution_outcome field to consensus_decisions table
ALTER TABLE consensus_decisions
ADD COLUMN execution_outcome VARCHAR(50) DEFAULT 'PENDING';

-- Set execution_outcome based on existing executed flag and notes
-- For existing records, infer the outcome from current state
UPDATE consensus_decisions
SET execution_outcome = CASE
    -- If executed is true, mark as EXECUTED
    WHEN executed = true THEN 'EXECUTED'
    -- If notes contain 'Trade rejected', mark as REJECTED
    WHEN notes LIKE '%Trade rejected%' THEN 'REJECTED'
    -- If notes contain 'No trade required' or final_action contains 'HOLD', mark as NO_TRADE_REQUIRED
    WHEN notes LIKE '%No trade required%' OR final_action::text LIKE '%"action": "HOLD"%' THEN 'NO_TRADE_REQUIRED'
    -- Otherwise keep as PENDING
    ELSE 'PENDING'
END;

-- Add index for efficient filtering by execution outcome
CREATE INDEX idx_consensus_decisions_execution_outcome ON consensus_decisions(execution_outcome);

-- Add comment for documentation
COMMENT ON COLUMN consensus_decisions.execution_outcome IS 'Execution outcome: PENDING, EXECUTED, NO_TRADE_REQUIRED, or REJECTED';
