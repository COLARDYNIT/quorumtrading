-- V125 marked the mock accounts created by CreateSimulationService, but missed the ones created
-- by CreateBenchmarkService: a benchmark creates one mock account per member simulation, named
-- "<benchmark name> - <strategy name>". Those are inline by exactly the same argument — the user
-- never chose them — but they were left inline = false, so they stayed visible in
-- list_broker_accounts and survived deletion of the simulation that was their only reason to
-- exist, ACTIVE and orphaned.
--
-- Backfill only accounts that are MOCK, still hold no reserved balance, and are referenced by at
-- most one simulation. An account referenced by several simulations is not owned by any one of
-- them and must keep its current lifecycle.
UPDATE broker_accounts ba
SET inline = TRUE
WHERE ba.inline = FALSE
  AND ba.broker_type = 'MOCK'
  AND ba.reserved_balance_amount = 0
  AND (
      SELECT count(*) FROM simulations s WHERE s.broker_account_id = ba.id
  ) <= 1
  AND EXISTS (
      SELECT 1 FROM benchmarks b WHERE ba.name LIKE b.name || ' - %'
  );
