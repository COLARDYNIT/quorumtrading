-- V116__widen_consensus_decision_symbol.sql
-- Saxo / EU-instrument support (keystone K7).
--
-- The consensus_decisions.symbol column was added in V19 as VARCHAR(10) sized for
-- bare US tickers (BTC, AAPL). EU instruments are persisted by their exchange-qualified
-- canonical form (e.g. "RHM.XETR"), which no longer fits in 10 characters. Widen to 20.
--
-- This is the storage half of K1-symbol-core's enriched Symbol model. canonical() is
-- byte-identical to the bare ticker for US names, so existing rows are unaffected and the
-- =?1 / symbol+"|"+ts hashing invariants hold. Widening a VARCHAR is metadata-only on
-- PostgreSQL (no table rewrite, no data loss).

ALTER TABLE consensus_decisions
    ALTER COLUMN symbol TYPE VARCHAR(20);

COMMENT ON COLUMN consensus_decisions.symbol IS 'Trading symbol; bare ticker for US (e.g. AAPL) or exchange-qualified canonical for EU (e.g. RHM.XETR)';
