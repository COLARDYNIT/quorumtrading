package com.quorumtrading.adapter.marketdata.rss;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RSSParserTest {

    private final RSSParser parser = new RSSParser();

    @Test
    void shouldParseRSS2Feed() {
        // Given
        String rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Test Feed</title>
                    <item>
                        <title>Market Update</title>
                        <description>Stocks rise on positive news</description>
                        <link>https://example.com/news1</link>
                        <pubDate>Mon, 04 Nov 2024 10:00:00 GMT</pubDate>
                    </item>
                </channel>
            </rss>
            """;

        InputStream inputStream = new ByteArrayInputStream(rss.getBytes());

        // When
        List<Map<String, Object>> items = parser.parse(inputStream, "TestSource");

        // Then
        assertNotNull(items);
        assertEquals(1, items.size());

        Map<String, Object> item = items.get(0);
        assertEquals("Market Update", item.get("title"));
        assertEquals("Stocks rise on positive news", item.get("description"));
        assertEquals("https://example.com/news1", item.get("link"));
        assertEquals("TestSource", item.get("source"));
        assertNotNull(item.get("pubDate"));
    }

    @Test
    void shouldParseAtomFeed() {
        // Given
        String atom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Test Feed</title>
                <entry>
                    <title>Market Update</title>
                    <summary>Stocks rise on positive news</summary>
                    <link href="https://example.com/news1"/>
                    <updated>2024-11-04T10:00:00Z</updated>
                </entry>
            </feed>
            """;

        InputStream inputStream = new ByteArrayInputStream(atom.getBytes());

        // When
        List<Map<String, Object>> items = parser.parse(inputStream, "TestSource");

        // Then
        assertNotNull(items);
        assertEquals(1, items.size());

        Map<String, Object> item = items.get(0);
        assertEquals("Market Update", item.get("title"));
        assertEquals("Stocks rise on positive news", item.get("description"));
        assertEquals("https://example.com/news1", item.get("link"));
    }

    @Test
    void shouldHandleInvalidXML() {
        // Given
        String invalid = "not xml";
        InputStream inputStream = new ByteArrayInputStream(invalid.getBytes());

        // When
        List<Map<String, Object>> items = parser.parse(inputStream, "TestSource");

        // Then
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }
}
