-- V101: Add public_visible flag to strategies for admin-curated public strategy library
ALTER TABLE orchestrator_strategies ADD COLUMN public_visible BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for efficient public strategy listing
CREATE INDEX idx_strategies_public_visible ON orchestrator_strategies (public_visible) WHERE public_visible = TRUE;
