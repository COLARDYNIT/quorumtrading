# ADR-0002: A shipped zero-config price source for the standalone edition

**Status:** Accepted — implemented (see Implementation section)
**Date:** 2026-07-13
**Context:** Standalone/self-host onboarding — first backfill/backtest requires a market-data key today

## Problem

The standalone edition boots with zero config, but the first useful action — `backfill_price_data`,
then any backtest — fails until the user obtains and configures a market-data key. There is **no
zero-auth price source today**:

- Only three providers advertise `BACKFILL` + `CANDLES`: Alpaca, Tiingo, Saxo — all credential-gated
  (`BackFillTickerPriceDataService` requires `BACKFILL+CANDLES+STOCK/CRYPTO`;
  `AlpacaMarketDataAdapter`/`TiingoMarketDataAdapter`/`SaxoMarketDataAdapter` all send credentials and
  return 401/`NoAccess` without them).
- The only auth-free providers (Reddit, RSS) supply NEWS/SOCIAL only — no `CANDLES` — so they can
  never satisfy a price backfill.

Result: a brand-new self-hoster hits an HTTP 401 (`Authentication failed for alpaca`) on their very
first agent command. We've now documented the Alpaca/Tiingo free-key path (README, LOCAL_SETUP §"Get
price data", `.env.local.example`), but the multi-step signup is friction we'd like to remove for a
true out-of-the-box first run.

## Decision (proposed)

Ship a **client-side market-data adapter** that fetches daily OHLCV bars from a **hosted, read-only,
heavily rate-limited price proxy** operated at quorumtrading.com. The adapter ships with an embedded,
non-secret **standalone identification key**, is **on by default only in the standalone edition**, and
is clearly labelled a getting-started convenience — not a long-term or production data source.

Because the backfill selector is "widest result wins" (`BackFillTickerPriceDataService` runs all
matching adapters and keeps the one returning the most events — no static priority), this new adapter
simply becomes another candidate: it makes the zero-key case work, and the moment a user configures
their own Alpaca/Tiingo key, the richer source naturally wins for the ranges it covers. No priority
wiring is needed.

> ⚠️ This decision is **blocked on the data-redistribution licensing question** below. The technical
> design is straightforward; the legal source is the real gate. Do not build until that is resolved.

## Client adapter shape

New module `quorumtrading-adapter-marketdata-qthosted` implementing
`com.quorumtrading.application.port.out.MarketDataPort`:

- `getProviderName()` → `"qt-hosted"`.
- `getSupportedFeatures()` → `{ STOCK, BACKFILL, CANDLES, DAILY }`. **Daily/EOD only** to start —
  this is deliberate (see licensing). Crypto and intraday are out of scope for v1.
- `fetchDataInRange(Set<String> cryptoTickers, Set<String> stockTickers, Instant start, Instant end,
  Granularity granularity)` → GET the proxy, map each bar to
  `MarketEvent(timestamp, EventType.PRICE, "qt-hosted", ticker, {open,high,low,close,volume,price})`
  (same rawData shape as the Alpaca/Tiingo adapters).
- `getRateLimit()` → advertise the client-side budget so callers self-throttle.
- Annotated `@ApplicationScoped` → auto-discovered by CDI `Instance<MarketDataPort>`
  (`CdiMarketDataPortDiscovery`); no manual registration.
- On any 401/403/429/5xx or network error: log one WARN and return an **empty list** (never throw),
  so the selector transparently falls back to the user's own providers or reports "no data" — a
  degraded hosted source must never break a backfill.

Config (mirrors the other marketdata adapters):

```properties
quorumtrading.marketdata.qthosted.enabled=${QT_HOSTED_PRICES_ENABLED:false}
%local.quorumtrading.marketdata.qthosted.enabled=true      # standalone default ON
quorumtrading.marketdata.qthosted.base-url=${QT_HOSTED_PRICES_URL:https://data.quorumtrading.com}
quorumtrading.marketdata.qthosted.identification-key=${QT_HOSTED_PRICES_KEY:<shipped-default>}
```

## Standalone identification key + rate limiting

The shipped key is an **identifier, not a secret** — its only jobs are to bucket anonymous standalone
traffic and give the operator a lever to throttle or block abuse.

- Client sends it as `X-QT-Standalone-Key` on every request; users may override with their own value.
- Server side (reuse the existing Redis rate-limiting infrastructure): token-bucket per
  `(key, client-IP)` with a conservative per-minute rate and a hard daily cap; `429` + `Retry-After`
  when exceeded. Abusive keys/IPs are blocklisted in Redis.
- The default key can be **rotated or deprecated per release**; the proxy can withdraw a key entirely.
  Docs and the boot banner must state plainly: *getting-started only, rate-limited, may be withdrawn —
  bring your own Alpaca/Tiingo key for anything real.*
- No PII: the proxy needs only the key, IP (for throttling), and the requested ticker/range.

## The crux: upstream data-redistribution licensing

Re-serving another provider's market data to third parties is a **redistribution**, which the
free/personal tiers of Alpaca, Tiingo, and most vendors typically **prohibit**. A public quorumtrading
proxy that relays Alpaca/Tiingo bars to anonymous self-hosters would very likely breach those terms —
regardless of how the client is authenticated.

> Note: the specific clauses below must be re-verified against each provider's **current** terms
> before any build — this analysis reflects general market-data licensing norms as of the knowledge
> cutoff, not a live legal reading.

Options for a redistribution-safe source, roughly in order of preference:

1. **Operator-licensed redistribution feed** — subscribe to a market-data plan whose terms explicitly
   permit redistribution to end users (many vendors sell a "redistribution"/"non-display" tier).
   Cleanest legally; has a recurring cost. Recommended if the proxy is to be a real feature.
2. **A genuinely redistribution-permissive free source** — e.g. an EOD source whose terms allow
   non-commercial redistribution. Candidates exist (Stooq-style EOD CSV, some exchange-published EOD
   files), but each must be license-checked; "no API key" ≠ "redistribution allowed."
3. **EOD/delayed only** — constraining the proxy to end-of-day daily bars (no real-time/intraday)
   materially lowers entitlement/redistribution exposure with most sources and is sufficient for the
   getting-started backtest use case. This is why the adapter is **daily-only** in v1.

**Recommendation:** pick a source under (1) or (2), constrain to (3) daily/EOD, and confirm current
terms in writing before writing code. If no acceptable source is found, do **not** ship the proxy —
keep the documented free-key on-ramp instead.

## Where it lives

- **Client adapter:** this repo (`quorumtrading-adapter-marketdata-qthosted`), off by default outside
  `%local`, so the open-source build carries no hidden network dependency.
- **Hosted proxy:** the operator's service (backend `public`/anonymous endpoint + the private
  `quorumtrading-infra` deploy). It is a **product/ops commitment**: uptime expectations, egress cost,
  abuse surface, and the licensing above. The open-source project must remain fully usable **without**
  it (bring-your-own-key), so forks aren't tied to a quorumtrading-operated endpoint.

## Alternatives considered

- **Bundle a static seed dataset** (like the e2e `E2eMarketDataSeeder`, which seeds 16 days of BTC
  bars): zero network, zero licensing exposure for a fixed sample, but stale and limited to a handful
  of tickers/dates — fine for a demo, not for real backtests. Could ship *alongside* the proxy as an
  offline fallback.
- **Just document the free-key path** (what we did now): zero cost/risk, but keeps the multi-step
  signup friction. This ADR exists because we want to remove that friction *if* it can be done safely.
- **Proxy Alpaca/Tiingo directly with the operator's keys**: simplest to build, but the redistribution
  breach makes it a non-starter.

## Consequences

- **If shipped safely:** standalone backfill/backtest works with zero user keys; the biggest onboarding
  cliff disappears. The adapter degrades to empty on failure, so it never breaks a run.
- **Operational:** quorumtrading.com runs and pays for a public, abuse-exposed service; the default key
  must be rotatable and the proxy withdrawable.
- **Positioning:** it must be unmistakably "getting-started only, daily-only, rate-limited" so nobody
  builds a real strategy on it or treats it as an SLA'd feed.
- **Open questions to close before build:** (a) which licensed/permissive source; (b) EOD-only vs
  delayed intraday; (c) crypto in scope?; (d) cost ceiling + who owns the ops; (e) exact rate limits
  and default-key rotation policy.

## Research findings (2026-07-13) — source ToS survey

A multi-source, adversarially-verified survey (24 confirmed claims against primary ToS documents;
re-verify before any launch, terms change often) settled the source question:

**Model A (central proxy / redistribution) is dead for every free source.** Yahoo, Stooq, Alpha
Vantage, Tiingo, and Polygon (now rebranded **Massive**) *all* explicitly forbid re-serving data to
third parties without a separate paid agreement. Evidence: Stooq ToS §5.3 "Redistribution of data …
is not allowed without the consent of Stooq"; Alpha Vantage classifies any use that "allows
individuals or entities other than User to access information" as commercial (needs `premium@`);
Tiingo "all data via the API is for internal consumption only … redistribution … comes with
additional fees"; Yahoo Developer ToS §1.7.4 bars "sell, lease, share, transfer, or sublicense";
Massive individuals ToS bars transferring access. **⇒ A quorumtrading.com price proxy would require a
purchased redistribution/commercial license (Massive business, Tiingo redistribution, or Alpha
Vantage premium) — there is no free path.** This confirms the ADR's lean against a hosted proxy.

**Model B (each install queries directly) is the better strategy** — it sidesteps redistribution
entirely. Winners:

- **Crypto — genuine zero-config:** Binance's market-data-only endpoints
  (`data-api.binance.vision` REST, `data-stream.binance.vision` WS) require **no API key, no signup**,
  are officially documented, and serve `/api/v3/klines` OHLCV bars. IP rate limits apply and
  Binance.com is geo-blocked in some regions (e.g. US). *This is the one true keyless, ToS-clean,
  ship-by-default source we found* — but crypto only.
  (`https://developers.binance.com/docs/binance-spot-api-docs/faqs/market_data_only`)
- **Stocks — free key, ToS-clean for personal use:** Alpha Vantage or Tiingo, each under the
  self-hoster's own free key. ⚠️ Alpha Vantage's commercial-use definition explicitly names
  "investment analysis, research, testing" — a trading/backtest tool is a genuine gray zone; Tiingo's
  "personal or internal business" grant is cleaner. Both need a free key (not keyless).
- **Stocks — keyless stopgap, accept the risk:** Yahoo/yfinance and Stooq's per-symbol CSV need no
  key but ride unofficial/undocumented endpoints, are personal-/non-commercial-only, and are fragile
  (Yahoo actively 429s/IP-bans scrapers; Stooq bulk archive is CAPTCHA-gated). The comparable OSS
  backtester TuringTrader ships Yahoo as its zero-config default — but that greyness is riskier for a
  project with a commercial hosted edition than for a pure hobby tool.

**Google: no.** The standalone Google Finance API was deprecated in 2012 and never restored;
`GOOGLEFINANCE()` is a Google Sheets **cell function**, not a queryable API — unusable as an automated
feed. (`https://support.google.com/docs/answer/3093281`)

**Databento:** paid — signup + API key required, "free" is a one-time $125 credit, not a tier.

**Recommendation update.** Drop the hosted proxy as a *free* option (it isn't one). Instead pursue
**Model B (direct local query)**: add a **keyless Binance market-data adapter** so crypto backfill
works out-of-the-box with zero setup (the closest thing to the "ship a no-auth source by default"
goal), and keep **bring-your-own-free-key** (Alpaca/Tiingo, already supported) as the stock path,
documented as in LOCAL_SETUP §3. Only revisit a proxy if the operator buys a redistribution license.

**Coverage gap (not a negative verdict):** the survey produced no verified claims for Finnhub, Twelve
Data, FMP, Marketstack, Nasdaq Data Link/Quandl, EODHD, IEX (shutdown unconfirmed), Cboe, SEC EDGAR
(fundamentals, not prices), or non-Binance crypto (Coinbase/Kraken/CoinGecko/CoinCap) — a targeted
second pass is warranted before finalizing, especially for a keyless non-Binance crypto fallback for
geo-blocked users.

### Gap-pass findings (2026-07-13) — the uncovered sources

A focused second pass (23 confirmed claims, one live keyless test) closed most of the gap and
**revised the crypto conclusion**:

- **Binance is NOT the only keyless crypto source.** Two more expose keyless OHLCV *and are
  US-available*, so they fill the Binance geo-block gap:
  - **Kraken** — `GET https://api.kraken.com/0/public/OHLC` (`security: []`, live-tested keyless;
    returns time/O/H/L/C/vwap/volume/count), plus a keyless WS v2 OHLC channel. Cleanest option.
    ⚠️ REST history is capped at **~720 candles per interval** (older data unavailable) — enough to
    get started, limiting for deep backtests. (`https://docs.kraken.com/api/docs/rest-api/get-ohlc-data`)
  - **Coinbase Exchange** — `GET https://api.exchange.coinbase.com/products/{id}/candles` (OpenAPI
    `security: []`, keyless), granularities 1m/5m/15m/1h/6h/1d, ~3–10 req/s. Note: the *Advanced
    Trade* candles endpoint needs a signed key, but the Exchange path (and "Get Public Product
    Candles") are keyless. (`https://docs.cdp.coinbase.com/api-reference/exchange-api/rest-api/products/get-product-candles`)
  - These are keyless for **direct local query (Model B) only** — exchange ToS still restrict
    commercial redistribution, and IP rate limits (~3–10 req/s) would throttle a central proxy. So
    they do NOT change the Model A verdict.
- **CoinGecko / CoinPaprika:** keyless access exists but is a weak fit — CoinGecko's keyless tier is
  explicitly "not suitable for scheduled polling," and whether either exposes a *keyless OHLC*
  endpoint is unconfirmed (a CoinGecko keyless-OHLC claim was refuted). **CoinCap** is not viable
  (v3 is key-gated). Treat these as last-resort fallbacks only.
- **IEX Cloud is dead** — announced 2024-05-31, all products disabled **2024-08-31**; no keyless
  successor confirmed. Remove from consideration.
- **SEC EDGAR / data.sec.gov** — keyless (User-Agent + ~10 req/s fair access), public-domain,
  genuinely redistribution-friendly — but **fundamentals/XBRL filings, not price bars**. Useful for
  the Fundamental agent, useless as a price feed.
- **Twelve Data:** free key required, free tier bars commercial use, redistribution needs a paid
  add-on — a keyed stock option, not zero-config, not proxyable.
- **Still unconfirmed after two passes:** Finnhub, FMP, Marketstack, Nasdaq Data Link/Quandl, EODHD
  (all are free-key stock vendors at best — none would be keyless), and keyless OHLC on other
  exchanges (Binance.US, Bitstamp, Bitfinex, KuCoin, OKX) + CryptoCompare.

**Net recommendation (unchanged strategy, sharper crypto pick):** no free proxy source exists (Model
A stays dead); stocks remain bring-your-own-free-key (Model B). For the zero-config crypto win,
prefer a **US-available keyless exchange — Kraken and/or Coinbase Exchange — over Binance** (which is
US-geo-blocked), ideally a small multi-exchange adapter that tries one and falls back, slotting into
the existing "widest-result-wins" selector.

### Empirical keyless probe (2026-07-13, live curl, no auth)

| Question | Kraken `/0/public/OHLC` | Coinbase Exchange `/products/{id}/candles` |
|---|---|---|
| All OHLCV present? | ✅ `[time,open,high,low,close,vwap,volume,count]` — OHLCV **+ vwap + trade count**. Values are **strings**. | ✅ `[time,low,high,open,close,volume]` — OHLCV. ⚠️ field order is **low,high,open,close** (mapping gotcha). Values numeric. |
| Granularity | minutes: 1,5,15,30,60,240,**1440 (1d)**,10080 (1w),21600 (15d). Finest 1m, coarsest 15d; has native weekly. | seconds: 60,300,900,3600,21600,**86400 (1d)**. Finest 1m, coarsest **1 day** (no weekly). |
| Recency (near-now) | Newest bar = **today's in-progress candle** (real-time up to now); `last` cursor = last complete bar. | Newest bar = **today** (real-time up to now). |
| History depth (past) | **Hard cap ~720 candles per interval**, regardless of `since`. Daily → ~720 days (~2yr, oldest 2024-07-23); 1-min → ~12h; **15-day bars → back to 2013**. One call, no pagination. | Back to **~2016** for BTC-USD daily (2015 empty), BUT **≤300 candles/request** — a 4-yr daily range returns `HTTP 400 "exceeds 300"`. Deep history needs **pagination** (walk start/end backward). |
| Rate limit (per-IP?) | Per-IP. **25 rapid calls → 25×200, 0 throttled.** | Per-IP (~10 req/s documented). **25 rapid calls → 25×200, 0 throttled.** No rate-limit headers surfaced on success. |
| Symbol format | Kraken pairs: request `XBTUSD`, result key `XXBTZUSD` (X/Z prefixes) — needs ticker→pair mapping (BTC→XBT). | Simple `BTC-USD`. Requires a `User-Agent` header. |

**Design implication:** both are keyless → genuine zero-config, and both map cleanly onto
`MarketDataPort.fetchDataInRange(...)`. **Kraken is the simplest primary** (one call yields up to 720
daily bars incl. today — ample for a getting-started backtest; native weekly for deeper trends).
**Coinbase Exchange is the deeper-daily-history option** but needs a ≤300-candle pagination loop. A
small adapter with Kraken primary + Coinbase fallback covers both and fits "widest-result-wins".
Per-IP limits confirm standalone is a non-issue — and re-confirm a central proxy is not (many users
→ one egress IP → throttled).

## Implementation (2026-07-13)

Shipped as **two per-provider adapters**, not one consolidated module and not a delegating
orchestrator:

- `quorumtrading-adapter-marketdata-kraken` → `KrakenMarketDataAdapter` (`quorumtrading.marketdata.kraken.enabled`)
- `quorumtrading-adapter-marketdata-coinbase` → `CoinbaseMarketDataAdapter` (`quorumtrading.marketdata.coinbase.enabled`)

Each is a plain `MarketDataPort` (`@ApplicationScoped`, keyless, crypto-only capabilities), mapping
its exchange's public candles to `MarketEvent`s and degrading to empty on any failure. **There is no
orchestrator/"standalone" module**: the application layer's existing provider selection composes them
(widest-result-wins on the MCP path, fallback chain on the async path), so "Kraken primary, Coinbase
fallback" is an emergent property of the selector rather than hand-rolled coordination. "Standalone-
only" is expressed purely by the `enabled` flags (default on in dev + `%local`, off in prod where
Alpaca is primary, off in test/e2e so CI makes no live calls) — deployment scope is config, not module
identity.

Why not the alternatives: a single `marketdata-crypto` module was rejected because it names an adapter
after an asset class (Alpaca also serves crypto) and hides two external systems behind one bean with a
merged, partly-untrue capability set; a delegating `marketdata-standalone` module was rejected as
overdesign because it would re-implement the port-selection composition the hexagon already owns.

The merge/gap-fill logic from the first cut was dropped: Coinbase alone paginates deeper than Kraken
for daily bars, so "widest wins" yields equal-or-better data with less code. Verified: unit tests green
for both modules (Kraken string-value parsing; Coinbase low/high/open/close ordering; unsupported-
granularity and disabled guards), full reactor build + bootstrap augmentation clean with both crypto
`MarketDataPort` beans discovered.
