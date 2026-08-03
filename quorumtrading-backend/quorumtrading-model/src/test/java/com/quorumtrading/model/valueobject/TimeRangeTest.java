package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeRangeTest {

    @Test
    void shouldCreateValidTimeRange() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-10T00:00:00Z");

        TimeRange range = new TimeRange(start, end);

        assertThat(range.start()).isEqualTo(start);
        assertThat(range.end()).isEqualTo(end);
    }

    @Test
    void shouldRejectEndBeforeStart() {
        Instant start = Instant.parse("2025-01-10T00:00:00Z");
        Instant end = Instant.parse("2025-01-01T00:00:00Z");

        assertThatThrownBy(() -> new TimeRange(start, end))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("end must be after start");
    }

    @Test
    void shouldCalculateDuration() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = start.plus(7, ChronoUnit.DAYS);

        TimeRange range = new TimeRange(start, end);

        assertThat(range.durationSeconds()).isEqualTo(7 * 24 * 60 * 60);
    }

    @Test
    void shouldCheckIfInstantIsWithinRange() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-10T00:00:00Z");
        TimeRange range = new TimeRange(start, end);

        assertThat(range.contains(Instant.parse("2025-01-05T12:00:00Z"))).isTrue();
        assertThat(range.contains(start)).isTrue();
        assertThat(range.contains(end)).isTrue();
        assertThat(range.contains(Instant.parse("2024-12-31T23:59:59Z"))).isFalse();
        assertThat(range.contains(Instant.parse("2025-01-11T00:00:01Z"))).isFalse();
    }
}
