package com.quorumtrading.model.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Curated financial metrics for fundamental analysis of a stock.
 * Combines company profile, valuation ratios, growth, profitability,
 * financial health, earnings history, and analyst recommendations.
 *
 * Not applicable to crypto or ETFs — only individual stocks have these metrics.
 */
public record FinancialMetrics(
    // Company Profile
    String companyName,
    String industry,
    BigDecimal marketCapMillions,
    BigDecimal sharesOutstandingMillions,

    // Valuation
    BigDecimal peRatioTTM,
    BigDecimal forwardPE,
    BigDecimal priceToBook,
    BigDecimal priceToSales,
    BigDecimal evToEbitda,
    BigDecimal pegRatio,

    // Growth
    BigDecimal epsGrowthYoYPercent,
    BigDecimal epsGrowth5YPercent,
    BigDecimal revenueGrowthYoYPercent,
    BigDecimal revenueGrowth5YPercent,

    // Profitability
    BigDecimal grossMarginPercent,
    BigDecimal operatingMarginPercent,
    BigDecimal netProfitMarginPercent,
    BigDecimal returnOnEquityPercent,
    BigDecimal returnOnAssetsPercent,

    // Financial Health
    BigDecimal debtToEquity,
    BigDecimal currentRatio,
    BigDecimal quickRatio,

    // Per Share
    BigDecimal epsTTM,
    BigDecimal bookValuePerShare,
    BigDecimal dividendYieldPercent,

    // Risk
    BigDecimal beta,

    // Earnings History (most recent quarters)
    List<EarningsRecord> recentEarnings,

    // Analyst Recommendations
    AnalystConsensus analystConsensus,

    // Metadata
    Instant fetchedAt
) {

    public boolean hasValuationData() {
        return peRatioTTM != null || forwardPE != null || priceToBook != null;
    }

    public boolean hasEarningsData() {
        return recentEarnings != null && !recentEarnings.isEmpty();
    }

    public boolean hasAnalystData() {
        return analystConsensus != null && analystConsensus.totalAnalysts() > 0;
    }
}
