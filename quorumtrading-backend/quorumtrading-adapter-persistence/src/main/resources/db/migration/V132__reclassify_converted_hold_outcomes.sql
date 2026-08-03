-- Reclassify historical BUY/SELL→HOLD conversions now that execution_outcome can say WHY
-- nothing traded. Until now every conversion was recorded as NO_TRADE_REQUIRED — the same
-- label a deliberate HOLD gets — so a run whose BUYs were all refused by risk controls was
-- indistinguishable from one that chose to hold, and the new decisionsNotExecuted count
-- would report 0 for every run finished before the vocabulary existed.
--
-- The notes matched here are machine-written constants from ExecuteConsensusDecisionService
-- (convertToHold call sites), never user text, so the patterns are exact:
--   "… converted to HOLD: Trade value $X is below minimum $Y …"     → BELOW_MINIMUM_TRADE_VALUE
--   "BUY converted to HOLD: BUY rounds to 0 whole shares …"          → BELOW_MINIMUM_TRADE_VALUE
--   "BUY converted to HOLD: BUY blocked by strategy risk controls …" → BLOCKED_BY_RISK_LIMITS
-- SELL-with-no-holdings conversions stay NO_TRADE_REQUIRED on purpose: a SELL of nothing
-- genuinely requires no trade.

UPDATE consensus_decisions
SET execution_outcome = 'BELOW_MINIMUM_TRADE_VALUE'
WHERE execution_outcome = 'NO_TRADE_REQUIRED'
  AND (notes LIKE '%converted to HOLD: Trade value%is below minimum%'
       OR notes LIKE '%converted to HOLD: BUY rounds to 0 whole shares%');

UPDATE consensus_decisions
SET execution_outcome = 'BLOCKED_BY_RISK_LIMITS'
WHERE execution_outcome = 'NO_TRADE_REQUIRED'
  AND notes LIKE '%converted to HOLD: BUY blocked by strategy risk controls%';
