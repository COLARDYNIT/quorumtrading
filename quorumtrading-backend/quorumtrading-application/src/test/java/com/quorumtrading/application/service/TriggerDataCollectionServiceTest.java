package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.DataCollectionResult;
import com.quorumtrading.application.usecase.command.TriggerDataCollectionCommand;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.TickerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for TriggerDataCollectionService with rate limiting.
 */
class TriggerDataCollectionServiceTest {

    @Mock
    private MarketEventRepository marketEventRepository;

    @Mock
    private MarketDataPort unlimitedProvider;

    @Mock
    private MarketDataPort limitedProvider;

    @Mock
    private MarketEvent mockEvent;

    @Mock
    private TickerRegistryService tickerRegistryService;

    private RateLimitTracker rateLimitTracker;
    private TriggerDataCollectionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rateLimitTracker = new InMemoryRateLimitTracker();

        // Configure unlimited provider
        when(unlimitedProvider.getProviderName()).thenReturn("UnlimitedProvider");
        when(unlimitedProvider.getRateLimit()).thenReturn(RateLimit.unlimited());
        // Provider supports NEWS feature (required for feature-based filtering)
        when(unlimitedProvider.hasFeature(MarketDataFeature.NEWS)).thenReturn(true);

        // Configure limited provider (3 requests per minute)
        when(limitedProvider.getProviderName()).thenReturn("LimitedProvider");
        when(limitedProvider.getRateLimit()).thenReturn(RateLimit.perMinute(3));
        // Provider supports NEWS feature (required for feature-based filtering)
        when(limitedProvider.hasFeature(MarketDataFeature.NEWS)).thenReturn(true);

        // Configure mock event saving
        when(marketEventRepository.save(any())).thenReturn(mockEvent);

        // Configure ticker registry to return empty by default (uses adapter defaults)
        when(tickerRegistryService.getAllRequiredTickers()).thenReturn(TickerRegistry.empty());
    }

    @Test
    void shouldCallProviderWhenUnderRateLimit() {
        service = new TriggerDataCollectionService(
            List.of(limitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        MarketEvent event = mock(MarketEvent.class);
        when(limitedProvider.fetchLatestDataForTickers(any())).thenReturn(List.of(event));

        TriggerDataCollectionCommand command = new TriggerDataCollectionCommand(null);
        DataCollectionResult result = service.execute(command);

        verify(limitedProvider, times(1)).fetchLatestDataForTickers(any());
        verify(marketEventRepository, times(1)).save(event);
        assertEquals(1, result.eventsCollected());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldSkipProviderWhenAtRateLimit() {
        service = new TriggerDataCollectionService(
            List.of(limitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        // Exhaust rate limit (3 calls)
        for (int i = 0; i < 3; i++) {
            rateLimitTracker.recordCall("LimitedProvider");
        }

        TriggerDataCollectionCommand command = new TriggerDataCollectionCommand(null);
        DataCollectionResult result = service.execute(command);

        // Provider should not be called
        verify(limitedProvider, never()).fetchLatestDataForTickers(any());
        verify(marketEventRepository, never()).save(any());
        assertEquals(0, result.eventsCollected());
    }

    @Test
    void shouldRecordSuccessfulCallInTracker() {
        service = new TriggerDataCollectionService(
            List.of(limitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        MarketEvent event = mock(MarketEvent.class);
        when(limitedProvider.fetchLatestDataForTickers(any())).thenReturn(List.of(event));

        assertEquals(0, rateLimitTracker.getCallsInWindow("LimitedProvider", limitedProvider.getRateLimit()),
            "Should start with 0 calls");

        service.execute(new TriggerDataCollectionCommand(null));

        assertEquals(1, rateLimitTracker.getCallsInWindow("LimitedProvider", limitedProvider.getRateLimit()),
            "Should record successful call");
    }

    @Test
    void shouldRecordFailedCallInTracker() {
        service = new TriggerDataCollectionService(
            List.of(limitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        when(limitedProvider.fetchLatestDataForTickers(any())).thenThrow(new RuntimeException("API Error"));

        assertEquals(0, rateLimitTracker.getCallsInWindow("LimitedProvider", limitedProvider.getRateLimit()));

        DataCollectionResult result = service.execute(new TriggerDataCollectionCommand(null));

        // Even failed calls should be recorded for rate limiting
        assertEquals(1, rateLimitTracker.getCallsInWindow("LimitedProvider", limitedProvider.getRateLimit()),
            "Should record failed call for rate limiting");
        assertFalse(result.errors().isEmpty(), "Should report error");
    }

    @Test
    void shouldNeverSkipUnlimitedProvider() {
        service = new TriggerDataCollectionService(
            List.of(unlimitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        // Simulate many calls
        for (int i = 0; i < 1000; i++) {
            rateLimitTracker.recordCall("UnlimitedProvider");
        }

        when(unlimitedProvider.fetchLatestDataForTickers(any())).thenReturn(Collections.emptyList());

        DataCollectionResult result = service.execute(new TriggerDataCollectionCommand(null));

        // Unlimited provider should always be called
        verify(unlimitedProvider, times(1)).fetchLatestDataForTickers(any());
        assertEquals(0, result.eventsCollected());
    }

    @Test
    void shouldHandleMultipleProvidersWithDifferentLimits() {
        service = new TriggerDataCollectionService(
            List.of(unlimitedProvider, limitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        // Exhaust limited provider's quota
        rateLimitTracker.recordCall("LimitedProvider");
        rateLimitTracker.recordCall("LimitedProvider");
        rateLimitTracker.recordCall("LimitedProvider");

        MarketEvent event = mock(MarketEvent.class);
        when(unlimitedProvider.fetchLatestDataForTickers(any())).thenReturn(List.of(event));

        DataCollectionResult result = service.execute(new TriggerDataCollectionCommand(null));

        // Unlimited should be called, limited should be skipped
        verify(unlimitedProvider, times(1)).fetchLatestDataForTickers(any());
        verify(limitedProvider, never()).fetchLatestDataForTickers(any());
        assertEquals(1, result.eventsCollected());
    }

    @Test
    void shouldNotCountDuplicateEvents() {
        service = new TriggerDataCollectionService(
            List.of(limitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        MarketEvent event = mock(MarketEvent.class);
        when(limitedProvider.fetchLatestDataForTickers(any())).thenReturn(List.of(event));

        // Simulate duplicate detection - repository returns null for duplicates
        when(marketEventRepository.save(event)).thenReturn(null);

        DataCollectionResult result = service.execute(new TriggerDataCollectionCommand(null));

        verify(limitedProvider, times(1)).fetchLatestDataForTickers(any());
        verify(marketEventRepository, times(1)).save(event);
        assertEquals(0, result.eventsCollected(), "Duplicates should not be counted");
    }

    @Test
    void shouldUseProvidedDateRange() {
        service = new TriggerDataCollectionService(
            List.of(limitedProvider),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");
        TriggerDataCollectionCommand command = new TriggerDataCollectionCommand(null, start, end);

        when(limitedProvider.fetchDataInRange(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        service.execute(command);

        verify(limitedProvider, times(1)).fetchDataInRange(
            eq(Collections.emptySet()),
            eq(Collections.emptySet()),
            eq(start),
            eq(end)
        );
        verify(limitedProvider, never()).fetchLatestDataForTickers(any());
    }

    @Test
    void shouldHandleNewsAPIScenario() {
        // NewsAPI: 100 requests/day, scheduler runs every 5 minutes (288 times/day)
        MarketDataPort newsApiPort = mock(MarketDataPort.class);
        when(newsApiPort.getProviderName()).thenReturn("NewsAPI");
        when(newsApiPort.getRateLimit()).thenReturn(RateLimit.perDay(100));
        when(newsApiPort.hasFeature(MarketDataFeature.NEWS)).thenReturn(true);
        when(newsApiPort.fetchLatestDataForTickers(any())).thenReturn(Collections.emptyList());

        service = new TriggerDataCollectionService(
            List.of(newsApiPort),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService
        );

        TriggerDataCollectionCommand command = new TriggerDataCollectionCommand(null);

        // Simulate 100 scheduler runs
        for (int i = 0; i < 100; i++) {
            service.execute(command);
        }

        // 100 calls should have been made
        verify(newsApiPort, times(100)).fetchLatestDataForTickers(any());

        // 101st call should be skipped
        service.execute(command);
        verify(newsApiPort, times(100)).fetchLatestDataForTickers(any()); // Still 100, not 101
    }
}
