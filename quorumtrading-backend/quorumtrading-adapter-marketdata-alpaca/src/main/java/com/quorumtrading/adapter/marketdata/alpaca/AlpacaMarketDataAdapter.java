package com.quorumtrading.adapter.marketdata.alpaca;

import com.quorumtrading.adapter.marketdata.alpaca.client.AlpacaMarketDataClient;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.AlpacaNewsArticle;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.AlpacaNewsResponse;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.BarData;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.HistoricalBarsResponse;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.LatestBarsResponse;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.exception.MarketDataFetchException;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.TickerRegistry;
import jakarta.ws.rs.WebApplicationException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Alpaca market data adapter for fetching stock and crypto prices.
 *
 * Uses Alpaca Market Data API v1beta3 (crypto) and v2 (stocks) via Quarkus REST Client.
 * Fetches latest minute-bar data for pricing.
 *
 * Configuration properties:
 * - quorumtrading.marketdata.alpaca.api-key: Alpaca API key
 * - quorumtrading.marketdata.alpaca.api-secret: Alpaca API secret
 * - quorumtrading.marketdata.alpaca.base-url: API base URL (default: https://data.alpaca.markets)
 * - quorumtrading.marketdata.alpaca.enabled: Enable/disable adapter (default: false)
 *
 * NOTE: Uses close price from latest minute bar for market data events.
 */
@ApplicationScoped
public class AlpacaMarketDataAdapter implements MarketDataPort {

    private static final Logger LOG = Logger.getLogger(AlpacaMarketDataAdapter.class);
    private static final String PROVIDER_NAME = "alpaca";

    @RestClient
    AlpacaMarketDataClient marketDataClient;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.crypto-symbols", defaultValue = "")
    String configuredCryptoSymbols;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.stock-symbols", defaultValue = "")
    String configuredStockSymbols;

    // Stock data feed: 'iex' (free, IEX only) or 'sip' (paid, consolidated)
    // Free Alpaca accounts MUST use 'iex' - SIP returns 403 Forbidden
    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.stock-feed", defaultValue = "iex")
    String stockFeed;

    // Only used to decide whether polling should run at all — the actual auth headers are
    // attached by AlpacaMarketDataAuthHeadersFactory. 'not-configured' is the repo-wide
    // placeholder for unset credentials (empty defaults break Quarkus config validation).
    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.api-key", defaultValue = "not-configured")
    String apiKey;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.api-secret", defaultValue = "not-configured")
    String apiSecret;

    @jakarta.annotation.PostConstruct
    void init() {
        LOG.infof("Alpaca market data adapter initialized: enabled=%s, stockFeed=%s, cryptoSymbols=%s, stockSymbols=%s, features=%s",
                 enabled, stockFeed, configuredCryptoSymbols, configuredStockSymbols, getSupportedFeatures());
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Polling calls are skipped (WARN, not ERROR) when no real credentials are set — a clean
     * zero-key boot must not log ERRORs. Explicit user actions (backfill, range fetches) still
     * go through and surface real auth failures.
     */
    @Override
    public boolean isConfigured() {
        return credentialsConfigured();
    }

    private boolean credentialsConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey)
                && apiSecret != null && !apiSecret.isBlank() && !"not-configured".equals(apiSecret);
    }

    @Override
    public List<MarketEvent> fetchLatestDataForTickers(TickerRegistry tickerRegistry) {
        if (!enabled) {
            LOG.debug("Alpaca market data adapter disabled, skipping fetch");
            return Collections.emptyList();
        }

        if (!credentialsConfigured()) {
            LOG.warn("Alpaca market data credentials not configured (ALPACA_API_KEY/ALPACA_API_SECRET) — skipping fetch");
            return Collections.emptyList();
        }

        // Convert domain Symbols to string tickers for API calls (adapter layer uses strings)
        Set<String> cryptoTickers = tickerRegistry.getCryptoTickerStrings();
        Set<String> stockTickers = tickerRegistry.getStockTickerStrings();

        // Fall back to configured defaults if no strategies specify tickers
        if (cryptoTickers.isEmpty() && stockTickers.isEmpty()) {
            LOG.debug("No tickers in strategy registry, using adapter defaults");
            cryptoTickers = parseSymbols(configuredCryptoSymbols);
            stockTickers = parseSymbols(configuredStockSymbols);
        }

        if (cryptoTickers.isEmpty() && stockTickers.isEmpty()) {
            LOG.debug("No crypto or stock symbols configured for Alpaca - returning empty");
            return Collections.emptyList();
        }

        LOG.infof("Fetching latest data for %d crypto tickers and %d stock tickers from strategies",
            cryptoTickers.size(), stockTickers.size());
        return fetchMarketData(cryptoTickers, stockTickers);
    }

    @Override
    @Deprecated
    public List<MarketEvent> fetchLatestData() {
        if (!enabled) {
            LOG.debug("Alpaca market data adapter disabled, skipping fetch");
            return Collections.emptyList();
        }

        if (!credentialsConfigured()) {
            LOG.warn("Alpaca market data credentials not configured (ALPACA_API_KEY/ALPACA_API_SECRET) — skipping fetch");
            return Collections.emptyList();
        }

        // Parse configured symbols
        Set<String> cryptoTickers = parseSymbols(configuredCryptoSymbols);
        Set<String> stockTickers = parseSymbols(configuredStockSymbols);

        if (cryptoTickers.isEmpty() && stockTickers.isEmpty()) {
            LOG.debug("No crypto or stock symbols configured for Alpaca - returning empty");
            return Collections.emptyList();
        }

        LOG.infof("Fetching latest data for %d crypto symbols and %d stock symbols",
            cryptoTickers.size(), stockTickers.size());
        return fetchMarketData(cryptoTickers, stockTickers);
    }

    /**
     * Parse comma-separated symbol string from config.
     */
    private Set<String> parseSymbols(String symbolsConfig) {
        if (symbolsConfig == null || symbolsConfig.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(symbolsConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Fetch market data for specific tickers within a time range.
     * Uses Alpaca historical bars API to fetch 5-minute interval data.
     *
     * @param cryptoTickers Set of crypto tickers (e.g., "BTC", "ETH")
     * @param stockTickers Set of stock tickers (e.g., "AAPL", "TSLA")
     * @param start Start time for historical data
     * @param end End time for historical data
     * @return List of market events with historical prices
     */
    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end) {
        // Delegate to granularity-aware method with default 5-minute granularity
        return fetchDataInRange(cryptoTickers, stockTickers, start, end, com.quorumtrading.model.enums.Granularity.FIVE_MINUTES);
    }

    /**
     * Fetch market data for specific tickers within a time range with configurable granularity.
     * Uses Alpaca historical bars API to fetch data at the specified interval.
     *
     * @param cryptoTickers Set of crypto tickers (e.g., "BTC", "ETH")
     * @param stockTickers Set of stock tickers (e.g., "AAPL", "TSLA")
     * @param start Start time for historical data
     * @param end End time for historical data
     * @param granularity Time interval for bars (e.g., '1Min', '5Min', '1Hour', '1Day')
     * @return List of market events with historical prices at the specified granularity
     */
    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end,
            com.quorumtrading.model.enums.Granularity granularity) {

        LOG.debugf("fetchDataInRange called: enabled=%s, cryptoTickers=%s, stockTickers=%s, start=%s, end=%s, granularity=%s",
                  enabled, cryptoTickers, stockTickers, start, end, granularity);

        if (!enabled) {
            LOG.warnf("Alpaca market data adapter DISABLED - skipping historical fetch for stocks=%s, crypto=%s",
                     stockTickers, cryptoTickers);
            return Collections.emptyList();
        }

        // Same gate as the latest-data paths: without real credentials, skip cleanly instead of
        // calling Alpaca with the "not-configured" placeholder and logging a 401 ERROR. This keeps
        // zero-key crypto backfills quiet — the keyless Kraken/Coinbase adapters supply the data.
        if (!credentialsConfigured()) {
            LOG.warnf("Alpaca market data credentials not configured (ALPACA_API_KEY/ALPACA_API_SECRET) — skipping historical fetch for stocks=%s, crypto=%s",
                     stockTickers, cryptoTickers);
            return Collections.emptyList();
        }

        // Use provided granularity or default to ONE_HOUR
        com.quorumtrading.model.enums.Granularity effectiveGranularity =
            (granularity == null) ? com.quorumtrading.model.enums.Granularity.ONE_HOUR : granularity;
        String alpacaTimeframe = effectiveGranularity.getApiFormat();

        LOG.debugf("Using granularity: %s (alpaca format: %s)", effectiveGranularity, alpacaTimeframe);

        List<MarketEvent> events = new ArrayList<>();

        // Fetch historical stock data — exceptions propagate to BackfillExecutor
        if (stockTickers != null && !stockTickers.isEmpty()) {
            LOG.debugf("Fetching historical STOCK data for tickers: %s", stockTickers);
            List<MarketEvent> stockEvents = fetchHistoricalStockData(stockTickers, start, end, alpacaTimeframe);
            LOG.debugf("Fetched %d historical STOCK events", stockEvents.size());
            events.addAll(stockEvents);
        } else {
            LOG.debugf("No stock tickers provided for historical fetch");
        }

        // Fetch historical crypto data — exceptions propagate to BackfillExecutor
        if (cryptoTickers != null && !cryptoTickers.isEmpty()) {
            LOG.debugf("Fetching historical CRYPTO data for tickers: %s", cryptoTickers);
            List<MarketEvent> cryptoEvents = fetchHistoricalCryptoData(cryptoTickers, start, end, alpacaTimeframe);
            LOG.debugf("Fetched %d historical CRYPTO events", cryptoEvents.size());
            events.addAll(cryptoEvents);
        } else {
            LOG.debugf("No crypto tickers provided for historical fetch");
        }

        // Fetch historical news data for all tickers
        Set<String> allTickers = new HashSet<>();
        if (stockTickers != null) allTickers.addAll(stockTickers);
        if (cryptoTickers != null) allTickers.addAll(cryptoTickers);

        if (!allTickers.isEmpty()) {
            LOG.debugf("Fetching historical NEWS for tickers: %s", allTickers);
            List<MarketEvent> newsEvents = fetchHistoricalNews(allTickers, start, end);
            LOG.debugf("Fetched %d historical NEWS events", newsEvents.size());
            events.addAll(newsEvents);
        }

        LOG.infof("Fetched %d historical events from Alpaca (%d stocks, %d crypto) for range %s to %s at %s granularity",
            events.size(),
            stockTickers != null ? stockTickers.size() : 0,
            cryptoTickers != null ? cryptoTickers.size() : 0,
            start, end, effectiveGranularity.getDisplayName());

        return events;
    }

    /**
     * Fetch market data for specific tickers.
     * This is the main method used by the market data scheduler.
     *
     * @param cryptoTickers Set of crypto tickers (e.g., "BTC", "ETH")
     * @param stockTickers Set of stock tickers (e.g., "AAPL", "TSLA")
     * @return List of market events with latest prices
     */
    public List<MarketEvent> fetchMarketData(Set<String> cryptoTickers, Set<String> stockTickers) {
        if (!enabled) {
            LOG.debug("Alpaca market data adapter disabled, skipping fetch");
            return Collections.emptyList();
        }

        List<MarketEvent> events = new ArrayList<>();

        try {
            // Fetch stock data
            if (stockTickers != null && !stockTickers.isEmpty()) {
                events.addAll(fetchStockData(stockTickers));
            }

            // Fetch crypto data
            if (cryptoTickers != null && !cryptoTickers.isEmpty()) {
                events.addAll(fetchCryptoData(cryptoTickers));
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch market data from Alpaca");
        }

        return events;
    }

    /**
     * Fetch stock price data from Alpaca.
     * Uses configured stock feed (iex for free accounts, sip for paid subscriptions).
     */
    private List<MarketEvent> fetchStockData(Set<String> tickers) {
        if (tickers.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String symbols = String.join(",", tickers);
            // Use configured feed (iex = free, sip = paid subscription)
            LatestBarsResponse response = marketDataClient.getLatestStockBars(symbols, stockFeed, null);

            if (response == null || response.getBars() == null) {
                LOG.warnf("No stock bar data returned for symbols: %s", symbols);
                return Collections.emptyList();
            }

            List<MarketEvent> events = new ArrayList<>();
            Map<String, BarData> bars = response.getBars();

            for (Map.Entry<String, BarData> entry : bars.entrySet()) {
                String ticker = entry.getKey();
                BarData bar = entry.getValue();

                if (bar.getClose() != null) {
                    // Create rawData map with OHLCV information
                    Map<String, Object> rawData = new HashMap<>();
                    rawData.put("price", bar.getClose());
                    rawData.put("open", bar.getOpen());
                    rawData.put("high", bar.getHigh());
                    rawData.put("low", bar.getLow());
                    rawData.put("close", bar.getClose());
                    rawData.put("volume", bar.getVolume());

                    MarketEvent event = new MarketEvent(
                        parseTimestamp(bar.getTimestamp()),
                        EventType.PRICE,
                        PROVIDER_NAME,
                        ticker,
                        rawData
                    );
                    events.add(event);

                    LOG.debugf("Stock price: %s = $%s (volume: %s)",
                        ticker, bar.getClose(), bar.getVolume());
                }
            }

            return events;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch stock data for tickers: %s", tickers);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch crypto price data from Alpaca.
     */
    private List<MarketEvent> fetchCryptoData(Set<String> tickers) {
        if (tickers.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // Convert tickers like "BTC" to "BTC/USD" format expected by Alpaca
            Set<String> cryptoPairs = new HashSet<>();
            for (String ticker : tickers) {
                if (!ticker.contains("/")) {
                    cryptoPairs.add(ticker + "/USD");
                } else {
                    cryptoPairs.add(ticker);
                }
            }

            String symbols = String.join(",", cryptoPairs);
            LatestBarsResponse response = marketDataClient.getLatestCryptoBars("us", symbols);

            if (response == null || response.getBars() == null) {
                LOG.warnf("No crypto bar data returned for symbols: %s", symbols);
                return Collections.emptyList();
            }

            List<MarketEvent> events = new ArrayList<>();
            Map<String, BarData> bars = response.getBars();

            for (Map.Entry<String, BarData> entry : bars.entrySet()) {
                String symbol = entry.getKey(); // e.g., "BTC/USD"
                BarData bar = entry.getValue();

                // Extract base ticker (e.g., "BTC" from "BTC/USD")
                String ticker = symbol.split("/")[0];

                if (bar.getClose() != null) {
                    // Create rawData map with OHLCV information
                    Map<String, Object> rawData = new HashMap<>();
                    rawData.put("price", bar.getClose());
                    rawData.put("open", bar.getOpen());
                    rawData.put("high", bar.getHigh());
                    rawData.put("low", bar.getLow());
                    rawData.put("close", bar.getClose());
                    rawData.put("volume", bar.getVolume());
                    rawData.put("symbol", symbol); // Include full symbol like "BTC/USD"

                    MarketEvent event = new MarketEvent(
                        parseTimestamp(bar.getTimestamp()),
                        EventType.PRICE,
                        PROVIDER_NAME,
                        ticker,
                        rawData
                    );
                    events.add(event);

                    LOG.debugf("Crypto price: %s = $%s (volume: %s)",
                        ticker, bar.getClose(), bar.getVolume());
                }
            }

            return events;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch crypto data for tickers: %s", tickers);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch historical stock price data from Alpaca.
     * Fetches bars at the specified granularity for the specified time range.
     * Uses configured stock feed (iex for free accounts, sip for paid subscriptions).
     */
    private List<MarketEvent> fetchHistoricalStockData(Set<String> tickers, Instant start, Instant end, String granularity) {
        if (tickers.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String symbols = String.join(",", tickers);
            String startTime = DateTimeFormatter.ISO_INSTANT.format(start);
            String endTime = DateTimeFormatter.ISO_INSTANT.format(end);

            LOG.infof("Fetching historical stock data for %s from %s to %s (feed=%s)", symbols, startTime, endTime, stockFeed);

            List<MarketEvent> events = new ArrayList<>();
            String pageToken = null;
            int pageCount = 0;
            final int MAX_PAGES = 100; // Safety limit to prevent infinite loops

            do {
                HistoricalBarsResponse response = marketDataClient.getStockBars(
                    symbols,
                    granularity, // Configurable granularity (e.g., 1Min, 5Min, 1Hour, 1Day)
                    startTime,
                    endTime,
                    10000,       // Max limit per page
                    stockFeed,   // Feed: 'iex' (free) or 'sip' (paid subscription)
                    pageToken,   // Page token (null for first page)
                    "split"      // Split-adjusted prices to handle stock splits correctly
                );

                if (response == null || response.getBars() == null) {
                    if (pageCount == 0) {
                        LOG.warnf("No historical stock bar data returned for symbols: %s", symbols);
                    }
                    break;
                }

                Map<String, List<BarData>> bars = response.getBars();

                for (Map.Entry<String, List<BarData>> entry : bars.entrySet()) {
                    String ticker = entry.getKey();
                    List<BarData> barList = entry.getValue();

                    for (BarData bar : barList) {
                        if (bar.getClose() != null) {
                            Map<String, Object> rawData = new HashMap<>();
                            rawData.put("price", bar.getClose());
                            rawData.put("open", bar.getOpen());
                            rawData.put("high", bar.getHigh());
                            rawData.put("low", bar.getLow());
                            rawData.put("close", bar.getClose());
                            rawData.put("volume", bar.getVolume());

                            MarketEvent event = new MarketEvent(
                                parseTimestamp(bar.getTimestamp()),
                                EventType.PRICE,
                                PROVIDER_NAME,
                                ticker,
                                rawData
                            );
                            events.add(event);
                        }
                    }

                    LOG.debugf("Fetched %d historical bars for stock %s (page %d)", (Object) barList.size(), ticker, (Object) (pageCount + 1));
                }

                // Get next page token
                pageToken = response.getNextPageToken();
                pageCount++;

                if (pageCount >= MAX_PAGES && pageToken != null) {
                    LOG.warnf("Reached maximum page limit (%d) for stock data, stopping pagination", MAX_PAGES);
                    break;
                }

            } while (pageToken != null && !pageToken.isEmpty());

            LOG.infof("Fetched %d total historical bars for stocks across %d pages", events.size(), pageCount);
            return events;

        } catch (Exception e) {
            throw toMarketDataFetchException(e, tickers);
        }
    }

    /**
     * Fetch historical crypto price data from Alpaca.
     * Fetches bars at the specified granularity for the specified time range.
     */
    private List<MarketEvent> fetchHistoricalCryptoData(Set<String> tickers, Instant start, Instant end, String granularity) {
        if (tickers.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // Convert tickers like "BTC" to "BTC/USD" format expected by Alpaca
            Set<String> cryptoPairs = new HashSet<>();
            for (String ticker : tickers) {
                if (!ticker.contains("/")) {
                    cryptoPairs.add(ticker + "/USD");
                } else {
                    cryptoPairs.add(ticker);
                }
            }

            String symbols = String.join(",", cryptoPairs);
            String startTime = DateTimeFormatter.ISO_INSTANT.format(start);
            String endTime = DateTimeFormatter.ISO_INSTANT.format(end);

            LOG.infof("Fetching historical crypto data for %s from %s to %s", symbols, startTime, endTime);

            List<MarketEvent> events = new ArrayList<>();
            String pageToken = null;
            int pageCount = 0;
            final int MAX_PAGES = 100; // Safety limit to prevent infinite loops

            do {
                HistoricalBarsResponse response = marketDataClient.getCryptoBars(
                    "us",        // Location
                    symbols,
                    granularity, // Configurable granularity (e.g., 1Min, 5Min, 1Hour, 1Day)
                    startTime,
                    endTime,
                    10000,       // Max limit per page
                    pageToken    // Page token (null for first page)
                );

                if (response == null || response.getBars() == null) {
                    if (pageCount == 0) {
                        LOG.warnf("No historical crypto bar data returned for symbols: %s", symbols);
                    }
                    break;
                }

                Map<String, List<BarData>> bars = response.getBars();

                for (Map.Entry<String, List<BarData>> entry : bars.entrySet()) {
                    String symbol = entry.getKey(); // e.g., "BTC/USD"
                    List<BarData> barList = entry.getValue();

                    // Extract base ticker (e.g., "BTC" from "BTC/USD")
                    String ticker = symbol.split("/")[0];

                    for (BarData bar : barList) {
                        if (bar.getClose() != null) {
                            Map<String, Object> rawData = new HashMap<>();
                            rawData.put("price", bar.getClose());
                            rawData.put("open", bar.getOpen());
                            rawData.put("high", bar.getHigh());
                            rawData.put("low", bar.getLow());
                            rawData.put("close", bar.getClose());
                            rawData.put("volume", bar.getVolume());
                            rawData.put("symbol", symbol); // Include full symbol like "BTC/USD"

                            MarketEvent event = new MarketEvent(
                                parseTimestamp(bar.getTimestamp()),
                                EventType.PRICE,
                                PROVIDER_NAME,
                                ticker,
                                rawData
                            );
                            events.add(event);
                        }
                    }

                    LOG.debugf("Fetched %d historical bars for crypto %s (page %d)", (Object) barList.size(), ticker, (Object) (pageCount + 1));
                }

                // Get next page token
                pageToken = response.getNextPageToken();
                pageCount++;

                if (pageCount >= MAX_PAGES && pageToken != null) {
                    LOG.warnf("Reached maximum page limit (%d) for crypto data, stopping pagination", MAX_PAGES);
                    break;
                }

            } while (pageToken != null && !pageToken.isEmpty());

            LOG.infof("Fetched %d total historical bars for crypto across %d pages", events.size(), pageCount);
            return events;

        } catch (Exception e) {
            throw toMarketDataFetchException(e, tickers);
        }
    }

    /**
     * Fetch historical news articles from Alpaca News API.
     * Uses paginated requests to collect all articles in the date range.
     * Alpaca news is sourced from Benzinga and goes back to 2015.
     *
     * @param tickers Set of ticker symbols to fetch news for
     * @param start Start time for historical data
     * @param end End time for historical data
     * @return List of NEWS market events
     */
    private List<MarketEvent> fetchHistoricalNews(Set<String> tickers, Instant start, Instant end) {
        try {
            String symbols = String.join(",", tickers);
            String startTime = DateTimeFormatter.ISO_INSTANT.format(start);
            String endTime = DateTimeFormatter.ISO_INSTANT.format(end);

            LOG.infof("Fetching historical news for %s from %s to %s", symbols, startTime, endTime);

            List<MarketEvent> events = new ArrayList<>();
            String pageToken = null;
            int pageCount = 0;
            final int MAX_PAGES = 100;

            do {
                AlpacaNewsResponse response = marketDataClient.getNews(
                    symbols,
                    startTime,
                    endTime,
                    50,         // Max articles per page
                    false,      // Don't include full content (save bandwidth)
                    true,       // Exclude contentless articles
                    pageToken
                );

                if (response == null || response.getNews() == null || response.getNews().isEmpty()) {
                    if (pageCount == 0) {
                        LOG.debugf("No historical news returned for symbols: %s", symbols);
                    }
                    break;
                }

                for (AlpacaNewsArticle article : response.getNews()) {
                    if (article.getHeadline() == null || article.getCreatedAt() == null) {
                        continue;
                    }

                    Map<String, Object> rawData = new HashMap<>();
                    rawData.put("headline", article.getHeadline());
                    rawData.put("summary", article.getSummary());
                    rawData.put("source", article.getSource());
                    rawData.put("url", article.getUrl());
                    rawData.put("related", article.getSymbols() != null ? String.join(",", article.getSymbols()) : "");

                    // Determine the most relevant symbol for this article
                    String eventSymbol = determineEventSymbol(article.getSymbols(), tickers);

                    MarketEvent event = new MarketEvent(
                        parseTimestamp(article.getCreatedAt()),
                        EventType.NEWS,
                        article.getSource() != null ? article.getSource() : PROVIDER_NAME,
                        eventSymbol,
                        rawData
                    );
                    events.add(event);
                }

                LOG.debugf("Fetched %d news articles (page %d)", response.getNews().size(), pageCount + 1);

                pageToken = response.getNextPageToken();
                pageCount++;

                if (pageCount >= MAX_PAGES && pageToken != null) {
                    LOG.warnf("Reached maximum page limit (%d) for news data, stopping pagination", MAX_PAGES);
                    break;
                }

            } while (pageToken != null && !pageToken.isEmpty());

            LOG.infof("Fetched %d total news articles across %d pages", events.size(), pageCount);
            return events;

        } catch (Exception e) {
            // News fetch failure should not prevent price data from being used
            LOG.warnf(e, "Failed to fetch historical news for tickers: %s — continuing with price data only", tickers);
            return Collections.emptyList();
        }
    }

    /**
     * Determine the best symbol to assign to a news article.
     * If the article mentions a ticker we're interested in, use that.
     * Otherwise, fall back to "MARKET" for general news.
     */
    private String determineEventSymbol(List<String> articleSymbols, Set<String> requestedTickers) {
        if (articleSymbols == null || articleSymbols.isEmpty()) {
            return "MARKET";
        }
        // Find the first article symbol that matches a requested ticker
        for (String articleSymbol : articleSymbols) {
            if (requestedTickers.contains(articleSymbol)) {
                return articleSymbol;
            }
        }
        // If no direct match, use the first article symbol
        return articleSymbols.get(0);
    }

    /**
     * Convert a caught exception into a typed MarketDataFetchException.
     * If the exception is already a MarketDataFetchException, re-throw it as-is.
     */
    private MarketDataFetchException toMarketDataFetchException(Exception e, Set<String> tickers) {
        if (e instanceof MarketDataFetchException mfe) {
            return mfe;
        }

        if (e instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            String message = String.format("tickers=%s: %s", tickers, wae.getMessage());

            if (status == 429) {
                LOG.warnf("Rate limited by Alpaca (HTTP 429) for tickers: %s", tickers);
                return MarketDataFetchException.rateLimited(PROVIDER_NAME);
            }
            if (status == 401 || status == 403) {
                LOG.errorf("Auth failed for Alpaca (HTTP %d) for tickers: %s", status, tickers);
                return MarketDataFetchException.authFailed(PROVIDER_NAME, status);
            }
            return MarketDataFetchException.fromHttpStatus(PROVIDER_NAME, status, message);
        }

        // Non-HTTP exceptions (timeout, connection refused, etc.)
        LOG.errorf(e, "Connection/transport failure fetching data for tickers: %s", tickers);
        return MarketDataFetchException.connectionFailed(PROVIDER_NAME, e);
    }

    /**
     * Parse ISO 8601 timestamp from Alpaca API.
     */
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return Instant.now();
        }

        try {
            return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant();
        } catch (Exception e) {
            LOG.warnf("Failed to parse timestamp: %s, using current time", timestamp);
            return Instant.now();
        }
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        return Set.of(
            MarketDataFeature.CRYPTO,        // Cryptocurrency data
            MarketDataFeature.STOCK,         // Stock market data
            MarketDataFeature.NEWS,          // News articles (Benzinga via Alpaca, back to 2015)
            MarketDataFeature.BACKFILL,      // Supports historical data via bars + news endpoints
            MarketDataFeature.CANDLES,       // Provides OHLCV candle/bar data
            MarketDataFeature.INTRADAY,      // Supports minute-level granularity
            MarketDataFeature.DAILY,         // Supports daily aggregations
            MarketDataFeature.LIVE_TRADING   // Official exchange data suitable for live trading
        );
    }
}
