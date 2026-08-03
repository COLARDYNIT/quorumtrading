-- V52: Allow null or empty target_assets in market_research
-- Target assets are now optional - the LLM can discover relevant assets from instructions

ALTER TABLE market_research ALTER COLUMN target_assets DROP NOT NULL;

-- Set a default empty string for any existing rows with null (if any)
UPDATE market_research SET target_assets = '' WHERE target_assets IS NULL;

COMMENT ON COLUMN market_research.target_assets IS 'Optional comma-separated list of symbols to research. Empty if LLM should discover assets from instructions.';
