package com.quorumtrading.adapter.marketdata.alpaca;

import com.quorumtrading.adapter.marketdata.alpaca.client.AlpacaMarketDataClient;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.AlpacaNewsArticle;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.AlpacaNewsResponse;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlpacaMarketDataAdapterNewsTest {

    @Mock
    private AlpacaMarketDataClient marketDataClient;

    @InjectMocks
    private AlpacaMarketDataAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        setField("enabled", true);
        setField("stockFeed", "iex");
        setField("configuredCryptoSymbols", "");
        setField("configuredStockSymbols", "");
        // Real credentials so the fetchDataInRange path runs (it now gates on credentialsConfigured()).
        setField("apiKey", "test-key");
        setField("apiSecret", "test-secret");
    }

    @Test
    void shouldSkipBackfillWhenCredentialsNotConfigured() throws Exception {
        // Zero-key standalone: Alpaca must skip cleanly (no 401 ERROR) and let keyless providers serve.
        setField("apiKey", "not-configured");
        setField("apiSecret", "not-configured");

        List<MarketEvent> events = adapter.fetchDataInRange(
            Set.of("BTC"), Collections.emptySet(),
            Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"));

        assertThat(events).isEmpty();
        verifyNoInteractions(marketDataClient);
    }

    @Test
    void shouldIncludeNewsFeatureInSupportedFeatures() {
        assertThat(adapter.getSupportedFeatures()).contains(MarketDataFeature.NEWS);
    }

    @Test
    void shouldFetchNewsInDateRange() {
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        AlpacaNewsResponse response = buildNewsResponse(
            List.of(
                buildArticle(1L, "AAPL beats earnings", "Strong Q2", "Benzinga", "2024-06-15T10:00:00Z", List.of("AAPL")),
                buildArticle(2L, "Fed holds rates", "No change expected", "Reuters", "2024-06-20T14:00:00Z", List.of("SPY", "QQQ"))
            ),
            null // no next page
        );

        // Mock: stock bars return empty (we only care about news in this test)
        when(marketDataClient.getStockBars(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), isNull(), anyString()))
            .thenReturn(null);
        when(marketDataClient.getNews(anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), isNull()))
            .thenReturn(response);

        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"), start, end);

        List<MarketEvent> newsEvents = events.stream()
            .filter(e -> e.eventType() == EventType.NEWS)
            .toList();

        assertThat(newsEvents).hasSize(2);
        assertThat(newsEvents.get(0).rawData().get("headline")).isEqualTo("AAPL beats earnings");
        assertThat(newsEvents.get(0).symbol()).isEqualTo("AAPL");
        assertThat(newsEvents.get(0).source()).isEqualTo("Benzinga");
        assertThat(newsEvents.get(1).rawData().get("headline")).isEqualTo("Fed holds rates");
    }

    @Test
    void shouldPaginateNewsResults() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-31T23:59:59Z");

        AlpacaNewsResponse page1 = buildNewsResponse(
            List.of(buildArticle(1L, "Page 1 news", "Summary", "Benzinga", "2024-01-10T10:00:00Z", List.of("TSLA"))),
            "page2token"
        );
        AlpacaNewsResponse page2 = buildNewsResponse(
            List.of(buildArticle(2L, "Page 2 news", "Summary", "Benzinga", "2024-01-20T10:00:00Z", List.of("TSLA"))),
            null
        );

        when(marketDataClient.getStockBars(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), isNull(), anyString()))
            .thenReturn(null);
        when(marketDataClient.getNews(anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), isNull()))
            .thenReturn(page1);
        when(marketDataClient.getNews(anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), eq("page2token")))
            .thenReturn(page2);

        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("TSLA"), start, end);

        List<MarketEvent> newsEvents = events.stream()
            .filter(e -> e.eventType() == EventType.NEWS)
            .toList();

        assertThat(newsEvents).hasSize(2);
        assertThat(newsEvents.get(0).rawData().get("headline")).isEqualTo("Page 1 news");
        assertThat(newsEvents.get(1).rawData().get("headline")).isEqualTo("Page 2 news");
    }

    @Test
    void shouldAssignMatchingTickerSymbolToNewsEvent() {
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        AlpacaNewsResponse response = buildNewsResponse(
            List.of(buildArticle(1L, "Multi-ticker news", "Summary", "Benzinga", "2024-06-15T10:00:00Z", List.of("MSFT", "AAPL", "GOOG"))),
            null
        );

        when(marketDataClient.getStockBars(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), isNull(), anyString()))
            .thenReturn(null);
        when(marketDataClient.getNews(anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), isNull()))
            .thenReturn(response);

        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"), start, end);

        List<MarketEvent> newsEvents = events.stream()
            .filter(e -> e.eventType() == EventType.NEWS)
            .toList();

        assertThat(newsEvents).hasSize(1);
        // Should match AAPL since that's what we requested
        assertThat(newsEvents.get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldContinueWithPriceDataWhenNewsFetchFails() {
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        when(marketDataClient.getStockBars(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), isNull(), anyString()))
            .thenReturn(null);
        when(marketDataClient.getNews(anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), isNull()))
            .thenThrow(new RuntimeException("API error"));

        // Should not throw — news failure is gracefully handled
        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"), start, end);

        assertThat(events).isEmpty(); // No price data either (mocked null)
    }

    @Test
    void shouldSkipArticlesWithoutHeadline() {
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        AlpacaNewsArticle noHeadline = buildArticle(1L, null, "Summary", "Benzinga", "2024-06-15T10:00:00Z", List.of("AAPL"));
        AlpacaNewsArticle withHeadline = buildArticle(2L, "Valid headline", "Summary", "Benzinga", "2024-06-16T10:00:00Z", List.of("AAPL"));

        AlpacaNewsResponse response = buildNewsResponse(List.of(noHeadline, withHeadline), null);

        when(marketDataClient.getStockBars(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), isNull(), anyString()))
            .thenReturn(null);
        when(marketDataClient.getNews(anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), isNull()))
            .thenReturn(response);

        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"), start, end);

        List<MarketEvent> newsEvents = events.stream()
            .filter(e -> e.eventType() == EventType.NEWS)
            .toList();

        assertThat(newsEvents).hasSize(1);
        assertThat(newsEvents.get(0).rawData().get("headline")).isEqualTo("Valid headline");
    }

    // --- Helper methods ---

    private AlpacaNewsArticle buildArticle(Long id, String headline, String summary, String source,
                                            String createdAt, List<String> symbols) {
        AlpacaNewsArticle article = new AlpacaNewsArticle();
        article.setId(id);
        article.setHeadline(headline);
        article.setSummary(summary);
        article.setSource(source);
        article.setCreatedAt(createdAt);
        article.setUpdatedAt(createdAt);
        article.setSymbols(symbols);
        article.setUrl("https://example.com/news/" + id);
        return article;
    }

    private AlpacaNewsResponse buildNewsResponse(List<AlpacaNewsArticle> articles, String nextPageToken) {
        AlpacaNewsResponse response = new AlpacaNewsResponse();
        response.setNews(articles);
        response.setNextPageToken(nextPageToken);
        return response;
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = AlpacaMarketDataAdapter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(adapter, value);
    }
}
