package com.quorumtrading.adapter.marketdata.kraken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.marketdata.kraken.client.KrakenRestClient;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Keyless Kraken crypto price adapter — one of the zero-config out-of-the-box crypto sources.
 *
 * <p>Maps Kraken's public OHLC endpoint to {@link MarketEvent} bars. No credentials are sent. It is
 * a plain {@link MarketDataPort}: composition with other crypto sources (e.g. Coinbase) is handled
 * by the application layer's provider selection, not here. Any failure degrades to an empty list so
 * a flaky exchange never breaks a backfill.</p>
 *
 * <p>Crypto only: {@code getSupportedFeatures()} advertises {@code CRYPTO} (not {@code STOCK}).
 * Kraken REST returns at most ~720 candles per interval, so daily history is roughly two years.</p>
 */
@ApplicationScoped
public class KrakenMarketDataAdapter implements MarketDataPort {

    private static final Logger LOG = Logger.getLogger(KrakenMarketDataAdapter.class);
    private static final String PROVIDER_NAME = "kraken";

    /** Granularity → Kraken interval in minutes (absent = unsupported by Kraken). */
    private static final Map<Granularity, Integer> INTERVAL_MINUTES = Map.of(
            Granularity.ONE_MINUTE, 1,
            Granularity.FIVE_MINUTES, 5,
            Granularity.FIFTEEN_MINUTES, 15,
            Granularity.THIRTY_MINUTES, 30,
            Granularity.ONE_HOUR, 60,
            Granularity.FOUR_HOURS, 240,
            Granularity.ONE_DAY, 1440,
            Granularity.ONE_WEEK, 10080);

    /** Ticker → Kraken asset code where it differs from the common symbol. */
    private static final Map<String, String> ASSET_ALIAS = Map.of(
            "BTC", "XBT",
            "DOGE", "XDG");

    private final ObjectMapper mapper = new ObjectMapper();

    @RestClient
    KrakenRestClient client;

    @ConfigProperty(name = "quorumtrading.marketdata.kraken.enabled", defaultValue = "false")
    boolean enabled;

    @PostConstruct
    void init() {
        LOG.infof("Kraken market data adapter initialized (enabled=%s, features=%s)", enabled, getSupportedFeatures());
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
            LOG.debugf("Kraken market data adapter DISABLED - skipping fetch for crypto=%s", cryptoTickers);
            return Collections.emptyList();
        }
        if (cryptoTickers == null || cryptoTickers.isEmpty()) {
            return Collections.emptyList();
        }
        Granularity effective = granularity == null ? Granularity.ONE_DAY : granularity;
        Integer interval = INTERVAL_MINUTES.get(effective);
        if (interval == null) {
            // Kraken cannot serve this granularity (e.g. monthly) — let another provider handle it.
            return Collections.emptyList();
        }

        List<MarketEvent> events = new ArrayList<>();
        for (String ticker : cryptoTickers) {
            events.addAll(fetchTicker(ticker.toUpperCase(), interval, start, end));
        }
        LOG.infof("Kraken backfill: %d bars for %s [%s..%s @ %s]",
                events.size(), cryptoTickers, start, end, effective.getApiFormat());
        return events;
    }

    private List<MarketEvent> fetchTicker(String ticker, int interval, Instant start, Instant end) {
        String pair = ASSET_ALIAS.getOrDefault(ticker, ticker) + "USD";
        try {
            return parse(client.getOhlc(pair, interval, start.getEpochSecond()), ticker, start, end);
        } catch (Exception e) {
            LOG.warnf("Kraken OHLC fetch failed for %s (pair=%s): %s", ticker, pair, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MarketEvent> parse(String body, String ticker, Instant start, Instant end) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode error = root.get("error");
        if (error != null && error.isArray() && error.size() > 0) {
            LOG.warnf("Kraken returned error for %s: %s", ticker, error);
            return Collections.emptyList();
        }
        JsonNode result = root.get("result");
        if (result == null || !result.isObject()) {
            return Collections.emptyList();
        }
        // result is keyed by the canonical pair (e.g. "XXBTZUSD") plus a "last" cursor — take the series.
        JsonNode series = null;
        for (Iterator<String> it = result.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!"last".equals(field)) {
                series = result.get(field);
                break;
            }
        }
        if (series == null || !series.isArray()) {
            return Collections.emptyList();
        }
        List<MarketEvent> events = new ArrayList<>();
        for (JsonNode row : series) {
            if (!row.isArray() || row.size() < 7) {
                continue;
            }
            // [time, open, high, low, close, vwap, volume, count] — OHLC values are strings.
            Instant ts = Instant.ofEpochSecond(row.get(0).asLong());
            if (ts.isBefore(start) || ts.isAfter(end)) {
                continue;
            }
            events.add(buildEvent(ts, ticker,
                    parseNum(row.get(1)),   // open
                    parseNum(row.get(2)),   // high
                    parseNum(row.get(3)),   // low
                    parseNum(row.get(4)),   // close
                    parseNum(row.get(6)))); // volume
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

    private static double parseNum(JsonNode node) {
        return Double.parseDouble(node.asText());
    }
}
