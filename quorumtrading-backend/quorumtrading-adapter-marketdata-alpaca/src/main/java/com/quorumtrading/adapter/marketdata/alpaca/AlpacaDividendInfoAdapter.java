package com.quorumtrading.adapter.marketdata.alpaca;

import com.quorumtrading.adapter.marketdata.alpaca.client.AlpacaMarketDataClient;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.CorporateActionsResponse;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.CorporateActionsResponse.CashDividend;
import com.quorumtrading.application.port.out.DividendInfoPort;
import com.quorumtrading.model.valueobject.DividendInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alpaca implementation of DividendInfoPort.
 * Queries the Alpaca Corporate Actions API for trailing 12-month cash dividend data.
 * Results are cached in-memory for 24 hours.
 */
@ApplicationScoped
public class AlpacaDividendInfoAdapter implements DividendInfoPort {

    private static final Logger LOG = Logger.getLogger(AlpacaDividendInfoAdapter.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    @RestClient
    AlpacaMarketDataClient marketDataClient;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.api-key", defaultValue = "not-configured")
    String apiKey;

    @ConfigProperty(name = "quorumtrading.marketdata.alpaca.api-secret", defaultValue = "not-configured")
    String apiSecret;

    /** Simple TTL cache: ticker → (DividendInfo, expiryTimestamp) */
    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

    private boolean credentialsConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey)
                && apiSecret != null && !apiSecret.isBlank() && !"not-configured".equals(apiSecret);
    }

    @Override
    public Map<String, DividendInfo> getDividendInfo(Set<String> stockTickers, Set<String> cryptoTickers) {
        Map<String, DividendInfo> result = new HashMap<>();

        // Crypto tickers short-circuit — never call API
        for (String ticker : cryptoTickers) {
            result.put(ticker, DividendInfo.crypto(ticker));
        }

        if (stockTickers.isEmpty()) {
            return result;
        }

        // Without real credentials, skip the corporate-actions call instead of hitting Alpaca with
        // the "not-configured" placeholder and logging a 401. Stock dividend info stays unknown
        // (enrichment is best-effort and tolerates its absence) — keeps zero-key boots quiet.
        if (!credentialsConfigured()) {
            LOG.warnf("Alpaca dividend info credentials not configured (ALPACA_API_KEY/ALPACA_API_SECRET) — skipping corporate-actions fetch for %d stock ticker(s)", stockTickers.size());
            return result;
        }

        // Check cache first, collect uncached tickers
        Set<String> uncached = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        for (String ticker : stockTickers) {
            CachedEntry entry = cache.get(ticker);
            if (entry != null && entry.expiry > now) {
                result.put(ticker, entry.info);
            } else {
                uncached.add(ticker);
            }
        }

        if (uncached.isEmpty()) {
            return result;
        }

        // Fetch from Alpaca Corporate Actions API
        try {
            Map<String, DividendInfo> fetched = fetchDividendInfoFromAlpaca(uncached);
            for (Map.Entry<String, DividendInfo> entry : fetched.entrySet()) {
                cache.put(entry.getKey(), new CachedEntry(entry.getValue(), now + CACHE_TTL_MS));
                result.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            LOG.warnf("Failed to fetch dividend info from Alpaca for %s: %s", uncached, e.getMessage());
            // Graceful degradation: return unknown for failed tickers
            for (String ticker : uncached) {
                DividendInfo unknown = DividendInfo.unknown(ticker, "STOCK");
                result.put(ticker, unknown);
            }
        }

        return result;
    }

    private Map<String, DividendInfo> fetchDividendInfoFromAlpaca(Set<String> tickers) {
        String symbols = String.join(",", tickers);
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = endDate.minusYears(1);

        List<CashDividend> allDividends = new ArrayList<>();
        String pageToken = null;

        // Paginate through all results
        do {
            CorporateActionsResponse response;
            try {
                response = marketDataClient.getCorporateActions(
                    symbols, "cash_dividend",
                    startDate.format(DATE_FMT), endDate.format(DATE_FMT),
                    pageToken);
            } catch (WebApplicationException e) {
                LOG.warnf("Alpaca corporate actions API error (HTTP %d): %s",
                    e.getResponse().getStatus(), e.getMessage());
                throw e;
            }

            if (response != null && response.getCorporateActions() != null
                    && response.getCorporateActions().getCashDividends() != null) {
                allDividends.addAll(response.getCorporateActions().getCashDividends());
            }
            pageToken = response != null ? response.getNextPageToken() : null;
        } while (pageToken != null && !pageToken.isEmpty());

        // Group dividends by symbol
        Map<String, List<CashDividend>> bySymbol = new HashMap<>();
        for (CashDividend div : allDividends) {
            bySymbol.computeIfAbsent(div.getSymbol(), k -> new ArrayList<>()).add(div);
        }

        // Build DividendInfo for each ticker
        Map<String, DividendInfo> result = new HashMap<>();
        for (String ticker : tickers) {
            List<CashDividend> dividends = bySymbol.getOrDefault(ticker, List.of());
            if (dividends.isEmpty()) {
                result.put(ticker, DividendInfo.noDividends(ticker));
            } else {
                // Sum annual dividends per share and estimate yield
                BigDecimal totalDividendsPerShare = dividends.stream()
                    .map(CashDividend::getRate)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                // We don't have current price here, so store the per-share total and count
                // The yield estimate will be calculated separately if a current price is available
                result.put(ticker, DividendInfo.withDividends(
                    ticker, totalDividendsPerShare, dividends.size()));
            }
        }

        LOG.debugf("Fetched dividend info for %d tickers: %d pay dividends",
            tickers.size(), result.values().stream().filter(DividendInfo::paysDividendsKnown).count());

        return result;
    }

    private record CachedEntry(DividendInfo info, long expiry) {}
}
