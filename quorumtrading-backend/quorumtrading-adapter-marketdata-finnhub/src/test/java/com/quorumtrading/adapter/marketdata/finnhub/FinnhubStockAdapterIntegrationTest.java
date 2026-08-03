package com.quorumtrading.adapter.marketdata.finnhub;

import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for FinnhubStockAdapter.
 *
 * Tests real API calls to Finnhub (requires FINNHUB_API_KEY environment variable).
 * Uses free tier API calls.
 *
 * NOTE: These tests are disabled by default because they require a valid API key.
 * To enable them, remove the @Disabled annotation and set FINNHUB_API_KEY environment variable.
 */
@QuarkusTest
@org.junit.jupiter.api.Disabled("Integration test requires valid FINNHUB_API_KEY environment variable")
class FinnhubStockAdapterIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(FinnhubStockAdapterIntegrationTest.class);

    @Inject
    FinnhubStockAdapter finnhubAdapter;

    @Test
    @DisplayName("shouldFetchRealStockPrice_WhenValidSymbolProvided")
    void shouldFetchRealStockPrice_WhenValidSymbolProvided() {
        // Arrange
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        Instant now = Instant.now();

        // Act
        List<MarketEvent> events = finnhubAdapter.fetchSymbolPriceData(symbol, now, now);

        // Assert
        assertThat(events)
            .as("Should return at least one price event")
            .isNotEmpty();

        MarketEvent event = events.get(0);
        assertThat(event.symbol()).isEqualTo("AAPL");
        assertThat(event.eventType().toString()).isEqualTo("PRICE");
        assertThat(event.source()).isEqualTo("finnhub");

        // Verify rawData contains expected fields
        assertThat(event.rawData())
            .containsKeys("price", "currency", "change", "changePercent");

        // Verify price is reasonable (AAPL typically trades above $100)
        Object priceObj = event.rawData().get("price");
        assertThat(priceObj).isNotNull();
        double price = (priceObj instanceof Number) ? ((Number) priceObj).doubleValue() : Double.parseDouble(priceObj.toString());
        assertThat(price)
            .as("AAPL price should be > 0")
            .isGreaterThan(0.0);

        log.info("Finnhub API Response: symbol={}, price=${}, change={}, changePercent={}, high=${}, low=${}, timestamp={}",
            event.symbol(), price, event.rawData().get("change"), event.rawData().get("changePercent"),
            event.rawData().get("high"), event.rawData().get("low"), event.timestamp());
    }

    @Test
    @DisplayName("shouldFetchMultipleStocks_WhenFetchLatestDataCalled")
    void shouldFetchMultipleStocks_WhenFetchLatestDataCalled() {
        // Act
        List<MarketEvent> events = finnhubAdapter.fetchLatestData();

        // Assert - should fetch all configured symbols (default: AAPL, MSFT, GOOGL, AMZN, TSLA)
        assertThat(events)
            .as("Should fetch prices for all configured symbols")
            .hasSizeGreaterThanOrEqualTo(1); // At least 1 symbol should succeed

        log.info("Fetched {} stock prices", events.size());
        events.forEach(event ->
            log.info("  {}: ${}", event.symbol(), event.rawData().get("price"))
        );
    }

    @Test
    @DisplayName("shouldReturnProviderName")
    void shouldReturnProviderName() {
        // Act
        String providerName = finnhubAdapter.getProviderName();

        // Assert
        assertThat(providerName).isEqualTo("finnhub");
    }
}
