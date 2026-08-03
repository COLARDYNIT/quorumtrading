-- Remove public/private visibility feature: all strategies are now private-only

-- Drop index on visibility column
DROP INDEX IF EXISTS idx_strategies_visibility;

-- Drop visibility and featured columns from orchestrator_strategies
ALTER TABLE orchestrator_strategies DROP COLUMN IF EXISTS visibility;
ALTER TABLE orchestrator_strategies DROP COLUMN IF EXISTS featured;

-- Drop visibility column from market_research
ALTER TABLE market_research DROP COLUMN IF EXISTS visibility;
