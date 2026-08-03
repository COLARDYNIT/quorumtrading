# Finnhub Stock Market Data Adapter

Stock market data adapter for QuorumTrading using the Finnhub API.

## Features

- Real-time stock quotes for NASDAQ, NYSE, and other exchanges (`FinnhubStockAdapter`)
- Company news (`FinnhubNewsAdapter`) and financial metrics (`FinnhubFinancialDataAdapter`)
- Free tier: 60 API calls/minute
- Comprehensive quote data: current price, change, volume, high/low, open/close
- Automatic rate limiting (500ms delay between calls)
- Configurable stock symbols to track

## Setup

### 1. Get Free API Key

1. Visit [Finnhub Dashboard](https://finnhub.io/dashboard)
2. Sign up for a free account
3. Copy your API key

### 2. Configure Environment

Add your API key to environment variables (MicroProfile config maps this to `quorumtrading.providers.finnhub.api-key`):

```bash
export QUORUMTRADING_PROVIDERS_FINNHUB_API_KEY="your_api_key_here"
```

Or add to `quorumtrading-backend/quorumtrading-bootstrap/src/main/resources/application.properties`:

```properties
quorumtrading.providers.finnhub.api-key=your_api_key_here
```

### 3. Configure Stock Symbols (Optional)

Default symbols tracked (see `quorumtrading.providers.finnhub.symbols` in `application.properties`): `AAPL, MSFT, GOOGL, AMZN, TSLA, NVDA, AMD, META, PLTR, COIN, SPY, QQQ, GME, AMC, BBIG, DIA, GLD, IWM, TLT`. Tickers referenced by active strategies take precedence over this default list.

To customize, add to `application.properties`:

```properties
# Comma-separated list of stock ticker symbols
quorumtrading.providers.finnhub.symbols=AAPL,MSFT,NVDA,META,TSLA

# Enable/disable the adapter (env: FINNHUB_ENABLED, default true)
quorumtrading.providers.finnhub.enabled=true

# Enable/disable company news collection (env: FINNHUB_NEWS_ENABLED, default true)
quorumtrading.providers.finnhub.news.enabled=true
```

## Usage

### Automatic Collection (Scheduled)

The Finnhub adapter automatically fetches stock prices every 5 minutes via the `MarketDataCollectionJob` scheduler.

### Manual Collection (API)

Trigger manual collection via REST API:

```bash
curl -X POST http://localhost:8085/api/v1/market-data/collect
```

### Query Historical Data

Fetch stock prices from database (`from` and `to` are required):

```bash
# Get prices for specific ticker in a time range
curl "http://localhost:8085/api/v1/market-data/events?from=2025-11-01T00:00:00Z&to=2025-11-02T23:59:59Z&symbol=AAPL&eventType=PRICE&limit=20"
```

## API Response Format

Each stock price event contains:

```json
{
  "timestamp": "2025-11-02T12:34:56Z",
  "eventType": "PRICE_UPDATE",
  "source": "finnhub",
  "symbol": "AAPL",
  "rawData": {
    "price": 261.74,
    "currency": "USD",
    "change": 0.32,
    "changePercent": 0.12,
    "high": 263.31,
    "low": 260.68,
    "open": 261.07,
    "previousClose": 261.42
  }
}
```

## Rate Limiting

- **Free tier limit**: 60 API calls/minute
- **Implemented strategy**: 500ms delay between calls (safe for free tier)
- **Note**: With the 5-minute collection interval, one call is made per tracked symbol per collection

## Supported Exchanges

Finnhub supports:
- US exchanges: NASDAQ, NYSE, AMEX
- International: LSE, TSX, ASX, and 60+ others
- Crypto: BTC, ETH, and more (QuorumTrading uses the Alpaca adapter for crypto)

## Troubleshooting

### No data collected

1. Check API key is configured: `echo $QUORUMTRADING_PROVIDERS_FINNHUB_API_KEY` or check application.properties
2. Check adapter is enabled: `grep "finnhub.enabled" application.properties`
3. Check logs: `tail -f /tmp/quorumtrading.log | grep -i finnhub`

### Rate limit errors

- Reduce number of symbols in `quorumtrading.providers.finnhub.symbols`
- Increase collection interval (default is 5 minutes)

### Invalid symbol errors

- Verify ticker symbol exists on supported exchanges at [Finnhub Stock Symbols](https://finnhub.io/docs/api/stock-symbols)
- Use correct format (e.g., `AAPL` not `AAPL.US`)

## Architecture

```
FinnhubStockAdapter (implements MarketDataPort)
    ↓
FinnhubRestClient (REST client interface)
    ↓
Finnhub API (https://finnhub.io/api/v1/quote)
    ↓
MarketEvent entity
    ↓
PostgreSQL market_events table
```

## Development

### Running Tests

```bash
cd quorumtrading-backend
./mvnw test -pl quorumtrading-adapter-marketdata-finnhub -am
```

### Testing with Real API

```bash
# Set API key
export FINNHUB_API_KEY="your_key"

# Run Quarkus in dev mode
cd quorumtrading-backend
./mvnw quarkus:dev

# Watch logs for Finnhub data collection
tail -f /tmp/quorumtrading.log | grep -i finnhub
```

## References

- [Finnhub API Documentation](https://finnhub.io/docs/api)
- [Stock Quote Endpoint](https://finnhub.io/docs/api/quote)
- [API Rate Limits](https://finnhub.io/pricing)
