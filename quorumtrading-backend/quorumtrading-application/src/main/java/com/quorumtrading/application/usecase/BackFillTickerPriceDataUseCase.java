package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand;

/**
 * Use case for backfilling historical price data for a specific ticker.
 *
 * This use case fetches historical OHLC (candlestick) data from market data providers
 * and inserts it into the price_bars table. It is resilient
 * to duplicate insertions by checking the content_hash unique constraint.
 */
public interface BackFillTickerPriceDataUseCase {

    /**
     * Execute the backfill operation for a ticker.
     *
     * @param command the command containing ticker, asset type, and date range
     * @return result containing statistics on records inserted/skipped/failed
     */
    BackFillResult execute(BackFillTickerPriceDataCommand command);
}
