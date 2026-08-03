-- V4__orchestrator_strategies_and_enum_fix.sql
-- Fixes consensus_method enum and adds updated_at to orchestrator_strategies

-- Fix enum: rename CONFIDENCE_THRESHOLD to CONFIDENCE_BASED
ALTER TYPE consensus_method_type RENAME VALUE 'CONFIDENCE_THRESHOLD' TO 'CONFIDENCE_BASED';

-- Add updated_at column to orchestrator_strategies (V1 only has created_at)
ALTER TABLE orchestrator_strategies ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- Create index for version lookup (if not exists)
CREATE INDEX IF NOT EXISTS idx_strategies_version ON orchestrator_strategies(version);

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_orchestrator_strategies_updated_at
    BEFORE UPDATE ON orchestrator_strategies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
