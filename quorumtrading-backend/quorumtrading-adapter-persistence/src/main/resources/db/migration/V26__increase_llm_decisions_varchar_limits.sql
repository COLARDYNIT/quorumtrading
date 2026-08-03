-- V26: Increase VARCHAR limits in llm_decisions table to handle longer model names
-- Addresses issue where some LLM model names can exceed 255 characters
-- Example: Very long model names with version strings and provider prefixes

-- llm_model: Can have long model names like "meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo:free"
-- Increase from 255 to 500 to provide headroom for future longer model names
ALTER TABLE llm_decisions ALTER COLUMN llm_model TYPE VARCHAR(500);

-- Other VARCHAR(255) columns are appropriate:
-- - agent_id: UUID strings (36 chars)
-- - role: Agent role names (short enum-like values)
-- - recommendation: Recommendation type (BUY, SELL, HOLD)
-- - ticker: Symbol ticker (typically 3-10 chars)

-- Note: prompt_used, reasoning, recommended_action are already TEXT/JSONB (unlimited)
