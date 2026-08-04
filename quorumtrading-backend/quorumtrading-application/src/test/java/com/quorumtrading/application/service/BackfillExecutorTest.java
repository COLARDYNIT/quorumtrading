package com.quorumtrading.application.service;

import com.quorumtrading.application.event.BackfillProgressEvent;
import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.exception.MarketDataFetchException;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.GapProgress;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TimeRange;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.enterprise.event.Event;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackfillExecutorTest {

    @Mock
    BackfillJobRepository jobRepository;

    @Mock
    MarketEventRepository eventRepository;

    @Mock
    Instance<MarketDataPort> marketDataPorts;

    @Mock
    RateLimitTracker rateLimitTracker;

    @Mock
    Event<BackfillProgressEvent> progressEvent;

    @Mock
    Event<BackfillStatusChangedEvent> statusEvent;

    @Mock
    DataHorizonService dataHorizonService;

    @InjectMocks
    BackfillExecutor executor;

    private MarketDataPort mockProvider;
    private BackfillJob job;

    @BeforeEach
    void setUp() {
        mockProvider = mock(MarketDataPort.class);
        lenient().when(mockProvider.getProviderName()).thenReturn("alpaca");
        lenient().when(mockProvider.getSupportedFeatures()).thenReturn(Set.of(
            MarketDataFeature.BACKFILL,
            MarketDataFeature.STOCK,
            MarketDataFeature.CANDLES
        ));
        lenient().when(mockProvider.hasFeature(any())).thenAnswer(inv -> {
            MarketDataFeature f = inv.getArgument(0);
            return mockProvider.getSupportedFeatures().contains(f);
        });
        lenient().when(mockProvider.getRateLimit()).thenReturn(RateLimit.perMinute(200));
        // Usable by default: enabled + credentialed (the executor now skips providers
        // that are switched off or hold no key, instead of calling them).
        lenient().when(mockProvider.isEnabled()).thenReturn(true);
        lenient().when(mockProvider.isConfigured()).thenReturn(true);

        // Set up CDI Instance to return the mock provider
        lenient().when(marketDataPorts.isUnsatisfied()).thenReturn(false);
        lenient().when(marketDataPorts.stream()).thenReturn(Stream.of(mockProvider));
        lenient().doAnswer(inv -> {
            java.util.function.Consumer<MarketDataPort> consumer = inv.getArgument(0);
            consumer.accept(mockProvider);
            return null;
        }).when(marketDataPorts).forEach(any());

        List<MarketDataGap> gaps = List.of(
            new MarketDataGap(
                new Symbol("AAPL", InvestmentType.STOCK),
                EventType.PRICE,
                TimeRange.of(LocalDate.of(2020, 2, 1), LocalDate.of(2020, 2, 28))
            )
        );
        job = new BackfillJob(gaps);
        job.startProcessing();
    }

    @Test
    void execute_retryableException_shouldScheduleRetryAndReturn() {
        // Given: provider throws a retryable (rate limit) exception
        when(mockProvider.fetchDataInRange(any(), any(), any(), any(), any()))
            .thenThrow(MarketDataFetchException.rateLimited("alpaca"));

        // When
        executor.execute(job);

        // Then: job should be in RETRY_PENDING status
        assertThat(job.getStatus()).isEqualTo(BackfillStatus.RETRY_PENDING);
        assertThat(job.getRetryAfter()).isNotNull();
        assertThat(job.getRetryAfter()).isAfter(Instant.now());

        // Verify job was updated in repository
        verify(jobRepository, atLeastOnce()).update(job);

        // Verify RETRY_PENDING status event was fired
        ArgumentCaptor<BackfillStatusChangedEvent> eventCaptor =
            ArgumentCaptor.forClass(BackfillStatusChangedEvent.class);
        verify(statusEvent, atLeastOnce()).fire(eventCaptor.capture());

        boolean hasRetryEvent = eventCaptor.getAllValues().stream()
            .anyMatch(e -> e.newStatus() == BackfillStatus.RETRY_PENDING);
        assertThat(hasRetryEvent).isTrue();
    }

    @Test
    void execute_nonRetryableException_shouldMarkGapFailed() {
        // Given: provider throws a non-retryable (auth) exception
        when(mockProvider.fetchDataInRange(any(), any(), any(), any(), any()))
            .thenThrow(MarketDataFetchException.authFailed("alpaca", 401));

        // When
        executor.execute(job);

        // Then: job should be FAILED (all gaps failed, no retry)
        assertThat(job.getStatus()).isEqualTo(BackfillStatus.FAILED);
        assertThat(job.getRetryAfter()).isNull();
    }

    @Test
    void execute_successfulFetch_shouldCompleteJob() {
        // Given: provider returns data
        MarketEvent event = new MarketEvent(
            Instant.now(),
            EventType.PRICE,
            "alpaca",
            "AAPL",
            Map.of("price", 150.0)
        );
        when(mockProvider.fetchDataInRange(any(), any(), any(), any(), any()))
            .thenReturn(List.of(event));

        // When
        executor.execute(job);

        // Then: job should be COMPLETED
        assertThat(job.getStatus()).isEqualTo(BackfillStatus.COMPLETED);
    }

    @Test
    void execute_providerReturnsZeroEvents_shouldCompleteWithZeroRecordsAndNoDataGap() {
        // Given: provider runs without error but has no data for the range
        when(mockProvider.fetchDataInRange(any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        // When
        executor.execute(job);

        // Then: job completes (no error) but reports ZERO rows fetched, not the gap count
        assertThat(job.getStatus()).isEqualTo(BackfillStatus.COMPLETED);

        BackfillStatusChangedEvent completed = captureCompletedStatusEvent();
        assertThat(completed.totalRecords()).isZero();
        assertThat(completed.errorMessage()).contains("no data");

        // And the gap is recorded as NO_DATA, never SUCCESS
        GapProgress lastGap = captureLastGapProgress();
        assertThat(lastGap.status()).isEqualTo(GapProgress.GapStatus.NO_DATA);
        assertThat(lastGap.eventsFetched()).isZero();

        // And it feeds back to the horizon cache (raise the floor for this range)
        verify(dataHorizonService).recordNoData(any(), eq(EventType.PRICE), any());
    }

    @Test
    void execute_successfulFetch_shouldReportRowsFetchedNotGapCount() {
        // Given: a single gap, but the provider returns three price rows
        when(mockProvider.fetchDataInRange(any(), any(), any(), any(), any()))
            .thenReturn(List.of(priceEvent(1), priceEvent(2), priceEvent(3)));

        // When
        executor.execute(job);

        // Then: completion reports 3 rows fetched, not 1 (the gap count)
        assertThat(job.getStatus()).isEqualTo(BackfillStatus.COMPLETED);

        BackfillStatusChangedEvent completed = captureCompletedStatusEvent();
        assertThat(completed.totalRecords()).isEqualTo(3);

        // A successful fill feeds back as an observed-fetch (may lower the horizon)
        verify(dataHorizonService).recordObservedFetch(any(), eq(EventType.PRICE), any());
    }

    @Test
    void execute_uncredentialedProvider_shouldSkipWithKeyMessageAndNeverTouchHorizon() {
        // Given: the only stock-capable provider holds no credentials (keyless install)
        when(mockProvider.isConfigured()).thenReturn(false);

        // When
        executor.execute(job);

        // Then: the job completes with the gap SKIPPED — not FAILED, not NO_DATA
        assertThat(job.getStatus()).isEqualTo(BackfillStatus.COMPLETED);

        GapProgress lastGap = captureLastGapProgress();
        assertThat(lastGap.status()).isEqualTo(GapProgress.GapStatus.SKIPPED);
        assertThat(lastGap.errorMessage())
            .contains("No market-data credentials are configured")
            .contains("alpaca")
            .contains("Set the API key");

        // The provider is never called — no outbound request without credentials
        verify(mockProvider, never()).fetchDataInRange(any(), any(), any(), any(), any());

        // And crucially: the horizon cache is never "raised" from a skipped gap. That
        // feedback is what painted keyless installs with a fake permanent data floor.
        verify(dataHorizonService, never()).recordNoData(any(), any(), any());
    }

    private static MarketEvent priceEvent(int dayOfMonth) {
        return new MarketEvent(
            LocalDate.of(2020, 2, dayOfMonth).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            EventType.PRICE,
            "alpaca",
            "AAPL",
            Map.of("price", 150.0 + dayOfMonth)
        );
    }

    private BackfillStatusChangedEvent captureCompletedStatusEvent() {
        ArgumentCaptor<BackfillStatusChangedEvent> captor =
            ArgumentCaptor.forClass(BackfillStatusChangedEvent.class);
        verify(statusEvent, atLeastOnce()).fire(captor.capture());
        return captor.getAllValues().stream()
            .filter(e -> e.newStatus() == BackfillStatus.COMPLETED)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No COMPLETED status event was fired"));
    }

    private GapProgress captureLastGapProgress() {
        ArgumentCaptor<BackfillProgressEvent> captor =
            ArgumentCaptor.forClass(BackfillProgressEvent.class);
        verify(progressEvent, atLeastOnce()).fire(captor.capture());
        List<BackfillProgressEvent> events = captor.getAllValues();
        return events.get(events.size() - 1).currentGap();
    }
}
