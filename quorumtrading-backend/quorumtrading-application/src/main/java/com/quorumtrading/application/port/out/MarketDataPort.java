package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TickerRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port interface for market data providers.
 * Implemented by market data adapters (Coinbase, NewsAPI, Reddit, etc.)
 */
public interface MarketDataPort {
    String getProviderName();

    /**
     * Get all features supported by this market data provider.
     *
     * <p>Used for capability-based provider selection (e.g., finding providers that support backfill).</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li><b>Coinbase:</b> {CRYPTO, BACKFILL, CANDLES, INTRADAY, RESEARCH}</li>
     *   <li><b>Alpaca:</b> {CRYPTO, STOCK, BACKFILL, CANDLES, INTRADAY, LIVE_TRADING}</li>
     *   <li><b>NewsAPI:</b> {NEWS, BACKFILL, RESEARCH}</li>
     *   <li><b>Reddit:</b> {SOCIAL_MEDIA, NEWS, BACKFILL, RESEARCH}</li>
     * </ul>
     *
     * @return Set of features supported by this provider
     */
    Set<MarketDataFeature> getSupportedFeatures();

    /**
     * Check if this provider supports a specific feature.
     *
     * <p>Default implementation checks against {@link #getSupportedFeatures()}.</p>
     *
     * @param feature The feature to check
     * @return true if the feature is supported, false otherwise
     */
    default boolean hasFeature(MarketDataFeature feature) {
        return getSupportedFeatures().contains(feature);
    }

    /**
     * Whether this adapter has the credentials it needs to return anything.
     *
     * <p>Discovery selects adapters on {@link #hasFeature}, which describes what a provider
     * <em>can</em> serve and deliberately never reads configuration — so a key-requiring adapter
     * with no key is still discovered, is still tried, and still returns an empty list without
     * error. On a zero-credential install that made every failure look like a coverage problem:
     * a stock backfill answered "tried 3 configured provider(s) … most likely not covered by
     * them … use a ticker one of these providers carries", every clause of which was false, and
     * the one actionable fact — that no market-data credential was set — was the one thing not
     * said.</p>
     *
     * <p>Keyless adapters need not override this.</p>
     */
    default boolean isConfigured() {
        return true;
    }

    /**
     * Whether this adapter is switched on for this deployment.
     *
     * <p>Distinct from {@link #isConfigured()}: a provider can hold perfectly good credentials and
     * still be deliberately off (Saxo is off in every profile until broker approval), and a keyless
     * provider needs no credentials but can still be disabled.</p>
     *
     * <p>Every adapter already had this as a private {@code enabled} field with no way to read it
     * from outside, which is why a stock backfill on a container running {@code SAXO_ENABLED=false}
     * reported <em>"Tried 3 configured provider(s): alpaca, tiingo, saxo"</em>. That message is the
     * only in-product signal separating "missing credentials" from "symbol not covered", and naming
     * a provider that was never switched on makes it useless for exactly that purpose — besides
     * spending an outbound request on it.</p>
     *
     * <p>Adapters with no switch need not override this.</p>
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Fetch latest market data using adapter's default ticker configuration.
     *
     * @deprecated Use {@link #fetchLatestDataForTickers(TickerRegistry)} instead to fetch
     *             data for tickers specified in active strategies. This method will be removed
     *             in a future version.
     */
    @Deprecated
    List<MarketEvent> fetchLatestData();

    /**
     * Get the rate limit configuration for this provider.
     * Used by scheduler to avoid exceeding API limits.
     *
     * Default implementation returns unlimited (no rate limiting).
     * Providers should override this if they have API rate limits.
     *
     * @return RateLimit configuration
     */
    default RateLimit getRateLimit() {
        return RateLimit.unlimited();
    }

    /**
     * Fetch latest market data for tickers from all active strategies.
     *
     * <p>Adapters filter by their supported asset types:</p>
     * <ul>
     *   <li><b>Coinbase:</b> processes {@code tickerRegistry.cryptoTickers()} only</li>
     *   <li><b>Finnhub/Alpaca:</b> processes {@code tickerRegistry.stockTickers()} only</li>
     *   <li><b>NewsAPI/RSS/Reddit:</b> ignore tickers (fetch general news/sentiment)</li>
     * </ul>
     *
     * <p>If the ticker registry is empty, adapters should fall back to their
     * configured default ticker lists (for backward compatibility).</p>
     *
     * <p>Default implementation falls back to {@link #fetchLatestData()} for backward
     * compatibility with adapters that haven't implemented this method yet.</p>
     *
     * @param tickerRegistry Registry of tickers aggregated from all active strategies
     * @return List of market events for tickers supported by this adapter
     */
    default List<MarketEvent> fetchLatestDataForTickers(TickerRegistry tickerRegistry) {
        // Default: fall back to old behavior for backward compatibility
        return fetchLatestData();
    }

    /**
     * Fetch market data within a specified time range for specific tickers.
     * Adapters should filter tickers based on their supported asset types:
     * - Coinbase: processes crypto tickers only
     * - Finnhub: processes stock tickers only
     * - NewsAPI/Reddit: ignore tickers (fetch general news/sentiment)
     *
     * Default implementation falls back to fetchLatestData() (ignoring date range and tickers).
     *
     * @param cryptoTickers Set of cryptocurrency ticker symbols (e.g., BTC, ETH) from StrategyConfig
     * @param stockTickers  Set of stock ticker symbols (e.g., AAPL, MSFT) from StrategyConfig
     * @param start         Start of time range (inclusive)
     * @param end           End of time range (inclusive)
     * @return List of market events within the specified range for supported tickers
     */
    default List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end) {
        // Default: just fetch latest data (ignore date range and tickers)
        return fetchLatestData();
    }

    /**
     * Fetch market data within a specified time range for specific tickers with configurable granularity.
     * Adapters should filter tickers based on their supported asset types:
     * - Alpaca: processes both crypto and stock tickers
     * - Coinbase: processes crypto tickers only
     * - NewsAPI/Reddit: ignore tickers (fetch general news/sentiment)
     *
     * Default implementation delegates to fetchDataInRange() (ignoring granularity).
     *
     * @param cryptoTickers Set of cryptocurrency ticker symbols (e.g., BTC, ETH) from StrategyConfig
     * @param stockTickers  Set of stock ticker symbols (e.g., AAPL, MSFT) from StrategyConfig
     * @param start         Start of time range (inclusive)
     * @param end           End of time range (inclusive)
     * @param granularity   Time interval for bars (e.g., Granularity.ONE_DAY, Granularity.ONE_HOUR)
     * @return List of market events within the specified range for supported tickers at the specified granularity
     */
    default List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end,
            Granularity granularity) {
        // Default: delegate to existing method (ignore granularity for backward compatibility)
        return fetchDataInRange(cryptoTickers, stockTickers, start, end);
    }

    /**
     * Cheap authoritative lookup of the earliest date this provider has data for
     * a (symbol, dataType, granularity), if the provider can answer it in a single
     * request (e.g. a bars query with {@code sort=asc&limit=1}).
     *
     * <p>Default returns {@link Optional#empty()} ("unknown") so callers fall back
     * to the generic month-window probe in {@code DataHorizonProbeService}. Adapters
     * with a cheap authoritative path may override.</p>
     *
     * @param symbol      the symbol to query
     * @param dataType    the event type to query
     * @param granularity the bar granularity
     * @return the earliest available date, or empty if unknown / unsupported
     */
    default Optional<LocalDate> earliestAvailable(Symbol symbol, EventType dataType, Granularity granularity) {
        return Optional.empty();
    }

    /**
     * Whether this provider can supply market data for a specific instrument.
     *
     * <p>Used by instrument-aware provider selection so EU-listed symbols can be priced
     * from the venue they trade on (same exchange/currency/clock), while US symbols keep
     * resolving to today's providers.</p>
     *
     * <p>Default returns {@code true}: existing providers make no instrument-level claim
     * and continue to self-filter by asset type at fetch time, so current routing is
     * byte-for-byte identical. An EU-only provider (e.g. Saxo) overrides this to cover
     * only the instruments it is entitled to — typically gated on
     * {@link MarketDataFeature#EU_INSTRUMENT} and the symbol's exchange.</p>
     *
     * @param symbol the instrument to check coverage for
     * @return true if this provider can supply data for the symbol; true by default
     */
    default boolean coversInstrument(Symbol symbol) {
        return true;
    }
}
