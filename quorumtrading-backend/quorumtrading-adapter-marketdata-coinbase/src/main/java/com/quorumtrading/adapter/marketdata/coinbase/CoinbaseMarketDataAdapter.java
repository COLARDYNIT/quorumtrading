package com.quorumtrading.adapter.marketdata.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.marketdata.coinbase.client.CoinbaseRestClient;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Keyless Coinbase Exchange crypto price adapter — one of the zero-config out-of-the-box crypto
 * sources, and the deeper-daily-history one (Coinbase serves daily bars back to ~2016).
 *
 * <p>Maps Coinbase Exchange's public candles endpoint to {@link MarketEvent} bars. No credentials
 * are sent. It is a plain {@link MarketDataPort}: composition with other crypto sources (e.g.
 * Kraken) is handled by the application layer's provider selection, not here. Any failure degrades
 * to an empty list so a flaky exchange never breaks a backfill.</p>
 *
 * <p>Crypto only: {@code getSupportedFeatures()} advertises {@code CRYPTO} (not {@code STOCK}).
 * Coinbase returns at most 300 candles per request, so history is walked in paginated windows.</p>
 */
@ApplicationScoped
public class CoinbaseMarketDataAdapter implements MarketDataPort {

    private static final Logger LOG = Logger.getLogger(CoinbaseMarketDataAdapter.class);
    private static final String PROVIDER_NAME = "coinbase";

    /** Coinbase returns at most 300 candles per request. */
    private static final int MAX_CANDLES = 300;

    /** Granularity → Coinbase granularity in seconds (absent = unsupported by Coinbase). */
    private static final Map<Granularity, Integer> GRANULARITY_SECONDS = Map.of(
            Granularity.ONE_MINUTE, 60,
            Granularity.FIVE_MINUTES, 300,
            Granularity.FIFTEEN_MINUTES, 900,
            Granularity.ONE_HOUR, 3600,
            Granularity.ONE_DAY, 86400);

    private final ObjectMapper mapper = new ObjectMapper();

    @RestClient
    CoinbaseRestClient client;

    @ConfigProperty(name = "quorumtrading.marketdata.coinbase.enabled", defaultValue = "false")
    boolean enabled;

    @PostConstruct
    void init() {
        LOG.infof("Coinbase market data adapter initialized (enabled=%s, features=%s)", enabled, getSupportedFeatures());
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        return EnumSet.of(
                MarketDataFeature.CRYPTO,
                MarketDataFeature.BACKFILL,
                MarketDataFeature.CANDLES,
                MarketDataFeature.INTRADAY,
                MarketDataFeature.DAILY);
    }

    @Override
    @Deprecated
    public List<MarketEvent> fetchLatestData() {
        return Collections.emptyList();
    }

    @Override
    public List<MarketEvent> fetchDataInRange(Set<String> cryptoTickers,
                                              Set<String> stockTickers,
                                              Instant start,
                                              Instant end,
                                              Granularity granularity) {
        if (!enabled) {
            LOG.debugf("Coinbase market data adapter DISABLED - skipping fetch for crypto=%s", cryptoTickers);
            return Collections.emptyList();
        }
        if (cryptoTickers == null || cryptoTickers.isEmpty()) {
            return Collections.emptyList();
        }
        Granularity effective = granularity == null ? Granularity.ONE_DAY : granularity;
        Integer seconds = GRANULARITY_SECONDS.get(effective);
        if (seconds == null) {
            // Coinbase cannot serve this granularity (e.g. 4h/weekly/monthly) — yield to another provider.
            return Collections.emptyList();
        }

        List<MarketEvent> events = new ArrayList<>();
        for (String ticker : cryptoTickers) {
            events.addAll(fetchTicker(ticker.toUpperCase(), seconds, start, end));
        }
        LOG.infof("Coinbase backfill: %d bars for %s [%s..%s @ %s]",
                events.size(), cryptoTickers, start, end, effective.getApiFormat());
        return events;
    }

    private List<MarketEvent> fetchTicker(String ticker, int seconds, Instant start, Instant end) {
        String product = ticker + "-USD";
        long windowSpan = (long) seconds * MAX_CANDLES;
        // De-dup by bar timestamp — adjacent pagination windows can share a boundary candle.
        Map<Long, MarketEvent> byTimestamp = new LinkedHashMap<>();
        Instant windowEnd = end;
        int guard = 0;
        while (windowEnd.isAfter(start) && guard++ < 120) {
            Instant windowStart = windowEnd.minusSeconds(windowSpan);
            if (windowStart.isBefore(start)) {
                windowStart = start;
            }
            try {
                String body = client.getCandles(product, seconds, windowStart.toString(), windowEnd.toString());
                List<MarketEvent> batch = parse(body, ticker, start, end);
                if (batch.isEmpty()) {
                    break;
                }
                for (MarketEvent e : batch) {
                    byTimestamp.putIfAbsent(e.timestamp().getEpochSecond(), e);
                }
            } catch (Exception e) {
                LOG.warnf("Coinbase candles fetch failed for %s (product=%s): %s", ticker, product, e.getMessage());
                break;
            }
            if (windowStart.equals(start)) {
                break;
            }
            windowEnd = windowStart;
        }
        return new ArrayList<>(byTimestamp.values());
    }

    private List<MarketEvent> parse(String body, String ticker, Instant start, Instant end) throws Exception {
        JsonNode root = mapper.readTree(body);
        if (root == null || !root.isArray()) {
            // Coinbase returns an object like {"message": "..."} on error.
            if (root != null && root.has("message")) {
                LOG.warnf("Coinbase returned error for %s: %s", ticker, root.get("message").asText());
            }
            return Collections.emptyList();
        }
        List<MarketEvent> events = new ArrayList<>();
        for (JsonNode row : root) {
            if (!row.isArray() || row.size() < 6) {
                continue;
            }
            // [time, low, high, open, close, volume] — note low/high precede open/close.
            Instant ts = Instant.ofEpochSecond(row.get(0).asLong());
            if (ts.isBefore(start) || ts.isAfter(end)) {
                continue;
            }
            events.add(buildEvent(ts, ticker,
                    row.get(3).asDouble(),   // open
                    row.get(2).asDouble(),   // high
                    row.get(1).asDouble(),   // low
                    row.get(4).asDouble(),   // close
                    row.get(5).asDouble())); // volume
        }
        return events;
    }

    private MarketEvent buildEvent(Instant timestamp, String ticker,
                                   double open, double high, double low, double close, double volume) {
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("price", close);
        rawData.put("open", open);
        rawData.put("high", high);
        rawData.put("low", low);
        rawData.put("close", close);
        rawData.put("volume", volume);
        rawData.put("symbol", ticker + "/USD");
        return new MarketEvent(timestamp, EventType.PRICE, PROVIDER_NAME, ticker, rawData);
    }
}
