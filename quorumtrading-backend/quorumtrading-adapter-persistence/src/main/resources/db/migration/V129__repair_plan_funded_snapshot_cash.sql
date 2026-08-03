-- Repair the cash term of portfolio snapshots belonging to plan-funded simulations.
--
-- WHAT WENT WRONG
-- A simulation created by an investment plan was provisionally seeded, in the mock broker, with the
-- backing ACCOUNT's capital instead of the amount the plan actually funded it with. The periodic
-- valuation reads the broker, so every snapshot after the first overwrote a correct value with an
-- account-level one. Measured on the local instance:
--
--   a81d46f1  funded 1,000  ->  snapshots said 12,000   (account capital)  -> reported +1100%
--   dfcb0883  funded 1,000  ->  snapshots said 100,000  (account capital)  -> reported +9900%
--   854f1f75  funded 12,000 ->  snapshots said 495.04   (account's AVAILABLE was 0, all reserved,
--                                                        so the same defect ran the other way)
--
-- WHY THIS ONLY TOUCHES THE CASH TERM
-- A snapshot's total is cash + the market value of its positions. Only the cash term was wrong; the
-- market value the engine computed is kept exactly as it stands. Recomputing market value here would
-- put a second implementation of the valuation rule in SQL, and two copies of one rule disagreeing is
-- the very defect being repaired. So:
--
--   corrected_cash  = initialCash + contributions recorded at or before the snapshot - cost basis of
--                     the positions that snapshot itself holds
--   corrected_total = corrected_cash + (stored_total - stored_cash)      <- market value preserved
--
-- Every input is already stored, so nothing is inferred. Verified by hand against 854f1f75:
-- 500 + 11,500 - (0.00696985 x 64,272.92 = 447.97) = 11,552.03, which is exactly what
-- simulation_portfolios.cash_available already holds for that simulation.
--
-- CONTRIBUTIONS ARE MATCHED ON created_at, NOT contribution_date
-- contribution_date is truncated to midnight, so using it would retroactively credit a contribution
-- to snapshots taken earlier the same day. created_at is the instant the money was recorded.
--
-- SCOPE AND SAFETY
--  * Only simulations with at least one plan-sourced contribution — the population where funded
--    capital differs from account capital by design. An ordinary backtest is seeded with its
--    account's capital on purpose, so the formula agrees and its rows are left alone.
--  * Only rows whose cash actually disagrees by more than a cent, so this is idempotent: run it
--    twice and the second pass matches nothing.
--  * Never writes a non-positive total. PortfolioSnapshot rejects a total value <= 0, so a row that
--    would compute to zero or less is left untouched and reported below rather than made unreadable.
--  * A no-op on any environment that does not have the defect.
--
-- ORDERING — READ THIS BEFORE DEPLOYING
-- This repairs history. It does NOT prevent recurrence; that is the seeding fix in the mock broker
-- adapter. **Do not let this migration run before that fix is in the same build.** Flyway applies a
-- versioned migration exactly once, so if it runs first the repaired rows are re-corrupted by the
-- next periodic valuation minutes later, the migration is already recorded as applied, and undoing
-- that needs a fresh V-numbered migration. Ship the code fix and this together.
--
-- Verified by running the whole block against the local instance inside a transaction and rolling
-- back: 140 rows repaired, and afterwards the three simulations above reported 1,000 / 1,000 /
-- 12,002.25 against 1,000 / 1,000 / 12,000 funded — i.e. 0%, 0% and +0.02% instead of +1100%,
-- +9900% and -95.87%.
--
-- KNOWN IMPRECISION, STATED RATHER THAN HIDDEN
-- Cash is reconstructed as contributions minus cost basis, which does not model commission or
-- slippage. On the local data one row moved by $1.12 for that reason, against an error of $11,000
-- it was fixing. Rows taken before any contribution are excluded entirely, since those were already
-- right and only carry that residue.

DO $$
DECLARE
    repaired   bigint;
    unrepaired bigint;
BEGIN
    CREATE TEMP TABLE snapshot_cash_repair AS
    SELECT
        ps.id,
        ps.cash_available                                        AS stored_cash,
        ps.total_value_usd                                       AS stored_total,
        (s.initial_portfolio ->> 'initialCash')::numeric
            + COALESCE((
                SELECT sum(c.amount)
                FROM simulation_contributions c
                WHERE c.simulation_id = ps.simulation_id
                  AND c.created_at <= ps.timestamp
              ), 0)
            - COALESCE((
                SELECT sum((p ->> 'quantity')::numeric * (p ->> 'averagePrice')::numeric)
                FROM jsonb_array_elements(ps.positions) p
              ), 0)                                              AS corrected_cash,
        ps.total_value_usd - ps.cash_available                    AS market_value
    FROM portfolio_snapshots ps
    JOIN simulations s ON s.id = ps.simulation_id
    WHERE ps.cash_available IS NOT NULL
      AND ps.total_value_usd IS NOT NULL
      AND s.initial_portfolio ->> 'initialCash' IS NOT NULL
      -- Only snapshots taken AFTER money was actually contributed. This is the defect's
      -- precondition — the snapshot ignored a contribution that had already been recorded — and
      -- restricting to it keeps the repair off rows that were already correct. Those earlier rows
      -- differ from this formula by a dollar or so because the broker's cash also carried commission
      -- and slippage, which a cost-basis calculation does not model. Repairing them would replace a
      -- right number with a nearly-right one.
      AND EXISTS (
            SELECT 1 FROM simulation_contributions c
            WHERE c.simulation_id = ps.simulation_id
              AND c.source_plan_id IS NOT NULL
              AND c.created_at <= ps.timestamp
          );

    UPDATE portfolio_snapshots ps
    SET cash_available  = round(r.corrected_cash, 2),
        total_value_usd = round(r.corrected_cash + r.market_value, 2)
    FROM snapshot_cash_repair r
    WHERE ps.id = r.id
      AND abs(r.corrected_cash - r.stored_cash) > 0.01
      AND r.corrected_cash + r.market_value > 0;
    GET DIAGNOSTICS repaired = ROW_COUNT;

    SELECT count(*) INTO unrepaired
    FROM snapshot_cash_repair r
    WHERE abs(r.corrected_cash - r.stored_cash) > 0.01
      AND r.corrected_cash + r.market_value <= 0;

    RAISE NOTICE 'V129: repaired cash on % plan-funded portfolio snapshot(s)', repaired;
    IF unrepaired > 0 THEN
        RAISE WARNING 'V129: left % snapshot(s) untouched because the corrected total would not be '
                      'positive, which PortfolioSnapshot rejects — inspect these by hand', unrepaired;
    END IF;

    DROP TABLE snapshot_cash_repair;
END $$;
