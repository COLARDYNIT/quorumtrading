package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalEventRepository;
import com.quorumtrading.application.usecase.ListHistoricalEventsUseCase;
import com.quorumtrading.application.usecase.query.ListHistoricalEventsQuery;
import com.quorumtrading.model.entity.HistoricalEvent;

import java.util.List;

public class ListHistoricalEventsService implements ListHistoricalEventsUseCase {

    private final HistoricalEventRepository repository;

    public ListHistoricalEventsService(HistoricalEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<HistoricalEvent> execute(ListHistoricalEventsQuery query) {
        boolean hasFilters = query.category().isPresent()
                || query.assetClass().isPresent()
                || query.sentiment().isPresent();

        if (hasFilters) {
            return repository.findByFilters(query.category(), query.assetClass(), query.sentiment());
        }
        return repository.findAll();
    }
}
