-- Backfill consensus_decisions cost columns from their linked llm_decisions.
-- These were never populated by ReachConsensusService prior to this fix.
UPDATE consensus_decisions cd
SET raw_cost_usd = sub.total_raw_cost,
    prompt_tokens = sub.total_prompt_tokens,
    completion_tokens = sub.total_completion_tokens,
    total_tokens = sub.total_tokens
FROM (
    SELECT cd2.id,
           COALESCE(SUM(ld.raw_cost_usd), 0) AS total_raw_cost,
           COALESCE(SUM(ld.prompt_tokens), 0) AS total_prompt_tokens,
           COALESCE(SUM(ld.completion_tokens), 0) AS total_completion_tokens,
           COALESCE(SUM(ld.total_tokens), 0) AS total_tokens
    FROM consensus_decisions cd2
    JOIN llm_decisions ld ON ld.id = ANY(cd2.llm_decision_ids)
    GROUP BY cd2.id
) sub
WHERE cd.id = sub.id
  AND (cd.raw_cost_usd IS NULL OR cd.raw_cost_usd = 0);
