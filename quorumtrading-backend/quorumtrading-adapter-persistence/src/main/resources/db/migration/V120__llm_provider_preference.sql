-- Runtime LLM provider selection (self-host edition).
--
-- Single-row table: the deployment-wide choice between the local CLI engine and OpenRouter,
-- plus an optional user-submitted OpenRouter API key (AES-256-GCM, same encryption-at-rest
-- scheme as broker_accounts.credentials_encrypted).
--
-- provider is NULL until the user makes their first selection — a key can be stored before
-- any provider is chosen (the setup flow saves the key, probes availability, then selects).

CREATE TABLE llm_provider_preference (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    provider VARCHAR(20),
    openrouter_api_key_encrypted BYTEA,
    configured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT llm_provider_preference_single_row CHECK (id = 1),
    CONSTRAINT llm_provider_preference_provider_known
        CHECK (provider IS NULL OR provider IN ('LOCAL', 'OPENROUTER'))
);

COMMENT ON TABLE llm_provider_preference IS
    'Deployment-wide LLM engine selection (self-host edition). Single row, id always 1.';
COMMENT ON COLUMN llm_provider_preference.provider IS
    'Selected engine (LOCAL | OPENROUTER); NULL until the user has chosen.';
COMMENT ON COLUMN llm_provider_preference.openrouter_api_key_encrypted IS
    'User-submitted OpenRouter API key, AES-256-GCM: IV(12) || ciphertext || tag(16).';
