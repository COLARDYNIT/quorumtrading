package com.quorumtrading.adapter.marketdata.rss;

import com.quorumtrading.model.marketdata.MarketEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RSSFeedAdapterTest {

    @Test
    void shouldReturnEmptyListWhenDisabled() {
        // Given
        List<String> feedUrls = List.of("https://example.com/feed.xml");
        RSSFeedAdapter adapter = new RSSFeedAdapter(feedUrls, false);

        // When
        List<MarketEvent> events = adapter.fetchLatestData();

        // Then
        assertTrue(events.isEmpty());
    }

    @Test
    void shouldReturnProviderName() {
        // Given
        List<String> feedUrls = List.of("https://example.com/feed.xml");
        RSSFeedAdapter adapter = new RSSFeedAdapter(feedUrls, true);

        // When
        String name = adapter.getProviderName();

        // Then
        assertEquals("RSS Feeds", name);
    }
}
