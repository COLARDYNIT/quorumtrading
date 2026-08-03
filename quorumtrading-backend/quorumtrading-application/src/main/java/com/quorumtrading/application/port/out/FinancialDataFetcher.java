package com.quorumtrading.application.port.out;

import com.quorumtrading.model.marketdata.FinancialMetrics;
import com.quorumtrading.model.valueobject.Symbol;

import java.util.Optional;

/**
 * Raw financial data fetcher — performs the actual API call to an external provider.
 * No caching. Implementations should be wrapped by a {@link FinancialDataPort}
 * that adds caching (e.g., Redis).
 */
public interface FinancialDataFetcher {

    Optional<FinancialMetrics> fetchFinancialMetrics(Symbol symbol);

    String getProviderName();
}
