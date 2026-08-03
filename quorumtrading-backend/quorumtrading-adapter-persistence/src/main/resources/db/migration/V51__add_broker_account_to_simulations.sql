-- V51__add_broker_account_to_simulations.sql
-- Add broker_account_id and trading_limit to simulations table

ALTER TABLE simulations
    ADD COLUMN broker_account_id UUID REFERENCES broker_accounts(id),
    ADD COLUMN trading_limit_amount DECIMAL(20,2),
    ADD COLUMN trading_limit_currency VARCHAR(3);

CREATE INDEX idx_simulations_broker_account ON simulations(broker_account_id);

COMMENT ON COLUMN simulations.broker_account_id IS 'Reference to broker account (nullable during migration)';
COMMENT ON COLUMN simulations.trading_limit_amount IS 'Reserved capital for this simulation';
