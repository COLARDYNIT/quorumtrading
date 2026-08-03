package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.NewsEventJpaEntity;
import com.quorumtrading.adapter.persistence.entity.PriceBarJpaEntity;
import com.quorumtrading.adapter.persistence.entity.SocialEventJpaEntity;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MarketEventRepositoryDualWriteTest {

    private static final String TEST_SYMBOL = "DWTST";

    @Inject
    MarketEventRepositoryImpl repository;

    @BeforeEach
    @Transactional
    void setup() {
        PriceBarJpaEntity.delete("symbol = ?1", TEST_SYMBOL);
        NewsEventJpaEntity.delete("symbol = ?1", TEST_SYMBOL);
        SocialEventJpaEntity.delete("symbol = ?1", TEST_SYMBOL);
    }

    @Test
    void shouldSavePriceEventToPriceBars() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("open", 100.50);
        rawData.put("high", 105.75);
        rawData.put("low", 99.25);
        rawData.put("close", 103.00);
        rawData.put("volume", 50000L);

        MarketEvent event = new MarketEvent(now, EventType.PRICE, "test-source", TEST_SYMBOL, rawData);
        MarketEvent saved = repository.save(event);

        assertNotNull(saved, "Save should succeed");

        List<PriceBarJpaEntity> priceBars = PriceBarJpaEntity.find("symbol = ?1", TEST_SYMBOL).list();
        assertEquals(1, priceBars.size(), "Should have 1 row in price_bars");

        PriceBarJpaEntity bar = priceBars.get(0);
        assertEquals(TEST_SYMBOL, bar.symbol);
        assertEquals(now, bar.timestamp);
        assertEquals("test-source", bar.source);
        assertEquals(0, BigDecimal.valueOf(103.00).compareTo(bar.close));
        assertNotNull(bar.contentHash);
    }

    @Test
    void latestQuoteShouldReplaceSameDayIntradayRowsButKeepDailyBar() {
        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);

        // Canonical daily bar at midnight (from backfill) — must survive.
        repository.save(priceEvent(dayStart, 100.0));
        // Two earlier intraday quote polls — must be replaced.
        repository.saveLatestPriceQuote(priceEvent(dayStart.plus(3, ChronoUnit.HOURS), 101.0));
        repository.saveLatestPriceQuote(priceEvent(dayStart.plus(4, ChronoUnit.HOURS), 102.0));
        // Latest poll.
        MarketEvent saved = repository.saveLatestPriceQuote(priceEvent(dayStart.plus(5, ChronoUnit.HOURS), 103.0));
        assertNotNull(saved);

        List<PriceBarJpaEntity> bars = PriceBarJpaEntity
            .find("symbol = ?1 ORDER BY timestamp", TEST_SYMBOL).list();
        assertEquals(2, bars.size(), "Should keep exactly the midnight daily bar + the latest quote");
        assertEquals(dayStart, bars.get(0).timestamp);
        assertEquals(dayStart.plus(5, ChronoUnit.HOURS), bars.get(1).timestamp);
        assertEquals(0, BigDecimal.valueOf(103.0).compareTo(bars.get(1).close));
    }

    private MarketEvent priceEvent(Instant timestamp, double close) {
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("open", close - 1);
        rawData.put("high", close + 1);
        rawData.put("low", close - 2);
        rawData.put("close", close);
        rawData.put("volume", 1000L);
        return new MarketEvent(timestamp, EventType.PRICE, "test-source", TEST_SYMBOL, rawData);
    }

    @Test
    void shouldSaveNewsEventToNewsEvents() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("headline", "Test headline");
        rawData.put("summary", "Test summary content");
        rawData.put("url", "https://example.com/test-article");
        rawData.put("category", "technology");

        MarketEvent event = new MarketEvent(now, EventType.NEWS, "Finnhub", TEST_SYMBOL, rawData);
        MarketEvent saved = repository.save(event);

        assertNotNull(saved, "Save should succeed");

        List<NewsEventJpaEntity> newsEvents = NewsEventJpaEntity.find("symbol = ?1", TEST_SYMBOL).list();
        assertEquals(1, newsEvents.size(), "Should have 1 row in news_events");

        NewsEventJpaEntity news = newsEvents.get(0);
        assertEquals(TEST_SYMBOL, news.symbol);
        assertEquals("Test headline", news.headline);
        assertEquals("https://example.com/test-article", news.url);
        assertEquals("technology", news.category);
        assertNotNull(news.contentHash);
    }

    @Test
    void shouldSaveSocialEventToSocialEvents() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("title", "Test Reddit post");
        rawData.put("author", "testuser");
        rawData.put("score", 42);
        rawData.put("num_comments", 15);
        rawData.put("permalink", "/r/test/comments/abc123/test_post");
        rawData.put("subreddit", "wallstreetbets");

        MarketEvent event = new MarketEvent(now, EventType.SOCIAL, "reddit-wsb", TEST_SYMBOL, rawData);
        MarketEvent saved = repository.save(event);

        assertNotNull(saved, "Save should succeed");

        List<SocialEventJpaEntity> socialEvents = SocialEventJpaEntity.find("symbol = ?1", TEST_SYMBOL).list();
        assertEquals(1, socialEvents.size(), "Should have 1 row in social_events");

        SocialEventJpaEntity social = socialEvents.get(0);
        assertEquals(TEST_SYMBOL, social.symbol);
        assertEquals("Test Reddit post", social.title);
        assertEquals("testuser", social.author);
        assertEquals(42, social.score);
        assertEquals(15, social.numComments);
        assertEquals("/r/test/comments/abc123/test_post", social.permalink);
        assertEquals("wallstreetbets", social.subreddit);
        assertNotNull(social.contentHash);
    }

    @Test
    void shouldSkipDuplicates() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("close", 103.00);

        MarketEvent event = new MarketEvent(now, EventType.PRICE, "test-source", TEST_SYMBOL, rawData);

        MarketEvent first = repository.save(event);
        assertNotNull(first, "First save should succeed");

        MarketEvent second = repository.save(event);
        assertNull(second, "Duplicate save should return null");

        long priceBarsCount = PriceBarJpaEntity.count("symbol = ?1", TEST_SYMBOL);
        assertEquals(1, priceBarsCount, "Should have exactly 1 row in price_bars");
    }
}
