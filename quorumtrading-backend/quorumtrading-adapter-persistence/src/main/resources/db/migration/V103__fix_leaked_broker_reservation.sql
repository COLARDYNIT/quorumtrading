-- Fix leaked broker account reservation from deleted simulation.
-- Broker account a1c4e702-3ded-46cc-acee-29aa0f7d2f19 has reserved_balance_amount = 19200.00
-- but only one active simulation (0a1f2f21) with trading_limit_amount = 9600.00.
-- The extra 9600.00 leaked from a previously deleted simulation.
--
-- Recalculate reserved_balance_amount as sum of trading_limit_amount from all non-terminal simulations.
UPDATE broker_accounts ba
SET reserved_balance_amount = COALESCE((
    SELECT SUM(s.trading_limit_amount)
    FROM simulations s
    WHERE s.broker_account_id = ba.id
      AND s.status NOT IN ('COMPLETED', 'FAILED')
), 0),
    updated_at = NOW()
WHERE ba.id = 'a1c4e702-3ded-46cc-acee-29aa0f7d2f19'
  AND ba.reserved_balance_amount != COALESCE((
    SELECT SUM(s.trading_limit_amount)
    FROM simulations s
    WHERE s.broker_account_id = ba.id
      AND s.status NOT IN ('COMPLETED', 'FAILED')
), 0);
