package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualityMetadataTest {

    @Test
    void shouldCreateMetadataWithFullCoverage() {
        DataQualityMetadata metadata = new DataQualityMetadata(
            0.85, // 85% cache hit
            1.0,  // 100% coverage
            List.of(),
            false // not partial
        );

        assertThat(metadata.cacheHitRatio()).isEqualTo(0.85);
        assertThat(metadata.coveragePercentage()).isEqualTo(1.0);
        assertThat(metadata.missingRanges()).isEmpty();
        assertThat(metadata.isPartialData()).isFalse();
    }

    @Test
    void shouldFlagAsPartialWhenCoverageBelowThreshold() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-10T00:00:00Z");
        List<TimeRange> gaps = List.of(new TimeRange(start, end));

        DataQualityMetadata metadata = new DataQualityMetadata(
            0.0,  // 0% cache hit (all from API)
            0.7,  // 70% coverage (below 90% threshold)
            gaps,
            true  // partial data
        );

        assertThat(metadata.isPartialData()).isTrue();
        assertThat(metadata.missingRanges()).hasSize(1);
    }

    @Test
    void shouldProvideHumanReadableSummary() {
        DataQualityMetadata metadata = new DataQualityMetadata(
            0.65,
            0.88,
            List.of(),
            true
        );

        String summary = metadata.summary();

        assertThat(summary).contains("Cache hit: 65.0%");
        assertThat(summary).contains("Coverage: 88.0%");
        assertThat(summary).contains("Partial data: true");
    }
}
