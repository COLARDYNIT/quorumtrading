-- V117__saxo_broker_accounts_oauth_lease.sql
-- Saxo / EU-instrument support (keystone K7) — merged broker_accounts ALTER.
--
-- Adds the Saxo OAuth + refresh-lease columns to broker_accounts in ONE additive ALTER.
-- Every column is NULLABLE and has no default beyond NULL, so existing MOCK / ALPACA_*
-- rows are untouched and the change is backwards-compatible. The Wave-2 persistence layer
-- (PB3-jpa-columns, OA3-lease-adapter) will read/write these; here they only need to EXIST.
--
-- Exactly ONE environment column: broker_environment ('SIM' | 'LIVE'), the Saxo
-- SaxoEnvironment value for a SAXO_SIM / SAXO_LIVE account. (Alpaca paper/live is already
-- encoded in broker_type, so no second environment column is introduced.)
--
-- Refresh-lease columns implement the per-account atomic UPDATE-WHERE lease (R5) that makes
-- the rotating-refresh-token chain safe across multiple ECS tasks:
--   access_token_expires_at - when the cached ~20-min access token expires (proactive refresh trigger)
--   refresh_generation       - monotonic watermark bumped on each successful rotation (lost-update guard)
--   refresh_locked_by        - identity of the instance currently holding the refresh lease (NULL = free)
--   refresh_locked_at        - when the lease was acquired (drives forceReleaseStale of crashed holders)

ALTER TABLE broker_accounts
    ADD COLUMN broker_environment       VARCHAR(10),
    ADD COLUMN access_token_expires_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN refresh_generation       BIGINT,
    ADD COLUMN refresh_locked_by        VARCHAR(255),
    ADD COLUMN refresh_locked_at        TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN broker_accounts.broker_environment IS 'Saxo environment for SAXO_* accounts: SIM or LIVE (NULL for MOCK/ALPACA_*)';
COMMENT ON COLUMN broker_accounts.access_token_expires_at IS 'Expiry of the cached OAuth access token; drives proactive refresh (NULL for non-OAuth brokers)';
COMMENT ON COLUMN broker_accounts.refresh_generation IS 'Monotonic watermark bumped on each refresh-token rotation; lost-update guard';
COMMENT ON COLUMN broker_accounts.refresh_locked_by IS 'Instance identity holding the per-account refresh lease (NULL = unlocked)';
COMMENT ON COLUMN broker_accounts.refresh_locked_at IS 'When the refresh lease was acquired; drives stale-lease force-release';

-- Partial index for the refresh-lease probe: the scheduler finds OAuth accounts due for a
-- proactive token refresh by access_token_expires_at, restricted to rows that actually
-- participate in the OAuth lifecycle (those with an expiry watermark set). Keeps the index
-- tiny by excluding every MOCK / ALPACA_* row.
CREATE INDEX idx_broker_accounts_token_refresh
    ON broker_accounts(access_token_expires_at)
    WHERE access_token_expires_at IS NOT NULL;
