-- Support GROUP BY actual_model with time filter for model reliability stats
CREATE INDEX idx_llm_parse_failures_model ON llm_parse_failures(actual_model);

-- Support GROUP BY llm_model with time filter for model reliability stats
CREATE INDEX idx_llm_decisions_model ON llm_decisions(llm_model);
