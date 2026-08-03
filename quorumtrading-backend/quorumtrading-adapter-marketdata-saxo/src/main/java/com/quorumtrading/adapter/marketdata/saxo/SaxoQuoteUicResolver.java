package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.adapter.marketdata.saxo.client.SaxoMdReferenceDataClient;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.SaxoMdInstrumentSearchResponse;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.SaxoMdInstrumentSummary;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link Symbol} to the Saxo {@code (Uic, AssetType)} identity an InfoPrices quote
 * call needs — the market-data module's <em>own</em> instrument resolver.
 *
 * <p><b>MD2-live-quotes.</b> Saxo addresses instruments by a numeric {@code Uic} paired with an
 * {@code AssetType}, not by ticker. The live-quote path needs that identity (plus the settlement
 * currency, to stamp the {@code MarketEvent}). This resolver bridges our {@link Symbol}
 * (ticker + MIC) to it with a single Reference Data <b>search</b> call,
 * {@code GET /ref/v1/instruments?Keywords=<root>&AssetTypes=<type>&ExchangeId=<venue>}, pinned to
 * the symbol's venue via {@link SaxoMdExchangeIdMapper}.</p>
 *
 * <p><b>Isolation.</b> Adapters must not depend on each other (ArchUnit-enforced), so this is a
 * deliberate market-data copy of the broker-saxo {@code SaxoUicResolver} — sharing only the model
 * {@code SymbolParser}/{@code MIC}. The market-data resolver is lighter: pricing needs only the
 * {@code (Uic, AssetType)} pair + currency, so it skips the instrument-details call (lot/min-value
 * constraints are an order-sizing concern, not a pricing one).</p>
 *
 * <h2>Extended AssetTypes (Etf vs Stock)</h2>
 * <p>Whether the app sees {@code Etf} is registration-dependent (SAXO_ANSWERS §2.4 ⚠️), so the
 * resolver tries {@code Etf} first then falls back to {@code Stock} for an equity-class symbol,
 * taking whichever venue-pinned search returns a hit.</p>
 *
 * <h2>Caching</h2>
 * <p>Successful resolutions are cached per {@link Symbol#canonical()} with a short TTL so repeated
 * quote fetches within a tick do not re-search. Negative results are not cached.</p>
 *
 * <p>Issues Saxo REST calls, so callers must invoke it where the per-account bearer is attached
 * (the {@code SaxoMarketDataAdapter} pins the account before each call).</p>
 */
@ApplicationScoped
public class SaxoQuoteUicResolver {

    private static final Logger LOG = Logger.getLogger(SaxoQuoteUicResolver.class);

    /** Cache TTL for a successfully resolved instrument. */
    static final long CACHE_TTL_MINUTES = 10;

    /** Saxo AssetType strings (extended types). Equities/ETFs only. */
    static final String SAXO_ASSET_TYPE_ETF = "Etf";
    static final String SAXO_ASSET_TYPE_STOCK = "Stock";

    @Inject
    @RestClient
    SaxoMdReferenceDataClient referenceDataClient;

    /** Resolved-instrument cache keyed by {@link Symbol#canonical()}. */
    private final ConcurrentHashMap<String, CachedResolution> cache = new ConcurrentHashMap<>();

    /**
     * Resolves a {@link Symbol} to its Saxo {@code (Uic, AssetType)} pricing identity.
     *
     * @param symbol the instrument to resolve (ticker + MIC)
     * @return the resolved instrument, or empty if the name is not found on the symbol's venue
     */
    public Optional<ResolvedQuoteInstrument> resolve(Symbol symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        String cacheKey = symbol.canonical();

        CachedResolution cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.debugf("Saxo MD Uic cache hit for %s: uic=%d assetType=%s",
                    cacheKey, cached.resolution().uic(), cached.resolution().saxoAssetType());
            return Optional.of(cached.resolution());
        }

        Optional<ResolvedQuoteInstrument> resolved = doResolve(symbol);
        resolved.ifPresent(r -> cache.put(cacheKey, new CachedResolution(r, Instant.now())));
        return resolved;
    }

    private Optional<ResolvedQuoteInstrument> doResolve(Symbol symbol) {
        MIC mic = symbol.exchange();
        String exchangeId = SaxoMdExchangeIdMapper.exchangeIdOrMic(mic);
        if (mic != null && !SaxoMdExchangeIdMapper.isMapped(mic)) {
            LOG.warnf("Saxo MD ExchangeId mapping for MIC %s is unverified; searching with "
                    + "ExchangeId=%s (MIC verbatim). TODO(MD2): confirm via /ref/v1/exchanges.",
                    mic.code(), exchangeId);
        }

        String keywords = symbol.ticker();

        // Extended-AssetTypes fallback: Etf first, then Stock (registration-dependent, §2.4 ⚠️).
        for (String saxoAssetType : List.of(SAXO_ASSET_TYPE_ETF, SAXO_ASSET_TYPE_STOCK)) {
            Optional<SaxoMdInstrumentSummary> hit = searchVenue(keywords, saxoAssetType, exchangeId, symbol);
            if (hit.isPresent()) {
                ResolvedQuoteInstrument resolved = toResolved(hit.get(), symbol, saxoAssetType);
                LOG.debugf("Saxo MD resolved %s → Uic=%d assetType=%s currency=%s",
                        symbol.canonical(), resolved.uic(), resolved.saxoAssetType(), resolved.currency());
                return Optional.of(resolved);
            }
        }
        LOG.debugf("Saxo MD resolution: no Etf or Stock instrument found for %s (ExchangeId=%s).",
                symbol.canonical(), exchangeId);
        return Optional.empty();
    }

    /**
     * Searches one venue for one Saxo asset type and picks the hit whose {@code Symbol} root
     * matches the requested ticker (disambiguating a keyword that returns several names).
     */
    private Optional<SaxoMdInstrumentSummary> searchVenue(String keywords, String saxoAssetType,
                                                          String exchangeId, Symbol symbol) {
        SaxoMdInstrumentSearchResponse response;
        try {
            response = referenceDataClient.searchInstruments(keywords, saxoAssetType, exchangeId);
        } catch (WebApplicationException e) {
            LOG.warnf("Saxo MD instrument search failed for %s (assetType=%s, ExchangeId=%s): HTTP %d",
                    symbol.canonical(), saxoAssetType, exchangeId, statusOf(e));
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warnf("Saxo MD instrument search error for %s (assetType=%s, ExchangeId=%s): %s",
                    symbol.canonical(), saxoAssetType, exchangeId, e.getMessage());
            return Optional.empty();
        }

        List<SaxoMdInstrumentSummary> rows = response == null ? null : response.getData();
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }

        SaxoMdInstrumentSummary exactRootMatch = null;
        SaxoMdInstrumentSummary firstWithUic = null;
        for (SaxoMdInstrumentSummary row : rows) {
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

    private ResolvedQuoteInstrument toResolved(SaxoMdInstrumentSummary summary, Symbol symbol,
                                               String searchedAssetType) {
        String saxoAssetType = summary.getAssetType() != null ? summary.getAssetType() : searchedAssetType;
        AssetType assetType = toModelAssetType(saxoAssetType);
        Money.Currency currency = resolveCurrency(summary.getCurrencyCode(), symbol);
        return new ResolvedQuoteInstrument(
                summary.getUic(),
                assetType,
                saxoAssetType,
                currency,
                summary.getDescription());
    }

    /**
     * Maps a Saxo {@code AssetType} string to our broker-neutral {@link AssetType}. Unknown
     * equity-class variants default to {@link AssetType#STOCK}.
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
            default -> AssetType.STOCK;
        };
    }

    /**
     * Resolves the settlement currency from the search hit's {@code CurrencyCode}, falling back to
     * the symbol's venue currency (via {@link SymbolParser}) when the field is absent/unparseable.
     */
    private static Money.Currency resolveCurrency(String currencyCode, Symbol symbol) {
        if (currencyCode != null && !currencyCode.isBlank()) {
            try {
                return Money.Currency.valueOf(currencyCode.trim().toUpperCase());
            } catch (IllegalArgumentException unsupported) {
                LOG.debugf("Saxo MD currency %s for %s is not a supported Money.Currency; "
                        + "falling back to the venue default.", currencyCode, symbol.canonical());
            }
        }
        return SymbolParser.currencyFor(symbol);
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
     * A resolved Saxo instrument for the pricing path: the {@code (Uic, AssetType)} identity plus
     * the settlement currency used to stamp the {@code MarketEvent}.
     *
     * @param uic           numeric Saxo instrument id
     * @param assetType     broker-neutral asset type
     * @param saxoAssetType the Saxo {@code AssetType} string to send on the InfoPrices call
     * @param currency      settlement currency
     * @param description   human-readable instrument name (nullable)
     */
    public record ResolvedQuoteInstrument(
            Long uic,
            AssetType assetType,
            String saxoAssetType,
            Money.Currency currency,
            String description) {

        public ResolvedQuoteInstrument {
            if (uic == null) {
                throw new IllegalArgumentException("ResolvedQuoteInstrument requires a non-null Uic");
            }
            if (assetType == null) {
                throw new IllegalArgumentException("ResolvedQuoteInstrument requires a non-null AssetType");
            }
        }
    }

    /** TTL-bounded cache entry. */
    private record CachedResolution(ResolvedQuoteInstrument resolution, Instant cachedAt) {
        boolean isExpired() {
            return Duration.between(cachedAt, Instant.now()).toMinutes() >= CACHE_TTL_MINUTES;
        }
    }
}
