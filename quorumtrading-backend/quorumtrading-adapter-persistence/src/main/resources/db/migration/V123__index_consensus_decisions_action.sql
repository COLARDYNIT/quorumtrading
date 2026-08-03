-- Expression index on the JSONB action key used by the two repository filters
-- (final_action->>'action' = :action): the cooldown probe runs every consensus tick
-- per ticker and the trade-history block runs per consensus round.
-- Additive, no rows touched. Severable if migration-number contention arises.
CREATE INDEX IF NOT EXISTS idx_consensus_decisions_action
    ON consensus_decisions ((final_action->>'action'));
