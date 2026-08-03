package com.quorumtrading.adapter.marketdata.reddit;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GapDetectionTest {

    private final RedditAdapter adapter = new RedditAdapter();

    @Test
    void shouldDetectGapWhenNoCachedData() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-10T00:00:00Z");

        List<TimeRange> gaps = adapter.detectGaps(List.of(), start, end);

        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0)).isEqualTo(new TimeRange(start, end));
    }

    @Test
    void shouldDetectGapBeforeFirstEvent() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-10T00:00:00Z");
        List<MarketEvent> cached = List.of(
            createEvent(Instant.parse("2025-01-05T12:00:00Z"))
        );

        List<TimeRange> gaps = adapter.detectGaps(cached, start, end);

        assertThat(gaps).hasSize(2);
        assertThat(gaps.get(0).start()).isEqualTo(start);
        assertThat(gaps.get(0).end()).isEqualTo(Instant.parse("2025-01-05T12:00:00Z"));
    }

    @Test
    void shouldDetectGapBetweenEvents() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-10T00:00:00Z");
        List<MarketEvent> cached = List.of(
            createEvent(Instant.parse("2025-01-02T12:00:00Z")),
            createEvent(Instant.parse("2025-01-08T12:00:00Z")) // 6 days gap
        );

        List<TimeRange> gaps = adapter.detectGaps(cached, start, end);

        assertThat(gaps).hasSize(3);
        // Gap before first, gap between events (> 24h), gap after last
        assertThat(gaps.get(1).start()).isEqualTo(Instant.parse("2025-01-02T12:00:00Z"));
        assertThat(gaps.get(1).end()).isEqualTo(Instant.parse("2025-01-08T12:00:00Z"));
    }

    @Test
    void shouldNotDetectGapWhenEventsCloseTogether() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-03T00:00:00Z");
        List<MarketEvent> cached = List.of(
            createEvent(start.plus(1, ChronoUnit.HOURS)),
            createEvent(start.plus(3, ChronoUnit.HOURS)),
            createEvent(start.plus(6, ChronoUnit.HOURS))
        );

        List<TimeRange> gaps = adapter.detectGaps(cached, start, end);

        // Only gaps at start and end, no gaps between events (all < 24h apart)
        assertThat(gaps).hasSize(2);
    }

    private MarketEvent createEvent(Instant timestamp) {
        return new MarketEvent(
            timestamp,
            EventType.SOCIAL,
            "reddit-test",
            "BTC",
            java.util.Map.of("test", "data")
        );
    }
}
