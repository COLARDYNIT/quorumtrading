-- V48: Add ownership and visibility to market_research table
-- Market research items should be owned by users and default to private

-- Add owner_id column (nullable for existing data)
ALTER TABLE market_research ADD COLUMN IF NOT EXISTS owner_id UUID;

-- Add visibility column with default of PRIVATE
ALTER TABLE market_research ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

-- Add foreign key constraint to users table
ALTER TABLE market_research
    ADD CONSTRAINT fk_market_research_owner
    FOREIGN KEY (owner_id)
    REFERENCES users(id)
    ON DELETE SET NULL;

-- Create index for faster user-based queries
CREATE INDEX IF NOT EXISTS idx_market_research_owner_id ON market_research(owner_id);

-- Update existing records to use system user if they have NULL owner
UPDATE market_research
SET owner_id = '00000000-0000-0000-0000-000000000000'
WHERE owner_id IS NULL;

COMMENT ON COLUMN market_research.owner_id IS 'User who owns this market research configuration';
COMMENT ON COLUMN market_research.visibility IS 'Visibility: PUBLIC (anyone can view) or PRIVATE (owner only)';
