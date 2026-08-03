package com.quorumtrading.model.enums;

import java.util.List;
import java.util.Set;

/**
 * Enum representing the type of investment asset.
 * Used to categorize trading decisions by asset class.
 */
public enum InvestmentType {
    CRYPTO("Cryptocurrency", "Digital currencies like BTC, ETH"),
    STOCK("Stock", "Traditional equity securities");

    private final String displayName;
    private final String description;

    /**
     * Canonical set of crypto tickers for heuristic ticker→type classification.
     * Single source of truth: REST symbol parsing and backfill-job rehydration both
     * delegate to {@link #fromTicker(String)} so a symbol's asset class survives the
     * persist→reload round-trip (otherwise crypto symbols default to STOCK and get
     * fetched via the equity/weekday-only path, silently dropping crypto weekends).
     */
    private static final Set<String> CRYPTO_TICKERS = Set.of(
        "BTC", "ETH", "SOL", "DOGE", "LTC", "XRP", "ADA", "DOT", "LINK", "AVAX",
        "MATIC", "SHIB", "UNI", "ATOM", "XLM", "ALGO", "FIL", "NEAR", "APE", "SAND",
        "MANA", "AAVE", "CRV", "MKR", "SNX", "COMP", "YFI", "SUSHI", "ENJ", "BAT", "BNB"
    );

    InvestmentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** Quote currencies that may be concatenated onto a crypto base with no separator. */
    private static final List<String> CONCATENATED_QUOTES = List.of("USDT", "USDC", "USD");

    /**
     * Heuristically classify a ticker symbol as CRYPTO or STOCK. Normalises common
     * pair suffixes ("BTC/USD", "BTC-USD", "BTCUSD" → "BTC"). Unknown tickers default to STOCK.
     *
     * @param ticker the ticker symbol (e.g. "BTC", "AAPL", "ETH/USD", "SOLUSD"); null/blank → STOCK
     * @return CRYPTO if the (normalised) ticker is a known crypto, else STOCK
     */
    public static InvestmentType fromTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return STOCK;
        }
        String normalized = ticker.toUpperCase();
        int slash = normalized.indexOf('/');
        if (slash > 0) {
            normalized = normalized.substring(0, slash);
        } else if (normalized.endsWith("-USD") || normalized.endsWith("-USDT") || normalized.endsWith("-USDC")) {
            normalized = normalized.substring(0, normalized.indexOf('-'));
        }
        if (CRYPTO_TICKERS.contains(normalized)) {
            return CRYPTO;
        }
        // Separator-less pairs (SOLUSD, BTCUSDT). Only strip when the remainder is itself a
        // known crypto, so an equity whose ticker merely ends in those letters is untouched.
        for (String quote : CONCATENATED_QUOTES) {
            if (normalized.length() > quote.length() && normalized.endsWith(quote)) {
                String base = normalized.substring(0, normalized.length() - quote.length());
                if (CRYPTO_TICKERS.contains(base)) {
                    return CRYPTO;
                }
            }
        }
        return STOCK;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
