CREATE TABLE llm_usage_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    source VARCHAR(20) NOT NULL,
    raw_cost_usd DECIMAL(18, 8) NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_llm_usage_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_llm_usage_log_user_date ON llm_usage_log(user_id, created_at);
CREATE INDEX idx_llm_usage_log_source ON llm_usage_log(source);
