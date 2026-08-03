-- V118__investment_allocations_fractional.sql
-- Saxo / EU-instrument support (keystone K7) — fractional-execution audit columns.
--
-- Saxo equities/ETFs trade in whole shares only (D3): a contribution may not fully deploy
-- because the residue below one whole share is carried to the next tick. The allocation
-- audit row needs to record how much of the intended amount was actually put to work, plus
-- whether the broker forced whole-share rounding for that execution.
--
-- Both columns are NULLABLE and additive:
--   deployed_amount       - cash actually deployed this tick (NULL for legacy/fractional rows
--                           where intended == deployed; NUMERIC(20,8) matches the table's
--                           existing intended_amount / actual_amount precision).
--   fractional_constraint - TRUE when the executing broker rounded down to whole shares
--                           (Saxo); NULL/FALSE for fractional brokers (Alpaca, Mock).
--
-- The Wave-2/3 layer (FX4-allocation-schema) maps these onto InvestmentAllocation and the
-- new AllocationStatus.EXECUTED_PARTIAL_DEPLOY; here they only need to EXIST and be nullable.

ALTER TABLE investment_allocations
    ADD COLUMN deployed_amount       NUMERIC(20, 8),
    ADD COLUMN fractional_constraint BOOLEAN;

COMMENT ON COLUMN investment_allocations.deployed_amount IS 'Cash actually deployed this tick after whole-share rounding; NULL when fully deployed (no residue)';
COMMENT ON COLUMN investment_allocations.fractional_constraint IS 'TRUE when the broker enforced whole-share rounding (e.g. Saxo equities/ETFs); NULL/FALSE for fractional brokers';
