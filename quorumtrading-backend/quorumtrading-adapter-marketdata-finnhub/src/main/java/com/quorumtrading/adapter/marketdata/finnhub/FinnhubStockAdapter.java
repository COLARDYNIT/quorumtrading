package com.quorumtrading.adapter.marketdata.finnhub;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TickerRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Market data adapter for Finnhub stock market data.
 *
 * Fetches real-time stock quotes from Finnhub API.
 * Free tier: 60 API calls/minute
 *
 * Default symbols tracked: AAPL, MSFT, GOOGL, AMZN, TSLA
 * Configurable via quorumtrading.providers.finnhub.symbols property
 */
@ApplicationScoped
@jakarta.inject.Named("finnhub")
public class FinnhubStockAdapter implements MarketDataPort {

    private static final Logger LOG = Logger.getLogger(FinnhubStockAdapter.class);
    private static final String PROVIDER_NAME = "finnhub";

    // Default stock symbols to track
    private static final List<String> DEFAULT_SYMBOLS = List.of("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");

    // US stock market hours (Eastern Time)
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);  // 9:30 AM EST
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0); // 4:00 PM EST

    // Batch processing to avoid Vert.x thread blocking (60s limit)
    // With 500ms rate limiting, we can do ~50 tickers in 25 seconds (safe margin)
    private static final int MAX_TICKERS_PER_BATCH = 50;
    private static final long RATE_LIMIT_DELAY_MS = 500; // 500ms = 120 calls/min capacity (we use 60)

    // Track batch offset for rotating through large ticker sets
    private final AtomicInteger batchOffset = new AtomicInteger(0);

    @Inject
    @RestClient
    FinnhubRestClient restClient;

    @ConfigProperty(name = "quorumtrading.providers.finnhub.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "quorumtrading.providers.finnhub.symbols", defaultValue = "AAPL,MSFT,GOOGL,AMZN,TSLA")
    String symbolsConfig;

    @ConfigProperty(name = "quorumtrading.providers.finnhub.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public List<MarketEvent> fetchLatestDataForTickers(TickerRegistry tickerRegistry) {
        if (!enabled) {
            LOG.debug("Finnhub adapter is disabled");
            return Collections.emptyList();
        }

        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            LOG.warn("Finnhub API key not configured - skipping stock data fetch");
            return Collections.emptyList();
        }

        // Skip API calls when market is closed to avoid stale data and duplicate keys
        if (!isMarketOpen()) {
            LOG.info("Skipping Finnhub data fetch - US stock market is closed");
            return Collections.emptyList();
        }

        // Convert domain Symbols to string tickers for API calls (adapter layer uses strings)
        Set<String> stockTickers = tickerRegistry.getStockTickerStrings();

        // Fall back to configured defaults if no strategies specify stock tickers
        if (stockTickers.isEmpty()) {
            LOG.debug("No stock tickers in strategy registry, using adapter defaults");
            stockTickers = Set.copyOf(parseSymbols(symbolsConfig));
        }

        // Convert to list for indexed access (batch processing)
        List<String> tickerList = new ArrayList<>(stockTickers);
        int totalTickers = tickerList.size();

        // If small enough, fetch all at once
        if (totalTickers <= MAX_TICKERS_PER_BATCH) {
            LOG.infof("Fetching latest prices for %d stock tickers (single batch): %s",
                totalTickers, tickerList);
            return fetchTickerBatch(tickerList);
        }

        // Batch processing: fetch a subset and rotate through the full list
        int currentOffset = batchOffset.get();
        if (currentOffset >= totalTickers) {
            currentOffset = 0;
            batchOffset.set(0);
        }

        int endIndex = Math.min(currentOffset + MAX_TICKERS_PER_BATCH, totalTickers);
        List<String> batch = tickerList.subList(currentOffset, endIndex);

        LOG.infof("Fetching batch %d-%d of %d stock tickers (batch size: %d): %s",
            currentOffset + 1, endIndex, totalTickers, batch.size(), batch);

        List<MarketEvent> events = fetchTickerBatch(batch);

        // Update offset for next run
        int nextOffset = (endIndex >= totalTickers) ? 0 : endIndex;
        batchOffset.set(nextOffset);
        LOG.debugf("Next batch will start at offset %d", nextOffset);

        return events;
    }

    /**
     * Fetch price data for a batch of tickers with rate limiting.
     */
    private List<MarketEvent> fetchTickerBatch(List<String> tickers) {
        List<MarketEvent> events = new ArrayList<>();

        for (String ticker : tickers) {
            try {
                Symbol symbol = new Symbol(ticker, InvestmentType.STOCK);
                events.addAll(fetchSymbolPriceData(symbol, Instant.now(), Instant.now()));

                // Rate limiting: free tier allows 60 calls/minute
                // Using 500ms gives us 120 calls/min capacity, safely within limit
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Finnhub adapter interrupted during rate limiting sleep");
                break;
            } catch (Exception e) {
                LOG.warnf("Failed to fetch Finnhub data for symbol %s: %s", ticker, e.getMessage());
            }
        }

        LOG.infof("Finnhub adapter collected %d stock price events from batch of %d tickers",
            events.size(), tickers.size());
        return events;
    }

    @Override
    @Deprecated
    public List<MarketEvent> fetchLatestData() {
        if (!enabled) {
            LOG.debug("Finnhub adapter is disabled");
            return Collections.emptyList();
        }

        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            LOG.warn("Finnhub API key not configured - skipping stock data fetch");
            return Collections.emptyList();
        }

        // Skip API calls when market is closed to avoid stale data and duplicate keys
        if (!isMarketOpen()) {
            LOG.info("Skipping Finnhub data fetch - US stock market is closed");
            return Collections.emptyList();
        }

        List<String> symbols = parseSymbols(symbolsConfig);
        return fetchTickerBatch(symbols);
    }

    /**
     * Fetch stock price data for specified tickers within a time range.
     * Only processes stock tickers - ignores crypto tickers.
     *
     * @param cryptoTickers Crypto symbols - ignored by this adapter
     * @param stockTickers  Stock symbols from StrategyConfig (e.g., AAPL, MSFT)
     * @param start         Start time (currently not used - fetches latest)
     * @param end           End time (currently not used - fetches latest)
     * @return Market events for stock tickers
     */
    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end) {

        if (!enabled) {
            LOG.debug("Finnhub adapter is disabled");
            return Collections.emptyList();
        }

        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            LOG.warn("Finnhub API key not configured - skipping stock data fetch");
            return Collections.emptyList();
        }

        if (stockTickers == null || stockTickers.isEmpty()) {
            LOG.debug("No stock tickers specified, falling back to default latest data");
            return fetchLatestData();
        }

        // If start and end are the same or very close (< 1 minute apart), fetch latest quote
        if (start != null && end != null && java.time.Duration.between(start, end).toMinutes() < 1) {
            // Skip API calls when market is closed (only for current data, not historical)
            if (!isMarketOpen()) {
                LOG.info("Skipping Finnhub latest quotes fetch - US stock market is closed");
                return Collections.emptyList();
            }

            LOG.debugf("Fetching latest quotes for %d stock tickers", stockTickers.size());
            List<MarketEvent> events = new ArrayList<>();
            for (String ticker : stockTickers) {
                try {
                    Symbol symbol = new Symbol(ticker, InvestmentType.STOCK);
                    events.addAll(fetchSymbolPriceData(symbol, start, end));
                    Thread.sleep(RATE_LIMIT_DELAY_MS); // Rate limiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Finnhub adapter interrupted during rate limiting sleep");
                    break;
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to fetch Finnhub data for symbol: %s", ticker);
                }
            }
            return events;
        }

        // Otherwise fetch historical candles
        LOG.debugf("Fetching historical Finnhub candles for %d stock tickers from %s to %s",
            stockTickers.size(), start, end);

        List<MarketEvent> events = new ArrayList<>();
        for (String ticker : stockTickers) {
            try {
                Symbol symbol = new Symbol(ticker, InvestmentType.STOCK);
                events.addAll(fetchHistoricalCandles(symbol, start, end));

                // Rate limiting: free tier allows 60 calls/minute
                // Using 500ms gives us 120 calls/min capacity, safely within limit
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Finnhub adapter interrupted during rate limiting sleep");
                break;
            } catch (Exception e) {
                LOG.errorf(e, "Failed to fetch Finnhub candles for symbol: %s", ticker);
            }
        }

        LOG.infof("Finnhub adapter collected %d stock price events", events.size());
        return events;
    }

    public List<MarketEvent> fetchSymbolPriceData(Symbol symbol, Instant start, Instant end) {
        try {
            LOG.debugf("Fetching Finnhub quote for %s", symbol.ticker());

            Map<String, Object> response = restClient.getQuote(symbol.ticker(), apiKey);

            // Check if quote is valid (current price exists)
            Object currentPriceObj = response.get("c");
            if (currentPriceObj == null) {
                LOG.warnf("No price data returned for symbol: %s", symbol.ticker());
                return Collections.emptyList();
            }

            double currentPrice = convertToDouble(currentPriceObj);
            double change = convertToDouble(response.getOrDefault("d", 0.0));
            double changePercent = convertToDouble(response.getOrDefault("dp", 0.0));
            double high = convertToDouble(response.getOrDefault("h", currentPrice));
            double low = convertToDouble(response.getOrDefault("l", currentPrice));
            double open = convertToDouble(response.getOrDefault("o", currentPrice));
            double previousClose = convertToDouble(response.getOrDefault("pc", currentPrice));
            long timestamp = convertToLong(response.getOrDefault("t", System.currentTimeMillis() / 1000));

            // Create rawData map with comprehensive quote information
            Map<String, Object> rawData = new HashMap<>();
            rawData.put("price", currentPrice);
            rawData.put("currency", "USD");
            rawData.put("change", change);
            rawData.put("changePercent", changePercent);
            rawData.put("high", high);
            rawData.put("low", low);
            rawData.put("open", open);
            rawData.put("previousClose", previousClose);
            rawData.put("symbol", symbol.ticker());

            // Use API timestamp if available, otherwise use current time
            Instant eventTime = timestamp > 0 ? Instant.ofEpochSecond(timestamp) : Instant.now();

            MarketEvent event = new MarketEvent(
                eventTime,
                EventType.PRICE,
                PROVIDER_NAME,
                symbol.ticker(),
                rawData
            );

            LOG.debugf("Successfully fetched %s price: %.2f USD (change: %.2f%%)",
                symbol.ticker(), currentPrice, changePercent);
            return List.of(event);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch Finnhub quote for %s", symbol.ticker());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch historical OHLC candles for a stock symbol from Finnhub API.
     * Default resolution: 5 minutes.
     *
     * @param symbol the stock symbol (e.g., AAPL, TSLA)
     * @param start start time (inclusive)
     * @param end end time (inclusive)
     * @return list of market events with OHLC data
     */
    public List<MarketEvent> fetchHistoricalCandles(Symbol symbol, Instant start, Instant end) {
        try {
            String resolution = "5"; // 5 minutes
            long fromTimestamp = start.getEpochSecond();
            long toTimestamp = end.getEpochSecond();

            LOG.debugf("Fetching Finnhub candles for %s from %d to %d (resolution: %s min)",
                symbol.ticker(), fromTimestamp, toTimestamp, resolution);

            // Fetch candles
            Map<String, Object> response = restClient.getCandles(
                symbol.ticker(), resolution, fromTimestamp, toTimestamp, apiKey
            );

            // Check status
            String status = (String) response.get("s");
            if (!"ok".equalsIgnoreCase(status)) {
                LOG.warnf("Finnhub returned status '%s' for %s", status, symbol.ticker());
                return Collections.emptyList();
            }

            // Extract OHLC arrays
            @SuppressWarnings("unchecked")
            List<Number> closes = (List<Number>) response.get("c");
            @SuppressWarnings("unchecked")
            List<Number> highs = (List<Number>) response.get("h");
            @SuppressWarnings("unchecked")
            List<Number> lows = (List<Number>) response.get("l");
            @SuppressWarnings("unchecked")
            List<Number> opens = (List<Number>) response.get("o");
            @SuppressWarnings("unchecked")
            List<Number> timestamps = (List<Number>) response.get("t");
            @SuppressWarnings("unchecked")
            List<Number> volumes = (List<Number>) response.get("v");

            if (closes == null || closes.isEmpty()) {
                LOG.warnf("No candle data returned for %s", symbol.ticker());
                return Collections.emptyList();
            }

            List<MarketEvent> events = new ArrayList<>();
            int count = closes.size();

            // Parse each candle
            for (int i = 0; i < count; i++) {
                double close = closes.get(i).doubleValue();
                double high = highs.get(i).doubleValue();
                double low = lows.get(i).doubleValue();
                double open = opens.get(i).doubleValue();
                long timestamp = timestamps.get(i).longValue();
                double volume = volumes.get(i).doubleValue();

                // Create rawData map with OHLC information
                Map<String, Object> rawData = new HashMap<>();
                rawData.put("price", close);  // Use close as primary price
                rawData.put("open", open);
                rawData.put("high", high);
                rawData.put("low", low);
                rawData.put("close", close);
                rawData.put("volume", volume);
                rawData.put("currency", "USD");
                rawData.put("symbol", symbol.ticker());
                rawData.put("resolution", resolution);

                MarketEvent event = new MarketEvent(
                    Instant.ofEpochSecond(timestamp),
                    EventType.PRICE,
                    PROVIDER_NAME,
                    symbol.ticker(),
                    rawData
                );

                events.add(event);
            }

            LOG.infof("Successfully fetched %d candles for %s", events.size(), symbol.ticker());
            return events;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch Finnhub candles for %s", symbol.ticker());
            // Check for authentication errors (401/403) and propagate them
            // so BackfillExecutor can try alternate providers
            String message = e.getMessage();
            if (message != null && (message.contains("403") || message.contains("401") ||
                    message.contains("Forbidden") || message.contains("Unauthorized"))) {
                throw new RuntimeException("Finnhub API authentication failed for " + symbol.ticker() +
                        ". Check FINNHUB_API_KEY configuration.", e);
            }
            // For other errors (rate limit, network), return empty to allow partial progress
            return Collections.emptyList();
        }
    }

    public List<MarketEvent> fetchSymbolNews(Symbol symbol, Instant start, Instant end) {
        // Finnhub provides price data only in this adapter
        // Company news would require a separate endpoint and implementation
        return Collections.emptyList();
    }

    public List<MarketEvent> fetchSymbolSentiment(Symbol symbol, Instant start, Instant end) {
        // Finnhub provides price data only in this adapter
        return Collections.emptyList();
    }

    public List<MarketEvent> fetchGeneralMarketNews(Instant start, Instant end) {
        // Finnhub provides price data only in this adapter
        return Collections.emptyList();
    }

    public List<MarketEvent> fetchEconomicIndicators(Instant start, Instant end) {
        // Finnhub provides price data only in this adapter
        return Collections.emptyList();
    }

    public List<MarketEvent> fetchMarketSentiment(Instant start, Instant end) {
        // Finnhub provides price data only in this adapter
        return Collections.emptyList();
    }

    public List<MarketEvent> searchNews(String query, Instant start, Instant end) {
        // Finnhub provides price data only in this adapter
        return Collections.emptyList();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    // Helper methods

    private List<String> parseSymbols(String config) {
        if (config == null || config.isBlank()) {
            return DEFAULT_SYMBOLS;
        }
        return List.of(config.split(","));
    }

    /**
     * Check if US stock market is currently open.
     * Market hours: Monday-Friday, 9:30 AM - 4:00 PM Eastern Time (EST/EDT)
     *
     * @return true if market is open, false otherwise
     */
    private boolean isMarketOpen() {
        ZonedDateTime nowEastern = ZonedDateTime.now(US_EASTERN);
        DayOfWeek dayOfWeek = nowEastern.getDayOfWeek();
        LocalTime currentTime = nowEastern.toLocalTime();

        // Check if it's a weekday (Monday-Friday)
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            LOG.debug("Market is closed (weekend)");
            return false;
        }

        // Check if current time is within market hours (9:30 AM - 4:00 PM EST)
        if (currentTime.isBefore(MARKET_OPEN) || currentTime.isAfter(MARKET_CLOSE)) {
            LOG.debugf("Market is closed (outside trading hours: %s EST)", currentTime);
            return false;
        }

        LOG.debugf("Market is open (trading hours: %s EST)", currentTime);
        return true;
    }

    private double convertToDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private long convertToLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        // NOTE: BACKFILL and CANDLES removed - Finnhub free tier ($0/month) does NOT
        // support the /stock/candle endpoint. Only the $3000/month "All-In-One" plan
        // includes historical candles. Use Alpaca or AlphaVantage for backfill instead.
        return Set.of(
            MarketDataFeature.STOCK,         // Stock market data (quotes only on free tier)
            MarketDataFeature.RESEARCH       // Free tier suitable for research quotes
        );
    }
}
