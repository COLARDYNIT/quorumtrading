package com.quorumtrading.adapter.marketdata.finnhub;

import com.quorumtrading.application.port.out.FinancialDataFetcher;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.AnalystConsensus;
import com.quorumtrading.model.marketdata.EarningsRecord;
import com.quorumtrading.model.marketdata.FinancialMetrics;
import com.quorumtrading.model.valueobject.Symbol;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches structured financial data from Finnhub for fundamental analysis.
 * Combines 4 endpoints: /stock/metric, /stock/profile2, /stock/earnings, /stock/recommendation.
 *
 * Only works for individual stocks — returns empty for crypto and ETFs.
 * No caching — caching is handled by the FinancialDataPort decorator (Redis).
 */
@ApplicationScoped
public class FinnhubFinancialDataAdapter implements FinancialDataFetcher {

    private static final Logger LOG = Logger.getLogger(FinnhubFinancialDataAdapter.class);
    private static final long RATE_LIMIT_MS = 300;

    @Inject
    @RestClient
    FinnhubRestClient restClient;

    @ConfigProperty(name = "quorumtrading.providers.finnhub.api-key", defaultValue = "")
    String apiKey;

    @Override
    public Optional<FinancialMetrics> fetchFinancialMetrics(Symbol symbol) {
        if (apiKey.isBlank() || "not-configured".equals(apiKey) || symbol.type() != InvestmentType.STOCK) {
            return Optional.empty();
        }

        try {
            LOG.infof("FinnhubFinancialData: fetching metrics for %s", symbol.ticker());

            Map<String, Object> metricResponse = restClient.getStockMetrics(symbol.ticker(), "all", apiKey);
            rateLimitPause();

            Map<String, Object> profileResponse = restClient.getCompanyProfile(symbol.ticker(), apiKey);
            rateLimitPause();

            List<Map<String, Object>> earningsResponse = restClient.getEarnings(symbol.ticker(), apiKey);
            rateLimitPause();

            List<Map<String, Object>> recommendationResponse = restClient.getRecommendation(symbol.ticker(), apiKey);

            // If profile is empty, this is likely an ETF — skip
            if (profileResponse == null || profileResponse.isEmpty()) {
                LOG.infof("FinnhubFinancialData: no profile data for %s (likely ETF), skipping", symbol.ticker());
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = metricResponse != null
                ? (Map<String, Object>) metricResponse.getOrDefault("metric", Map.of())
                : Map.of();

            FinancialMetrics result = buildFinancialMetrics(
                metrics, profileResponse, earningsResponse, recommendationResponse);

            return Optional.of(result);
        } catch (Exception e) {
            LOG.warnf("FinnhubFinancialData: failed to fetch metrics for %s: %s",
                symbol.ticker(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String getProviderName() {
        return "Finnhub Financial";
    }

    private FinancialMetrics buildFinancialMetrics(
            Map<String, Object> metrics,
            Map<String, Object> profile,
            List<Map<String, Object>> earnings,
            List<Map<String, Object>> recommendations) {

        return new FinancialMetrics(
            // Company Profile
            getString(profile, "name"),
            getString(profile, "finnhubIndustry"),
            getBigDecimal(profile, "marketCapitalization"),
            getBigDecimal(profile, "shareOutstanding"),

            // Valuation
            getBigDecimal(metrics, "peTTM"),
            getBigDecimal(metrics, "forwardPE"),
            getBigDecimal(metrics, "pbAnnual"),
            getBigDecimal(metrics, "psAnnual"),
            getBigDecimal(metrics, "evEbitdaTTM"),
            getBigDecimal(metrics, "pegTTM"),

            // Growth
            getBigDecimal(metrics, "epsGrowthQuarterlyYoy"),
            getBigDecimal(metrics, "epsGrowth5Y"),
            getBigDecimal(metrics, "revenueGrowthQuarterlyYoy"),
            getBigDecimal(metrics, "revenueGrowth5Y"),

            // Profitability
            getBigDecimal(metrics, "grossMarginAnnual"),
            getBigDecimal(metrics, "operatingMarginAnnual"),
            getBigDecimal(metrics, "netProfitMarginAnnual"),
            getBigDecimal(metrics, "roeTTM"),
            getBigDecimal(metrics, "roaTTM"),

            // Financial Health
            getBigDecimal(metrics, "totalDebt/totalEquityAnnual"),
            getBigDecimal(metrics, "currentRatioAnnual"),
            getBigDecimal(metrics, "quickRatioAnnual"),

            // Per Share
            getBigDecimal(metrics, "epsTTM"),
            getBigDecimal(metrics, "bookValuePerShareAnnual"),
            getBigDecimal(metrics, "currentDividendYieldTTM"),

            // Risk
            getBigDecimal(metrics, "beta"),

            // Earnings
            parseEarnings(earnings),

            // Analyst
            parseRecommendation(recommendations),

            // Metadata
            Instant.now()
        );
    }

    private List<EarningsRecord> parseEarnings(List<Map<String, Object>> earnings) {
        if (earnings == null || earnings.isEmpty()) {
            return List.of();
        }
        List<EarningsRecord> records = new ArrayList<>();
        int limit = Math.min(earnings.size(), 4);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> e = earnings.get(i);
            records.add(new EarningsRecord(
                getString(e, "period"),
                getInt(e, "quarter"),
                getInt(e, "year"),
                getBigDecimal(e, "actual"),
                getBigDecimal(e, "estimate"),
                getBigDecimal(e, "surprise"),
                getBigDecimal(e, "surprisePercent")
            ));
        }
        return List.copyOf(records);
    }

    private AnalystConsensus parseRecommendation(List<Map<String, Object>> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return null;
        }
        // Take the most recent month
        Map<String, Object> latest = recommendations.get(0);
        return new AnalystConsensus(
            getString(latest, "period"),
            getInt(latest, "strongBuy"),
            getInt(latest, "buy"),
            getInt(latest, "hold"),
            getInt(latest, "sell"),
            getInt(latest, "strongSell")
        );
    }

    private void rateLimitPause() {
        try {
            Thread.sleep(RATE_LIMIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        return 0;
    }

}
