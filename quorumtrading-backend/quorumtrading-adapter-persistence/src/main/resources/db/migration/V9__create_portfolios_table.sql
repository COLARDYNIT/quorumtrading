-- V9__create_portfolios_table.sql
-- Create portfolios table for Portfolio domain entity

CREATE TABLE portfolios (
    id BIGSERIAL PRIMARY KEY,
    simulation_id UUID REFERENCES simulations(id) ON DELETE CASCADE,
    cash_available DECIMAL(20,2),
    total_value_usd DECIMAL(20,2),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_portfolios_simulation ON portfolios(simulation_id);
