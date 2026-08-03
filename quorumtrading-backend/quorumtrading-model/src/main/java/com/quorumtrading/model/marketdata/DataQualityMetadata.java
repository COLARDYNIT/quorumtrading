package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.valueobject.TimeRange;

import java.util.List;

/**
 * Tracks data quality metrics for market data fetches.
 * Used to flag simulations with incomplete historical data.
 */
public record DataQualityMetadata(
    double cacheHitRatio,        // 0.0 to 1.0 (percentage of data from cache vs API)
    double coveragePercentage,   // 0.0 to 1.0 (percentage of requested time range with data)
    List<TimeRange> missingRanges, // Time ranges where data could not be fetched
    boolean isPartialData        // True if coverage < 90%
) {

    public DataQualityMetadata {
        if (cacheHitRatio < 0.0 || cacheHitRatio > 1.0) {
            throw new IllegalArgumentException("cacheHitRatio must be between 0.0 and 1.0");
        }
        if (coveragePercentage < 0.0 || coveragePercentage > 1.0) {
            throw new IllegalArgumentException("coveragePercentage must be between 0.0 and 1.0");
        }
        if (missingRanges == null) {
            throw new IllegalArgumentException("missingRanges must not be null");
        }
    }

    /**
     * Generate human-readable summary of data quality.
     */
    public String summary() {
        return String.format(
            "Cache hit: %.1f%%, Coverage: %.1f%%, Missing ranges: %d, Partial data: %b",
            cacheHitRatio * 100,
            coveragePercentage * 100,
            missingRanges.size(),
            isPartialData
        );
    }

    /**
     * Create metadata for perfect quality (100% coverage, 100% from cache).
     */
    public static DataQualityMetadata perfect() {
        return new DataQualityMetadata(1.0, 1.0, List.of(), false);
    }

    /**
     * Create metadata indicating no data available.
     */
    public static DataQualityMetadata empty() {
        return new DataQualityMetadata(0.0, 0.0, List.of(), true);
    }
}
