-- V58__fix_simulations_without_broker_account.sql
-- Fix existing LIVE/SIMULATING simulations that don't have a broker_account_id set
-- These simulations were created before the broker account fix and would incorrectly
-- route to the global Alpaca broker instead of using mock broker accounts.
--
-- This migration:
-- 1. Creates mock broker accounts for affected simulations
-- 2. Links simulations to their new mock broker accounts
-- 3. Sets trading limits based on the simulation's initial cash

-- Create mock broker accounts for simulations without broker_account_id
-- that are in LIVE or SIMULATING status (active simulations that need routing)
INSERT INTO broker_accounts (
    id,
    user_id,
    name,
    broker_type,
    total_balance_amount,
    total_balance_currency,
    reserved_balance_amount,
    status,
    mock_config,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid() AS id,
    st.owner_id AS user_id,
    'Auto-created Mock Broker for ' || s.name AS name,
    'MOCK' AS broker_type,
    COALESCE((s.initial_portfolio->>'initialCash')::DECIMAL(20,2), 100000) AS total_balance_amount,
    COALESCE(s.initial_portfolio->>'currency', 'USD') AS total_balance_currency,
    0 AS reserved_balance_amount,
    'ACTIVE' AS status,
    jsonb_build_object(
        'initialCapital', jsonb_build_object(
            'amount', COALESCE((s.initial_portfolio->>'initialCash')::DECIMAL(20,2), 100000),
            'currency', COALESCE(s.initial_portfolio->>'currency', 'USD')
        ),
        'commissionRate', 0,
        'slippage', jsonb_build_object('type', 'NONE')
    ) AS mock_config,
    NOW() AS created_at,
    NOW() AS updated_at
FROM simulations s
JOIN orchestrator_strategies st ON s.strategy_id = st.id
WHERE s.broker_account_id IS NULL
  AND s.status IN ('LIVE', 'SIMULATING');

-- Now link simulations to their newly created broker accounts
-- We use a CTE to create a mapping between simulations and the broker accounts we just created
WITH simulation_broker_mapping AS (
    SELECT
        s.id AS simulation_id,
        ba.id AS broker_account_id,
        COALESCE((s.initial_portfolio->>'initialCash')::DECIMAL(20,2), 100000) AS trading_limit_amount,
        COALESCE(s.initial_portfolio->>'currency', 'USD') AS trading_limit_currency
    FROM simulations s
    JOIN orchestrator_strategies st ON s.strategy_id = st.id
    JOIN broker_accounts ba ON ba.user_id = st.owner_id
        AND ba.name = 'Auto-created Mock Broker for ' || s.name
        AND ba.broker_type = 'MOCK'
    WHERE s.broker_account_id IS NULL
      AND s.status IN ('LIVE', 'SIMULATING')
)
UPDATE simulations s
SET
    broker_account_id = m.broker_account_id,
    trading_limit_amount = m.trading_limit_amount,
    trading_limit_currency = m.trading_limit_currency
FROM simulation_broker_mapping m
WHERE s.id = m.simulation_id;

-- Log the count of fixed simulations for audit purposes
DO $$
DECLARE
    fixed_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO fixed_count
    FROM simulations
    WHERE broker_account_id IS NOT NULL
      AND status IN ('LIVE', 'SIMULATING');

    RAISE NOTICE 'Fixed % LIVE/SIMULATING simulations with mock broker accounts', fixed_count;
END $$;
