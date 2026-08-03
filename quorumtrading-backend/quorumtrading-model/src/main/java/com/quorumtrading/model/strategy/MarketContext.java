package com.quorumtrading.model.strategy;

import com.quorumtrading.model.valueobject.Symbol;
import java.util.Objects;
import java.util.Set;

/**
 * Market context provided to strategy templates for markdown generation.
 * Contains tickers, sentiment, and rationale from market research.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 */
public record MarketContext(
    Symbol primaryTicker,
    Set<Symbol> stockTickers,
    Set<Symbol> cryptoTickers,
    String rationale
) {
    public MarketContext {
        Objects.requireNonNull(primaryTicker, "Primary ticker cannot be null");
        Objects.requireNonNull(stockTickers, "Stock tickers cannot be null");
        Objects.requireNonNull(cryptoTickers, "Crypto tickers cannot be null");
        Objects.requireNonNull(rationale, "Rationale cannot be null");

        stockTickers = Set.copyOf(stockTickers);
        cryptoTickers = Set.copyOf(cryptoTickers);
    }
}
