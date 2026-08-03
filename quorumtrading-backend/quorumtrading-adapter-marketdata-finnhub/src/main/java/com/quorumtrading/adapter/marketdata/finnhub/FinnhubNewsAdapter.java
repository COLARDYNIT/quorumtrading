package com.quorumtrading.adapter.marketdata.finnhub;

import com.quorumtrading.adapter.marketdata.finnhub.dto.FinnhubNewsResponse;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@ApplicationScoped
public class FinnhubNewsAdapter implements MarketDataPort {

    private static final Logger logger = Logger.getLogger(FinnhubNewsAdapter.class);

    private final FinnhubRestClient restClient;
    private final String apiKey;
    private final boolean enabled;

    @Inject
    public FinnhubNewsAdapter(
            @RestClient FinnhubRestClient restClient,
            @ConfigProperty(name = "quorumtrading.providers.finnhub.api-key", defaultValue = "") String apiKey,
            @ConfigProperty(name = "quorumtrading.providers.finnhub.news.enabled", defaultValue = "true") boolean enabled) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.enabled = enabled;
    }

    @Override
    public List<MarketEvent> fetchLatestData() {
        // Fetch latest news from last 24 hours
        Instant end = Instant.now();
        Instant start = end.minus(24, java.time.temporal.ChronoUnit.HOURS);
        return fetchMarketNews(start, end);
    }

    @Override
    public String getProviderName() {
        return "Finnhub News";
    }

    /**
     * Fetch general market news within a date range.
     * Used by fetchLatestData() for recent news and by fetchDataInRange() for broader market context.
     */
    public List<MarketEvent> fetchMarketNews(Instant start, Instant end) {
        if (!enabled) {
            logger.debug("FinnhubNewsAdapter is disabled");
            return Collections.emptyList();
        }

        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            logger.warn("Finnhub API key not configured, skipping news collection");
            return Collections.emptyList();
        }

        try {
            logger.infof("Fetching Finnhub market news from %s to %s", start, end);

            List<MarketEvent> events = new ArrayList<>();
            List<FinnhubNewsResponse> newsItems = restClient.getMarketNews("general", apiKey);

            for (FinnhubNewsResponse item : newsItems) {
                MarketEvent event = toMarketEvent(item, "MARKET", start, end);
                if (event != null) {
                    events.add(event);
                }
            }

            logger.infof("Fetched %d Finnhub news events", events.size());
            return events;

        } catch (Exception e) {
            logger.error("Failed to fetch Finnhub news", e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch historical news for specific tickers within a date range.
     * Calls Finnhub company news endpoint per stock ticker (ticker-specific news)
     * plus general market news for broad market context.
     */
    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end) {
        if (!enabled) {
            logger.debug("FinnhubNewsAdapter is disabled");
            return Collections.emptyList();
        }

        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            logger.warn("Finnhub API key not configured, skipping news collection");
            return Collections.emptyList();
        }

        List<MarketEvent> events = new ArrayList<>();

        String fromDate = LocalDate.ofInstant(start, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String toDate = LocalDate.ofInstant(end, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Fetch ticker-specific company news for each stock
        if (stockTickers != null) {
            for (String ticker : stockTickers) {
                try {
                    logger.debugf("Fetching Finnhub company news for %s from %s to %s", ticker, fromDate, toDate);
                    List<FinnhubNewsResponse> companyNews = restClient.getCompanyNews(ticker, fromDate, toDate, apiKey);

                    if (companyNews != null) {
                        for (FinnhubNewsResponse item : companyNews) {
                            MarketEvent event = toMarketEvent(item, ticker, start, end);
                            if (event != null) {
                                events.add(event);
                            }
                        }
                        logger.debugf("Fetched %d company news items for %s", companyNews.size(), ticker);
                    }
                } catch (Exception e) {
                    logger.warnf(e, "Failed to fetch company news for %s — continuing with other tickers", ticker);
                }
            }
        }

        // Also fetch general market news for broader context
        try {
            List<MarketEvent> marketNews = fetchMarketNews(start, end);
            events.addAll(marketNews);
        } catch (Exception e) {
            logger.warnf(e, "Failed to fetch general market news — continuing with company news only");
        }

        logger.infof("Fetched %d total Finnhub news events for range %s to %s", events.size(), fromDate, toDate);
        return events;
    }

    /**
     * Convert a Finnhub news response item to a MarketEvent, filtering by date range.
     * Returns null if the item is outside the date range or has no timestamp.
     */
    private MarketEvent toMarketEvent(FinnhubNewsResponse item, String symbol, Instant start, Instant end) {
        if (item.getDatetime() == null) {
            return null;
        }

        Instant eventTime = Instant.ofEpochSecond(item.getDatetime());
        if (eventTime.isBefore(start) || eventTime.isAfter(end)) {
            return null;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("headline", item.getHeadline());
        data.put("summary", item.getSummary());
        data.put("source", item.getSource());
        data.put("url", item.getUrl());
        data.put("category", item.getCategory());
        data.put("related", item.getRelated());

        return new MarketEvent(
            eventTime,
            EventType.NEWS,
            item.getSource() != null ? item.getSource() : "Finnhub",
            symbol,
            data
        );
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        return Set.of(
            MarketDataFeature.NEWS,          // News articles
            MarketDataFeature.BACKFILL,      // Supports historical news via date range
            MarketDataFeature.DAILY,         // News is aggregated daily
            MarketDataFeature.RESEARCH       // Free tier suitable for research/backtesting
        );
    }
}
