# Broker Configuration Guide

## Architectural Split: BACKTEST vs LIVE Trading

QuorumTrading enforces a clear architectural separation between backtesting and live trading based on the simulation's **end date**.

### BACKTEST Mode (Historical Simulations)
- **Trigger:** `simulation.endDate <= now` → Status = `SIMULATING`
- **Broker:** Always uses `MockBrokerAdapter`
- **Portfolio State:** In-memory (per simulation)
- **Pricing:** Historical market data from `MarketEventRepository`
- **Features:** Realistic slippage, commissions, concurrent simulations supported
- **Use Case:** Historical analysis, strategy testing against past data

### LIVE Mode (Real-Time Trading)
- **Trigger:** `simulation.endDate > now` OR `simulation.endDate == null` → Status = `LIVE`
- **Broker:** Configured broker (`MockBrokerAdapter`, `AlpacaBrokerAdapter`, or `SaxoBrokerAdapter`)
- **Portfolio State:** Managed by broker (MockBroker in-memory; Alpaca/Saxo external account)
- **Pricing:** Real-time market prices
- **Constraint:** Each simulation reserves a budget from the broker account's available balance
- **Validation:** **LIVE simulations require `startDate >= today 00:00:00`** (cannot start in the past)
- **Use Case:** Real-time trading, ongoing strategies

## Available Brokers

- `MockBrokerAdapter` - In-memory mock for BACKTEST + LIVE
- `AlpacaBrokerAdapter` - Real Alpaca paper/live trading for LIVE only
- `SaxoBrokerAdapter` — ⚠️ **experimental, self-hosted only.** Saxo SIM/LIVE connectivity via OAuth. `quorumtrading.broker.saxo.enabled` (default `false`) is a **hard gate**: it hides Saxo from new onboarding *and* `BrokerRouter` refuses to route trades/queries for already-linked Saxo accounts, throwing `BrokerIntegrationDisabledException` with recovery instructions. It stays `false` on the hosted service — **trading European equities is not available there and enabling Saxo locally does not unlock it either**: exchange-qualified tickers (`ASML:XAMS`, `BMW.DE`) are refused unconditionally at strategy creation by `TickerValidationHelper`, independently of which broker is attached. Treat Saxo as an experimental integration you can connect and exercise on a self-hosted instance, not as EU market access. See [CAPABILITIES.md § 4](../CAPABILITIES.md).
- `BrokerRouter` - Routes per linked broker account, falling back to simulation mode + asset type

## BrokerRouter Configuration

```properties
# Legacy/fallback broker selection for LIVE mode without a linked broker account
# (BACKTEST always uses MockBroker). Shipped values; code-level default is "mock".
quorumtrading.broker.crypto.type=alpaca   # Crypto → alpaca or mock
quorumtrading.broker.stock.type=alpaca    # Stocks → alpaca or mock

# Alpaca configuration (required for AlpacaBrokerAdapter)
quorumtrading.broker.alpaca.enabled=true
quorumtrading.broker.alpaca.api-key=${ALPACA_API_KEY:not-configured}
quorumtrading.broker.alpaca.api-secret=${ALPACA_API_SECRET:not-configured}
quorumtrading.broker.alpaca.base-url=https://paper-api.alpaca.markets  # or live

# Saxo configuration (required for SaxoBrokerAdapter; OAuth app credentials)
quorumtrading.broker.saxo.enabled=${SAXO_ENABLED:false}
quorumtrading.broker.saxo.app-key=${SAXO_APP_KEY:not-configured}
quorumtrading.broker.saxo.app-secret=${SAXO_APP_SECRET:not-configured}
quorumtrading.broker.saxo.base-url-sim=${SAXO_BASE_URL_SIM:https://gateway.saxobank.com/sim/openapi}
quorumtrading.broker.saxo.base-url-live=${SAXO_BASE_URL_LIVE:https://gateway.saxobank.com/openapi}
```

Note: the global `crypto.type`/`stock.type` properties only apply to the legacy fallback path and accept `mock` or `alpaca`. Saxo is reachable exclusively through per-account routing (a simulation linked to a `SAXO_SIM`/`SAXO_LIVE` broker account).

## Investment Type Detection (DDD)

- Investment type (CRYPTO vs STOCK) is an explicit, mandatory component of the `Symbol` value object (`Symbol.type()`), supplied at construction — the constructor throws if it is null
- The ticker→type classification heuristic lives in `InvestmentType.fromTicker(String)` (model layer enum with a known-crypto ticker set), used by `Symbol.parse()` when only a raw ticker string is available
- Additional application-layer classification helpers exist in `ChatToolExecutor` and `GetDividendInfoService`
- No external configuration needed - this is pure domain logic, not infrastructure concern

## Routing Logic

`BrokerRouter` routes in two tiers:

1. **Per-account routing (preferred):** if the simulation has a linked `BrokerAccountId`, the account's `BrokerType` (`MOCK`, `ALPACA_PAPER`, `ALPACA_LIVE`, `SAXO_SIM`, `SAXO_LIVE`) picks the adapter — regardless of global configuration — and the adapter is configured with that account's credentials/settings
2. **Legacy fallback (no linked account):**
   - Status = `SIMULATING` → Always route to `MockBrokerAdapter` (BACKTEST)
   - Status = `LIVE` → Route by Symbol investment type + `quorumtrading.broker.{crypto,stock}.type` (`mock` or `alpaca` only)
3. Multiple concurrent LIVE simulations supported via per-account budget reservations

## CDI Architecture

- `BrokerRouter` = default `BrokerPort` (`@Priority(100)`)
- `MockBrokerAdapter` = `@Named("mockBroker")` `@ApplicationScoped`
- `AlpacaBrokerAdapter` = `@Named("alpacaBroker")` `@ApplicationScoped`
- `SaxoBrokerAdapter` = `@Named("saxoBroker")` `@ApplicationScoped`
- All three injected as `Instance<BrokerPort>` (module may not be available)
- Routing logic: `linked broker account` → else `simulation.status` + `symbol type` + `config` → broker selection

## Alpaca Market Hours & Order Routing

### Trading Hours
- **Stocks:** Regular market hours only (9:30 AM - 4:00 PM EST, Mon-Fri) — extended hours trading is deliberately disabled
- **Crypto:** Uses `time_in_force = "day"` (24/7 trading, Alpaca requirement for fractional orders)
- **Outside regular hours:** Stock orders rejected gracefully with clear logging

### Order Types
- **Stocks:** `time_in_force = "day"` with `extended_hours = false` (regular hours only, see Trading Hours above)
- **Crypto:** `time_in_force = "day"` with `extended_hours = false`
  - **Note:** Alpaca rejects fractional crypto with `"ioc"` or `"gtc"` - must use `"day"`

### Rejection Handling
- Clean `WARN` logs, no stack traces
- Returns `TradeStatus.REJECTED` with reason
- **Pending/unfilled orders:** Crypto `"day"` orders may get **accepted** but not **fill** if Alpaca has low liquidity. This is handled gracefully — the consensus decision is marked `REJECTED` with a "Trade pending (not filled)" note and persisted; no error is raised.

## Alpaca Limitations

1. **No backtesting support** - Alpaca cannot execute historical trades
2. **Market hours enforcement** - Stock orders rejected outside regular hours (see Trading Hours above)
3. **Portfolio state managed externally** - Alpaca account state lives in their servers
4. **Manual liquidation required** - `clearPortfolio()` cannot auto-liquidate positions

## Integration Tests

- `AlpacaBrokerIntegrationTest` - Read-only API tests (connectivity, balance, positions, portfolio sync)
- `AlpacaMarketDataIntegrationTest` - Read-only price fetch tests (stocks, crypto, market events)
- Tests skip gracefully if `ALPACA_API_KEY` not configured
- Test configs (one per module):
  - `quorumtrading-adapter-broker-alpaca/src/test/resources/application.properties` (broker tests)
  - `quorumtrading-adapter-marketdata-alpaca/src/test/resources/application.properties` (market data tests)
- Tests validate Alpaca API but do NOT execute trades (read-only)
