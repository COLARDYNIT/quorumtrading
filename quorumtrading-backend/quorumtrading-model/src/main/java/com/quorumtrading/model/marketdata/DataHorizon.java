package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GranularityBucket;
import com.quorumtrading.model.enums.HorizonSource;
import com.quorumtrading.model.valueobject.Symbol;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable value object: the measured earliest-available date a provider has
 * for a (symbol, dataType, granularity bucket), with provenance and a probe
 * timestamp.
 *
 * <p>This is a <b>floor detector</b>, not a coverage map — it answers "what is
 * the earliest reachable date?", not "is every interior day present?". Interior
 * holes remain the job of coverage analysis.</p>
 *
 * <p>{@code earliestAvailable} is {@code Optional.empty()} when a probe ran but
 * found no data (so we don't re-probe constantly), so {@link #hasData()} must be
 * consulted to distinguish "known floor" from "known to have no data / unknown".</p>
 */
public record DataHorizon(
        String provider,
        Symbol symbol,
        EventType dataType,
        GranularityBucket bucket,
        Optional<LocalDate> earliestAvailable,
        Instant probedAt,
        HorizonSource source
) {

    public DataHorizon {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be null/blank");
        }
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(earliestAvailable, "earliestAvailable Optional must not be null");
        Objects.requireNonNull(probedAt, "probedAt must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }

    /**
     * @return true if a concrete earliest-available date is known.
     */
    public boolean hasData() {
        return earliestAvailable.isPresent();
    }

    /**
     * @return true if {@code date} is reachable: either no floor is known, or
     *         {@code date} is on/after the measured earliest-available date.
     */
    public boolean isReachable(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return earliestAvailable
                .map(earliest -> !date.isBefore(earliest))
                .orElse(true);
    }

    /**
     * @return true if this horizon is older than {@code ttl} relative to {@code now}.
     */
    public boolean isStale(Duration ttl, Instant now) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return probedAt.plus(ttl).isBefore(now);
    }
}
