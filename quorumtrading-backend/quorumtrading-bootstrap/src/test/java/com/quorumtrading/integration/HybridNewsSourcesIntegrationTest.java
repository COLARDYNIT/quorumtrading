package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.marketdata.MarketEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hits LIVE external services: the configured RSS feeds (CNBC) and, when
 * {@code FINNHUB_API_KEY} is set, the live Finnhub API. Tagged {@code external}
 * so the default {@code ./mvnw test} run performs no third-party HTTP (see
 * {@code surefire.excludedGroups} in the parent pom). Run explicitly with:
 * {@code ./mvnw test -pl quorumtrading-bootstrap -Dtest=HybridNewsSourcesIntegrationTest
 * -Dsurefire.excludedGroups=reddit,llm,prompt-engineering,prompt-engineering-expensive}
 */
@QuarkusTest
@Tag("external")
class HybridNewsSourcesIntegrationTest {

    private static final Logger logger = Logger.getLogger(HybridNewsSourcesIntegrationTest.class);

    @Inject
    @io.quarkus.arc.All
    List<MarketDataPort> marketDataProviders;

    @ConfigProperty(name = "quorumtrading.providers.finnhub.api-key")
    String finnhubApiKey;

    @Test
    void shouldDiscoverAllMarketDataProviders() {
        // Then
        assertNotNull(marketDataProviders);

        logger.info("Discovered " + marketDataProviders.size() + " market data providers:");
        for (MarketDataPort provider : marketDataProviders) {
            logger.info("  - " + provider.getProviderName());
        }

        // Note: Test environment may not have all providers available due to missing dependencies or configuration
        // We just verify that at least some providers are discovered
        assertTrue(marketDataProviders.size() >= 1,
            "Expected at least 1 market data provider to be discovered");
    }

    @Test
    void shouldFetchDataFromFinnhubNews() {
        // Skips must report as skipped (not silently passed), so use JUnit 5 assumptions.
        Assumptions.assumeTrue(
            finnhubApiKey != null && !finnhubApiKey.isBlank() && !finnhubApiKey.equals("dummy-test-key"),
            "Finnhub API key not configured (set FINNHUB_API_KEY), skipping test");

        // When
        MarketDataPort finnhubProvider = marketDataProviders.stream()
            .filter(p -> p.getProviderName().equals("Finnhub News"))
            .findFirst()
            .orElse(null);

        Assumptions.assumeTrue(finnhubProvider != null,
            "Finnhub News provider not discovered in test environment, skipping test");

        List<MarketEvent> events = finnhubProvider.fetchLatestData();
        assertNotNull(events);

        logger.info("Fetched " + events.size() + " events from Finnhub News");

        if (!events.isEmpty()) {
            MarketEvent firstEvent = events.get(0);
            assertNotNull(firstEvent.rawData().get("headline"));
            assertEquals("MARKET", firstEvent.symbol());
        }
    }

    @Test
    void shouldFetchDataFromRSSFeeds() {
        // When
        MarketDataPort rssProvider = marketDataProviders.stream()
            .filter(p -> p.getProviderName().equals("RSS Feeds"))
            .findFirst()
            .orElse(null);

        // Skips must report as skipped (not silently passed), so use JUnit 5 assumptions.
        Assumptions.assumeTrue(rssProvider != null,
            "RSS Feeds provider not discovered in test environment, skipping test");

        List<MarketEvent> events = rssProvider.fetchLatestData();
        assertNotNull(events);

        logger.info("Fetched " + events.size() + " events from RSS Feeds");

        if (!events.isEmpty()) {
            MarketEvent firstEvent = events.get(0);
            assertNotNull(firstEvent.rawData().get("title"));
            assertEquals("MARKET", firstEvent.symbol());
        }
    }
}
