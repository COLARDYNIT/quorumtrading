package com.quorumtrading.application.service;

import com.quorumtrading.application.event.BackfillJobCreatedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.*;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestBackfillServiceTest {

    @Mock
    private BackfillJobRepository backfillJobRepository;

    @Mock
    private AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase;

    @Mock
    private Event<BackfillJobCreatedEvent> jobCreatedEvent;

    @InjectMocks
    private RequestBackfillService service;

    @Test
    void shouldNotTriggerBackfillWhenDataIsComplete() {
        var command = new RequestBackfillCommand(
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            Set.of(EventType.PRICE),
            TimeRange.of(LocalDate.now().minusDays(7), LocalDate.now()),
            false
        );

        // Mock complete data availability with no gaps
        DataAvailability availability = mock(DataAvailability.class);
        when(availability.identifyGaps()).thenReturn(List.of());
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);

        RequestBackfillResult result = service.execute(command);

        assertThat(result.backfillTriggered()).isFalse();
        verify(backfillJobRepository, never()).save(any());
    }

    @Test
    void shouldTriggerBackfillWhenGapsExist() {
        var command = new RequestBackfillCommand(
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            Set.of(EventType.PRICE),
            TimeRange.of(LocalDate.now().minusDays(7), LocalDate.now()),
            false
        );

        // Mock incomplete data with gaps
        DataAvailability availability = mock(DataAvailability.class);

        List<MarketDataGap> gaps = List.of(
            new MarketDataGap(
                new Symbol("BTC", InvestmentType.CRYPTO),
                EventType.PRICE,
                TimeRange.of(LocalDate.now(), LocalDate.now())
            )
        );
        when(availability.identifyGaps()).thenReturn(gaps);
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);

        RequestBackfillResult result = service.execute(command);

        assertThat(result.backfillTriggered()).isTrue();
        assertThat(result.jobId()).isNotNull();
        verify(backfillJobRepository).save(any(BackfillJob.class));
    }
}
