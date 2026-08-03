-- E2E seed: deterministic BTC market data for the core-loop Playwright spec.
-- Executed at startup by E2eMarketDataSeeder, gated on
-- quorumtrading.e2e.seed-market-data.enabled (only %e2e flips it on).
-- NOT a Flyway migration: quarkus.flyway.locations is build-time fixed, so a
-- profile-specific extra location cannot be added at runtime.
--
-- Coverage requirements (BackfillOrchestratorService):
--   * every analyzed calendar day needs >= 1 PRICE bar per ticker AND >= 1 NEWS event,
--     otherwise the simulation transitions to WAITING_FOR_DATA and a real backfill fires
--     (which fails in e2e — no provider keys).
--   * the warmup window (sim start - 70d) is clamped to the earliest available bar, so
--     seeding from now()-16d is enough as long as it starts BEFORE the sim start date
--     (the spec uses a 12d-ago → 1d-ago window: > the 1w backtest interval, and the
--     end date is in the past in every timezone so the end-simulation scheduler can
--     complete the run immediately).
--   * bars extend to now()+1d because the launch wizard converts local clock-face time to
--     UTC by appending "Z", which can push dates up to ~14h either way.
--     MockBrokerAdapter fills orders from the latest bar in the 24h before an evaluation,
--     so 4-hourly bars keep the max gap at 4h.
--
-- Delete-before-insert keeps backend restarts idempotent. The WHERE source='e2e-seed'
-- scoping never touches non-seed data.

DELETE FROM price_bars  WHERE source = 'e2e-seed';
DELETE FROM news_events WHERE source = 'e2e-seed';

INSERT INTO price_bars (symbol, timestamp, source, open, high, low, close, volume, content_hash)
SELECT 'BTC', ts, 'e2e-seed',
       40000 + n * 10, 40100 + n * 10, 39900 + n * 10, 40050 + n * 10, 100,
       'e2e-seed-price-BTC-' || to_char(ts AT TIME ZONE 'UTC', 'YYYY-MM-DD-HH24')
FROM (
    SELECT ts, row_number() OVER (ORDER BY ts) AS n
    FROM generate_series(
        date_trunc('hour', now()) - interval '16 days',
        date_trunc('hour', now()) + interval '1 day',
        interval '4 hours'
    ) AS ts
) s;

-- One news row per 12h (not per day) so every UTC calendar day in the analyzed window has
-- coverage regardless of DB session timezone. symbol must be non-NULL: the availability
-- query filters "symbol IN (:tickers)".
INSERT INTO news_events (symbol, timestamp, source, headline, summary, url, content_hash)
SELECT 'BTC', ts, 'e2e-seed',
       'E2E seeded market note', 'Deterministic filler news for the core-loop e2e.',
       'https://example.invalid/e2e-news',
       'e2e-seed-news-BTC-' || to_char(ts AT TIME ZONE 'UTC', 'YYYY-MM-DD-HH24')
FROM generate_series(
    date_trunc('day', now()) - interval '16 days',
    date_trunc('day', now()) + interval '2 days',
    interval '12 hours'
) AS ts;
