package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.service.BackfillExecutor;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TimeRange;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BackfillSchedulerTest {

    @Mock
    BackfillJobRepository repository;

    @Mock
    BackfillExecutor executor;

    @Mock
    Event<BackfillStatusChangedEvent> statusEvent;

    private BackfillScheduler scheduler;
    private List<MarketDataGap> gaps;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scheduler = new BackfillScheduler();
        scheduler.repository = repository;
        scheduler.executor = executor;
        scheduler.statusEvent = statusEvent;

        gaps = List.of(
            new MarketDataGap(
                new Symbol("AAPL", InvestmentType.STOCK),
                EventType.PRICE,
                TimeRange.of(LocalDate.of(2020, 2, 1), LocalDate.of(2020, 2, 28))
            )
        );
    }

    @Test
    void processBackfillQueue_retryPendingNotYetDue_shouldSkip() {
        // Given: a RETRY_PENDING job with future retry time
        BackfillJob job = new BackfillJob(gaps);
        job.startProcessing();
        job.scheduleRetry(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(repository.findPendingJobs()).thenReturn(List.of(job));

        // When
        scheduler.processBackfillQueue();

        // Then: executor should NOT be called
        verify(executor, never()).execute(any());
    }

    @Test
    void processBackfillQueue_retryPendingDue_shouldProcess() {
        // Given: a RETRY_PENDING job with past retry time
        BackfillJob job = new BackfillJob(gaps);
        job.startProcessing();
        job.scheduleRetry(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(repository.findPendingJobs()).thenReturn(List.of(job));

        // When
        scheduler.processBackfillQueue();

        // Then: job should be transitioned to IN_PROGRESS and executed
        assertEquals(BackfillStatus.IN_PROGRESS, job.getStatus());
        verify(executor).execute(job);
        verify(repository, atLeastOnce()).update(job);
    }

    @Test
    void processBackfillQueue_pendingJob_shouldProcess() {
        // Given: a normal PENDING job
        BackfillJob job = new BackfillJob(gaps);

        when(repository.findPendingJobs()).thenReturn(List.of(job));

        // When
        scheduler.processBackfillQueue();

        // Then: executor should be called
        assertEquals(BackfillStatus.IN_PROGRESS, job.getStatus());
        verify(executor).execute(job);
    }
}
