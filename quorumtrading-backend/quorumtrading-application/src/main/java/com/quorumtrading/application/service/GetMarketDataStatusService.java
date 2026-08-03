package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketDataPortDiscovery;
import com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase;
import com.quorumtrading.application.usecase.marketdata.MarketDataStatus;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Answers "do I have working price data, and for what?" from inside the product.
 *
 * <p>Modelled directly on the LLM status path, which is the one place in the product that both
 * reports configuration <em>and</em> proves it by calling out. Configuration alone cannot
 * distinguish a valid key from a revoked one, and that is exactly the case a self-hoster hits: the
 * key is present, the backfill returns nothing, and the failure looks like a coverage problem.</p>
 */
public class GetMarketDataStatusService implements GetMarketDataStatusUseCase {

    private static final Logger LOG = Logger.getLogger(GetMarketDataStatusService.class);

    /** Probe symbols: the most-covered instrument in each asset class, so a miss means the provider. */
    static final String CRYPTO_PROBE_SYMBOL = "BTC";
    static final String STOCK_PROBE_SYMBOL = "AAPL";

    /**
     * Probe window. Wide enough to span a long weekend or a market holiday — a 1-day window over a
     * Sunday returns nothing from a perfectly healthy equity provider, which would report a working
     * key as broken.
     */
    private static final Duration PROBE_WINDOW = Duration.ofDays(10);

    private final MarketDataPortDiscovery discovery;

    public GetMarketDataStatusService(MarketDataPortDiscovery discovery) {
        this.discovery = discovery;
    }

    @Override
    public MarketDataStatus get(boolean probe) {
        List<MarketDataStatus.ProviderStatus> providers = new ArrayList<>();
        boolean cryptoUsable = false;
        boolean stockUsable = false;

        // findAll, not a capability lookup: a provider that is switched off has to be visible
        // precisely so the answer to "why is my stock backfill failing?" can be "that one is off".
        for (MarketDataPort port : discovery.findAll()) {
            boolean servesCandles = port.hasFeature(MarketDataFeature.CANDLES)
                && port.hasFeature(MarketDataFeature.BACKFILL);
            if (!servesCandles) {
                // News/social sources are a different question and answering it here would
                // dilute the one this tool exists for.
                continue;
            }
            boolean crypto = port.hasFeature(MarketDataFeature.CRYPTO);
            boolean stock = port.hasFeature(MarketDataFeature.STOCK);
            boolean usable = port.isEnabled() && port.isConfigured();
            cryptoUsable |= usable && crypto;
            stockUsable |= usable && stock;

            List<String> assetClasses = new ArrayList<>();
            if (crypto) {
                assetClasses.add("CRYPTO");
            }
            if (stock) {
                assetClasses.add("STOCK");
            }

            providers.add(new MarketDataStatus.ProviderStatus(
                port.getProviderName(),
                port.isEnabled(),
                port.isConfigured(),
                List.copyOf(assetClasses),
                port.getSupportedFeatures().stream().map(Enum::name).sorted().toList(),
                probe && usable ? probe(port, crypto) : null));
        }

        return new MarketDataStatus(cryptoUsable, stockUsable, List.copyOf(providers));
    }

    private MarketDataStatus.Probe probe(MarketDataPort port, boolean crypto) {
        String symbol = crypto ? CRYPTO_PROBE_SYMBOL : STOCK_PROBE_SYMBOL;
        Instant end = Instant.now();
        Instant start = end.minus(PROBE_WINDOW);
        try {
            List<MarketEvent> events = crypto
                ? port.fetchDataInRange(Set.of(symbol), Set.of(), start, end, Granularity.ONE_DAY)
                : port.fetchDataInRange(Set.of(), Set.of(symbol), start, end, Granularity.ONE_DAY);
            int count = events == null ? 0 : events.size();
            if (count > 0) {
                return new MarketDataStatus.Probe(symbol, true,
                    "Returned " + count + " event(s) for " + symbol + " over the last 10 days.");
            }
            return new MarketDataStatus.Probe(symbol, false,
                "Answered without error but returned nothing for " + symbol
                    + " — the credentials may be present but not entitled to this data.");
        } catch (Exception e) {
            LOG.warnf("Market-data probe failed for provider %s: %s", port.getProviderName(), e.getMessage());
            return new MarketDataStatus.Probe(symbol, false,
                "Call failed: " + summarize(e));
        }
    }

    /** Same treatment as backfill errors: the actionable half, without the framework noise. */
    private static String summarize(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        int restClientNoise = message.indexOf(" when invoking REST Client method");
        if (restClientNoise > 0) {
            message = message.substring(0, restClientNoise);
        }
        message = message.replaceAll("\\b(?:[a-z][a-zA-Z0-9]*\\.){2,}[A-Z][A-Za-z0-9]*(?:#\\w+)?",
            "").replaceAll("\\s{2,}", " ").trim();
        return message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
