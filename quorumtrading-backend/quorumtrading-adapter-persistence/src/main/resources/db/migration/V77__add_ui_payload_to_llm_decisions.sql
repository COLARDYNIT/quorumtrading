-- Add optional A2UI declarative UI payload to LLM decisions.
-- Stores JSON describing visual components for agent decision explanations.
ALTER TABLE llm_decisions ADD COLUMN ui_payload JSONB;
