package com.quorumtrading.application.usecase.query;

import java.util.List;

/**
 * Query to fetch dividend information for a list of tickers.
 * Tickers may be stock or crypto — the service determines the type.
 */
public record GetDividendInfoQuery(
    List<String> tickers
) {
    public GetDividendInfoQuery {
        if (tickers == null || tickers.isEmpty()) {
            throw new IllegalArgumentException("At least one ticker is required");
        }
    }
}
