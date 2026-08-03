package com.quorumtrading.adapter.marketdata.reddit;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RedditAdapter.
 *
 * NOTE: These tests will make real API calls to Reddit's public JSON API.
 * They may fail if:
 * - Reddit API is down or rate-limiting
 * - No crypto posts are found in the subreddits
 * - Network connectivity issues
 *
 * The tests are designed to be resilient to empty results but verify
 * the adapter's ability to parse and filter Reddit data correctly.
 *
 * These tests are tagged with "reddit" and excluded from default test runs.
 * Run with: ./mvnw test -pl quorumtrading-adapter-marketdata-reddit -DexcludedGroups=""
 */
@QuarkusTest
@Tag("reddit")
class RedditAdapterTest {

    @Inject
    RedditAdapter adapter;

    private Symbol btcSymbol;
    private Symbol ethSymbol;
    private Instant now;
    private Instant oneHourAgo;
    private Instant oneDayAgo;

    @BeforeEach
    void setUp() {
        btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
        ethSymbol = new Symbol("ETH", InvestmentType.CRYPTO);
        now = Instant.now();
        oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        oneDayAgo = now.minus(1, ChronoUnit.DAYS);
    }

    @Test
    void shouldReturnProviderName() {
        assertThat(adapter.getProviderName()).isEqualTo("reddit");
    }

    @Test
    void shouldFetchLatestData() {
        // Test that fetchLatestData returns empty list (stub implementation)
        List<MarketEvent> events = adapter.fetchLatestData();
        assertThat(events).isNotNull();
    }

    @Test
    void shouldFetchGeneralMarketNews() {
        // Test that fetchGeneralMarketNews returns events
        List<MarketEvent> events = adapter.fetchGeneralMarketNews(oneDayAgo, now);

        assertThat(events).isNotNull();
        // Events may be empty if no r/worldnews posts meet criteria (upvotes + financial keywords)
        // but the method should not throw exceptions
    }

    @Test
    void shouldCreateNewsEventsNotRedditPosts() {
        // Fetch news and verify all events have EventType.NEWS
        List<MarketEvent> events = adapter.fetchGeneralMarketNews(oneDayAgo, now);

        assertThat(events).isNotNull();

        // If we got events, they should all be NEWS type
        if (!events.isEmpty()) {
            assertThat(events)
                .allMatch(event -> event.eventType() == EventType.NEWS,
                    "All r/worldnews events should be EventType.NEWS");
        }
    }

    @Test
    void shouldIncludeSourceAsRedditWorldnews() {
        // Fetch news and verify source is "reddit-worldnews"
        List<MarketEvent> events = adapter.fetchGeneralMarketNews(oneDayAgo, now);

        assertThat(events).isNotNull();

        // If we got events, they should have correct source
        if (!events.isEmpty()) {
            assertThat(events)
                .allMatch(event -> event.source().equals("reddit-worldnews"),
                    "All events should have source 'reddit-worldnews'");
        }
    }

    @Test
    void shouldIncludeUrlAndDomainInRawData() {
        // Fetch news and verify rawData includes url and domain
        List<MarketEvent> events = adapter.fetchGeneralMarketNews(oneDayAgo, now);

        assertThat(events).isNotNull();

        // If we got events, they should have url and domain
        if (!events.isEmpty()) {
            for (MarketEvent event : events) {
                assertThat(event.rawData()).containsKey("url");
                assertThat(event.rawData()).containsKey("domain");
                assertThat(event.rawData()).containsKey("score");
                assertThat(event.rawData()).containsKey("title");

                // Verify upvotes meet minimum threshold (default 500)
                Integer score = (Integer) event.rawData().get("score");
                assertThat(score).isGreaterThanOrEqualTo(500);
            }
        }
    }

    @Test
    void shouldHavePrimarySymbolOrMarket() {
        // Fetch news and verify symbol is either a ticker or "MARKET"
        List<MarketEvent> events = adapter.fetchGeneralMarketNews(oneDayAgo, now);

        assertThat(events).isNotNull();

        // If we got events, they should have valid symbols
        if (!events.isEmpty()) {
            for (MarketEvent event : events) {
                assertThat(event.symbol()).isNotNull();
                assertThat(event.symbol()).isNotEmpty();
                // Symbol should be uppercase ticker or "MARKET"
                assertThat(event.symbol()).matches("[A-Z]+");
            }
        }
    }

    @Test
    void shouldThrowUnsupportedOperationForSymbolPrice() {
        assertThatThrownBy(() -> adapter.fetchSymbolPriceData(btcSymbol, oneDayAgo, now))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("price data");
    }

    @Test
    void shouldThrowUnsupportedOperationForEconomicIndicators() {
        assertThatThrownBy(() -> adapter.fetchEconomicIndicators(oneDayAgo, now))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("economic indicators");
    }
}
