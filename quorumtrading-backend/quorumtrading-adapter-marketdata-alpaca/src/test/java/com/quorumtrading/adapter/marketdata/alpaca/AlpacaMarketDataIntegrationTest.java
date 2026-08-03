package com.quorumtrading.adapter.marketdata.alpaca;

import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.enums.EventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AlpacaMarketDataAdapter.
 *
 * Tests verify read-only operations:
 * - API connectivity
 * - Stock price fetching
 * - Crypto price fetching
 * - Market data event creation
 *
 * Hits the LIVE data.alpaca.markets API, so — like the sibling
 * AlpacaBrokerIntegrationTest — the whole class is disabled unless
 * ALPACA_INTEGRATION_TESTS=true is set. Even then, individual tests skip
 * (as skipped, not passed) when no real API key is configured.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "ALPACA_INTEGRATION_TESTS", matches = "true")
class AlpacaMarketDataIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AlpacaMarketDataIntegrationTest.class);

    @Inject
    AlpacaMarketDataAdapter adapter;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.enabled", defaultValue = "false")
    boolean enabled;

    /**
     * These tests hit the live Alpaca API and only make sense with real credentials, supplied via
     * the ALPACA_API_KEY env var. The config default is the non-blank sentinel "not-configured"
     * (empty defaults break Quarkus config validation), so treat the sentinel as absent.
     */
    private boolean alpacaConfigured() {
        return enabled
            && apiKey.isPresent()
            && !apiKey.get().isEmpty()
            && !"not-configured".equals(apiKey.get());
    }

    @Test
    void testApiConnectivity() {
        // Skip (reported as skipped, not passed) if adapter disabled or API key not
        // configured — the real key comes via the ALPACA_API_KEY env var.
        Assumptions.assumeTrue(alpacaConfigured(),
            "Alpaca market data adapter not enabled or API key not configured");

        // Verify adapter is injectable
        assertNotNull(adapter, "AlpacaMarketDataAdapter should be injectable");
        assertEquals("alpaca", adapter.getProviderName(),
            "Provider name should be 'alpaca'");
    }

    @Test
    void testFetchStockPrice() {
        // Skip (reported as skipped, not passed) if adapter disabled or API key not
        // configured — the real key comes via the ALPACA_API_KEY env var.
        Assumptions.assumeTrue(alpacaConfigured(),
            "Alpaca market data adapter not enabled or API key not configured");

        // NOTE: Changed from fetchDataInRange() (historical, paid tier) to fetchMarketData() (latest, free tier)
        // Historical stock data endpoint requires paid Alpaca subscription (403 Forbidden on free tier)
        Set<String> stockTickers = Set.of("AAPL", "TSLA");
        Set<String> cryptoTickers = Set.of();

        List<MarketEvent> events = adapter.fetchMarketData(cryptoTickers, stockTickers);

        // CRITICAL: Verify we got actual data back (not empty due to auth failure)
        assertNotNull(events, "Events list should not be null");
        assertFalse(events.isEmpty(),
            "Stock price fetch should return latest data for AAPL/TSLA. " +
            "Empty list likely indicates authentication failure (401) or API error. " +
            "Check logs for errors.");

        // Verify at least one price update event exists
        boolean hasPriceUpdate = events.stream()
            .anyMatch(e -> e.eventType() == EventType.PRICE);
        assertTrue(hasPriceUpdate, "Should have at least one PRICE event");

        // Verify structure of first price event
        MarketEvent priceEvent = events.stream()
            .filter(e -> e.eventType() == EventType.PRICE)
            .findFirst()
            .orElseThrow();
        assertNotNull(priceEvent.symbol(), "Price event symbol should not be null");
        assertTrue(stockTickers.contains(priceEvent.symbol()),
            "Event symbol should be one of the requested stocks");
        assertNotNull(priceEvent.timestamp(), "Price event timestamp should not be null");

        log.info("Successfully fetched {} stock price events, sample: {} @ {}",
            events.size(), priceEvent.symbol(), priceEvent.timestamp());
    }

    @Test
    void testFetchCryptoPrice() {
        // Skip (reported as skipped, not passed) if adapter disabled or API key not
        // configured — the real key comes via the ALPACA_API_KEY env var.
        Assumptions.assumeTrue(alpacaConfigured(),
            "Alpaca market data adapter not enabled or API key not configured");

        // Fetch latest crypto data
        List<MarketEvent> events = adapter.fetchLatestData();

        // CRITICAL: Verify we got actual data back
        assertNotNull(events, "Events list should not be null");
        assertFalse(events.isEmpty(),
            "Crypto price fetch should return data (adapter configured with symbols). " +
            "Empty list likely indicates authentication failure (401), missing symbol config, or API error. " +
            "Check logs for errors.");

        // Verify at least one price update event exists
        boolean hasPriceUpdate = events.stream()
            .anyMatch(e -> e.eventType() == EventType.PRICE);
        assertTrue(hasPriceUpdate, "Should have at least one PRICE event");

        // Verify structure of first price event
        MarketEvent priceEvent = events.stream()
            .filter(e -> e.eventType() == EventType.PRICE)
            .findFirst()
            .orElseThrow();
        assertNotNull(priceEvent.symbol(), "Price event symbol should not be null");
        assertNotNull(priceEvent.timestamp(), "Price event timestamp should not be null");

        log.info("Successfully fetched {} crypto price events, sample: {} @ {}",
            events.size(), priceEvent.symbol(), priceEvent.timestamp());
    }
}
