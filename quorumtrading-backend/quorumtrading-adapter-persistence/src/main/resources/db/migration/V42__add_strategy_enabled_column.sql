-- V40: Add enabled column to orchestrator_strategies for Phase 4 auto-disable
-- Strategies can be automatically disabled when performance drops below thresholds

ALTER TABLE orchestrator_strategies
ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN orchestrator_strategies.enabled IS 'Whether strategy is enabled for scheduled simulations. Auto-disabled when underperforming.';
