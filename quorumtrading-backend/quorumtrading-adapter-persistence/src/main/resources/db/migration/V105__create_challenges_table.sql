-- Challenge table for gamification system
CREATE TABLE challenges (
    id             UUID PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    ticker         VARCHAR(20) NOT NULL,
    initial_cash   NUMERIC(19,4) NOT NULL,
    start_date     TIMESTAMPTZ NOT NULL,
    end_date       TIMESTAMPTZ NOT NULL,
    join_deadline  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID NOT NULL REFERENCES users(id),
    CONSTRAINT chk_challenge_dates CHECK (end_date > start_date),
    CONSTRAINT chk_challenge_join CHECK (join_deadline IS NULL OR join_deadline <= end_date)
);

-- Add challenge_id to simulations (nullable FK, SET NULL on delete like benchmarks)
ALTER TABLE simulations ADD COLUMN challenge_id UUID REFERENCES challenges(id) ON DELETE SET NULL;

-- Index for finding simulations by challenge
CREATE INDEX idx_simulations_challenge_id ON simulations(challenge_id) WHERE challenge_id IS NOT NULL;

-- Index for listing active/upcoming challenges efficiently
CREATE INDEX idx_challenges_end_date ON challenges(end_date, start_date);
