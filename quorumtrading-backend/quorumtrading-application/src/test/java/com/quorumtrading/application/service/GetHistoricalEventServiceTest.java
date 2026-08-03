package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalEventRepository;
import com.quorumtrading.application.usecase.query.GetHistoricalEventQuery;
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

class GetHistoricalEventServiceTest {

    private final HistoricalEventRepository repository = mock(HistoricalEventRepository.class);
    private final GetHistoricalEventService service = new GetHistoricalEventService(repository);

    private static HistoricalEvent sampleEvent(String slug) {
        return new HistoricalEvent(1L, slug, "Test Event",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 1),
                EventCategory.CRISIS, AssetClass.BOTH, MarketSentiment.BEARISH,
                "desc", "impact", List.of("SPY"), List.of("keyword"));
    }

    @Test
    void shouldReturnExactSlugMatch() {
        var event = sampleEvent("covid-crash");
        when(repository.findBySlug("covid-crash")).thenReturn(Optional.of(event));

        var result = service.execute(new GetHistoricalEventQuery("covid-crash"));

        assertEquals(1, result.size());
        assertEquals("covid-crash", result.get(0).slug());
        verify(repository, never()).searchByKeyword(any());
    }

    @Test
    void shouldFallBackToKeywordSearchWhenSlugNotFound() {
        when(repository.findBySlug("pandemic")).thenReturn(Optional.empty());
        when(repository.searchByKeyword("pandemic")).thenReturn(List.of(sampleEvent("covid-crash")));

        var result = service.execute(new GetHistoricalEventQuery("pandemic"));

        assertEquals(1, result.size());
        verify(repository).findBySlug("pandemic");
        verify(repository).searchByKeyword("pandemic");
    }

    @Test
    void shouldReturnEmptyWhenNoMatch() {
        when(repository.findBySlug("unknown")).thenReturn(Optional.empty());
        when(repository.searchByKeyword("unknown")).thenReturn(List.of());

        var result = service.execute(new GetHistoricalEventQuery("unknown"));

        assertEquals(0, result.size());
    }
}
