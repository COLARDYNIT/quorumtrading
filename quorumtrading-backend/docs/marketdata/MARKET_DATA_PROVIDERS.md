# Market Data Provider Capabilities

This document describes the capabilities and limitations of each market data provider adapter in QuorumTrading.

## Provider Comparison Matrix

| Provider | Live Data | Backfill | Asset Types | Granularity | Enabled By Default | Notes |
|----------|-----------|----------|-------------|-------------|--------------------|-------|
| **Alpaca** | ✅ Yes | ✅ Yes | Crypto + Stocks | 1min-1day | ✅ Yes | Primary price source (live + backfill) |
| **Tiingo** | ❌ No | ✅ Yes | Stocks only | Daily | ✅ Yes (`%prod`: off) | Deep-history daily fallback for backfill |
| **Finnhub** | ✅ Yes | ⚠️ News only | Stock quotes + news + financials | Quotes: live; news: daily | ✅ Yes | Not a price backfill source |
| **Reddit** | ✅ Yes | ✅ Yes | Social sentiment | N/A | ✅ Yes (`%prod`: off) | Historical posts via `/search.json` pagination |
| **RSS** | ✅ Yes | ❌ No | News articles | N/A | ✅ Yes | Free general-news feeds |
| **Saxo** | ✅ Yes | ✅ Yes | Stocks (incl. EU instruments) | Candles | ❌ No (`SAXO_ENABLED`) | EU broker market data; off on SaaS |

### Removed providers

The following adapters were removed in January 2026 and no longer exist in the codebase:

- **Coinbase** — crypto prices consolidated to Alpaca for a single crypto price source
- **NewsAPI** — replaced by free Finnhub news + RSS feeds
- **AlphaVantage** — Alpaca (with Tiingo fallback) handles stock backfill

## Provider Details

### Alpaca (alpaca)

**Primary use case:** Live and historical price data (crypto + stocks)

**Capabilities:**
- ✅ Live minute-bar data via REST API (Market Data API v1beta3 for crypto, v2 for stocks)
- ✅ Historical OHLCV backfill via bars endpoints
- ✅ News articles (Benzinga via Alpaca)
- ✅ Supports both crypto and stocks
- ✅ Intraday and daily granularity

**Feature flags (code):** `CRYPTO`, `STOCK`, `NEWS`, `BACKFILL`, `CANDLES`, `INTRADAY`, `DAILY`, `LIVE_TRADING`

**Configuration:**
```properties
quorumtrading.marketdata.alpaca.enabled=true
quorumtrading.marketdata.alpaca.api-key=${ALPACA_API_KEY:not-configured}
quorumtrading.marketdata.alpaca.api-secret=${ALPACA_API_SECRET:not-configured}
quorumtrading.marketdata.alpaca.base-url=${ALPACA_MARKETDATA_BASE_URL:https://data.alpaca.markets}
quorumtrading.marketdata.alpaca.stock-feed=${ALPACA_STOCK_FEED:iex}
quorumtrading.marketdata.alpaca.crypto-symbols=BTC,ETH,SOL,...
quorumtrading.marketdata.alpaca.stock-symbols=AAPL,MSFT,TSLA,...
```

**Limitations:**
- ⚠️ Depth of history depends on the API subscription; for very long ranges the backfill service may prefer Tiingo when it returns more complete data

**Code reference:**
- `AlpacaMarketDataAdapter.java` (`quorumtrading-adapter-marketdata-alpaca`)

---

### Tiingo (tiingo)

**Primary use case:** Deep-history daily stock backfill (fallback when Alpaca's history is incomplete)

**Capabilities:**
- ✅ Historical daily OHLC data for stocks, many years back
- ✅ Selected automatically by the backfill service when it returns more bars than Alpaca

**Feature flags (code):** `STOCK`, `DAILY`, `BACKFILL`, `CANDLES`

**Limitations:**
- ❌ No crypto support
- ❌ Daily granularity only
- ⚠️ Disabled in the `prod` profile by default

**Configuration:**
```properties
quorumtrading.marketdata.tiingo.enabled=${TIINGO_ENABLED:true}
%prod.quorumtrading.marketdata.tiingo.enabled=false
quorumtrading.marketdata.tiingo.api-token=${TIINGO_API_TOKEN:not-configured}
```

**Code reference:**
- `TiingoMarketDataAdapter.java` (`quorumtrading-adapter-marketdata-tiingo`)

---

### Finnhub (finnhub)

**Primary use case:** Live stock quotes, company news, and financial metrics

**Capabilities:**
- ✅ Real-time stock quotes (`FinnhubStockAdapter`)
- ✅ Company news with historical date-range backfill (`FinnhubNewsAdapter`)
- ✅ Financial metrics (`FinnhubFinancialDataAdapter`)

**Limitations:**
- ❌ Not a price backfill source (quotes only on free tier)
- ❌ No crypto support in this deployment
- ⚠️ News backfill is bounded by `quorumtrading.news.earliest-date.finnhub` (default `2020-01-01`)

**Configuration:**
```properties
quorumtrading.providers.finnhub.enabled=${FINNHUB_ENABLED:true}
quorumtrading.providers.finnhub.news.enabled=${FINNHUB_NEWS_ENABLED:true}
quorumtrading.providers.finnhub.api-key=...
quorumtrading.providers.finnhub.symbols=AAPL,MSFT,...
```

See the [module README](../../quorumtrading-adapter-marketdata-finnhub/README.md) for setup details.

---

### Reddit (reddit)

**Primary use case:** Social sentiment from finance subreddits

**Capabilities:**
- ✅ Live posts and historical posts via `/search.json` pagination (~2-3 weeks back)
- ✅ Upvote-threshold filtering

**Configuration:**
```properties
quorumtrading.providers.reddit.enabled=${REDDIT_ENABLED:true}
%prod.quorumtrading.providers.reddit.enabled=false
quorumtrading.providers.reddit.min-upvotes=${REDDIT_MIN_UPVOTES:500}
reddit.client.id=${REDDIT_CLIENT_ID:}
reddit.client.secret=${REDDIT_CLIENT_SECRET:}
```

**Code reference:**
- `RedditAdapter.java` (`quorumtrading-adapter-marketdata-reddit`)

---

### RSS (rss)

**Primary use case:** Free general market news headlines

**Capabilities:**
- ✅ Aggregates configured RSS feeds (business/markets news)

**Configuration:**
```properties
quorumtrading.providers.rss.enabled=${RSS_FEEDS_ENABLED:true}
quorumtrading.providers.rss.feed-urls=<comma-separated feed URLs>
```

**Code reference:**
- `RSSFeedAdapter.java` (`quorumtrading-adapter-marketdata-rss`)

---

### Saxo (saxo)

**Primary use case:** EU-instrument market data for the self-host / EU edition

**Capabilities:**
- ✅ Stock quotes and candles, including European instruments
- ✅ Backfill via the Saxo Charts API

**Limitations:**
- ❌ Disabled by default; gated behind `SAXO_ENABLED` (on in the `local` profile, off on SaaS)
- ⚠️ Saxo SIM environment has no equity/ETF market data (LIVE-only for those)

**Configuration:**
```properties
quorumtrading.marketdata.saxo.enabled=${SAXO_ENABLED:false}
%local.quorumtrading.marketdata.saxo.enabled=${SAXO_ENABLED:true}
```

**Code reference:**
- `SaxoMarketDataAdapter.java` (`quorumtrading-adapter-marketdata-saxo`)

---

## Backfill

### How the backfill service selects a provider

`BackFillTickerPriceDataService` uses **feature-based discovery** rather than hardcoded provider names. It asks for all adapters supporting `BACKFILL` + `CANDLES` + the asset feature (`CRYPTO` or `STOCK`), tries each one, and keeps the result with the **most events**:

```java
// quorumtrading-application/src/main/java/com/quorumtrading/application/service/BackFillTickerPriceDataService.java

MarketDataFeature assetFeature = command.isCrypto()
    ? MarketDataFeature.CRYPTO
    : MarketDataFeature.STOCK;

List<MarketDataPort> adapters = marketDataPortDiscovery.findAllByFeatures(
    MarketDataFeature.BACKFILL,
    MarketDataFeature.CANDLES,
    assetFeature
);
// Try all adapters and keep the one that returns the most events.
```

This handles partial data: for a very long stock range, Alpaca may return only recent bars while Tiingo returns the full history — the more complete dataset wins.

**Effective routing:**
- `CRYPTO` → Alpaca
- `STOCK` → Alpaca and Tiingo (most complete result wins); Saxo participates when enabled

### Backfill usage

```bash
curl -X POST http://localhost:8085/api/v1/market-data/backfill \
  -H "Content-Type: application/json" \
  -d '{
    "ticker": "BTC",
    "assetType": "CRYPTO",
    "startDate": "2023-11-18T00:00:00Z",
    "endDate": "2025-11-18T23:59:59Z",
    "granularity": "1Day"
  }'
```

For stocks, use `"assetType": "STOCK"`. Use `1Day` granularity for long ranges; intraday granularities are only available from Alpaca.

---

## Database Schema

Backfilled data is stored in the `market_events` table:

```sql
-- Key fields
symbol VARCHAR(20)           -- Ticker symbol (e.g., "BTC", "AAPL")
event_type VARCHAR(50)       -- Always "PRICE_UPDATE" for backfill
source VARCHAR(50)           -- Provider name (e.g., "alpaca", "tiingo")
timestamp TIMESTAMPTZ        -- Price timestamp
raw_data JSONB              -- OHLC data: {open, high, low, close, volume, price}
content_hash VARCHAR(64)     -- SHA-256 hash (prevents duplicates)

-- Unique constraint prevents duplicate backfills
CONSTRAINT market_events_content_hash_key UNIQUE (content_hash)
```

**Duplicate Handling:**
- Content hash is computed from: `ticker + timestamp + source + event_type`
- Duplicate inserts are silently skipped (caught by unique constraint)
- The backfill API reports: `recordsInserted`, `recordsSkipped`, `recordsFailed`

---

## Best Practices

1. **Always specify granularity** for consistent data intervals
2. **Verify data** in database after backfill:
   ```sql
   SELECT
     symbol,
     source,
     COUNT(*) as total_records,
     MIN(timestamp) as earliest,
     MAX(timestamp) as latest
   FROM market_events
   WHERE event_type = 'PRICE_UPDATE'
   GROUP BY symbol, source
   ORDER BY symbol, source;
   ```
3. **Measure data floors, don't assume them** — how far back a provider's data goes varies per ticker and subscription; check `MIN(timestamp)` after a backfill instead of assuming a fixed earliest date

---

## Troubleshooting

### "No market data adapter found supporting features: BACKFILL, CANDLES, ..."
**Solution:** Ensure the relevant adapter is enabled in `application.properties`:
```properties
quorumtrading.marketdata.alpaca.enabled=true
quorumtrading.marketdata.tiingo.enabled=true
```
and that credentials are configured (`ALPACA_API_KEY`/`ALPACA_API_SECRET`, `TIINGO_API_TOKEN`).

### Duplicate records warning
**Expected behavior:** The `content_hash` constraint automatically prevents duplicates. The API will report `recordsSkipped` for any duplicates encountered.

---

## See Also

- [Quick Reference Guide](../../../docs/development/QUICK_REFERENCE.md) - All environment variables and configuration
- [Common Development Tasks](../../../docs/development/COMMON_TASKS.md) - Step-by-step backfill guides
- [Database Schema](../../../docs/database/DATABASE_SCHEMA.md) - `market_events` table structure
