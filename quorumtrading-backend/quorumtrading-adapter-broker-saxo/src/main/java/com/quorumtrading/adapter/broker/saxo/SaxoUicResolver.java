package com.quorumtrading.adapter.broker.saxo;

import com.quorumtrading.adapter.broker.saxo.client.SaxoReferenceDataClient;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoInstrumentDetails;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoInstrumentSearchResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoInstrumentSummary;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link Symbol} to a Saxo instrument identity — the {@code (Uic, AssetType,
 * ExchangeId)} triple — plus the per-listing trading constraints (tradability, currency,
 * lot/min-value) the order-placement and sizing chain need.
 *
 * <p><b>SB3-uic-resolver.</b> Saxo addresses instruments by a numeric {@code Uic} paired with an
 * {@code AssetType}, not by ticker. This resolver bridges our {@link Symbol} (ticker + MIC) to
 * that identity in two Reference Data calls:</p>
 * <ol>
 *   <li><b>Search</b> {@code GET /ref/v1/instruments?Keywords=<root>&AssetTypes=<type>&ExchangeId=<venue>}
 *       — pinned to the symbol's venue (via {@link SaxoExchangeIdMapper}) so a multi-listing name
 *       resolves to exactly one venue's {@code Uic} (SAXO_ANSWERS §2.5). The ticker root matched
 *       against the search hit's {@code Symbol} ({@code "RHM:xetr"}) disambiguates a keyword that
 *       returns several names.</li>
 *   <li><b>Details</b> {@code GET /ref/v1/instruments/details/{Uic}/{AssetType}} — confirms
 *       {@code IsTradable}, the settlement {@code CurrencyCode}, and surfaces
 *       {@code LotSize}/{@code MinimumTradeSize}/{@code MinimumOrderValue}/{@code AmountDecimals}/
 *       {@code TickSize} (SAXO_ANSWERS §2.2).</li>
 * </ol>
 *
 * <h2>Extended AssetTypes (Etf vs Stock)</h2>
 * <p>Saxo split the legacy {@code Stock} type into {@code Stock}, {@code Etf}, … — but whether our
 * app <em>sees</em> {@code Etf} is registration-dependent: a legacy app may still receive ETFs as
 * {@code Stock} (SAXO_ANSWERS §2.4 ⚠️). So the resolver <b>tries {@code Etf} first, then falls
 * back to {@code Stock}</b> for an equity-class symbol, taking whichever venue-pinned search
 * returns a tradable instrument. The fallback is TODO-flagged because it cannot be settled without
 * confirming this app's Extended-AssetTypes registration.</p>
 *
 * <h2>Caching</h2>
 * <p>Resolution is two network hops, so successful resolutions are cached per
 * {@link Symbol#canonical()} with a short TTL ({@value #CACHE_TTL_MINUTES} min) — long enough to
 * spare repeated lookups within a tick, short enough that a delisting/re-listing is picked up next
 * tick. Negative results (not found / not tradable) are <b>not</b> cached so a transient API hiccup
 * does not pin a name as untradable.</p>
 *
 * <p>This resolver issues Saxo REST calls, so callers must invoke it inside an activated request
 * context with the account pinned on the {@code SaxoRequestTokenHolder} (the
 * {@code SaxoBrokerAdapter.withAccountContext} seam, D10) so the per-account bearer is attached.</p>
 */
@ApplicationScoped
public class SaxoUicResolver {

    private static final Logger LOG = Logger.getLogger(SaxoUicResolver.class);

    /** Cache TTL for a successfully resolved instrument. */
    static final long CACHE_TTL_MINUTES = 10;

    /** Saxo AssetType strings (extended types). Equities/ETFs only — see boundary. */
    static final String SAXO_ASSET_TYPE_ETF = "Etf";
    static final String SAXO_ASSET_TYPE_STOCK = "Stock";

    @Inject
    @RestClient
    SaxoReferenceDataClient referenceDataClient;

    /** Resolved-instrument cache keyed by {@link Symbol#canonical()}. */
    private final ConcurrentHashMap<String, CachedResolution> cache = new ConcurrentHashMap<>();

    /**
     * Resolves a {@link Symbol} to its Saxo instrument identity + trading constraints.
     *
     * <p>For an equity-class symbol the resolver tries {@code Etf} then {@code Stock} (the
     * Extended-AssetTypes fallback). The first asset type whose venue-pinned search yields a hit
     * whose details confirm the instrument (tradable or not) wins; a name that returns no hit on
     * either type resolves to {@link Optional#empty()}.</p>
     *
     * @param symbol the instrument to resolve (ticker + MIC)
     * @return the resolved instrument, or empty if the name is not found on the symbol's venue
     */
    public Optional<ResolvedInstrument> resolve(Symbol symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        String cacheKey = symbol.canonical();

        CachedResolution cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.debugf("Saxo Uic cache hit for %s: uic=%d assetType=%s tradable=%s",
                    cacheKey, cached.resolution().uic(), cached.resolution().assetType(),
                    cached.resolution().tradable());
            return Optional.of(cached.resolution());
        }

        Optional<ResolvedInstrument> resolved = doResolve(symbol);
        resolved.ifPresent(r -> cache.put(cacheKey, new CachedResolution(r, Instant.now())));
        return resolved;
    }

    private Optional<ResolvedInstrument> doResolve(Symbol symbol) {
        MIC mic = symbol.exchange();
        String exchangeId = SaxoExchangeIdMapper.exchangeIdOrMic(mic);
        if (mic != null && !SaxoExchangeIdMapper.isMapped(mic)) {
            LOG.warnf("Saxo ExchangeId mapping for MIC %s is unverified; searching with ExchangeId=%s "
                    + "(MIC verbatim). TODO(SB3): confirm via /ref/v1/exchanges.", mic.code(), exchangeId);
        }

        String keywords = symbol.ticker();

        // Extended-AssetTypes fallback: Etf first, then Stock (registration-dependent, §2.4 ⚠️).
        for (String saxoAssetType : List.of(SAXO_ASSET_TYPE_ETF, SAXO_ASSET_TYPE_STOCK)) {
            Optional<SaxoInstrumentSummary> hit = searchVenue(keywords, saxoAssetType, exchangeId, symbol);
            if (hit.isEmpty()) {
                continue;
            }
            SaxoInstrumentSummary summary = hit.get();
            Optional<ResolvedInstrument> resolved = loadDetails(summary, symbol, saxoAssetType);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        LOG.debugf("Saxo resolution: no Etf or Stock instrument found for %s (ExchangeId=%s).",
                symbol.canonical(), exchangeId);
        return Optional.empty();
    }

    /**
     * Searches one venue for one Saxo asset type and picks the hit whose {@code Symbol} root
     * matches the requested ticker (disambiguating a keyword that returns several names).
     */
    private Optional<SaxoInstrumentSummary> searchVenue(String keywords, String saxoAssetType,
                                                        String exchangeId, Symbol symbol) {
        SaxoInstrumentSearchResponse response;
        try {
            response = referenceDataClient.searchInstruments(keywords, saxoAssetType, exchangeId);
        } catch (WebApplicationException e) {
            LOG.warnf("Saxo instrument search failed for %s (assetType=%s, ExchangeId=%s): HTTP %d",
                    symbol.canonical(), saxoAssetType, exchangeId, statusOf(e));
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warnf("Saxo instrument search error for %s (assetType=%s, ExchangeId=%s): %s",
                    symbol.canonical(), saxoAssetType, exchangeId, e.getMessage());
            return Optional.empty();
        }

        List<SaxoInstrumentSummary> rows = response == null ? null : response.getData();
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }

        // Prefer the hit whose symbol root matches the requested ticker; else the first hit that
        // carries a Uic (Saxo orders the most-relevant hit first).
        SaxoInstrumentSummary exactRootMatch = null;
        SaxoInstrumentSummary firstWithUic = null;
        for (SaxoInstrumentSummary row : rows) {
            if (row.getUic() == null) {
                continue;
            }
            if (firstWithUic == null) {
                firstWithUic = row;
            }
            if (rootMatches(row.getSymbol(), symbol.ticker())) {
                exactRootMatch = row;
                break;
            }
        }
        return Optional.ofNullable(exactRootMatch != null ? exactRootMatch : firstWithUic);
    }

    /**
     * Loads instrument details for a search hit and builds the {@link ResolvedInstrument}.
     * A 404 (instrument not present on this venue/assetType) yields empty so the caller can try
     * the next asset type.
     */
    private Optional<ResolvedInstrument> loadDetails(SaxoInstrumentSummary summary, Symbol symbol,
                                                     String searchedAssetType) {
        Long uic = summary.getUic();
        String detailsAssetType = summary.getAssetType() != null ? summary.getAssetType() : searchedAssetType;
        SaxoInstrumentDetails details;
        try {
            details = referenceDataClient.getInstrumentDetails(uic, detailsAssetType);
        } catch (WebApplicationException e) {
            int status = statusOf(e);
            if (status == 404) {
                LOG.debugf("Saxo instrument details 404 for %s (Uic=%d, assetType=%s) — not on this venue.",
                        symbol.canonical(), uic, detailsAssetType);
            } else {
                LOG.warnf("Saxo instrument details failed for %s (Uic=%d, assetType=%s): HTTP %d",
                        symbol.canonical(), uic, detailsAssetType, status);
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warnf("Saxo instrument details error for %s (Uic=%d, assetType=%s): %s",
                    symbol.canonical(), uic, detailsAssetType, e.getMessage());
            return Optional.empty();
        }

        if (details == null || details.getUic() == null) {
            return Optional.empty();
        }

        AssetType assetType = toModelAssetType(details.getAssetType() != null
                ? details.getAssetType() : detailsAssetType);
        MIC mic = symbol.exchange();
        Money.Currency currency = resolveCurrency(details.getCurrencyCode(), symbol);
        boolean tradable = Boolean.TRUE.equals(details.getIsTradable());
        boolean fractional = isFractional(details.getAmountDecimals());

        ResolvedInstrument resolved = new ResolvedInstrument(
                details.getUic(),
                assetType,
                details.getExchangeId(),
                mic,
                currency,
                tradable,
                details.getMinimumTradeSize(),
                details.getLotSize(),
                details.getMinimumOrderValue(),
                fractional,
                summary.getDescription() != null ? summary.getDescription() : details.getDescription());

        LOG.debugf("Saxo resolved %s → Uic=%d assetType=%s exchangeId=%s currency=%s tradable=%s "
                + "lotSize=%s minTradeSize=%s minOrderValue=%s fractional=%s",
                symbol.canonical(), resolved.uic(), resolved.assetType(), resolved.exchangeId(),
                resolved.currency(), resolved.tradable(), resolved.lotSize(), resolved.minimumTradeSize(),
                resolved.minimumOrderValue(), resolved.fractional());
        return Optional.of(resolved);
    }

    /**
     * Maps a Saxo {@code AssetType} string to our broker-neutral {@link AssetType}. Unknown/CFD
     * variants for an equity-class lookup are treated as {@link AssetType#STOCK} (best-effort).
     */
    static AssetType toModelAssetType(String saxoAssetType) {
        if (saxoAssetType == null) {
            return AssetType.STOCK;
        }
        return switch (saxoAssetType) {
            case "Etf", "Etc", "Etn" -> AssetType.ETF;
            case "Fund", "MutualFund" -> AssetType.FUND;
            case "Bond" -> AssetType.BOND;
            case "FxSpot", "CryptoFx" -> AssetType.CRYPTO_FX;
            // "Stock", "StockIndex", and any CFD/SRD equity variant → STOCK
            default -> AssetType.STOCK;
        };
    }

    /**
     * Resolves the settlement currency from the details' {@code CurrencyCode}, falling back to the
     * symbol's venue currency (via {@link SymbolParser}) when the field is absent/unparseable.
     */
    private static Money.Currency resolveCurrency(String currencyCode, Symbol symbol) {
        if (currencyCode != null && !currencyCode.isBlank()) {
            try {
                return Money.Currency.valueOf(currencyCode.trim().toUpperCase());
            } catch (IllegalArgumentException unsupported) {
                LOG.debugf("Saxo currency %s for %s is not a supported Money.Currency; "
                        + "falling back to the venue default.", currencyCode, symbol.canonical());
            }
        }
        return SymbolParser.currencyFor(symbol);
    }

    /**
     * {@code AmountDecimals == 0} confirms a whole-share-only (non-fractional) instrument, which is
     * the case for Saxo cash equities/ETFs. A null value (field absent) defaults to non-fractional —
     * the safe assumption for the equity/ETF universe this resolver serves (SAXO_ANSWERS §2.1).
     */
    static boolean isFractional(Integer amountDecimals) {
        return amountDecimals != null && amountDecimals > 0;
    }

    /**
     * Matches a Saxo search hit's {@code Symbol} ({@code "RHM:xetr"}) against the requested ticker
     * root ({@code "RHM"}) case-insensitively. The Saxo symbol root is the segment before the colon.
     */
    static boolean rootMatches(String saxoSymbol, String ticker) {
        if (saxoSymbol == null || ticker == null) {
            return false;
        }
        int colon = saxoSymbol.indexOf(':');
        String root = colon >= 0 ? saxoSymbol.substring(0, colon) : saxoSymbol;
        return root.equalsIgnoreCase(ticker);
    }

    private static int statusOf(WebApplicationException e) {
        return e.getResponse() != null ? e.getResponse().getStatus() : -1;
    }

    /** Clears the resolution cache (test/maintenance hook). */
    void clearCache() {
        cache.clear();
    }

    /**
     * A resolved Saxo instrument: the {@code (Uic, AssetType, ExchangeId)} identity plus the
     * tradability flag, settlement currency, and the per-listing trading constraints the order
     * and sizing chain consume.
     *
     * @param uic               numeric Saxo instrument id
     * @param assetType         broker-neutral asset type the details confirmed
     * @param exchangeId        Saxo internal exchange id echoed by the details (nullable)
     * @param mic               our canonical exchange MIC (from the requested symbol, nullable)
     * @param currency          settlement currency
     * @param tradable          whether the instrument is currently tradable on this venue
     * @param minimumTradeSize  minimum order size in shares (nullable)
     * @param lotSize           board-lot increment in shares (nullable; orders are whole multiples)
     * @param minimumOrderValue minimum order value (Amount × Price) in {@link #currency} (nullable)
     * @param fractional        whether the instrument supports fractional amounts (false for equities/ETFs)
     * @param description       human-readable instrument name (nullable)
     */
    public record ResolvedInstrument(
            Long uic,
            AssetType assetType,
            String exchangeId,
            MIC mic,
            Money.Currency currency,
            boolean tradable,
            BigDecimal minimumTradeSize,
            BigDecimal lotSize,
            BigDecimal minimumOrderValue,
            boolean fractional,
            String description) {

        public ResolvedInstrument {
            if (uic == null) {
                throw new IllegalArgumentException("ResolvedInstrument requires a non-null Uic");
            }
            if (assetType == null) {
                throw new IllegalArgumentException("ResolvedInstrument requires a non-null AssetType");
            }
        }

        /** @return the Saxo {@code AssetType} string for order placement / details calls. */
        public String saxoAssetType() {
            return switch (assetType) {
                case ETF -> SAXO_ASSET_TYPE_ETF;
                default -> SAXO_ASSET_TYPE_STOCK;
            };
        }
    }

    /** TTL-bounded cache entry. */
    private record CachedResolution(ResolvedInstrument resolution, Instant cachedAt) {
        boolean isExpired() {
            return Duration.between(cachedAt, Instant.now()).toMinutes() >= CACHE_TTL_MINUTES;
        }
    }
}
