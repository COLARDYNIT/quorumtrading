package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.adapter.marketdata.saxo.client.SaxoOpenApiClient;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.ChartsResponse;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.InfoPricesResponse;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.valueobject.TickerRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Saxo Bank (EU) market-data adapter — the live-quote (PRICE) path
 * ({@code MD2-live-quotes}).
 *
 * <p>Prices EU-listed instruments from the venue/currency/clock they trade on, using Saxo's
 * {@code GET /trade/v1/infoprices} endpoint with
 * {@code FieldGroups=Quote,PriceInfo,PriceInfoDetails} (current quote + day High/Low/Open in one
 * call, SAXO_ANSWERS §3.3). Each result becomes a {@link EventType#PRICE} {@link MarketEvent} with
 * {@code source="saxo"}, the settlement currency from the resolved instrument, and the
 * {@code DelayedByMinutes}/{@code delayed} flag carried in {@code rawData} (0 = realtime).</p>
 *
 * <h2>LIVE-only</h2>
 * <p>Saxo SIM serves no equity/ETF market data — only Forex (SAXO_ANSWERS headline). So this path
 * is LIVE-only; the rest-client base URL is the LIVE gateway per environment (CFG owns it). Without
 * an exchange subscription LIVE prices are delayed 15 min (surfaced via {@code DelayedByMinutes});
 * with no entitlement at all the quote reports {@code PriceType=NoAccess}, which is handled
 * gracefully as an empty result, never an error.</p>
 *
 * <h2>Instrument resolution — the module's OWN resolver</h2>
 * <p>InfoPrices is keyed by {@code (Uic, AssetType)}, not ticker. {@link SaxoQuoteUicResolver}
 * (this module's own resolver + reference-data client — adapters must not depend on each other)
 * turns a {@link Symbol} into that identity, venue-pinned, with an Etf→Stock fallback.</p>
 *
 * <h2>Coverage</h2>
 * <p>{@link #coversInstrument(Symbol)} gates on the {@code EU_INSTRUMENT} feature + a non-US MIC,
 * so US symbols keep resolving to today's providers and only European-venue symbols route here
 * (MD4 wires the selection sites). {@link #getSupportedFeatures()} advertises
 * {@code STOCK + EU_INSTRUMENT}.</p>
 *
 * <p><b>Boundary:</b> live quotes only. Historical OHLC bars (Charts) are {@code MD3}; provider
 * selection wiring is {@code MD4}; the full test matrix is {@code MD5}.</p>
 */
@ApplicationScoped
@Named("saxo")
public class SaxoMarketDataAdapter implements MarketDataPort {

    private static final Logger LOG = Logger.getLogger(SaxoMarketDataAdapter.class);

    static final String PROVIDER_NAME = "saxo";

    /** Field groups requested so one InfoPrices call returns quote + day High/Low + day Open. */
    static final String INFO_PRICES_FIELD_GROUPS = "Quote,PriceInfo,PriceInfoDetails";

    @Inject
    @RestClient
    SaxoOpenApiClient openApiClient;

    @Inject
    SaxoQuoteUicResolver uicResolver;

    @Inject
    SaxoChartsPaginator chartsPaginator;

    /**
     * Feature flag (default OFF). Shares the broker-saxo flag key so the two Saxo adapters toggle
     * together. When OFF the adapter advertises no coverage and fetches nothing.
     */
    @ConfigProperty(name = "quorumtrading.marketdata.saxo.enabled", defaultValue = "false")
    boolean enabled;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * Off by default in every profile until broker approval — so a failed stock backfill must stop
     * counting Saxo among the providers it "tried". It never was tried; it advertises no coverage
     * and fetches nothing while this is false.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        // Saxo equity/ETF data from the EU venue they trade on. STOCK + EU_INSTRUMENT are the
        // WP-required features; REAL_TIME/RESEARCH express that the feed is suitable for live
        // pricing (when entitled) and research; BACKFILL/CANDLES advertise the historical OHLC
        // path (MD3 charts) so provider-selection can route EU-instrument backfill here.
        return Set.of(
                MarketDataFeature.STOCK,
                MarketDataFeature.EU_INSTRUMENT,
                MarketDataFeature.REAL_TIME,
                MarketDataFeature.RESEARCH,
                MarketDataFeature.BACKFILL,
                MarketDataFeature.CANDLES);
    }

    /**
     * Saxo prices EU-listed instruments only. Covers a symbol iff the adapter is enabled, it
     * advertises the {@link MarketDataFeature#EU_INSTRUMENT} capability, and the symbol carries a
     * non-US (European) MIC — US symbols continue to self-resolve to today's providers, so default
     * routing is unchanged for them.
     *
     * <p>The gate is the conjunction the WP requires: {@code EU_INSTRUMENT} (capability) AND the
     * symbol's MIC ({@link Symbol#isEuropean()} = a non-null, non-US venue code). Both must hold, so
     * a US bare ticker (null/US MIC) is never claimed and a future config that drops
     * {@code EU_INSTRUMENT} from the feature set would disable Saxo routing in lock-step.</p>
     */
    @Override
    public boolean coversInstrument(Symbol symbol) {
        if (!enabled || symbol == null) {
            return false;
        }
        return hasFeature(MarketDataFeature.EU_INSTRUMENT) && symbol.isEuropean();
    }

    /**
     * Fetch latest live quotes for the EU-listed stock tickers in the registry.
     *
     * <p>Filters to European-venue stock symbols (the only ones this adapter is entitled to and the
     * only ones {@link #coversInstrument(Symbol)} claims). US symbols and crypto are skipped.</p>
     */
    @Override
    public List<MarketEvent> fetchLatestDataForTickers(TickerRegistry tickerRegistry) {
        if (!enabled) {
            LOG.debug("Saxo market-data adapter is disabled; returning no events.");
            return Collections.emptyList();
        }
        if (tickerRegistry == null || tickerRegistry.stockTickers().isEmpty()) {
            LOG.debug("Saxo market-data: no stock tickers in registry; nothing to fetch.");
            return Collections.emptyList();
        }

        List<MarketEvent> events = new ArrayList<>();
        for (Symbol symbol : tickerRegistry.stockTickers()) {
            if (!coversInstrument(symbol)) {
                LOG.debugf("Saxo market-data: skipping %s — not an EU-listed instrument this adapter covers.",
                        symbol.canonical());
                continue;
            }
            events.addAll(fetchSymbolQuote(symbol));
        }
        LOG.infof("Saxo market-data collected %d live PRICE events for %d EU stock tickers.",
                events.size(), tickerRegistry.stockTickers().size());
        return events;
    }

    /** Legacy no-default-ticker path: the Saxo adapter has no default symbol list. */
    @Override
    @Deprecated
    public List<MarketEvent> fetchLatestData() {
        return Collections.emptyList();
    }

    /**
     * Resolve a single EU symbol to its Saxo {@code (Uic, AssetType)} and fetch its live quote as a
     * PRICE {@link MarketEvent}. Returns empty (never throws) for an unresolvable instrument, a
     * {@code NoAccess} entitlement gap, or a missing quote — the caller continues with other tickers.
     */
    public List<MarketEvent> fetchSymbolQuote(Symbol symbol) {
        if (!coversInstrument(symbol)) {
            return Collections.emptyList();
        }

        Optional<SaxoQuoteUicResolver.ResolvedQuoteInstrument> resolved = resolveSafely(symbol);
        if (resolved.isEmpty()) {
            LOG.debugf("Saxo market-data: %s did not resolve to a Saxo instrument; no quote.",
                    symbol.canonical());
            return Collections.emptyList();
        }
        SaxoQuoteUicResolver.ResolvedQuoteInstrument instrument = resolved.get();

        InfoPricesResponse response;
        try {
            response = openApiClient.getInfoPrices(
                    instrument.uic(), instrument.saxoAssetType(), INFO_PRICES_FIELD_GROUPS);
        } catch (RuntimeException e) {
            LOG.warnf("Saxo market-data: InfoPrices call failed for %s (Uic=%d, assetType=%s): %s",
                    symbol.canonical(), instrument.uic(), instrument.saxoAssetType(), e.getMessage());
            return Collections.emptyList();
        }

        return mapToEvent(symbol, instrument, response)
                .map(List::of)
                .orElse(Collections.emptyList());
    }

    // --- MD3: historical OHLC backfill (Charts) ------------------------------

    /**
     * Backfill historical OHLC bars for the EU-listed stock tickers in {@code [start, end]} at the
     * given {@code granularity}, via Saxo {@code Charts} ({@code MD3-charts-backfill}).
     *
     * <p>Crypto tickers are ignored (Saxo prices equities/ETFs here). Each stock ticker is parsed to
     * a {@link Symbol}; non-European symbols are skipped (US keeps resolving to today's providers).
     * For each covered symbol the {@link SaxoChartsPaginator} walks {@code /chart/v1/charts}
     * backward ({@code Mode=UpTo}, {@code Count<=1200}) until the array stops growing, then samples
     * are clipped to {@code [start, end]} and mapped to {@code PRICE} {@link MarketEvent}s stamped
     * with {@code source="saxo"} and the resolved venue currency. LIVE-only; an unresolvable symbol
     * or an empty (NoAccess/no-history) response yields no events for that symbol, never an error.</p>
     */
    @Override
    public List<MarketEvent> fetchDataInRange(Set<String> cryptoTickers,
                                              Set<String> stockTickers,
                                              Instant start,
                                              Instant end,
                                              Granularity granularity) {
        if (!enabled) {
            LOG.debug("Saxo market-data adapter is disabled; no historical bars.");
            return Collections.emptyList();
        }
        if (stockTickers == null || stockTickers.isEmpty()) {
            return Collections.emptyList();
        }
        Optional<SaxoChartsHorizon> horizon = SaxoChartsHorizon.forGranularity(granularity);
        if (horizon.isEmpty()) {
            LOG.debugf("Saxo charts: no Saxo horizon for granularity %s; skipping backfill.", granularity);
            return Collections.emptyList();
        }

        Instant windowEnd = end != null ? end : Instant.now();
        List<MarketEvent> events = new ArrayList<>();
        for (String ticker : stockTickers) {
            Symbol symbol = parseEuSymbol(ticker);
            if (symbol == null || !coversInstrument(symbol)) {
                continue;
            }
            events.addAll(fetchSymbolBars(symbol, horizon.get(), start, windowEnd));
        }
        LOG.infof("Saxo charts collected %d historical PRICE bars across %d EU stock ticker(s) "
                + "(granularity=%s, window=%s..%s).", events.size(), stockTickers.size(),
                granularity, start, windowEnd);
        return events;
    }

    /**
     * Empirical earliest-available date for a (symbol, PRICE, granularity), measured by paginating
     * Saxo {@code Charts} backward until no older sample appears — never an assumed floor.
     *
     * <p>Returns empty (so the caller falls back to the generic probe) when the symbol is not an
     * EU instrument this adapter covers, has no Saxo horizon for the granularity, does not resolve,
     * the data type is not {@code PRICE}, or the venue returns no samples.</p>
     */
    @Override
    public Optional<LocalDate> earliestAvailable(Symbol symbol, EventType dataType, Granularity granularity) {
        if (!enabled || symbol == null || dataType != EventType.PRICE || !coversInstrument(symbol)) {
            return Optional.empty();
        }
        Optional<SaxoChartsHorizon> horizon = SaxoChartsHorizon.forGranularity(granularity);
        if (horizon.isEmpty()) {
            return Optional.empty();
        }
        Optional<SaxoQuoteUicResolver.ResolvedQuoteInstrument> resolved = resolveSafely(symbol);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        SaxoQuoteUicResolver.ResolvedQuoteInstrument instrument = resolved.get();
        Optional<Instant> earliest = chartsPaginator.earliestAvailable(
                instrument.uic(), instrument.saxoAssetType(), horizon.get());
        return earliest.map(i -> i.atZone(ZoneOffset.UTC).toLocalDate());
    }

    /**
     * Resolves one EU symbol, stitches its backward chart history, clips to {@code [start, end]} and
     * maps each sample to a PRICE {@link MarketEvent}. Returns empty (never throws) for an
     * unresolvable instrument or an empty/NoAccess response.
     */
    List<MarketEvent> fetchSymbolBars(Symbol symbol, SaxoChartsHorizon horizon, Instant start, Instant end) {
        Optional<SaxoQuoteUicResolver.ResolvedQuoteInstrument> resolved = resolveSafely(symbol);
        if (resolved.isEmpty()) {
            LOG.debugf("Saxo charts: %s did not resolve to a Saxo instrument; no bars.",
                    symbol.canonical());
            return Collections.emptyList();
        }
        SaxoQuoteUicResolver.ResolvedQuoteInstrument instrument = resolved.get();

        List<ChartsResponse.Sample> samples =
                chartsPaginator.fetchBackward(instrument.uic(), instrument.saxoAssetType(), horizon, end);
        if (samples.isEmpty()) {
            return Collections.emptyList();
        }

        List<MarketEvent> events = new ArrayList<>(samples.size());
        for (ChartsResponse.Sample sample : samples) {
            Instant t = SaxoChartsPaginator.parseSampleTime(sample.time());
            if (t == null) {
                continue;
            }
            if (start != null && t.isBefore(start)) {
                continue;
            }
            if (end != null && t.isAfter(end)) {
                continue;
            }
            mapSampleToEvent(symbol, instrument, sample, t).ifPresent(events::add);
        }
        LOG.debugf("Saxo charts: %s → %d bar event(s) in window (Uic=%d, horizon=%dmin).",
                symbol.canonical(), events.size(), instrument.uic(), horizon.horizonMinutes());
        return events;
    }

    /** Maps one OHLC chart sample to a PRICE {@link MarketEvent}; empty when no close price exists. */
    Optional<MarketEvent> mapSampleToEvent(Symbol symbol,
                                           SaxoQuoteUicResolver.ResolvedQuoteInstrument instrument,
                                           ChartsResponse.Sample sample,
                                           Instant timestamp) {
        BigDecimal close = sample.close();
        if (close == null) {
            return Optional.empty();
        }
        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("price", close);
        putIfPresent(rawData, "open", sample.open());
        putIfPresent(rawData, "high", sample.high());
        putIfPresent(rawData, "low", sample.low());
        rawData.put("close", close);
        putIfPresent(rawData, "volume", sample.volume());
        rawData.put("currency", instrument.currency().name());
        rawData.put("symbol", symbol.canonical());
        rawData.put("uic", instrument.uic());
        rawData.put("assetType", instrument.saxoAssetType());

        return Optional.of(new MarketEvent(
                timestamp, EventType.PRICE, PROVIDER_NAME, symbol.canonical(), rawData));
    }

    /**
     * Parses a registry/strategy stock-ticker string into a {@link Symbol}, returning {@code null}
     * for an unparseable token (so backfill skips it rather than failing the whole run).
     */
    private static Symbol parseEuSymbol(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        try {
            return SymbolParser.parse(ticker, InvestmentType.STOCK);
        } catch (RuntimeException e) {
            LOG.debugf("Saxo charts: cannot parse ticker '%s' to a Symbol: %s", ticker, e.getMessage());
            return null;
        }
    }

    private Optional<SaxoQuoteUicResolver.ResolvedQuoteInstrument> resolveSafely(Symbol symbol) {
        try {
            return uicResolver.resolve(symbol);
        } catch (RuntimeException e) {
            LOG.warnf("Saxo market-data: Uic resolution failed for %s: %s",
                    symbol.canonical(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Maps a Saxo InfoPrices response to a PRICE {@link MarketEvent}, or empty when the quote is
     * absent or the account is not entitled to the feed ({@code PriceType=NoAccess}).
     */
    Optional<MarketEvent> mapToEvent(Symbol symbol,
                                     SaxoQuoteUicResolver.ResolvedQuoteInstrument instrument,
                                     InfoPricesResponse response) {
        if (response == null) {
            return Optional.empty();
        }
        InfoPricesResponse.Quote quote = response.quote();
        if (quote == null) {
            LOG.debugf("Saxo market-data: no Quote block for %s (Uic=%d) — empty.",
                    symbol.canonical(), instrument.uic());
            return Optional.empty();
        }
        if (quote.isNoAccess()) {
            LOG.infof("Saxo market-data: %s (Uic=%d) returned PriceType=NoAccess — no entitlement "
                    + "for this exchange feed; skipping (not an error).",
                    symbol.canonical(), instrument.uic());
            return Optional.empty();
        }

        BigDecimal price = decisionPrice(quote, response);
        if (price == null) {
            LOG.debugf("Saxo market-data: no usable price (mid/last/bid/ask) for %s (Uic=%d) — empty.",
                    symbol.canonical(), instrument.uic());
            return Optional.empty();
        }

        boolean realtime = quote.isRealtime();
        int delayedByMinutes = quote.delayedByMinutes() != null ? quote.delayedByMinutes() : -1;

        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("price", price);
        rawData.put("currency", instrument.currency().name());
        rawData.put("symbol", symbol.canonical());
        rawData.put("uic", instrument.uic());
        rawData.put("assetType", instrument.saxoAssetType());
        putIfPresent(rawData, "bid", quote.bid());
        putIfPresent(rawData, "ask", quote.ask());
        putIfPresent(rawData, "mid", quote.mid());
        if (response.priceInfo() != null) {
            putIfPresent(rawData, "high", response.priceInfo().high());
            putIfPresent(rawData, "low", response.priceInfo().low());
        }
        if (response.priceInfoDetails() != null) {
            putIfPresent(rawData, "open", response.priceInfoDetails().open());
            putIfPresent(rawData, "lastTraded", response.priceInfoDetails().lastTraded());
            putIfPresent(rawData, "lastClose", response.priceInfoDetails().lastClose());
            putIfPresent(rawData, "volume", response.priceInfoDetails().volume());
        }
        // Delay flags: 0 = realtime, >0 = exchange-delayed (typically 15 min without a subscription).
        rawData.put("delayed", !realtime);
        if (quote.delayedByMinutes() != null) {
            rawData.put("delayedByMinutes", delayedByMinutes);
        }
        if (quote.marketState() != null) {
            rawData.put("marketState", quote.marketState());
        }

        Instant timestamp = parseTimestamp(response.lastUpdated());

        MarketEvent event = new MarketEvent(
                timestamp,
                EventType.PRICE,
                PROVIDER_NAME,
                symbol.canonical(),
                rawData);

        LOG.debugf("Saxo market-data PRICE for %s: %s %s (Uic=%d, delayed=%s, delayByMin=%s)",
                symbol.canonical(), price, instrument.currency(), instrument.uic(),
                !realtime, quote.delayedByMinutes());
        return Optional.of(event);
    }

    /**
     * The decision price for a quote: prefer {@code Mid}, then {@code LastTraded}, then the
     * bid/ask midpoint, then either side alone. Returns {@code null} if none are present.
     */
    static BigDecimal decisionPrice(InfoPricesResponse.Quote quote, InfoPricesResponse response) {
        if (quote.mid() != null) {
            return quote.mid();
        }
        if (response.priceInfoDetails() != null && response.priceInfoDetails().lastTraded() != null) {
            return response.priceInfoDetails().lastTraded();
        }
        if (quote.bid() != null && quote.ask() != null) {
            return quote.bid().add(quote.ask()).divide(BigDecimal.valueOf(2));
        }
        if (quote.bid() != null) {
            return quote.bid();
        }
        return quote.ask();
    }

    private static void putIfPresent(Map<String, Object> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static Instant parseTimestamp(String lastUpdated) {
        if (lastUpdated == null || lastUpdated.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(lastUpdated);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }
}
