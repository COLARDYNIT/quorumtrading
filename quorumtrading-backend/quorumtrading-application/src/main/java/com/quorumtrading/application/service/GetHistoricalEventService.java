package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalEventRepository;
import com.quorumtrading.application.usecase.GetHistoricalEventUseCase;
import com.quorumtrading.application.usecase.query.GetHistoricalEventQuery;
import com.quorumtrading.model.entity.HistoricalEvent;

import java.util.List;
import java.util.Optional;

public class GetHistoricalEventService implements GetHistoricalEventUseCase {

    private final HistoricalEventRepository repository;

    public GetHistoricalEventService(HistoricalEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<HistoricalEvent> execute(GetHistoricalEventQuery query) {
        // Try exact slug match first
        Optional<HistoricalEvent> bySlug = repository.findBySlug(query.slugOrKeyword());
        if (bySlug.isPresent()) {
            return List.of(bySlug.get());
        }

        // Fall back to keyword search
        return repository.searchByKeyword(query.slugOrKeyword());
    }
}
