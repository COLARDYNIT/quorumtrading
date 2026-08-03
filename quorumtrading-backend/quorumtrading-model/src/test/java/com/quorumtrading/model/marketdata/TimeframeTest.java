package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimeframeTest {

    @Test
    void shouldHaveCorrectDurations() {
        assertEquals(Duration.ofHours(24), Timeframe.LAST_24H.getDuration());
        assertEquals(Duration.ofDays(7), Timeframe.LAST_7D.getDuration());
        assertEquals(Duration.ofDays(30), Timeframe.LAST_30D.getDuration());
        assertEquals(Duration.ofDays(90), Timeframe.LAST_90D.getDuration());
    }

    @Test
    void shouldCalculateCorrectStartInstant() {
        Instant now = Instant.parse("2025-01-15T12:00:00Z");

        Instant oneDayAgo = Timeframe.LAST_24H.calculateStart(now);
        assertEquals(Instant.parse("2025-01-14T12:00:00Z"), oneDayAgo);

        Instant sevenDaysAgo = Timeframe.LAST_7D.calculateStart(now);
        assertEquals(Instant.parse("2025-01-08T12:00:00Z"), sevenDaysAgo);

        Instant thirtyDaysAgo = Timeframe.LAST_30D.calculateStart(now);
        assertEquals(Instant.parse("2024-12-16T12:00:00Z"), thirtyDaysAgo);

        Instant ninetyDaysAgo = Timeframe.LAST_90D.calculateStart(now);
        assertEquals(Instant.parse("2024-10-17T12:00:00Z"), ninetyDaysAgo);
    }

    @Test
    void shouldCreateTimeRangeCorrectly() {
        Instant now = Instant.parse("2025-01-15T12:00:00Z");

        TimeRange range24h = Timeframe.LAST_24H.toTimeRange(now);
        assertEquals(Instant.parse("2025-01-14T12:00:00Z"), range24h.start());
        assertEquals(now, range24h.end());

        TimeRange range7d = Timeframe.LAST_7D.toTimeRange(now);
        assertEquals(Instant.parse("2025-01-08T12:00:00Z"), range7d.start());
        assertEquals(now, range7d.end());
    }

    @Test
    void shouldHaveHumanReadableDescriptions() {
        assertEquals("Last 24 hours", Timeframe.LAST_24H.getDescription());
        assertEquals("Last 7 days", Timeframe.LAST_7D.getDescription());
        assertEquals("Last 30 days", Timeframe.LAST_30D.getDescription());
        assertEquals("Last 90 days", Timeframe.LAST_90D.getDescription());
    }

    @Test
    void shouldFormatToString() {
        assertEquals("Last 24 hours", Timeframe.LAST_24H.toString());
        assertEquals("Last 7 days", Timeframe.LAST_7D.toString());
    }

    @Test
    void shouldHaveAllExpectedValues() {
        Timeframe[] values = Timeframe.values();
        assertEquals(4, values.length);
        assertEquals(Timeframe.LAST_24H, values[0]);
        assertEquals(Timeframe.LAST_7D, values[1]);
        assertEquals(Timeframe.LAST_30D, values[2]);
        assertEquals(Timeframe.LAST_90D, values[3]);
    }
}
