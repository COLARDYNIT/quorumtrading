package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.DividendInfo;

import java.util.Map;
import java.util.Set;

/**
 * Outbound port for fetching dividend information from external market data providers.
 *
 * <p><b>Hexagonal Architecture:</b> Outbound port (driven)</p>
 */
public interface DividendInfoPort {

    /**
     * Get dividend information for a set of tickers.
     * Crypto tickers should be short-circuited by the adapter (always return paysDividends=false).
     *
     * @param stockTickers  set of stock ticker symbols to check
     * @param cryptoTickers set of crypto ticker symbols (always non-dividend)
     * @return map of ticker symbol to DividendInfo
     */
    Map<String, DividendInfo> getDividendInfo(Set<String> stockTickers, Set<String> cryptoTickers);
}
