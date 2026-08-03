-- Record which model was ASKED for, alongside the one that answered.
--
-- llm_decisions.llm_model has always held the actual responder (the server's own `model` field,
-- the CLI tool name, the OpenRouter response body) and must keep doing so — it is the one field
-- in this table that never lied. What was missing is the other half: when an engine cannot serve
-- the model the resolution chain chose, the substitution was visible only as a WARN in a container
-- log, so nothing queryable ever recorded that a run used a different model than it was configured
-- to use.
--
-- Nullable, and left NULL when request and response agree: a value here means "these differ", which
-- makes the interesting rows findable with `WHERE requested_model IS NOT NULL`. Existing rows stay
-- NULL, which is honest — we do not know what they requested.
ALTER TABLE llm_decisions ADD COLUMN requested_model VARCHAR(255);

COMMENT ON COLUMN llm_decisions.requested_model IS
    'Model the resolution chain asked for, when it differs from llm_model (the model that actually answered). NULL when they agree or when unknown.';
