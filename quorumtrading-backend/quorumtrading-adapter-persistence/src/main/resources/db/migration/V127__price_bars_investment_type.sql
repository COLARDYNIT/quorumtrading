-- price_bars has no notion of what kind of instrument a symbol is. A ticker that exists as both
-- an equity and a crypto shares one series, and the PRICE dedup key is only (symbol, timestamp),
-- so bars from two different instruments interleave. Reads are worse than writes: the mock
-- broker asks for "the most recent bar for this ticker" with no asset-type filter and takes the
-- first row, so an intruding equity bar can become the fill price AND the valuation price for a
-- crypto position.
--
-- Nullable on purpose. Existing rows are backfilled with the same heuristic the domain uses, but
-- a NULL means "unknown, could be anything" and readers must keep accepting those rows or every
-- historical price disappears from every backtest.
ALTER TABLE price_bars
    ADD COLUMN investment_type VARCHAR(10);

-- Backfill using the canonical crypto ticker list (InvestmentType.CRYPTO_TICKERS), including the
-- separator-less pair forms the platform also stores (BTCUSD, SOLUSD).
UPDATE price_bars
SET investment_type = 'CRYPTO'
WHERE investment_type IS NULL
  AND (
    upper(symbol) IN (
      'BTC','ETH','SOL','DOGE','LTC','XRP','ADA','DOT','LINK','AVAX',
      'MATIC','SHIB','UNI','ATOM','XLM','ALGO','FIL','NEAR','APE','SAND',
      'MANA','AAVE','CRV','MKR','SNX','COMP','YFI','SUSHI','ENJ','BAT','BNB'
    )
    OR upper(symbol) ~ '^(BTC|ETH|SOL|DOGE|LTC|XRP|ADA|DOT|LINK|AVAX|MATIC|SHIB|UNI|ATOM|XLM|ALGO|FIL|NEAR|APE|SAND|MANA|AAVE|CRV|MKR|SNX|COMP|YFI|SUSHI|ENJ|BAT|BNB)(-|/)?(USD|USDT|USDC)$'
  );

-- Everything else that is not a known crypto is an equity.
UPDATE price_bars
SET investment_type = 'STOCK'
WHERE investment_type IS NULL;

-- Reads filter by (symbol, investment_type); keep the timestamp ordering usable.
CREATE INDEX idx_price_bars_symbol_type_timestamp
    ON price_bars (symbol, investment_type, "timestamp");
