package com.quorumtrading.adapter.marketdata.finnhub;

import com.quorumtrading.adapter.marketdata.finnhub.dto.FinnhubNewsResponse;
import com.quorumtrading.model.marketdata.MarketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinnhubNewsAdapterTest {

    @Mock
    private FinnhubRestClient restClient;

    private FinnhubNewsAdapter adapter;
    private String apiKey = "test-api-key";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new FinnhubNewsAdapter(restClient, apiKey, true);
    }

    @Test
    void shouldFetchMarketNews() {
        // Given
        Instant now = Instant.now();

        FinnhubNewsResponse newsItem = new FinnhubNewsResponse();
        newsItem.setHeadline("Fed raises interest rates");
        newsItem.setSummary("Federal Reserve announces rate hike");
        newsItem.setDatetime(now.getEpochSecond());
        newsItem.setSource("Reuters");
        newsItem.setUrl("https://example.com/news");

        when(restClient.getMarketNews(eq("general"), eq(apiKey)))
            .thenReturn(Arrays.asList(newsItem));

        // When
        List<MarketEvent> events = adapter.fetchLatestData();

        // Then
        assertNotNull(events);
        assertEquals(1, events.size());

        MarketEvent event = events.get(0);
        assertEquals("Fed raises interest rates", event.rawData().get("headline"));
        assertEquals("Reuters", event.source());
        assertEquals("MARKET", event.symbol());
    }

    @Test
    void shouldFilterOutEventsOutsideDateRange() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        FinnhubNewsResponse oldNews = new FinnhubNewsResponse();
        oldNews.setHeadline("Old news");
        oldNews.setDatetime(twoDaysAgo.getEpochSecond());

        FinnhubNewsResponse recentNews = new FinnhubNewsResponse();
        recentNews.setHeadline("Recent news");
        recentNews.setDatetime(now.getEpochSecond());

        when(restClient.getMarketNews(anyString(), anyString()))
            .thenReturn(Arrays.asList(oldNews, recentNews));

        // When - use internal method directly for testing date filtering
        List<MarketEvent> events = adapter.fetchMarketNews(yesterday, now);

        // Then
        assertEquals(1, events.size());
        assertEquals("Recent news", events.get(0).rawData().get("headline"));
    }

    @Test
    void shouldReturnEmptyListWhenDisabled() {
        // Given
        FinnhubNewsAdapter disabledAdapter = new FinnhubNewsAdapter(restClient, apiKey, false);

        // When
        List<MarketEvent> events = disabledAdapter.fetchLatestData();

        // Then
        assertTrue(events.isEmpty());
        verify(restClient, never()).getMarketNews(anyString(), anyString());
    }

    @Test
    void shouldSkipWithoutCallingApiWhenKeyIsPlaceholder() {
        // Given: the repo-wide 'not-configured' placeholder must count as "no key", not be sent to Finnhub
        FinnhubNewsAdapter placeholderAdapter = new FinnhubNewsAdapter(restClient, "not-configured", true);

        // When
        List<MarketEvent> events = placeholderAdapter.fetchMarketNews(
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

        // Then
        assertTrue(events.isEmpty());
        verifyNoInteractions(restClient);
    }

    @Test
    void shouldFetchCompanyNewsInDateRange() {
        // Given
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        FinnhubNewsResponse companyNews = new FinnhubNewsResponse();
        companyNews.setHeadline("AAPL launches new product");
        companyNews.setSummary("Apple announces new device");
        companyNews.setDatetime(Instant.parse("2024-06-15T10:00:00Z").getEpochSecond());
        companyNews.setSource("Bloomberg");

        when(restClient.getCompanyNews(eq("AAPL"), eq("2024-06-01"), eq("2024-06-30"), eq(apiKey)))
            .thenReturn(List.of(companyNews));
        when(restClient.getMarketNews(eq("general"), eq(apiKey)))
            .thenReturn(Collections.emptyList());

        // When
        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"), start, end);

        // Then
        assertEquals(1, events.size());
        MarketEvent event = events.get(0);
        assertEquals("AAPL launches new product", event.rawData().get("headline"));
        assertEquals("AAPL", event.symbol());
        assertEquals("Bloomberg", event.source());

        verify(restClient).getCompanyNews("AAPL", "2024-06-01", "2024-06-30", apiKey);
    }

    @Test
    void shouldFetchCompanyNewsForMultipleTickers() {
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        FinnhubNewsResponse aaplNews = new FinnhubNewsResponse();
        aaplNews.setHeadline("AAPL news");
        aaplNews.setDatetime(Instant.parse("2024-06-10T10:00:00Z").getEpochSecond());
        aaplNews.setSource("Reuters");

        FinnhubNewsResponse tslaNews = new FinnhubNewsResponse();
        tslaNews.setHeadline("TSLA news");
        tslaNews.setDatetime(Instant.parse("2024-06-20T10:00:00Z").getEpochSecond());
        tslaNews.setSource("Bloomberg");

        when(restClient.getCompanyNews(eq("AAPL"), anyString(), anyString(), eq(apiKey)))
            .thenReturn(List.of(aaplNews));
        when(restClient.getCompanyNews(eq("TSLA"), anyString(), anyString(), eq(apiKey)))
            .thenReturn(List.of(tslaNews));
        when(restClient.getMarketNews(eq("general"), eq(apiKey)))
            .thenReturn(Collections.emptyList());

        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL", "TSLA"), start, end);

        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> "AAPL".equals(e.symbol())));
        assertTrue(events.stream().anyMatch(e -> "TSLA".equals(e.symbol())));
    }

    @Test
    void shouldIncludeGeneralMarketNewsInDateRange() {
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        FinnhubNewsResponse marketNews = new FinnhubNewsResponse();
        marketNews.setHeadline("Fed holds rates steady");
        marketNews.setDatetime(Instant.parse("2024-06-15T14:00:00Z").getEpochSecond());
        marketNews.setSource("Reuters");

        when(restClient.getCompanyNews(eq("AAPL"), anyString(), anyString(), eq(apiKey)))
            .thenReturn(Collections.emptyList());
        when(restClient.getMarketNews(eq("general"), eq(apiKey)))
            .thenReturn(List.of(marketNews));

        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"), start, end);

        assertEquals(1, events.size());
        assertEquals("MARKET", events.get(0).symbol());
        assertEquals("Fed holds rates steady", events.get(0).rawData().get("headline"));
    }

    @Test
    void shouldContinueWhenCompanyNewsFails() {
        Instant start = Instant.parse("2024-06-01T00:00:00Z");
        Instant end = Instant.parse("2024-06-30T23:59:59Z");

        FinnhubNewsResponse marketNews = new FinnhubNewsResponse();
        marketNews.setHeadline("Market update");
        marketNews.setDatetime(Instant.parse("2024-06-15T14:00:00Z").getEpochSecond());
        marketNews.setSource("Reuters");

        when(restClient.getCompanyNews(eq("AAPL"), anyString(), anyString(), eq(apiKey)))
            .thenThrow(new RuntimeException("API error"));
        when(restClient.getMarketNews(eq("general"), eq(apiKey)))
            .thenReturn(List.of(marketNews));

        // Should not throw, should still return general market news
        List<MarketEvent> events = adapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"), start, end);

        assertEquals(1, events.size());
        assertEquals("MARKET", events.get(0).symbol());
    }

    @Test
    void shouldReturnEmptyFromFetchDataRangeWhenDisabled() {
        FinnhubNewsAdapter disabledAdapter = new FinnhubNewsAdapter(restClient, apiKey, false);

        List<MarketEvent> events = disabledAdapter.fetchDataInRange(
            Collections.emptySet(), Set.of("AAPL"),
            Instant.parse("2024-06-01T00:00:00Z"),
            Instant.parse("2024-06-30T23:59:59Z"));

        assertTrue(events.isEmpty());
        verifyNoInteractions(restClient);
    }

    @Test
    void shouldReturnEmptyListAndKeepRunningWhenTheFetchFails() {
        // Given — the provider is down, or the key is revoked
        when(restClient.getMarketNews(eq("general"), eq(apiKey)))
            .thenThrow(new RuntimeException("HTTP 401 from Finnhub"));

        // When
        List<MarketEvent> events = adapter.fetchLatestData();

        // Then — news degrades gracefully, it never breaks the collection pass
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void shouldSkipWithoutCallingFinnhubWhenKeyIsTheNotConfiguredPlaceholder() {
        // Given — a fresh keyless install boots with the literal placeholder
        adapter = new FinnhubNewsAdapter(restClient, "not-configured", true);

        // When
        List<MarketEvent> events = adapter.fetchLatestData();

        // Then
        assertTrue(events.isEmpty());
        verifyNoInteractions(restClient);
    }

    @Test
    void missingKeyIsReportedOnceNotOnEveryCollectionTick() {
        // Prod logged this WARN every 5 minutes (8,644 lines in 30 days) for a fact that cannot
        // change without a redeploy. The first call reports it; later calls stay quiet.
        FinnhubNewsAdapter keyless = new FinnhubNewsAdapter(restClient, "", true);
        assertFalse(keyless.missingKeyReported(), "nothing reported before the adapter is used");

        keyless.fetchMarketNews(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
        assertTrue(keyless.missingKeyReported(), "the first call reports the missing key");

        List<MarketEvent> later = keyless.fetchDataInRange(
                java.util.Set.of(), java.util.Set.of("AAPL"), Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
        assertTrue(later.isEmpty(), "still skips collection without a key");
        verifyNoInteractions(restClient);
    }
}
