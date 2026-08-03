-- Payment transactions table for tracking Stripe and other payment provider transactions
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider_transaction_id VARCHAR(255) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'usd',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description VARCHAR(500),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_payment_transactions_user_id ON payment_transactions(user_id);
CREATE INDEX idx_payment_transactions_provider_tx_id ON payment_transactions(provider_transaction_id);
CREATE INDEX idx_payment_transactions_status ON payment_transactions(status);
CREATE INDEX idx_payment_transactions_created_at ON payment_transactions(created_at DESC);

-- Comments
COMMENT ON TABLE payment_transactions IS 'Tracks payment transactions from Stripe and other payment providers';
COMMENT ON COLUMN payment_transactions.provider_transaction_id IS 'External transaction ID from payment provider (e.g., Stripe checkout session ID)';
COMMENT ON COLUMN payment_transactions.provider IS 'Payment provider name (e.g., stripe)';
COMMENT ON COLUMN payment_transactions.status IS 'PENDING, SUCCEEDED, FAILED, CANCELED, REFUNDED';
COMMENT ON COLUMN payment_transactions.metadata IS 'JSON string with provider-specific metadata';
