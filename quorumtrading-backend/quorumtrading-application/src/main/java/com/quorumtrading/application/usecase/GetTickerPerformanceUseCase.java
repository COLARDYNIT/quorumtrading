package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.GetTickerPerformanceQuery;
import com.quorumtrading.application.usecase.result.TickerPerformanceResult;

/**
 * Lightweight ticker performance lookup over a date range.
 * Returns start/end price and % change from existing price_bars data.
 */
public interface GetTickerPerformanceUseCase {
    TickerPerformanceResult execute(GetTickerPerformanceQuery query);
}
