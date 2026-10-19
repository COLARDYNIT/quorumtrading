package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.usecase.result.ClassifiedGap;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GapClassification;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.Symbol;
import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Enriches raw {@link MarketDataGap}s with a {@link GapClassification}, the
 * provider that would fill them, and an estimated record count.
 *
 * <p>Provider feature-matching mirrors {@code BackfillExecutor.findProvidersForGap}:
 * BACKFILL + asset feature (CRYPTO/STOCK) + data feature (CANDLES for PRICE, NEWS,
 * SOCIAL_MEDIA). Among the feature-matching providers, only a <em>usable</em> one
 * (enabled and credentialed) is named — a keyless install used to label crypto gaps
 * with "alpaca" (which would be skipped) while kraken/coinbase actually served them,
 * and news gaps with "Finnhub News" that silently no-opped.</p>
 *
 * <p>Classification precedence:</p>
 * <ol>
 *   <li>{@link GapClassification#NO_SOURCE} — no provider matches the features.</li>
 *   <li>{@link GapClassification#NO_CREDENTIALED_PROVIDER} — providers match, but none
 *       is enabled and credentialed. Checked before the horizon: a missing key must
 *       never masquerade as a permanent data floor.</li>
 *   <li>{@link GapClassification#FUTURE} — the range starts after today (UTC).</li>
 *   <li>{@link GapClassification#BEFORE_HORIZON} — symbol-specific and the whole gap
 *       precedes the symbol's measured provider coverage.</li>
 *   <li>{@link GapClassification#ACTIONABLE} — otherwise; fillable now.</li>
 * </ol>
 *
 * <p>A horizon lookup failure degrades to {@code ACTIONABLE} rather than throwing.</p>
 */
public class ClassifyGapsService {

    private static final Logger LOG = Logger.getLogger(ClassifyGapsService.class);

    private final List<MarketDataPort> marketDataPorts;
    private final DataHorizonService dataHorizonService;

    public ClassifyGapsService(List<MarketDataPort> marketDataPorts, DataHorizonService dataHorizonService) {
        this.marketDataPorts = marketDataPorts;
        this.dataHorizonService = dataHorizonService;
    }

    /**
     * Classify each gap independently. Order is preserved.
     */
    public List<ClassifiedGap> classify(List<MarketDataGap> gaps) {
        List<ClassifiedGap> result = new ArrayList<>(gaps.size());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (MarketDataGap gap : gaps) {
            List<MarketDataPort> matching = findMatchingProviders(gap);
            String provider = firstUsableProviderName(matching);
            int estimatedRecords = estimateRecords(gap);

            GapClassification classification;
            if (matching.isEmpty()) {
                classification = GapClassification.NO_SOURCE;
            } else if (provider == null) {
                // Providers could serve this gap, but none holds credentials (or is
                // switched on). Fixable by configuration — never a data floor.
                classification = GapClassification.NO_CREDENTIALED_PROVIDER;
            } else if (gap.period().getStart().isAfter(today)) {
                classification = GapClassification.FUTURE;
            } else if (isBeforeHorizon(gap)) {
                classification = GapClassification.BEFORE_HORIZON;
            } else {
                classification = GapClassification.ACTIONABLE;
            }

            result.add(new ClassifiedGap(gap, classification, provider, estimatedRecords));
        }

        return result;
    }

    /**
     * The whole gap is before the symbol's measured provider coverage when the
     * gap is symbol-specific and the gap's END is not reachable. A horizon
     * lookup failure degrades to "not before horizon" (so the gap stays
     * ACTIONABLE) — never throws.
     */
    private boolean isBeforeHorizon(MarketDataGap gap) {
        if (gap.isMarketWide()) {
            return false;
        }
        try {
            return !dataHorizonService.isReachable(gap.symbol(), gap.dataType(), gap.period().getEnd());
        } catch (RuntimeException e) {
            LOG.debugf("Horizon lookup failed for %s %s — treating gap as actionable: %s",
                gap.symbol().ticker(), gap.dataType(), e.getMessage());
            return false;
        }
    }

    /**
     * All providers whose features could serve this gap, regardless of whether they
     * are enabled or credentialed. Mirrors {@code BackfillExecutor.findProvidersForGap}
     * feature-matching.
     */
    private List<MarketDataPort> findMatchingProviders(MarketDataGap gap) {
        if (marketDataPorts.isEmpty()) {
            return List.of();
        }
        MarketDataFeature assetFeature = determineAssetFeature(gap);
        MarketDataFeature dataFeature = determineDataFeature(gap);

        return marketDataPorts.stream()
            .filter(p -> p.hasFeature(MarketDataFeature.BACKFILL))
            .filter(p -> assetFeature == null || p.hasFeature(assetFeature))
            .filter(p -> dataFeature == null || p.hasFeature(dataFeature))
            .toList();
    }

    /**
     * Name of the first provider that would <em>actually</em> be used — enabled and
     * credentialed — or {@code null} when none is. A provider that will be skipped
     * must never be named on a plan row.
     */
    private String firstUsableProviderName(List<MarketDataPort> matching) {
        return matching.stream()
            .filter(p -> p.isEnabled() && p.isConfigured())
            .map(MarketDataPort::getProviderName)
            .findFirst()
            .orElse(null);
    }

    private MarketDataFeature determineAssetFeature(MarketDataGap gap) {
        if (gap.symbol() == null) {
            return null; // market-wide (NEWS/SOCIAL)
        }
        return switch (gap.symbol().type()) {
            case CRYPTO -> MarketDataFeature.CRYPTO;
            case STOCK -> MarketDataFeature.STOCK;
        };
    }

    private MarketDataFeature determineDataFeature(MarketDataGap gap) {
        EventType dataType = gap.dataType();
        if (dataType == null) {
            return null;
        }
        return switch (dataType) {
            case PRICE -> MarketDataFeature.CANDLES;
            case NEWS -> MarketDataFeature.NEWS;
            case SOCIAL -> MarketDataFeature.SOCIAL_MEDIA;
        };
    }

    /**
     * Estimated row count for the gap range [start, end] inclusive:
     * <ul>
     *   <li>PRICE crypto → every calendar day (24/7 market).</li>
     *   <li>PRICE stock → weekdays only (Mon–Fri); ignores holidays — an estimate.</li>
     *   <li>NEWS/SOCIAL (market-wide) → total calendar days.</li>
     * </ul>
     * Never negative (an inverted range yields 0).
     */
    private int estimateRecords(MarketDataGap gap) {
        LocalDate start = gap.period().getStart();
        LocalDate end = gap.period().getEnd();
        if (end.isBefore(start)) {
            return 0;
        }

        boolean weekdaysOnly = gap.dataType() == EventType.PRICE
            && gap.symbol() != null
            && gap.symbol().type() == InvestmentType.STOCK;

        if (!weekdaysOnly) {
            // Crypto PRICE and market-wide NEWS/SOCIAL → every calendar day.
            return (int) (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        }

        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }
}
