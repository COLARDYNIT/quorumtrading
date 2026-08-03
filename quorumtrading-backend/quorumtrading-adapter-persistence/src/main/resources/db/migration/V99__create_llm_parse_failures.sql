CREATE TABLE llm_parse_failures (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    simulation_id       UUID,
    decision_timestamp  TIMESTAMPTZ,
    ticker              VARCHAR(20),
    agent_role          VARCHAR(30) NOT NULL,
    requested_model     VARCHAR(255),
    actual_model        VARCHAR(255),
    temperature         DOUBLE PRECISION,
    execution_time_ms   INTEGER NOT NULL,
    prompt              TEXT,
    raw_response        TEXT,
    error_message       TEXT NOT NULL,
    error_type          VARCHAR(255)
);

CREATE INDEX idx_llm_parse_failures_sim ON llm_parse_failures(simulation_id);
CREATE INDEX idx_llm_parse_failures_created ON llm_parse_failures(created_at);
