package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.FindTickerQuery;
import com.quorumtrading.model.valueobject.TickerSearchResult;

/**
 * Use case for finding a stock/crypto ticker by company name.
 * Uses LLM with web search to resolve the ticker, then validates
 * tradability against the broker (Alpaca).
 */
public interface FindTickerUseCase {

    /**
     * Find the ticker symbol for a company.
     *
     * @param query the search query containing the company name
     * @return the search result with ticker info and tradability status
     */
    TickerSearchResult execute(FindTickerQuery query);
}
