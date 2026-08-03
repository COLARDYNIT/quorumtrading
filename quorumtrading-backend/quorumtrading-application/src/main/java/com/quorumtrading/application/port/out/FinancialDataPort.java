package com.quorumtrading.application.port.out;

import com.quorumtrading.model.marketdata.FinancialMetrics;
import com.quorumtrading.model.valueobject.Symbol;

import java.util.Optional;

/**
 * Port for fetching structured financial data (valuation ratios, earnings,
 * analyst recommendations) for individual stocks.
 *
 * Returns empty for crypto, ETFs, or when the provider has no data.
 */
public interface FinancialDataPort {

    /**
     * Fetch curated financial metrics for a stock symbol.
     * Implementations should cache results (financial data changes slowly).
     *
     * @param symbol the stock symbol to look up
     * @return financial metrics, or empty if unavailable (crypto, ETF, API error)
     */
    Optional<FinancialMetrics> getFinancialMetrics(Symbol symbol);

    String getProviderName();
}
