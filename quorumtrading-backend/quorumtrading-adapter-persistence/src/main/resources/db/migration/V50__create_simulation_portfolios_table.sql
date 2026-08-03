-- V50__create_simulation_portfolios_table.sql
-- Create simulation_portfolios table for per-simulation P&L tracking

CREATE TABLE simulation_portfolios (
    simulation_id UUID PRIMARY KEY REFERENCES simulations(id) ON DELETE CASCADE,
    broker_account_id UUID NOT NULL REFERENCES broker_accounts(id),
    positions JSONB NOT NULL DEFAULT '{}',
    cash_available DECIMAL(20,2) NOT NULL,
    cash_reserved DECIMAL(20,2) NOT NULL,
    realized_pnl DECIMAL(20,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulation_portfolios_broker_account ON simulation_portfolios(broker_account_id);

COMMENT ON TABLE simulation_portfolios IS 'Per-simulation portfolio tracking for P&L isolation';
COMMENT ON COLUMN simulation_portfolios.positions IS 'JSON map of symbol -> {quantity, averagePrice}';
COMMENT ON COLUMN simulation_portfolios.cash_reserved IS 'Original trading limit (immutable)';
