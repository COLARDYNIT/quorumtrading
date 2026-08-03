-- V114: per-simulation notification controls + denormalised owner.
--
-- Adds two columns to simulations:
--   notify_decisions  - per-sim opt-in for SIMULATION_DECISION fires (default off)
--   owner_user_id     - denormalised FK to users, avoids broker_accounts join on
--                       every notification dispatch. Backfilled from broker_accounts,
--                       with strategies.owner_id as a fallback for any rows that
--                       somehow lack a broker_account_id.
--
-- LOW_BUDGET hysteresis is NOT tracked here — UserBudget already emits a
-- one-shot BudgetWarningEvent on threshold-crossing transitions (see
-- UserBudget.recordUsage); the notification listener consumes that event.

ALTER TABLE simulations
    ADD COLUMN notify_decisions BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN owner_user_id UUID;

-- Backfill: prefer broker_accounts.user_id (the trade-level owner).
UPDATE simulations s
   SET owner_user_id = ba.user_id
  FROM broker_accounts ba
 WHERE ba.id = s.broker_account_id
   AND s.owner_user_id IS NULL;

-- Fallback: any remaining rows (sims without broker_account) take owner from the strategy.
UPDATE simulations s
   SET owner_user_id = st.owner_id
  FROM orchestrator_strategies st
 WHERE st.id = s.strategy_id
   AND s.owner_user_id IS NULL;

-- Enforce the invariant going forward. If migration fails here, an orphaned simulation
-- exists with neither broker_account nor a strategy owner — surface it rather than
-- silently swallow it.
ALTER TABLE simulations
    ALTER COLUMN owner_user_id SET NOT NULL,
    ADD CONSTRAINT fk_simulations_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_simulations_owner_user ON simulations(owner_user_id);
