-- Allow LOCAL_HTTP — a local OpenAI-compatible model server (Ollama, LM Studio, llama.cpp, …) — to
-- be persisted as the deployment's chosen LLM engine. Widens the CHECK added in V120, which only
-- permitted 'LOCAL' and 'OPENROUTER'. Value is stored as the enum name; 'LOCAL_HTTP' fits VARCHAR(20).

ALTER TABLE llm_provider_preference
    DROP CONSTRAINT IF EXISTS llm_provider_preference_provider_known;

ALTER TABLE llm_provider_preference
    ADD CONSTRAINT llm_provider_preference_provider_known
        CHECK (provider IS NULL OR provider IN ('LOCAL', 'LOCAL_HTTP', 'OPENROUTER'));

COMMENT ON COLUMN llm_provider_preference.provider IS
    'Selected engine (LOCAL | LOCAL_HTTP | OPENROUTER); NULL until the user has chosen.';
