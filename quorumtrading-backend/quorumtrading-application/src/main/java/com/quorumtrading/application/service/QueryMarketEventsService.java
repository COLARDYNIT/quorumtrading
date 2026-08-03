package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.QueryMarketEventsUseCase;
import com.quorumtrading.application.usecase.query.QueryMarketEventsQuery;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;

import java.util.List;
import java.util.Set;

/**
 * Service implementation for querying market events.
 * Framework-agnostic, uses constructor injection.
 */
public class QueryMarketEventsService implements QueryMarketEventsUseCase {

    private final MarketEventRepository marketEventRepository;

    public QueryMarketEventsService(MarketEventRepository marketEventRepository) {
        this.marketEventRepository = marketEventRepository;
    }

    @Override
    public List<MarketEvent> execute(QueryMarketEventsQuery query) {
        // Convert optional single symbol to appropriate ticker set
        Set<String> cryptoTickers = null;
        Set<String> stockTickers = null;

        if (query.symbol() != null && !query.symbol().isBlank()) {
            Symbol symbol = new Symbol(query.symbol(), InvestmentType.fromTicker(query.symbol()));

            // Route to correct set based on investment type
            if (symbol.type() == InvestmentType.CRYPTO) {
                cryptoTickers = Set.of(symbol.ticker());
            } else {
                stockTickers = Set.of(symbol.ticker());
            }
        }

        return marketEventRepository.findByTickersAndTimeRange(
            cryptoTickers,
            stockTickers,
            query.from(),
            query.to(),
            query.eventType() != null ? EventType.valueOf(query.eventType()) : null,
            query.limit()
        );
    }
}
