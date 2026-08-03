-- V64: Add distributed locking columns to simulations and mock broker order persistence
-- Supports horizontal scaling: replaces in-memory ConcurrentHashMap locks with DB-based locks.

-- Distributed locking columns for SimulationExecutor idempotency
ALTER TABLE simulations ADD COLUMN locked_by VARCHAR(255);
ALTER TABLE simulations ADD COLUMN locked_at TIMESTAMP;
ALTER TABLE simulations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Index for finding stale locks efficiently
CREATE INDEX idx_simulations_locked_at ON simulations (locked_at) WHERE locked_by IS NOT NULL;

-- Mock broker order persistence (replaces in-memory orderHistory ConcurrentHashMap)
CREATE TABLE mock_broker_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    simulation_id UUID NOT NULL REFERENCES simulations(id) ON DELETE CASCADE,
    order_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity DECIMAL NOT NULL,
    price DECIMAL NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(simulation_id, order_id)
);

CREATE INDEX idx_mock_broker_orders_simulation ON mock_broker_orders (simulation_id);
