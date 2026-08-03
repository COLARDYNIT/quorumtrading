package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalEventRepository;
import com.quorumtrading.application.usecase.query.ListHistoricalEventsQuery;
import com.quorumtrading.model.entity.HistoricalEvent;
import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ListHistoricalEventsServiceTest {

    private final HistoricalEventRepository repository = mock(HistoricalEventRepository.class);
    private final ListHistoricalEventsService service = new ListHistoricalEventsService(repository);

    private static HistoricalEvent sampleEvent(String slug, EventCategory category) {
        return new HistoricalEvent(1L, slug, "Test Event",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 1),
                category, AssetClass.BOTH, MarketSentiment.BEARISH,
                "desc", "impact", List.of("SPY"), List.of("keyword"));
    }

    @Test
    void shouldReturnAllWhenNoFilters() {
        var events = List.of(sampleEvent("a", EventCategory.CRISIS), sampleEvent("b", EventCategory.BULL_RUN));
        when(repository.findAll()).thenReturn(events);

        var result = service.execute(new ListHistoricalEventsQuery());

        assertEquals(2, result.size());
        verify(repository).findAll();
        verify(repository, never()).findByFilters(any(), any(), any());
    }

    @Test
    void shouldDelegateToFilteredQueryWhenCategoryProvided() {
        var events = List.of(sampleEvent("a", EventCategory.CRISIS));
        when(repository.findByFilters(
                eq(Optional.of(EventCategory.CRISIS)),
                eq(Optional.empty()),
                eq(Optional.empty())))
                .thenReturn(events);

        var result = service.execute(new ListHistoricalEventsQuery(
                Optional.of(EventCategory.CRISIS), Optional.empty(), Optional.empty()));

        assertEquals(1, result.size());
        verify(repository, never()).findAll();
    }

    @Test
    void shouldDelegateToFilteredQueryWithMultipleFilters() {
        when(repository.findByFilters(
                eq(Optional.of(EventCategory.BULL_RUN)),
                eq(Optional.of(AssetClass.CRYPTO)),
                eq(Optional.empty())))
                .thenReturn(List.of());

        var result = service.execute(new ListHistoricalEventsQuery(
                Optional.of(EventCategory.BULL_RUN), Optional.of(AssetClass.CRYPTO), Optional.empty()));

        assertEquals(0, result.size());
    }
}
