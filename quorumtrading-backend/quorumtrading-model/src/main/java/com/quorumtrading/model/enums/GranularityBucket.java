package com.quorumtrading.model.enums;

/**
 * Coarse granularity bucket for data-horizon measurement.
 *
 * <p>A provider's earliest-available date can differ between daily and intraday
 * feeds (intraday history is usually shorter). v1 only probes {@link #DAILY};
 * {@link #INTRADAY} is part of the horizon primary key so it can be added later
 * without a schema change.</p>
 */
public enum GranularityBucket {
    DAILY,
    INTRADAY
}
