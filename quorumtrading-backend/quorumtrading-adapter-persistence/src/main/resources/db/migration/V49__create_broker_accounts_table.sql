-- V49__create_broker_accounts_table.sql
-- Create broker_accounts table for user-owned broker connections

CREATE TABLE broker_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    broker_type VARCHAR(50) NOT NULL,
    credentials_encrypted BYTEA,
    total_balance_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
    total_balance_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    reserved_balance_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    mock_config JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_broker_accounts_user_id ON broker_accounts(user_id);
CREATE INDEX idx_broker_accounts_broker_type ON broker_accounts(broker_type);
CREATE INDEX idx_broker_accounts_status ON broker_accounts(status);

COMMENT ON TABLE broker_accounts IS 'User-owned broker account connections (real or mock)';
COMMENT ON COLUMN broker_accounts.broker_type IS 'MOCK, ALPACA_PAPER, ALPACA_LIVE';
COMMENT ON COLUMN broker_accounts.credentials_encrypted IS 'AES-256 encrypted credentials (null for MOCK)';
COMMENT ON COLUMN broker_accounts.mock_config IS 'JSON config for mock brokers: initialCapital, commissionRate, slippage settings';
