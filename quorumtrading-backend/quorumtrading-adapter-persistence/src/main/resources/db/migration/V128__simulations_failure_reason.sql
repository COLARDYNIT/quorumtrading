-- Record WHY a simulation failed.
--
-- A FAILED simulation previously carried no cause anywhere in the domain or the API: every
-- endpoint answered with status='FAILED' and nothing else, so "no price data for that range",
-- "the LLM provider is down" and "the broker rejected the order" were indistinguishable to
-- anyone driving the platform through REST or MCP — three problems with three different fixes.
-- The reason existed only in the container log, which an API client cannot read.
--
-- Nullable: NULL means either "not failed" or "failed before this column existed". Rows that
-- failed under the old code keep NULL; there is nothing to backfill them from.
ALTER TABLE simulations ADD COLUMN failure_reason VARCHAR(1000);

COMMENT ON COLUMN simulations.failure_reason IS
    'Human-readable cause of failure. NULL unless status = FAILED (and NULL for runs that failed before V128).';
