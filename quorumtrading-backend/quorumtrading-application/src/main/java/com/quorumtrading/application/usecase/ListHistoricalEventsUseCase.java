package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.ListHistoricalEventsQuery;
import com.quorumtrading.model.entity.HistoricalEvent;

import java.util.List;

public interface ListHistoricalEventsUseCase {
    List<HistoricalEvent> execute(ListHistoricalEventsQuery query);
}
