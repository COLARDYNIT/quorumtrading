package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.GetHistoricalEventQuery;
import com.quorumtrading.model.entity.HistoricalEvent;

import java.util.List;

public interface GetHistoricalEventUseCase {
    /**
     * Get a historical event by slug or keyword search.
     * Tries exact slug match first, then falls back to keyword search.
     *
     * @return matching events (1 for exact slug match, possibly multiple for keyword search)
     */
    List<HistoricalEvent> execute(GetHistoricalEventQuery query);
}
