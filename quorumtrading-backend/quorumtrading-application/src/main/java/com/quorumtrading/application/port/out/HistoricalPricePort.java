package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.PriceCoverage;
import com.quorumtrading.model.valueobject.Symbol;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Port for fetching historical OHLCV price data.
 *
 * <p><b>Hexagonal Architecture:</b> Outbound port (driven)</p>
 * <p>Implementation queries price_bars table for OHLCV data.</p>
 */
public interface HistoricalPricePort {

    /**
     * Get historical price bars for a ticker, looking back from now.
     *
     * @param ticker Stock symbol (e.g., AAPL, NVDA)
     * @param days Number of days of history to fetch
     * @return List of price bars ordered by timestamp ascending, empty if no data
     */
    List<PriceBar> getHistoricalPrices(Symbol ticker, int days);

    /**
     * Get historical price bars for a ticker, looking back from a specific point in time.
     * Used during backtests to compute indicators as-of the simulated date.
     *
     * @param ticker Stock symbol (e.g., AAPL, NVDA)
     * @param days Number of days of history to fetch
     * @param asOf Upper bound timestamp (inclusive) — prices after this are excluded
     * @return List of price bars ordered by timestamp ascending, empty if no data
     */
    List<PriceBar> getHistoricalPrices(Symbol ticker, int days, Instant asOf);

    /**
     * Get historical price bars for a ticker within a specific date range.
     *
     * @param ticker Stock symbol (e.g., AAPL, NVDA)
     * @param from Start of range (inclusive)
     * @param to End of range (inclusive)
     * @return List of price bars ordered by timestamp ascending, empty if no data
     */
    List<PriceBar> getHistoricalPrices(Symbol ticker, Instant from, Instant to);

    /**
     * Get historical price bars downsampled to the requested resolution.
     * Picks the first (oldest) bar in each time bucket for consistency.
     *
     * @param ticker Stock symbol (e.g., AAPL, NVDA)
     * @param from Start of range (inclusive)
     * @param to End of range (inclusive)
     * @param resolution Time bucket: "1d" (daily), "1h" (hourly), "1w" (weekly). Null or empty defaults to all bars.
     * @return List of price bars ordered by timestamp ascending, empty if no data
     */
    List<PriceBar> getHistoricalPrices(Symbol ticker, Instant from, Instant to, String resolution);

    /**
     * Summarize locally stored price-bar coverage for a ticker: bar count, distinct days,
     * first/last bar timestamps, and the providers that supplied them. Never null — returns
     * {@link PriceCoverage#empty(String)} when no bars are stored.
     *
     * @param ticker the ticker to summarize
     * @return coverage summary for the ticker
     */
    PriceCoverage getCoverage(Symbol ticker);

    /**
     * As {@link #getCoverage(Symbol)}, plus what is covered inside a specific date range.
     *
     * <p>Global first/last bar cannot answer "is my backtest window covered?" — the question the
     * market-data guide sends users here with. A decade-wide first/last pair over 1,522 distinct
     * days reads as continuous and is 40% covered. The returned
     * {@link PriceCoverage#window()} carries expected-vs-covered day counts and the runs of
     * missing days.</p>
     *
     * @param ticker the ticker to summarize
     * @param from   first day of the window (inclusive)
     * @param to     last day of the window (inclusive)
     * @return coverage summary with {@link PriceCoverage#window()} populated
     */
    PriceCoverage getCoverage(Symbol ticker, LocalDate from, LocalDate to);
}
