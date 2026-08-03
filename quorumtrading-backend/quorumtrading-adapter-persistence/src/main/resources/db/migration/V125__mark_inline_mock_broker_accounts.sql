-- Inline mock broker accounts are created implicitly by CreateSimulationService when a
-- backtest is requested without a brokerAccountId. They belong to that one simulation and
-- have no meaning outside it, but until now they were indistinguishable from mock accounts
-- the user created deliberately: they accumulated in list_broker_accounts, survived the
-- deletion of the simulation that created them, and were reported as the simulation's
-- brokerAccountId even though the caller never supplied one.
--
-- The flag lets the lifecycle be owned by the simulation and lets the API report the
-- account for what it is.
ALTER TABLE broker_accounts
    ADD COLUMN inline BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: every account created by the inline path carries this generated name prefix.
-- Restricted to MOCK so a user-named account cannot be swept up by the pattern.
UPDATE broker_accounts
SET inline = TRUE
WHERE broker_type = 'MOCK'
  AND name LIKE 'Inline Mock Broker - %';

-- Listing broker accounts filters inline ones out, so index for that predicate.
CREATE INDEX idx_broker_accounts_user_not_inline
    ON broker_accounts (user_id)
    WHERE inline = FALSE;
