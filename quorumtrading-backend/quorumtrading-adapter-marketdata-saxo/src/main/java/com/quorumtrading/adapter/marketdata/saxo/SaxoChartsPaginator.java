package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.adapter.marketdata.saxo.client.SaxoOpenApiClient;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.ChartsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Walks Saxo's {@code GET /chart/v1/charts} backward to stitch a window of historical OHLC samples
 * for {@code MD3-charts-backfill}.
 *
 * <h2>Pagination contract (SAXO_ANSWERS §3.4)</h2>
 * <ul>
 *   <li><b>Count cap.</b> Max {@value #MAX_COUNT} datapoints per request.</li>
 *   <li><b>Backward walk.</b> {@code Mode=UpTo} returns the samples <em>up to and ending at</em> the
 *       {@code Time} anchor. We start at the requested window end and, after each page, set the next
 *       anchor to just before the oldest sample returned, then request again — stitching older and
 *       older windows.</li>
 *   <li><b>Stop condition.</b> Stop when a page returns no rows, or when it returns nothing strictly
 *       older than what we already hold (the array has stopped growing) — that empirically locates
 *       the earliest available sample. We never assume a data floor (MEMORY: "no assumed data
 *       floors").</li>
 * </ul>
 *
 * <h2>LIVE-only, entitlement-safe</h2>
 * <p>Saxo SIM serves no equity charts (SAXO_ANSWERS headline) — this path is LIVE-only. An empty
 * {@code Data} array (no entitlement / no history) yields an empty stitch, never an error; the
 * caller continues with other tickers.</p>
 *
 * <p><b>Isolation.</b> Lives in the market-data module and talks only to its own
 * {@link SaxoOpenApiClient}; it imports nothing from the broker-saxo module (adapters must not depend
 * on each other). Issues Saxo REST calls, so the per-account bearer must be active when invoked
 * (the adapter pins the account before each call).</p>
 */
@ApplicationScoped
public class SaxoChartsPaginator {

    private static final Logger LOG = Logger.getLogger(SaxoChartsPaginator.class);

    /** Saxo's hard cap on datapoints per Charts request. */
    static final int MAX_COUNT = 1200;

    /** {@code Mode} value for the backward (oldest-ward) walk. */
    static final String MODE_UP_TO = "UpTo";

    /**
     * Safety bound on the number of pages we will request for a single stitch/probe, so a
     * misbehaving stub or a venue that never stops returning data cannot loop unbounded. At 1200
     * daily samples/page this still spans well over a century of trading days.
     */
    static final int MAX_PAGES = 200;

    @Inject
    @RestClient
    SaxoOpenApiClient openApiClient;

    /**
     * Stitches all OHLC samples for {@code (uic, assetType)} at {@code horizon} from the venue's
     * earliest available point up to {@code windowEnd}, walking backward in {@link #MAX_COUNT}-sized
     * pages.
     *
     * @param uic        Saxo instrument id
     * @param assetType  Saxo asset type string (e.g. {@code Stock}, {@code Etf})
     * @param horizon    the chart horizon (minutes per sample + backward step)
     * @param windowEnd  the most recent instant of interest (the first {@code UpTo} anchor)
     * @return samples ordered oldest→newest, de-duplicated by timestamp; empty if none/NoAccess
     */
    public List<ChartsResponse.Sample> fetchBackward(long uic, String assetType,
                                                     SaxoChartsHorizon horizon, Instant windowEnd) {
        if (horizon == null) {
            return Collections.emptyList();
        }
        Instant anchor = windowEnd != null ? windowEnd : Instant.now();

        // Oldest-first accumulator keyed by sample time so re-overlapping pages can't double-count.
        Map<Instant, ChartsResponse.Sample> byTime = new LinkedHashMap<>();
        Instant oldestSeen = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            ChartsResponse response = requestPage(uic, assetType, horizon, anchor);
            List<ChartsResponse.Sample> rows = response == null ? null : response.data();
            if (rows == null || rows.isEmpty()) {
                LOG.debugf("Saxo charts: page %d for Uic=%d returned no samples — stitch complete.",
                        page, uic);
                break;
            }

            Instant pageOldest = null;
            int added = 0;
            for (ChartsResponse.Sample sample : rows) {
                Instant t = parseSampleTime(sample.time());
                if (t == null) {
                    continue;
                }
                if (byTime.putIfAbsent(t, sample) == null) {
                    added++;
                }
                if (pageOldest == null || t.isBefore(pageOldest)) {
                    pageOldest = t;
                }
            }

            // Array stopped growing: nothing new, or no sample older than what we already hold.
            boolean grewOlder = oldestSeen == null || (pageOldest != null && pageOldest.isBefore(oldestSeen));
            if (added == 0 || !grewOlder) {
                LOG.debugf("Saxo charts: page %d for Uic=%d added %d sample(s), oldest=%s (no older "
                        + "data) — earliest reached.", page, uic, added, pageOldest);
                break;
            }
            oldestSeen = pageOldest;

            // Next backward anchor: one horizon-step before the oldest sample so UpTo excludes it.
            anchor = pageOldest.minus(horizon.step());
        }

        List<ChartsResponse.Sample> ordered = new ArrayList<>(byTime.values());
        ordered.sort((a, b) -> {
            Instant ta = parseSampleTime(a.time());
            Instant tb = parseSampleTime(b.time());
            if (ta == null || tb == null) {
                return 0;
            }
            return ta.compareTo(tb);
        });
        LOG.debugf("Saxo charts: stitched %d sample(s) for Uic=%d (horizon=%dmin) up to %s.",
                ordered.size(), uic, horizon.horizonMinutes(), anchor);
        return ordered;
    }

    /**
     * Empirically probes the earliest available sample timestamp for {@code (uic, assetType)} at
     * {@code horizon} by stitching backward and taking the oldest sample. Returns empty when the
     * venue returns nothing (no history / no entitlement). The earliest is <em>measured</em>, never
     * assumed.
     *
     * @param uic       Saxo instrument id
     * @param assetType Saxo asset type string
     * @param horizon   the chart horizon
     * @return the earliest available sample instant, or empty if none
     */
    public Optional<Instant> earliestAvailable(long uic, String assetType, SaxoChartsHorizon horizon) {
        List<ChartsResponse.Sample> samples = fetchBackward(uic, assetType, horizon, Instant.now());
        if (samples.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(parseSampleTime(samples.get(0).time()));
    }

    private ChartsResponse requestPage(long uic, String assetType, SaxoChartsHorizon horizon, Instant anchor) {
        try {
            return openApiClient.getCharts(
                    uic,
                    assetType,
                    horizon.horizonMinutes(),
                    MAX_COUNT,
                    MODE_UP_TO,
                    DateTimeFormatter.ISO_INSTANT.format(anchor));
        } catch (RuntimeException e) {
            LOG.warnf("Saxo charts: request failed for Uic=%d (assetType=%s, horizon=%dmin, anchor=%s): %s",
                    uic, assetType, horizon.horizonMinutes(), anchor, e.getMessage());
            return null;
        }
    }

    /** Parses an ISO-8601 sample timestamp, returning {@code null} on absence/parse failure. */
    static Instant parseSampleTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(time);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
