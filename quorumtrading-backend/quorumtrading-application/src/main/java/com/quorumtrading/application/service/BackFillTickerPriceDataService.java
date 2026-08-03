package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketDataPortDiscovery;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.BackFillResult;
import com.quorumtrading.application.usecase.BackFillTickerPriceDataUseCase;
import com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

/**
 * Service for backfilling historical price data.
 *
 * Fetches historical OHLC data from market data providers (Coinbase for crypto, Finnhub for stocks)
 * and inserts it into typed tables (price_bars). Handles duplicate detection via content_hash constraint.
 *
 * Uses MarketDataPortDiscovery to dynamically discover MarketDataPort implementations at runtime,
 * avoiding direct dependencies on adapter implementations (hexagonal architecture compliance).
 *
 * Registered in ApplicationConfig as a bean producer.
 */
public class BackFillTickerPriceDataService implements BackFillTickerPriceDataUseCase {

    private static final Logger LOG = Logger.getLogger(BackFillTickerPriceDataService.class);

    private final MarketDataPortDiscovery marketDataPortDiscovery;
    private final MarketEventRepository marketEventRepository;

    public BackFillTickerPriceDataService(
            MarketDataPortDiscovery marketDataPortDiscovery,
            MarketEventRepository marketEventRepository) {
        this.marketDataPortDiscovery = marketDataPortDiscovery;
        this.marketEventRepository = marketEventRepository;
    }

    @Override
    public BackFillResult execute(BackFillTickerPriceDataCommand command) {
        try {
            // Validate command
            command.validate();

            LOG.infof("Starting backfill for %s (%s) from %s to %s with granularity %s",
                command.ticker(), command.assetType(), command.startDate(), command.endDate(), command.getGranularity().getDisplayName());

            // Dynamically select appropriate adapters based on asset type using feature-based discovery
            // Required features: BACKFILL (historical data) + CANDLES (OHLCV data) + asset type (CRYPTO or STOCK)
            MarketDataFeature assetFeature = command.isCrypto()
                ? MarketDataFeature.CRYPTO
                : MarketDataFeature.STOCK;

            List<MarketDataPort> adapters = marketDataPortDiscovery.findAllByFeatures(
                MarketDataFeature.BACKFILL,
                MarketDataFeature.CANDLES,
                assetFeature
            );

            if (adapters.isEmpty()) {
                String errorMsg = String.format(
                    "No market data adapter found supporting features: BACKFILL, CANDLES, %s for %s",
                    assetFeature, command.assetType());
                LOG.errorf(errorMsg);
                return BackFillResult.failure(command.ticker(), command.assetType(), errorMsg);
            }

            LOG.debugf("Found %d adapter(s) for %s: %s", adapters.size(), command.ticker(),
                adapters.stream().map(MarketDataPort::getProviderName).toList());

            // Only providers that can actually answer are worth an outbound request. Discovery
            // selects on capability and never reads configuration, so an adapter with no key used
            // to be called anyway — it returned an empty list, silently, and still counted toward
            // the "Tried N configured provider(s)" number in the failure message. The message is
            // the only in-product signal separating "missing credentials" from "symbol not
            // covered", so a name in it that was never configured makes it useless for its one
            // purpose. Adapters that are switched off are filtered out earlier, by discovery.
            List<MarketDataPort> usable = adapters.stream()
                .filter(MarketDataPort::isConfigured)
                .toList();

            // Try all adapters and keep the one that returns the most events.
            // This handles partial data: e.g. Alpaca returns 110 bars for a 18-year range
            // while Tiingo returns the full 4500. We want the most complete dataset.
            Set<String> tickers = Set.of(command.ticker());
            List<MarketEvent> events = List.of();
            String bestAdapterName = null;
            List<String> providerErrors = new java.util.ArrayList<>();

            for (MarketDataPort adapter : usable) {
                try {
                    LOG.debugf("Trying adapter: %s for %s", adapter.getProviderName(), command.ticker());

                    List<MarketEvent> adapterEvents;
                    if (command.isCrypto()) {
                        adapterEvents = adapter.fetchDataInRange(tickers, Set.of(), command.startDate(), command.endDate(), command.getGranularity());
                    } else {
                        adapterEvents = adapter.fetchDataInRange(Set.of(), tickers, command.startDate(), command.endDate(), command.getGranularity());
                    }

                    int count = (adapterEvents != null) ? adapterEvents.size() : 0;
                    LOG.infof("Adapter %s returned %d events for %s", adapter.getProviderName(), count, command.ticker());

                    if (count > events.size()) {
                        events = adapterEvents;
                        bestAdapterName = adapter.getProviderName();
                    }
                } catch (Exception adapterError) {
                    LOG.warnf(adapterError, "Adapter %s failed for %s: %s",
                        adapter.getProviderName(), command.ticker(), adapterError.getMessage());
                    providerErrors.add(adapter.getProviderName() + " ("
                        + summarize(adapterError) + ")");
                }
            }

            // A provider that threw is NOT a reason to abandon the diagnosis below. Rethrowing
            // here sent the whole call to the outer catch, which returns the raw exception text
            // — so the same user mistake produced a clean three-provider explanation when every
            // provider quietly returned nothing, and a REST-client FQCN when one of them
            // objected. Whether a provider 400s or returns an empty list is its own internal
            // business; the person who typed the ticker cannot see the difference and should not
            // have to. The error is carried into the message as a detail instead.

            if (bestAdapterName != null) {
                LOG.infof("Best adapter for %s: %s with %d events", command.ticker(), bestAdapterName, events.size());
            }

            // Insert events into database (duplicate handling via content_hash constraint).
            // Providers bundle news/sentiment events alongside price bars — count them separately
            // so recordsInserted honestly reports PRICE bars only.
            int inserted = 0;
            int skipped = 0;
            int failed = 0;
            int newsInserted = 0;
            int newsSkipped = 0;

            for (MarketEvent event : events) {
                boolean isPrice = event.eventType() == com.quorumtrading.model.enums.EventType.PRICE;
                try {
                    MarketEvent saved = marketEventRepository.save(event);
                    if (isPrice) {
                        if (saved != null) inserted++; else skipped++;
                    } else {
                        if (saved != null) newsInserted++; else newsSkipped++;
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("content_hash")) {
                        if (isPrice) skipped++; else newsSkipped++;
                        LOG.debugf("Skipped duplicate event for %s at %s", command.ticker(), event.timestamp());
                    } else if (isPrice) {
                        failed++;
                        LOG.errorf(e, "Failed to save event for %s at %s", command.ticker(), event.timestamp());
                    } else {
                        newsSkipped++;
                        LOG.debugf("Failed to save news event for %s at %s: %s", command.ticker(), event.timestamp(), e.getMessage());
                    }
                }
            }

            LOG.infof("Backfill complete for %s: %d price bars inserted, %d skipped, %d failed; %d news events inserted, %d skipped (total: %d events)",
                command.ticker(), inserted, skipped, failed, newsInserted, newsSkipped, events.size());

            // Providers bundle news alongside candles, so a response carrying only news used to
            // report success with 0 price bars — indistinguishable from a no-op re-run over an
            // already-covered range. Price bars are what was asked for. Reported after the save
            // loop so any news that did arrive is still persisted. Note `skipped` counts bars the
            // provider returned that we already held: that is a legitimate no-op, not a failure.
            if (inserted == 0 && skipped == 0) {
                // Three different causes end up here and they have three different fixes: no
                // provider is configured, a configured provider does not cover this instrument,
                // or the range predates the listing. This used to answer "set ALPACA_API_KEY /
                // TIINGO_API_TOKEN and restart" for ALL of them on the stock path — including
                // when several providers were configured and serving other tickers that same
                // second. A user bringing a ticker those providers simply do not carry (a
                // European listing, most often) was sent to re-check their .env and restart their
                // stack over a problem that was never theirs.
                //
                // `adapters` is the list actually discovered and tried, so we can tell the cases
                // apart: if it is non-empty, credentials are not the story and must not be named.
                // Discovery selects on capability, never on configuration, so an adapter that
                // needs a key is still discovered and tried without one — it just returns an
                // empty list, silently. That made `adapters` non-empty on a zero-credential
                // install and sent the message down the coverage-blaming path below, telling a
                // cold self-hoster that AAPL is "most likely not covered" by providers that were
                // never configured, and to go and pick a different ticker.
                List<MarketDataPort> configured = usable;
                if (configured.isEmpty()) {
                    String unconfigured = adapters.stream()
                        .map(MarketDataPort::getProviderName)
                        .collect(java.util.stream.Collectors.joining(", "));
                    String credentialsMsg = String.format(
                        "No price bars returned for %s: no market-data credentials are configured."
                            + " %d provider(s) could serve this request (%s) but none of them has"
                            + " credentials, so each was skipped. Set the API key for one of them"
                            + " and restart the backend. Crypto needs no key at all — if you are"
                            + " only backtesting crypto, use a bare asset symbol such as BTC.",
                        command.ticker(), adapters.size(), unconfigured);
                    LOG.warnf(credentialsMsg);
                    return BackFillResult.failure(command.ticker(), command.assetType(), credentialsMsg);
                }

                String providersTried = configured.stream()
                    .map(MarketDataPort::getProviderName)
                    .collect(java.util.stream.Collectors.joining(", "));
                // "Nobody carries this symbol" and "a provider fell over" lead to different next
                // moves — pick another ticker versus retry — so say which happened, without
                // pasting an exception the user cannot act on.
                String errorDetail = providerErrors.isEmpty() ? ""
                    : String.format(" %d of them errored rather than returning an empty result: %s.",
                        providerErrors.size(), String.join("; ", providerErrors));
                // A hint has to be true of the ticker in front of it. The crypto branch used to
                // print "write BTC, not BTC-USD" unconditionally, so FAKECOIN — correctly written,
                // bare, no quote currency — was told to remove a separator it did not have, when
                // the real answer is that the asset does not exist. Pair forms no longer reach
                // here at all (they are refused by TickerForm before any provider is called), so
                // this branch now says the thing that is actually left to say. The stock path had
                // no hint whatsoever, which is why ASML.AS (a real company on an unsupported
                // venue) and ZZZQQQ (nothing at all) produced identical text.
                String formatHint = command.isCrypto()
                    ? " Crypto tickers are bare asset symbols and always quote in USD, so the"
                        + " symbol itself is all a provider needs — if it is spelled correctly,"
                        + " no configured source carries this asset."
                    : " US listings are written bare (AAPL, BRK.B); a venue-qualified or suffixed"
                        + " form (AAPL.US, ASML.AS, ASML:XAMS) needs a provider covering that"
                        + " exchange — check get_market_data_status.";
                String errorMsg = String.format(
                    "No price bars returned for %s. Tried %d configured provider(s): %s —"
                        + " none returned data for this instrument.%s It is most likely not covered"
                        + " by them (they carry US-listed equities and major crypto; other"
                        + " exchanges are not served), or the range predates its listing.%s"
                        + " Check the symbol with find_ticker, or use a ticker one of these"
                        + " providers carries.",
                    command.ticker(), configured.size(), providersTried, errorDetail, formatHint);
                LOG.warnf(errorMsg);
                return BackFillResult.failure(command.ticker(), command.assetType(), errorMsg);
            }

            return BackFillResult.success(command.ticker(), command.assetType(), inserted, skipped, failed,
                newsInserted, newsSkipped);

        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid backfill command: %s", e.getMessage());
            return BackFillResult.failure(command.ticker(), command.assetType(), e.getMessage());
        } catch (Exception e) {
            // Reached only when the failure is not "no bars returned" — a repository fault, say.
            // Per-provider errors are handled in the loop and never arrive here.
            LOG.errorf(e, "Backfill failed for %s", command.ticker());
            return BackFillResult.failure(command.ticker(), command.assetType(),
                "Backfill failed for " + command.ticker() + ": " + summarize(e));
        }
    }

    /**
     * The part of an exception message a user can act on, with framework noise removed.
     *
     * <p>A REST-client failure arrives as {@code "alpaca returned HTTP 400, status code 400 when
     * invoking REST Client method: 'com.…AlpacaMarketDataClient#getCryptoBars'"}. The status code
     * is the useful half; the fully-qualified class and method name is an implementation detail
     * that reached users verbatim and told them nothing about what to do next.</p>
     */
    private static String summarize(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        int restClientNoise = message.indexOf(" when invoking REST Client method");
        if (restClientNoise > 0) {
            message = message.substring(0, restClientNoise);
        }
        // Any remaining package-qualified type name, e.g. a nested cause that was appended.
        message = message.replaceAll("\\b(?:[a-z][a-zA-Z0-9]*\\.){2,}[A-Z][A-Za-z0-9]*(?:#\\w+)?",
            "").replaceAll("\\s{2,}", " ").trim();
        return message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
