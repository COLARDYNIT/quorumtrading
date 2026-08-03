package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.GetMarketDataContextCommand;
import com.quorumtrading.model.marketdata.MarketDataContext;

import java.util.Map;

/**
 * Use case for retrieving the market data context that was (or would be) used
 * during consensus evaluation for a specific simulation at a specific timestamp.
 *
 * This ensures the frontend can display exactly the same market data that the
 * AI agents had access to during their analysis.
 */
public interface GetMarketDataContextUseCase {

    /**
     * Retrieves the market data context for a simulation at a specific analysis timestamp.
     * Returns the multi-timeframe market data grouped by ticker that matches exactly
     * what the agents see during consensus evaluation.
     *
     * @param command Command containing simulation ID and analysis timestamp
     * @return Map of ticker to MarketDataContext (one per ticker configured in strategy)
     */
    Map<String, MarketDataContext> execute(GetMarketDataContextCommand command);
}
