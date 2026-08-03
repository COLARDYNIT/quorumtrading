package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataHorizonRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GranularityBucket;
import com.quorumtrading.model.enums.HorizonSource;
import com.quorumtrading.model.marketdata.DataHorizon;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cache + TTL + runtime-feedback layer over {@link DataHorizonProbeService}.
 *
 * <p>v1 operates on the {@link GranularityBucket#DAILY} bucket only.</p>
 *
 * <ul>
 *   <li>{@link #earliest}: cache hit &amp; fresh → return; miss/stale → probe + upsert.
 *       A probe that finds no data still upserts a no-data row so we don't re-probe constantly.</li>
 *   <li>{@link #recordNoData}: raises the floor (conservatively) from a runtime NO_DATA outcome.</li>
 *   <li>{@link #recordObservedFetch}: lowers the floor when a real fetch returns earlier data.</li>
 * </ul>
 */
public class DataHorizonService {

    private static final Logger LOG = Logger.getLogger(DataHorizonService.class);
    private static final GranularityBucket BUCKET = GranularityBucket.DAILY;
    private static final String PROBE_PROVIDER = "probe";

    private final MarketDataHorizonRepository repository;
    private final DataHorizonProbeService probeService;

    int ttlDays;

    public DataHorizonService(MarketDataHorizonRepository repository,
                              DataHorizonProbeService probeService,
                              int ttlDays) {
        this.repository = repository;
        this.probeService = probeService;
        this.ttlDays = ttlDays;
    }

    private Duration ttl() {
        return Duration.ofDays(ttlDays);
    }

    /**
     * Resolve the earliest available date for (symbol, type) at daily granularity.
     * Returns empty when no floor is known (no provider, transient error, or
     * a cached no-data row).
     */
    public Optional<LocalDate> earliest(Symbol symbol, EventType type) {
        Instant now = Instant.now();
        Optional<DataHorizon> cached = repository.find(providerKey(symbol), symbol, type, BUCKET);

        if (cached.isPresent() && !cached.get().isStale(ttl(), now)) {
            return cached.get().earliestAvailable();
        }

        // Miss or stale → probe and cache the outcome (data or no-data).
        Optional<LocalDate> probed = probeService.probeEarliest(symbol, type);
        DataHorizon horizon = new DataHorizon(
                providerKey(symbol), symbol, type, BUCKET, probed, now, HorizonSource.PROBE);
        repository.upsert(horizon);

        if (probed.isPresent()) {
            LOG.infof("Probed horizon for %s %s: earliest=%s", symbol.ticker(), type, probed.get());
        } else {
            LOG.infof("Probed horizon for %s %s: no data / unknown (cached to avoid re-probing)",
                    symbol.ticker(), type);
        }
        return probed;
    }

    /**
     * @return true if {@code date} is reachable for (symbol, type). Unknown floor
     *         is treated as reachable (do not block on absence of measurement).
     */
    public boolean isReachable(Symbol symbol, EventType type, LocalDate date) {
        return earliest(symbol, type)
                .map(earliest -> !date.isBefore(earliest))
                .orElse(true);
    }

    /**
     * Runtime feedback: a fetch over {@code range} found NO data. If the range's
     * start is at/after the cached earliest (or no floor is cached), raise the
     * floor to {@code range.end + 1 day}, but ONLY when that is later than the
     * current earliest. Conservative — never lowers an existing floor.
     */
    public void recordNoData(Symbol symbol, EventType type, TimeRange range) {
        Instant now = Instant.now();
        Optional<DataHorizon> cached = repository.find(providerKey(symbol), symbol, type, BUCKET);
        LocalDate candidate = range.getEnd().plusDays(1);

        Optional<LocalDate> currentEarliest = cached.flatMap(DataHorizon::earliestAvailable);

        // Only raise if the no-data window touches/precedes the known floor, AND the
        // candidate is strictly later than what we already believe.
        boolean rangeStartsAtOrAboveFloor = currentEarliest
                .map(earliest -> !range.getStart().isBefore(earliest))
                .orElse(true);
        boolean candidateIsLater = currentEarliest
                .map(candidate::isAfter)
                .orElse(true);

        if (rangeStartsAtOrAboveFloor && candidateIsLater) {
            DataHorizon raised = new DataHorizon(
                    providerKey(symbol), symbol, type, BUCKET,
                    Optional.of(candidate), now, HorizonSource.NO_DATA_FEEDBACK);
            repository.upsert(raised);
            LOG.infof("Raised horizon floor for %s %s to %s from NO_DATA feedback over %s",
                    symbol.ticker(), type, candidate, range);
        }
    }

    /**
     * Runtime feedback: a real fetch returned data at {@code observedDate}. Lower
     * the floor if that is earlier than the cached earliest (or no floor cached).
     */
    public void recordObservedFetch(Symbol symbol, EventType type, LocalDate observedDate) {
        Instant now = Instant.now();
        Optional<DataHorizon> cached = repository.find(providerKey(symbol), symbol, type, BUCKET);
        Optional<LocalDate> currentEarliest = cached.flatMap(DataHorizon::earliestAvailable);

        boolean shouldLower = currentEarliest
                .map(observedDate::isBefore)
                .orElse(true);

        if (shouldLower) {
            DataHorizon lowered = new DataHorizon(
                    providerKey(symbol), symbol, type, BUCKET,
                    Optional.of(observedDate), now, HorizonSource.OBSERVED_FETCH);
            repository.upsert(lowered);
            LOG.infof("Lowered horizon floor for %s %s to %s from observed fetch",
                    symbol.ticker(), type, observedDate);
        }
    }

    /**
     * Provider key for the cached horizon. v1 uses a synthetic {@code "probe"} key
     * since the generic probe is provider-agnostic; the PK column exists so a
     * future per-provider authoritative path can store under the real provider name.
     */
    private String providerKey(Symbol symbol) {
        return PROBE_PROVIDER;
    }
}
