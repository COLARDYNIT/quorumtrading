-- GDPR-compliant anti-abuse table: stores SHA-256 hashes of deleted emails
-- to prevent re-registration abuse (e.g., claiming welcome bonus again).
-- Only the hash is stored — the original email is not recoverable.

CREATE TABLE deleted_account_hashes (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email_hash      VARCHAR(64)     NOT NULL UNIQUE,
    original_signup_date TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
