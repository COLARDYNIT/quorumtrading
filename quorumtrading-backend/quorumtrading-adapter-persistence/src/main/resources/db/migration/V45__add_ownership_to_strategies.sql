-- V45: Add ownership and visibility to strategies

-- Add owner_id and visibility columns
ALTER TABLE orchestrator_strategies
    ADD COLUMN owner_id UUID REFERENCES users(id),
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for owner queries
CREATE INDEX idx_strategies_owner_id ON orchestrator_strategies(owner_id);

-- Index for visibility queries (public strategies)
CREATE INDEX idx_strategies_visibility ON orchestrator_strategies(visibility);

-- Migrate existing strategies to system user
UPDATE orchestrator_strategies
SET owner_id = '00000000-0000-0000-0000-000000000000', visibility = 'PUBLIC'
WHERE owner_id IS NULL;

-- Make owner_id non-nullable after migration
ALTER TABLE orchestrator_strategies ALTER COLUMN owner_id SET NOT NULL;
