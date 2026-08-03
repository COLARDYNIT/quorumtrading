-- Ensure completed_at is set when status is SUCCEEDED
-- and that status values are within the allowed set
ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_status_values
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELED', 'REFUNDED'));

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_completed_at_required_on_success
    CHECK (status != 'SUCCEEDED' OR completed_at IS NOT NULL);
