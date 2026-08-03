package com.quorumtrading.model.enums;

/**
 * Provenance of a {@link com.quorumtrading.model.marketdata.DataHorizon}'s
 * earliest-available value. Every horizon traces to one of these — we measure,
 * never assume.
 */
public enum HorizonSource {
    /** Measured by the generic month-window probe against the provider. */
    PROBE,

    /** Lowered because a real fetch returned data earlier than the cached floor. */
    OBSERVED_FETCH,

    /** Raised because a runtime fetch found no data at/above the cached floor. */
    NO_DATA_FEEDBACK,

    /** Seeded from the local DB's earliest known price date (historical proof). */
    DB_SEED
}
