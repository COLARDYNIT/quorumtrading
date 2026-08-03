-- V39__add_strategy_metadata.sql
-- Add lineage tracking and test metadata to strategies

ALTER TABLE orchestrator_strategies
    ADD COLUMN parent_strategy_id UUID REFERENCES orchestrator_strategies(id),
    ADD COLUMN generation INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN last_tested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN test_frequency VARCHAR(50) DEFAULT 'MANUAL_ONLY' NOT NULL;

-- Index for lineage queries
CREATE INDEX idx_strategies_parent ON orchestrator_strategies(parent_strategy_id);

-- Index for finding strategies needing retest
CREATE INDEX idx_strategies_last_tested ON orchestrator_strategies(last_tested_at);

COMMENT ON COLUMN orchestrator_strategies.parent_strategy_id IS 'ID of parent strategy for derived/evolved strategies';
COMMENT ON COLUMN orchestrator_strategies.generation IS 'Generation number (0=original, increments for derived)';
COMMENT ON COLUMN orchestrator_strategies.last_tested_at IS 'Timestamp of last completed simulation';
COMMENT ON COLUMN orchestrator_strategies.test_frequency IS 'DAILY, WEEKLY, or MANUAL_ONLY';
