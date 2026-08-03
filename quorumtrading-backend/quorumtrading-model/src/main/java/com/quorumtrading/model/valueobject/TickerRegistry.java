package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable registry of tickers required across all strategies.
 *
 * <p>This value object aggregates stock and crypto symbols from all active strategies,
 * providing a unified view of what market data needs to be collected.</p>
 *
 * <p><b>Domain-Driven Design:</b></p>
 * Uses {@link Symbol} value objects (not raw strings) to maintain type safety and
 * encapsulate domain rules about ticker symbols.
 *
 * <p><b>Usage:</b></p>
 * <ul>
 *   <li>Market data adapters query this to know which symbols to fetch</li>
 *   <li>Application layer builds this from all Strategy entities</li>
 *   <li>Each adapter filters by their supported asset types (crypto vs stock)</li>
 * </ul>
 *
 * @param cryptoTickers Set of cryptocurrency Symbol value objects (e.g., Symbol("BTC", CRYPTO))
 * @param stockTickers Set of stock Symbol value objects (e.g., Symbol("AAPL", STOCK))
 */
public record TickerRegistry(
    Set<Symbol> cryptoTickers,
    Set<Symbol> stockTickers
) {
    /**
     * Compact constructor ensures immutability by creating defensive copies, and
     * drops any stock ticker that is a known crypto symbol.
     *
     * <p><b>Why the asset classes cannot overlap.</b> A price bar is stored under a bare
     * ticker string, and its asset class is re-derived from that string on write
     * ({@code InvestmentType.fromTicker}) because {@code MarketEvent} carries no asset
     * class of its own. So a quote fetched through an <em>equity</em> feed for a symbol
     * that {@code fromTicker} calls CRYPTO is filed into the <em>crypto</em> price series
     * for that ticker — the equity feed's answer silently becomes the coin's price.</p>
     *
     * <p>This is not hypothetical: one strategy declaring {@code stock_tickers: [BTC]} put
     * BTC into the stock universe for the whole instance, the equity feed answered with
     * the NYSE-listed spot-bitcoin ETF that also trades as "BTC" (~$28), and every LIVE
     * crypto simulation holding BTC was revalued from ~$64,000 to ~$28 a unit — reporting
     * a total loss on the position as an ordinary market move.</p>
     *
     * <p>Until a market event carries its own asset class, the registry refuses the
     * overlap. Crypto wins, because {@code fromTicker} would classify the symbol as
     * crypto at write time regardless of which feed produced it.</p>
     */
    public TickerRegistry {
        cryptoTickers = cryptoTickers != null ? Set.copyOf(cryptoTickers) : Set.of();
        stockTickers = stockTickers != null
            ? stockTickers.stream()
                .filter(s -> s != null
                    && InvestmentType.fromTicker(s.ticker()) != InvestmentType.CRYPTO)
                .collect(Collectors.toUnmodifiableSet())
            : Set.of();
    }

    /**
     * Stock tickers that were rejected as known crypto symbols — for callers that want to
     * tell the user which strategy configuration is being ignored and why.
     *
     * @param requestedStockTickers the stock symbols a caller intended to register
     * @return the subset that {@link #TickerRegistry} would drop (never null)
     */
    public static Set<Symbol> rejectedStockTickers(Set<Symbol> requestedStockTickers) {
        if (requestedStockTickers == null) {
            return Set.of();
        }
        return requestedStockTickers.stream()
            .filter(s -> s != null && InvestmentType.fromTicker(s.ticker()) == InvestmentType.CRYPTO)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Create an empty ticker registry (no tickers configured).
     * Useful as a null object pattern.
     */
    public static TickerRegistry empty() {
        return new TickerRegistry(Set.of(), Set.of());
    }

    /**
     * Checks if the registry has no tickers at all.
     * @return true if both crypto and stock ticker sets are empty
     */
    public boolean isEmpty() {
        return cryptoTickers.isEmpty() && stockTickers.isEmpty();
    }

    /**
     * Get combined set of all symbols (both crypto and stock).
     * Useful for logging or general queries.
     *
     * @return Immutable set containing all unique Symbol value objects
     */
    public Set<Symbol> getAllSymbols() {
        Set<Symbol> all = new HashSet<>(cryptoTickers);
        all.addAll(stockTickers);
        return Set.copyOf(all);
    }

    /**
     * Get total count of unique symbols across both asset types.
     */
    public int getTotalCount() {
        return cryptoTickers.size() + stockTickers.size();
    }

    /**
     * Convert crypto symbols to ticker strings for adapter compatibility.
     * Adapters (outer layer) still use strings for API calls.
     *
     * @return Set of crypto ticker strings (e.g., "BTC", "ETH")
     */
    public Set<String> getCryptoTickerStrings() {
        return cryptoTickers.stream()
            .map(Symbol::ticker)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Convert stock symbols to ticker strings for adapter compatibility.
     * Adapters (outer layer) still use strings for API calls.
     *
     * @return Set of stock ticker strings (e.g., "AAPL", "TSLA")
     */
    public Set<String> getStockTickerStrings() {
        return stockTickers.stream()
            .map(Symbol::ticker)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String toString() {
        return String.format("TickerRegistry[crypto=%d (%s), stock=%d (%s)]",
            cryptoTickers.size(), cryptoTickers.stream().map(Symbol::ticker).toList(),
            stockTickers.size(), stockTickers.stream().map(Symbol::ticker).toList());
    }
}
