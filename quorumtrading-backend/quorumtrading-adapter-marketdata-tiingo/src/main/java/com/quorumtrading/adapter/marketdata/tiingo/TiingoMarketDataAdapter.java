package com.quorumtrading.adapter.marketdata.tiingo;

import com.quorumtrading.adapter.marketdata.tiingo.client.TiingoRestClient;
import com.quorumtrading.adapter.marketdata.tiingo.client.dto.TiingoPriceBar;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.exception.MarketDataFetchException;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.TickerRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tiingo market data adapter for fetching historical daily stock prices.
 *
 * Tiingo provides 30+ years of split+dividend adjusted daily stock data,
 * serving as a fallback provider when Alpaca's ~5.5 year IEX history is insufficient.
 *
 * Capabilities:
 * - Daily, weekly, monthly stock bars (NO intraday, NO crypto)
 * - Split and dividend adjusted prices (adjOpen, adjHigh, adjLow, adjClose)
 * - Data back to ~1993 for most major US stocks
 *
 * Limitations:
 * - Single ticker per API request (iterated for multi-ticker backfill)
 * - Free tier: 50 requests/hour, 1000/day, 500 unique symbols/month
 * - No real-time or streaming data
 * - No cryptocurrency support
 */
@ApplicationScoped
public class TiingoMarketDataAdapter implements MarketDataPort {

    private static final Logger LOG = Logger.getLogger(TiingoMarketDataAdapter.class);
    private static final String PROVIDER_NAME = "tiingo";

    @RestClient
    TiingoRestClient tiingoClient;

    /** Config default for the API token — a non-blank sentinel, so it must never count as a real key. */
    static final String TOKEN_NOT_CONFIGURED = "not-configured";

    @ConfigProperty(name = "quorumtrading.marketdata.tiingo.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "quorumtrading.marketdata.tiingo.api-token", defaultValue = TOKEN_NOT_CONFIGURED)
    String apiToken;

    @jakarta.annotation.PostConstruct
    void init() {
        LOG.infof("Tiingo market data adapter initialized: enabled=%s, configured=%s, features=%s",
                 enabled, isConfigured(), getSupportedFeatures());
    }

    /**
     * The adapter ships enabled by default, but without a token there is nothing to call. Treating
     * the {@code not-configured} placeholder as a real key made every keyless stock backfill fail
     * with an opaque "Authentication failed for tiingo (HTTP 403)" — which reads as a broken key to
     * a user who never had one. Report it as unconfigured instead, like the other adapters do.
     */
    @Override
    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank() && !TOKEN_NOT_CONFIGURED.equals(apiToken);
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
        return Set.of(
            MarketDataFeature.STOCK,
            MarketDataFeature.BACKFILL,
            MarketDataFeature.CANDLES,
            MarketDataFeature.DAILY
        );
    }

    @Override
    public List<MarketEvent> fetchLatestData() {
        // Tiingo does not support real-time/latest data fetching
        return Collections.emptyList();
    }

    @Override
    public List<MarketEvent> fetchLatestDataForTickers(TickerRegistry tickerRegistry) {
        // Tiingo does not support real-time/latest data fetching
        return Collections.emptyList();
    }

    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end) {
        return fetchDataInRange(cryptoTickers, stockTickers, start, end, Granularity.ONE_DAY);
    }

    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end,
            Granularity granularity) {

        if (!enabled) {
            LOG.warnf("Tiingo market data adapter DISABLED - skipping historical fetch for stocks=%s", stockTickers);
            return Collections.emptyList();
        }

        if (!isConfigured()) {
            LOG.warnf("Tiingo market data adapter not configured (no TIINGO_API_TOKEN)"
                + " - skipping historical fetch for stocks=%s", stockTickers);
            return Collections.emptyList();
        }

        // Tiingo only supports daily or greater granularity
        if (granularity != null && granularity.isIntraday()) {
            LOG.debugf("Tiingo does not support intraday granularity (%s), returning empty", granularity);
            return Collections.emptyList();
        }

        // Tiingo does not support crypto
        if (stockTickers == null || stockTickers.isEmpty()) {
            LOG.debugf("No stock tickers provided for Tiingo historical fetch");
            return Collections.emptyList();
        }

        Granularity effectiveGranularity = (granularity == null) ? Granularity.ONE_DAY : granularity;
        String resampleFreq = toTiingoFrequency(effectiveGranularity);

        String startDate = formatDate(start);
        String endDate = formatDate(end);

        LOG.infof("Fetching historical stock data from Tiingo for %d tickers from %s to %s (%s)",
            stockTickers.size(), startDate, endDate, resampleFreq);

        List<MarketEvent> allEvents = new ArrayList<>();

        // Tiingo requires one API call per ticker
        for (String ticker : stockTickers) {
            try {
                List<MarketEvent> tickerEvents = fetchTickerData(ticker, startDate, endDate, resampleFreq);
                allEvents.addAll(tickerEvents);
                LOG.debugf("Fetched %d bars for %s from Tiingo", tickerEvents.size(), ticker);
            } catch (Exception e) {
                throw toMarketDataFetchException(e, ticker);
            }
        }

        LOG.infof("Fetched %d total historical bars from Tiingo for %d tickers (%s to %s at %s)",
            allEvents.size(), stockTickers.size(), startDate, endDate, effectiveGranularity.getDisplayName());

        return allEvents;
    }

    private List<TiingoPriceBar> fetchPricesFromApi(String ticker, String startDate, String endDate, String resampleFreq) {
        return tiingoClient.getDailyPrices(ticker, startDate, endDate, resampleFreq);
    }

    private List<MarketEvent> fetchTickerData(String ticker, String startDate, String endDate, String resampleFreq) {
        List<TiingoPriceBar> bars = fetchPricesFromApi(ticker, startDate, endDate, resampleFreq);

        if (bars == null || bars.isEmpty()) {
            LOG.warnf("No data returned from Tiingo for %s (%s to %s)", ticker, startDate, endDate);
            return Collections.emptyList();
        }

        List<MarketEvent> events = new ArrayList<>(bars.size());

        for (TiingoPriceBar bar : bars) {
            if (bar.getAdjClose() == null) {
                continue;
            }

            Map<String, Object> rawData = new HashMap<>();
            rawData.put("price", bar.getAdjClose());
            rawData.put("open", bar.getAdjOpen());
            rawData.put("high", bar.getAdjHigh());
            rawData.put("low", bar.getAdjLow());
            rawData.put("close", bar.getAdjClose());
            rawData.put("volume", bar.getAdjVolume());

            MarketEvent event = new MarketEvent(
                parseTimestamp(bar.getDate()),
                EventType.PRICE,
                PROVIDER_NAME,
                ticker,
                rawData
            );
            events.add(event);
        }

        return events;
    }

    private String toTiingoFrequency(Granularity granularity) {
        return switch (granularity) {
            case ONE_DAY -> "daily";
            case ONE_WEEK -> "weekly";
            case ONE_MONTH -> "monthly";
            default -> "daily";
        };
    }

    private String formatDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC).toString(); // YYYY-MM-DD
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return Instant.now();
        }
        try {
            return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant();
        } catch (Exception e) {
            try {
                // Fallback: try parsing as date only
                return LocalDate.parse(timestamp).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception e2) {
                LOG.warnf("Failed to parse Tiingo timestamp: %s, using current time", timestamp);
                return Instant.now();
            }
        }
    }

    private MarketDataFetchException toMarketDataFetchException(Exception e, String ticker) {
        if (e instanceof MarketDataFetchException mfe) {
            return mfe;
        }

        if (e instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            String message = String.format("ticker=%s: %s", ticker, wae.getMessage());

            if (status == 429) {
                LOG.warnf("Rate limited by Tiingo (HTTP 429) for ticker: %s", ticker);
                return MarketDataFetchException.rateLimited(PROVIDER_NAME);
            }
            if (status == 401 || status == 403) {
                LOG.errorf("Auth failed for Tiingo (HTTP %d) for ticker: %s", status, ticker);
                return MarketDataFetchException.authFailed(PROVIDER_NAME, status);
            }
            return MarketDataFetchException.fromHttpStatus(PROVIDER_NAME, status, message);
        }

        LOG.errorf(e, "Connection/transport failure fetching Tiingo data for ticker: %s", ticker);
        return MarketDataFetchException.connectionFailed(PROVIDER_NAME, e);
    }
}
