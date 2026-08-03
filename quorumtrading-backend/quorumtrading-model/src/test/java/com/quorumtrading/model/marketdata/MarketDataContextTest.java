package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataContextTest {

    private Instant analysisTime;
    private Symbol btc;
    private Symbol eth;

    @BeforeEach
    void setUp() {
        analysisTime = Instant.parse("2025-01-15T12:00:00Z");
        // MarketDataContext.from() resolves each ticker to its real asset class, so a lookup
        // must use the same type. It used to key everything as STOCK regardless, which meant a
        // crypto position could never be found by a correctly-typed Symbol.
        btc = new Symbol("BTC", InvestmentType.fromTicker("BTC"));
        eth = new Symbol("ETH", InvestmentType.fromTicker("ETH"));
    }

    @Test
    void shouldCreateEmptyContext() {
        MarketDataContext context = MarketDataContext.empty(analysisTime);

        assertTrue(context.isEmpty());
        assertEquals(0, context.getAvailableSymbols().size());
        assertEquals(analysisTime, context.getAnalysisTimestamp());
    }

    @Test
    void shouldThrowExceptionForNullTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> {
            MarketDataContext.empty(null);
        });
    }

    @Test
    void shouldCreateContextFromRawData() {
        MarketEvent event1 = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");
        MarketEvent event2 = createPriceEvent("BTC", analysisTime.minusSeconds(1800), "51000.00");
        MarketEvent event3 = createPriceEvent("ETH", analysisTime.minusSeconds(3600), "3000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event1, event2, event3),
            Timeframe.LAST_7D, List.of(event1, event3)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC", "ETH"),
            analysisTime
        );

        assertFalse(context.isEmpty());
        assertEquals(2, context.getAvailableSymbols().size());
        assertTrue(context.hasDataForSymbol(btc));
        assertTrue(context.hasDataForSymbol(eth));
    }

    @Test
    void shouldKeepSixCharacterCryptoPairs() {
        // A 1-5 uppercase-letter filter silently erased concatenated pairs like SOLUSD from the
        // context entirely, so agents were handed no price data at all for those symbols and
        // had to reason without them.
        MarketEvent event = createPriceEvent("SOLUSD", analysisTime.minusSeconds(1800), "180.00");

        MarketDataContext context = MarketDataContext.from(
            Map.of(Timeframe.LAST_24H, List.of(event)),
            Set.of("SOLUSD"),
            analysisTime
        );

        assertFalse(context.isEmpty(), "SOLUSD must not be dropped from the market data context");
        Symbol solusd = new Symbol("SOLUSD", InvestmentType.fromTicker("SOLUSD"));
        assertTrue(context.hasDataForSymbol(solusd));
    }

    @Test
    void shouldGetEventsForSymbolAndTimeframe() {
        MarketEvent event1 = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");
        MarketEvent event2 = createPriceEvent("BTC", analysisTime.minusSeconds(1800), "51000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event1, event2)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        List<MarketEvent> events = context.getEventsForSymbol(btc, Timeframe.LAST_24H);
        assertEquals(2, events.size());
    }

    @Test
    void shouldReturnEmptyListForNonExistentSymbol() {
        MarketDataContext context = MarketDataContext.empty(analysisTime);

        List<MarketEvent> events = context.getEventsForSymbol(btc, Timeframe.LAST_24H);
        assertTrue(events.isEmpty());
    }

    @Test
    void shouldGetAllEventsForSymbol() {
        MarketEvent event1 = createPriceEvent("BTC", analysisTime.minusSeconds(86400), "50000.00");
        MarketEvent event2 = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "51000.00");
        MarketEvent event3 = createPriceEvent("BTC", analysisTime.minusSeconds(1800), "52000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event2, event3),
            Timeframe.LAST_7D, List.of(event1)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        List<MarketEvent> allEvents = context.getAllEventsForSymbol(btc);
        assertEquals(3, allEvents.size());
        // Should be sorted by timestamp
        assertEquals(event1, allEvents.get(0));
        assertEquals(event2, allEvents.get(1));
        assertEquals(event3, allEvents.get(2));
    }

    @Test
    void shouldFilterPriceEvents() {
        MarketEvent priceEvent = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");
        MarketEvent newsEvent = createNewsEvent("BTC", analysisTime.minusSeconds(1800), "Bitcoin hits new high");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(priceEvent, newsEvent)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        List<MarketEvent> priceEvents = context.getPriceEvents(btc, Timeframe.LAST_24H);
        assertEquals(1, priceEvents.size());
        assertEquals(EventType.PRICE, priceEvents.get(0).eventType());
    }

    @Test
    void shouldGetNewsEvents() {
        MarketEvent priceEvent = createPriceEvent("BTC", analysisTime.minusSeconds(7200), "50000.00");
        MarketEvent newsEvent1 = createNewsEvent("BTC", analysisTime.minusSeconds(3600), "Bitcoin news 1");
        MarketEvent newsEvent2 = createNewsEvent("BTC", analysisTime.minusSeconds(1800), "Bitcoin news 2");
        MarketEvent redditEvent = createRedditEvent("BTC", analysisTime.minusSeconds(900), "Reddit post");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(priceEvent, newsEvent1, newsEvent2, redditEvent)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        List<MarketEvent> newsEvents = context.getNewsEvents(btc, 10);
        assertEquals(3, newsEvents.size()); // newsEvent1, newsEvent2, redditEvent
        // Should be sorted newest first
        assertEquals(redditEvent, newsEvents.get(0));
        assertEquals(newsEvent2, newsEvents.get(1));
    }

    @Test
    void shouldLimitNewsEvents() {
        MarketEvent news1 = createNewsEvent("BTC", analysisTime.minusSeconds(3600), "News 1");
        MarketEvent news2 = createNewsEvent("BTC", analysisTime.minusSeconds(1800), "News 2");
        MarketEvent news3 = createNewsEvent("BTC", analysisTime.minusSeconds(900), "News 3");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(news1, news2, news3)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        List<MarketEvent> newsEvents = context.getNewsEvents(btc, 2);
        assertEquals(2, newsEvents.size());
    }

    @Test
    void shouldFormatNewsHeadlines() {
        MarketEvent newsEvent = createNewsEvent("BTC", analysisTime.minusSeconds(3600), "Bitcoin hits new high");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(newsEvent)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        List<String> headlines = context.getNewsHeadlines(btc, 10);
        assertEquals(1, headlines.size());
        assertTrue(headlines.get(0).contains("Bitcoin hits new high"));
    }

    @Test
    void shouldCalculatePriceStatistics() {
        MarketEvent event1 = createPriceEvent("BTC", analysisTime.minusSeconds(7200), "50000.00");
        MarketEvent event2 = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "52000.00");
        MarketEvent event3 = createPriceEvent("BTC", analysisTime.minusSeconds(1800), "51000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event1, event2, event3)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        PriceStatistics stats = context.calculatePriceStatistics(btc, Timeframe.LAST_24H);

        assertTrue(stats.hasData());
        assertEquals(new BigDecimal("50000.00"), stats.earliestPrice());
        assertEquals(new BigDecimal("51000.00"), stats.latestPrice());
        assertEquals(new BigDecimal("50000.00"), stats.minPrice());
        assertEquals(new BigDecimal("52000.00"), stats.maxPrice());
    }

    @Test
    void shouldGetLatestPrice() {
        MarketEvent oldEvent = createPriceEvent("BTC", analysisTime.minusSeconds(86400), "45000.00");
        MarketEvent recentEvent = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");
        MarketEvent latestEvent = createPriceEvent("BTC", analysisTime.minusSeconds(1800), "51000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(recentEvent, latestEvent),
            Timeframe.LAST_7D, List.of(oldEvent)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        Optional<BigDecimal> latestPrice = context.getLatestPrice(btc);
        assertTrue(latestPrice.isPresent());
        assertEquals(new BigDecimal("51000.00"), latestPrice.get());
    }

    @Test
    void shouldReturnEmptyOptionalForNoPrice() {
        MarketDataContext context = MarketDataContext.empty(analysisTime);

        Optional<BigDecimal> latestPrice = context.getLatestPrice(btc);
        assertFalse(latestPrice.isPresent());
    }

    @Test
    void shouldGetEventCount() {
        MarketEvent event1 = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");
        MarketEvent event2 = createPriceEvent("BTC", analysisTime.minusSeconds(1800), "51000.00");
        MarketEvent event3 = createNewsEvent("BTC", analysisTime.minusSeconds(900), "News");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event1, event2, event3)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        assertEquals(3, context.getEventCount(btc));
    }

    @Test
    void shouldGetEventTypeCounts() {
        MarketEvent priceEvent1 = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");
        MarketEvent priceEvent2 = createPriceEvent("BTC", analysisTime.minusSeconds(1800), "51000.00");
        MarketEvent newsEvent = createNewsEvent("BTC", analysisTime.minusSeconds(900), "News");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(priceEvent1, priceEvent2, newsEvent)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        Map<EventType, Long> typeCounts = context.getEventTypeCounts(btc, Timeframe.LAST_24H);
        assertEquals(2L, typeCounts.get(EventType.PRICE));
        assertEquals(1L, typeCounts.get(EventType.NEWS));
    }

    @Test
    void shouldBeImmutable() {
        MarketEvent event = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC"),
            analysisTime
        );

        // Getting events should return an immutable list
        List<MarketEvent> events = context.getEventsForSymbol(btc, Timeframe.LAST_24H);
        assertThrows(UnsupportedOperationException.class, () -> {
            events.add(createPriceEvent("BTC", analysisTime, "52000.00"));
        });
    }

    @Test
    void shouldFilterInvalidSymbols() {
        MarketEvent event = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event)
        );

        // Include invalid symbols in the set
        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC", "invalid-ticker", "", "TOOLONG"),
            analysisTime
        );

        // Only valid symbol should be included
        assertEquals(1, context.getAvailableSymbols().size());
        assertTrue(context.hasDataForSymbol(btc));
    }

    @Test
    void shouldAcceptMARKETSymbolForGeneralMarketNews() {
        Symbol marketSymbol = new Symbol("MARKET", InvestmentType.fromTicker("MARKET"));
        MarketEvent marketNewsEvent = createNewsEvent("MARKET", analysisTime.minusSeconds(3600), "Fed announces rate hike");
        MarketEvent btcNewsEvent = createNewsEvent("BTC", analysisTime.minusSeconds(1800), "Bitcoin news");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(marketNewsEvent, btcNewsEvent)
        );

        // Include both BTC and MARKET symbols
        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC", "MARKET"),
            analysisTime
        );

        // Both symbols should be accepted
        assertEquals(2, context.getAvailableSymbols().size());
        assertTrue(context.hasDataForSymbol(btc));
        assertTrue(context.hasDataForSymbol(marketSymbol));

        // Market news should be accessible
        List<MarketEvent> marketEvents = context.getNewsEvents(marketSymbol, 10);
        assertEquals(1, marketEvents.size());
        assertEquals("Fed announces rate hike", marketEvents.get(0).rawData().get("headline"));
    }

    @Test
    void shouldCombineTickerSpecificAndMarketNews() {
        Symbol marketSymbol = new Symbol("MARKET", InvestmentType.fromTicker("MARKET"));

        MarketEvent btcPrice = createPriceEvent("BTC", analysisTime.minusSeconds(7200), "50000.00");
        MarketEvent btcNews = createNewsEvent("BTC", analysisTime.minusSeconds(3600), "Bitcoin ETF approved");
        MarketEvent marketNews1 = createNewsEvent("MARKET", analysisTime.minusSeconds(5400), "Fed rate decision pending");
        MarketEvent marketNews2 = createNewsEvent("MARKET", analysisTime.minusSeconds(1800), "Stock market volatility rises");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(btcPrice, btcNews, marketNews1, marketNews2)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC", "MARKET"),
            analysisTime
        );

        // BTC should have 3 events (1 price + 1 news)
        assertEquals(2, context.getEventCount(btc));

        // MARKET should have 2 events (2 news)
        assertEquals(2, context.getEventCount(marketSymbol));

        // BTC news should only include BTC-specific news
        List<MarketEvent> btcNewsEvents = context.getNewsEvents(btc, 10);
        assertEquals(1, btcNewsEvents.size());
        assertEquals("Bitcoin ETF approved", btcNewsEvents.get(0).rawData().get("headline"));

        // MARKET news should only include market-wide news
        List<MarketEvent> marketNewsEvents = context.getNewsEvents(marketSymbol, 10);
        assertEquals(2, marketNewsEvents.size());

        // Market news should be sorted newest first
        assertEquals("Stock market volatility rises", marketNewsEvents.get(0).rawData().get("headline"));
        assertEquals("Fed rate decision pending", marketNewsEvents.get(1).rawData().get("headline"));
    }

    @Test
    void shouldGetNewsHeadlinesForMARKETSymbol() {
        Symbol marketSymbol = new Symbol("MARKET", InvestmentType.fromTicker("MARKET"));
        MarketEvent marketNews = createNewsEvent("MARKET", analysisTime.minusSeconds(3600), "Global economic outlook improves");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(marketNews)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("MARKET"),
            analysisTime
        );

        List<String> headlines = context.getNewsHeadlines(marketSymbol, 10);
        assertEquals(1, headlines.size());
        assertTrue(headlines.get(0).contains("Global economic outlook improves"));
    }

    @Test
    void shouldHandleEmptyMARKETNewsGracefully() {
        Symbol marketSymbol = new Symbol("MARKET", InvestmentType.fromTicker("MARKET"));
        MarketEvent btcEvent = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(btcEvent)
        );

        // Include MARKET in symbols but no MARKET events exist
        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC", "MARKET"),
            analysisTime
        );

        // MARKET should not be in available symbols (no data for it)
        assertEquals(1, context.getAvailableSymbols().size());
        assertTrue(context.hasDataForSymbol(btc));
        assertFalse(context.hasDataForSymbol(marketSymbol));

        // Getting MARKET news should return empty list
        List<MarketEvent> marketNews = context.getNewsEvents(marketSymbol, 10);
        assertEquals(0, marketNews.size());
    }

    @Test
    void shouldFormatToStringCorrectly() {
        MarketEvent event1 = createPriceEvent("BTC", analysisTime.minusSeconds(3600), "50000.00");
        MarketEvent event2 = createPriceEvent("ETH", analysisTime.minusSeconds(1800), "3000.00");

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, List.of(event1, event2)
        );

        MarketDataContext context = MarketDataContext.from(
            eventsByTimeframe,
            Set.of("BTC", "ETH"),
            analysisTime
        );

        String str = context.toString();
        assertTrue(str.contains("symbols=2"));
        assertTrue(str.contains("events=2"));
    }

    // Helper methods

    private MarketEvent createPriceEvent(String symbol, Instant timestamp, String price) {
        return new MarketEvent(
            timestamp,
            EventType.PRICE,
            "test-source",
            symbol,
            Map.of("price", price)
        );
    }

    private MarketEvent createNewsEvent(String symbol, Instant timestamp, String headline) {
        return new MarketEvent(
            timestamp,
            EventType.NEWS,
            "test-news",
            symbol,
            Map.of("headline", headline)
        );
    }

    private MarketEvent createRedditEvent(String symbol, Instant timestamp, String title) {
        return new MarketEvent(
            timestamp,
            EventType.SOCIAL,
            "reddit",
            symbol,
            Map.of("title", title)
        );
    }
}
