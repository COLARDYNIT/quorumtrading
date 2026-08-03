-- V3__decisions_and_simulations.sql
CREATE TYPE recommendation_type AS ENUM ('BUY', 'SELL', 'HOLD');
CREATE TYPE consensus_method_type AS ENUM ('MAJORITY', 'WEIGHTED', 'CONFIDENCE_THRESHOLD', 'ORCHESTRATOR_OVERRIDE');
CREATE TYPE simulation_status_type AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED');

CREATE TABLE simulations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    strategy_id BIGINT REFERENCES orchestrator_strategies(id),
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    initial_portfolio JSONB NOT NULL,
    configuration JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    results JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE TABLE llm_decisions (
    id BIGSERIAL PRIMARY KEY,
    simulation_id UUID REFERENCES simulations(id),
    decision_timestamp TIMESTAMPTZ NOT NULL,
    agent_id VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    llm_model VARCHAR(255),
    prompt_used TEXT,
    reasoning JSONB NOT NULL,
    recommendation VARCHAR(255),
    recommended_action JSONB,
    confidence_score NUMERIC(38,2),
    execution_time_ms INTEGER
);

CREATE INDEX idx_decisions_simulation ON llm_decisions(simulation_id);
CREATE INDEX idx_decisions_timestamp ON llm_decisions(decision_timestamp);

CREATE TABLE consensus_decisions (
    id BIGSERIAL PRIMARY KEY,
    simulation_id UUID REFERENCES simulations(id),
    decision_timestamp TIMESTAMPTZ NOT NULL,
    trigger_event_id BIGINT REFERENCES market_events(id),
    llm_decision_ids BIGINT[] NOT NULL,
    consensus_method consensus_method_type,
    final_action JSONB NOT NULL,
    executed BOOLEAN DEFAULT FALSE,
    execution_price DECIMAL(20,8),
    notes TEXT
);

CREATE INDEX idx_consensus_simulation ON consensus_decisions(simulation_id);

CREATE TABLE portfolio_snapshots (
    id BIGSERIAL PRIMARY KEY,
    simulation_id UUID REFERENCES simulations(id),
    timestamp TIMESTAMPTZ NOT NULL,
    positions JSONB NOT NULL,
    total_value_usd DECIMAL(20,2),
    cash_available DECIMAL(20,2)
);

CREATE INDEX idx_snapshots_simulation_time ON portfolio_snapshots(simulation_id, timestamp);

CREATE TABLE llm_performance (
    id SERIAL PRIMARY KEY,
    simulation_id UUID REFERENCES simulations(id),
    agent_id VARCHAR(100) NOT NULL,
    total_decisions INTEGER,
    correct_predictions INTEGER,
    avg_confidence DECIMAL(3,2),
    sharpe_ratio DECIMAL(10,4),
    notes JSONB,
    calculated_at TIMESTAMPTZ DEFAULT NOW()
);
