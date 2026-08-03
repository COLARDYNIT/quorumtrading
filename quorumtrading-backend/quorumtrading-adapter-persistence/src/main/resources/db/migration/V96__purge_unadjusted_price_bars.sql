-- Purge all stock price bars so they can be re-backfilled with split-adjusted prices.
--
-- Context: The Alpaca market data adapter was fetching raw/unadjusted prices (default).
-- This caused incorrect portfolio valuations when stock splits occurred mid-backtest
-- (e.g., XLE 2:1 split on Dec 5, 2025 appeared as a 50% overnight crash).
--
-- Fix: The adapter now passes adjustment=split to the Alpaca bars API.
-- All price data must be re-fetched to ensure consistency (no mix of adjusted/unadjusted).
-- Simulations will re-backfill price data automatically when they run.
TRUNCATE TABLE price_bars;
