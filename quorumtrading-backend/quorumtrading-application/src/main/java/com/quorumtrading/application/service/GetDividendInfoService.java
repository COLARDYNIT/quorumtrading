package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.DividendInfoPort;
import com.quorumtrading.application.usecase.GetDividendInfoUseCase;
import com.quorumtrading.application.usecase.query.GetDividendInfoQuery;
import com.quorumtrading.model.valueobject.DividendInfo;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service that fetches dividend information for tickers, separating stock from crypto.
 * Crypto tickers are short-circuited (always non-dividend), stock tickers are resolved via the port.
 */
public class GetDividendInfoService implements GetDividendInfoUseCase {

    private static final Logger LOG = Logger.getLogger(GetDividendInfoService.class.getName());

    /** Well-known crypto tickers — same set as Symbol.KNOWN_CRYPTO_TICKERS. */
    private static final Set<String> KNOWN_CRYPTO_TICKERS = Set.of(
        "BTC", "ETH", "ADA", "SOL", "XRP", "DOGE", "DOT", "MATIC", "AVAX", "LINK",
        "UNI", "ATOM", "LTC", "BCH", "XLM", "ALGO", "VET", "FIL", "TRX", "ETC",
        "HBAR", "ICP", "NEAR", "APT", "ARB", "OP", "SUI", "SEI", "SHIB", "PEPE"
    );

    private final DividendInfoPort dividendInfoPort;

    public GetDividendInfoService(DividendInfoPort dividendInfoPort) {
        this.dividendInfoPort = dividendInfoPort;
    }

    @Override
    public Map<String, DividendInfo> execute(GetDividendInfoQuery query) {
        Set<String> stockTickers = new LinkedHashSet<>();
        Set<String> cryptoTickers = new LinkedHashSet<>();

        for (String ticker : query.tickers()) {
            String normalized = ticker.toUpperCase().trim();
            if (KNOWN_CRYPTO_TICKERS.contains(normalized)) {
                cryptoTickers.add(normalized);
            } else {
                stockTickers.add(normalized);
            }
        }

        try {
            return dividendInfoPort.getDividendInfo(stockTickers, cryptoTickers);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch dividend info, returning unknown status: {0}", e.getMessage());
            // Graceful degradation: return unknown for all tickers
            Map<String, DividendInfo> fallback = new HashMap<>();
            for (String ticker : stockTickers) {
                fallback.put(ticker, DividendInfo.unknown(ticker, "STOCK"));
            }
            for (String ticker : cryptoTickers) {
                fallback.put(ticker, DividendInfo.crypto(ticker));
            }
            return fallback;
        }
    }
}
