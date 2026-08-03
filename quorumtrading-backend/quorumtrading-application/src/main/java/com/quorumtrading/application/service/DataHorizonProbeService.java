package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.exception.MarketDataFetchException;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.Symbol;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Measures (never assumes) the earliest month a provider has data for a
 * (symbol, dataType) at daily granularity, using a generic month-window probe.
 *
 * <p>Each probe asks "does a whole-month window contain &ge;1 event of this type?".
 * A whole-month window is immune to weekend/holiday false-negatives. The algorithm
 * exponentially gallops backwards to bracket the floor, then binary-searches to
 * month precision — O(log range) provider calls (~12-18 for a 25-year history).</p>
 *
 * <p>Truthfulness guarantees:</p>
 * <ul>
 *   <li>An empty 200 response means "no data in that month" (moves the search).</li>
 *   <li>A retryable {@link MarketDataFetchException} (rate-limit/transient) ABORTS to
 *       {@link Optional#empty()} — never records a false floor.</li>
 *   <li>A non-retryable provider error also aborts to empty.</li>
 *   <li>Rate-limit budget is respected via {@link RateLimitTracker}; if exhausted, abort to empty.</li>
 * </ul>
 */
public class DataHorizonProbeService {

    private static final Logger LOG = Logger.getLogger(DataHorizonProbeService.class);

    /** Coarse search lower bounds — pure search boundaries, NOT claimed horizons. */
    private static final YearMonth CRYPTO_FLOOR = YearMonth.of(2009, 1);
    private static final YearMonth STOCK_FLOOR = YearMonth.of(1990, 1);

    /** How many months to step back from "now" looking for the first month with data. */
    private static final int ANCHOR_LOOKBACK_STEPS = 6;

    private final List<MarketDataPort> marketDataPorts;
    private final RateLimitTracker rateLimitTracker;

    public DataHorizonProbeService(List<MarketDataPort> marketDataPorts, RateLimitTracker rateLimitTracker) {
        this.marketDataPorts = marketDataPorts;
        this.rateLimitTracker = rateLimitTracker;
    }

    /**
     * Signals that the probe must abort without recording a floor (transient
     * error, non-retryable error, or rate-limit exhaustion).
     */
    private static final class ProbeAbortedException extends RuntimeException {
        ProbeAbortedException(String message) {
            super(message);
        }
    }

    /**
     * Probe the earliest available date for a symbol/type at daily granularity.
     *
     * @return the first day of the earliest month with data, or empty if unknown
     *         (no provider, transient error, or genuinely no data near now).
     */
    public Optional<LocalDate> probeEarliest(Symbol symbol, EventType type) {
        List<MarketDataPort> providers = findProviders(symbol, type);
        if (providers.isEmpty()) {
            LOG.infof("No provider supports probing %s %s — horizon unknown", symbol.ticker(), type);
            return Optional.empty();
        }
        MarketDataPort provider = providers.get(0);

        // Search lower bound is the coarse hard floor ONLY. We deliberately do NOT
        // clamp it to what we've already ingested: the whole point is to measure the
        // PROVIDER's true earliest, which may be earlier than our DB. Anchoring the
        // search at "now" (below) also keeps us honest under rolling-window feeds,
        // where the provider's current earliest can be LATER than a date we once stored.
        YearMonth floor = symbol.type() == InvestmentType.CRYPTO ? CRYPTO_FLOOR : STOCK_FLOOR;

        YearMonth nowMonth = YearMonth.from(LocalDate.now(ZoneOffset.UTC));

        try {
            return search(provider, symbol, type, floor, nowMonth);
        } catch (ProbeAbortedException e) {
            LOG.infof("Probe aborted for %s %s: %s — horizon left unknown",
                    symbol.ticker(), type, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Exponential search down + binary search for the earliest month with data.
     */
    private Optional<LocalDate> search(MarketDataPort provider, Symbol symbol, EventType type,
                                       YearMonth floor, YearMonth nowMonth) {
        // 1. Anchor: find a month WITH data at/near now. Some feeds lag by a few
        //    days, so step back if the current month is empty.
        YearMonth withData = null;
        YearMonth anchor = nowMonth;
        for (int step = 0; step <= ANCHOR_LOOKBACK_STEPS && !anchor.isBefore(floor); step++) {
            if (hasDataInMonth(provider, symbol, type, anchor)) {
                withData = anchor;
                break;
            }
            anchor = anchor.minusMonths(1);
        }
        if (withData == null) {
            // No data anywhere near now → either no data at all, or floor itself.
            // Check the hard floor once before giving up.
            if (hasDataInMonth(provider, symbol, type, floor)) {
                return Optional.of(floor.atDay(1));
            }
            LOG.infof("No data found near now for %s %s — horizon unknown", symbol.ticker(), type);
            return Optional.empty();
        }

        // 2. Gallop backwards by 1,2,4,8,... months to find a month WITHOUT data
        //    (the lower bracket), or hit the hard floor (floor is the earliest).
        YearMonth without = null;
        long galloping = 1;
        YearMonth probe = withData.minusMonths(galloping);
        while (!probe.isBefore(floor)) {
            if (hasDataInMonth(provider, symbol, type, probe)) {
                withData = probe; // still has data → push the upper bracket down
                galloping *= 2;
                probe = withData.minusMonths(galloping);
            } else {
                without = probe; // found the lower bracket
                break;
            }
        }

        if (without == null) {
            // Galloped past the hard floor with data all the way down.
            // The floor month is the earliest reachable.
            return Optional.of(floor.atDay(1));
        }

        // 3. Binary search in (without, withData] for the earliest month with data.
        //    Invariant: 'without' has NO data, 'withData' HAS data.
        while (monthsBetween(without, withData) > 1) {
            YearMonth mid = without.plusMonths(monthsBetween(without, withData) / 2);
            if (hasDataInMonth(provider, symbol, type, mid)) {
                withData = mid;
            } else {
                without = mid;
            }
        }

        return Optional.of(withData.atDay(1));
    }

    private long monthsBetween(YearMonth a, YearMonth b) {
        return java.time.temporal.ChronoUnit.MONTHS.between(a, b);
    }

    /**
     * One probe: does the provider return &ge;1 event of {@code type} for the whole
     * calendar month {@code ym} at daily granularity?
     *
     * @throws ProbeAbortedException on rate-limit exhaustion or any provider error
     */
    private boolean hasDataInMonth(MarketDataPort provider, Symbol symbol, EventType type, YearMonth ym) {
        RateLimit rateLimit = provider.getRateLimit();
        if (rateLimitTracker.wouldExceedLimit(provider.getProviderName(), rateLimit)) {
            throw new ProbeAbortedException("rate limit budget exhausted for " + provider.getProviderName());
        }

        // Whole-month window: [first day 00:00 UTC, first day of next month 00:00 UTC).
        java.time.Instant from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        java.time.Instant to = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        java.util.Set<String> cryptoTickers;
        java.util.Set<String> stockTickers;
        if (symbol.type() == InvestmentType.CRYPTO) {
            cryptoTickers = java.util.Set.of(symbol.ticker());
            stockTickers = java.util.Set.of();
        } else {
            cryptoTickers = java.util.Set.of();
            stockTickers = java.util.Set.of(symbol.ticker());
        }

        try {
            List<MarketEvent> events = provider.fetchDataInRange(
                    cryptoTickers, stockTickers, from, to, Granularity.ONE_DAY);
            rateLimitTracker.recordCall(provider.getProviderName());

            boolean hasData = events.stream().anyMatch(e -> e.eventType() == type);
            LOG.debugf("Probe %s %s %s: %s", symbol.ticker(), type, ym, hasData ? "DATA" : "empty");
            return hasData;
        } catch (MarketDataFetchException e) {
            rateLimitTracker.recordCall(provider.getProviderName());
            // Retryable (rate-limit/transient) OR non-retryable: both abort to empty.
            // We never record a false floor from an error response.
            throw new ProbeAbortedException(
                    (e.isRetryable() ? "retryable" : "non-retryable")
                            + " provider error: " + e.getMessage());
        } catch (Exception e) {
            rateLimitTracker.recordCall(provider.getProviderName());
            throw new ProbeAbortedException("provider error: " + e.getMessage());
        }
    }

    /**
     * Select providers that can answer the probe, reusing BackfillExecutor's
     * feature-matching: BACKFILL + asset feature (CRYPTO/STOCK) + data feature
     * (CANDLES for PRICE, NEWS, SOCIAL_MEDIA).
     *
     * <p>For a European-venue symbol the result is reordered so an instrument-covering
     * provider (e.g. Saxo) is probed first ({@code probeEarliest} uses {@code get(0)}),
     * matching where the EU name actually trades. US/crypto symbols are returned in the
     * original feature-matched order — see {@link #preferInstrumentCoveringProviders}.</p>
     */
    private List<MarketDataPort> findProviders(Symbol symbol, EventType type) {
        if (marketDataPorts.isEmpty()) {
            return List.of();
        }
        MarketDataFeature assetFeature = switch (symbol.type()) {
            case CRYPTO -> MarketDataFeature.CRYPTO;
            case STOCK -> MarketDataFeature.STOCK;
        };
        MarketDataFeature dataFeature = switch (type) {
            case PRICE -> MarketDataFeature.CANDLES;
            case NEWS -> MarketDataFeature.NEWS;
            case SOCIAL -> MarketDataFeature.SOCIAL_MEDIA;
        };

        List<MarketDataPort> matching = marketDataPorts.stream()
                .filter(p -> p.hasFeature(MarketDataFeature.BACKFILL))
                .filter(p -> p.hasFeature(assetFeature))
                .filter(p -> p.hasFeature(dataFeature))
                .toList();

        return preferInstrumentCoveringProviders(matching, symbol);
    }

    /**
     * EU-instrument probe preference (additive, mock-safe). Identical contract to
     * {@code BackfillExecutor.preferInstrumentCoveringProviders}: for a European-venue
     * symbol, stable-partition instrument-covering providers (Saxo) to the front; for a
     * null/non-European symbol return the list unchanged and never call
     * {@code coversInstrument}. Keeps US/crypto probe behaviour byte-identical.
     */
    private List<MarketDataPort> preferInstrumentCoveringProviders(List<MarketDataPort> providers, Symbol symbol) {
        if (symbol == null || !symbol.isEuropean() || providers.size() < 2) {
            return providers;
        }
        // Venue-native = advertises EU_INSTRUMENT AND covers this instrument (Saxo). The
        // EU_INSTRUMENT gate is what keeps a US-default provider — whose coversInstrument() returns
        // the interface default true — from looking "covering" and stealing the preference.
        List<MarketDataPort> venueNative = new java.util.ArrayList<>();
        List<MarketDataPort> others = new java.util.ArrayList<>();
        for (MarketDataPort provider : providers) {
            if (provider.hasFeature(MarketDataFeature.EU_INSTRUMENT) && provider.coversInstrument(symbol)) {
                venueNative.add(provider);
            } else {
                others.add(provider);
            }
        }
        if (venueNative.isEmpty() || others.isEmpty()) {
            return providers;
        }
        List<MarketDataPort> ordered = new java.util.ArrayList<>(providers.size());
        ordered.addAll(venueNative);
        ordered.addAll(others);
        return ordered;
    }
}
