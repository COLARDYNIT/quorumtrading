-- Archived LLM cost columns for restart-from-decision feature.
-- When decisions are deleted during a restart, their cost is archived here
-- so CalculateSimulationCostService can still report accurate total costs.
ALTER TABLE simulations ADD COLUMN archived_llm_cost_usd DECIMAL(18,8) NOT NULL DEFAULT 0;
ALTER TABLE simulations ADD COLUMN archived_llm_prompt_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE simulations ADD COLUMN archived_llm_completion_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE simulations ADD COLUMN archived_llm_total_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE simulations ADD COLUMN archived_llm_decision_count INTEGER NOT NULL DEFAULT 0;
