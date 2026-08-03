package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.TickerRegistry;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Mock MarketDataPort implementation for testing backfill functionality.
 * Generates synthetic market data for any requested date range.
 */
@Mock
@ApplicationScoped
public class MockMarketDataAdapter implements MarketDataPort {

    @Override
    public String getProviderName() {
        return "mock-provider";
    }

    @Override
    public List<MarketEvent> fetchLatestData() {
        return List.of();
    }

    @Override
    public List<MarketEvent> fetchLatestDataForTickers(TickerRegistry tickerRegistry) {
        return List.of();
    }

    @Override
    public RateLimit getRateLimit() {
        // Unlimited for testing
        return RateLimit.unlimited();
    }

    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end) {

        List<MarketEvent> events = new ArrayList<>();

        // Combine all tickers
        Set<String> allTickers = new HashSet<>();
        allTickers.addAll(cryptoTickers);
        allTickers.addAll(stockTickers);

        // Generate events for each ticker
        for (String ticker : allTickers) {
            InvestmentType type = cryptoTickers.contains(ticker)
                ? InvestmentType.CRYPTO
                : InvestmentType.STOCK;

            // Generate daily events for the range
            Instant current = start;
            while (current.isBefore(end)) {
                events.add(createMockEvent(ticker, type, current));
                current = current.plus(Duration.ofDays(1));
            }
        }

        return events;
    }

    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end,
            Granularity granularity) {
        // For testing, ignore granularity and delegate to standard method
        return fetchDataInRange(cryptoTickers, stockTickers, start, end);
    }

    /**
     * Create a mock market event for testing.
     *
     * @param ticker the ticker symbol
     * @param type the investment type
     * @param timestamp the event timestamp
     * @return a mock MarketEvent
     */
    private MarketEvent createMockEvent(String ticker, InvestmentType type, Instant timestamp) {
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("price", 50000.00);
        rawData.put("volume", 1000.0);
        rawData.put("open", 49500.00);
        rawData.put("high", 50500.00);
        rawData.put("low", 49000.00);
        rawData.put("close", 50000.00);

        return new MarketEvent(
            timestamp,
            EventType.PRICE,
            "mock-provider",
            ticker,
            rawData
        );
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        // Mock supports all features for testing purposes
        return Set.of(
            MarketDataFeature.CRYPTO,
            MarketDataFeature.STOCK,
            MarketDataFeature.NEWS,
            MarketDataFeature.SOCIAL_MEDIA,
            MarketDataFeature.BACKFILL,
            MarketDataFeature.CANDLES,
            MarketDataFeature.INTRADAY,
            MarketDataFeature.DAILY,
            MarketDataFeature.RESEARCH
        );
    }
}
