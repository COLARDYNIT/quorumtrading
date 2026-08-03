package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.QueryMarketEventsQuery;
import com.quorumtrading.model.marketdata.MarketEvent;
import java.util.List;

/**
 * Use case for retrieving market events with optional filters.
 */
public interface QueryMarketEventsUseCase {

    /**
     * Execute the query market events use case.
     *
     * @param query the query containing time range and optional filters
     * @return list of market events matching the query
     */
    List<MarketEvent> execute(QueryMarketEventsQuery query);
}
