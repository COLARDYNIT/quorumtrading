package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.exception.MarketDataFetchException;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DataHorizonProbeServiceTest {

    @Mock
    RateLimitTracker rateLimitTracker;

    private DataHorizonProbeService probeService;

    private MarketDataPort provider;
    private List<MarketDataPort> providers;
    private final AtomicInteger callCount = new AtomicInteger();

    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataPort.class);
        lenient().when(provider.getProviderName()).thenReturn("alpaca");
        lenient().when(provider.getSupportedFeatures()).thenReturn(Set.of(
                MarketDataFeature.BACKFILL, MarketDataFeature.CRYPTO, MarketDataFeature.CANDLES));
        lenient().when(provider.hasFeature(any())).thenAnswer(inv ->
                provider.getSupportedFeatures().contains((MarketDataFeature) inv.getArgument(0)));
        lenient().when(provider.getRateLimit()).thenReturn(RateLimit.perMinute(200));

        providers = List.of(provider);

        lenient().when(rateLimitTracker.wouldExceedLimit(any(), any())).thenReturn(false);

        probeService = new DataHorizonProbeService(providers, rateLimitTracker);
    }

    /**
     * Configure the provider so it returns >=1 daily bar for any month-window whose
     * start is on/after {@code firstAvailable}, and an empty list before that.
     * Counts calls to assert logarithmic behaviour.
     */
    private void providerHasDataFrom(LocalDate firstAvailable) {
        lenient().when(provider.fetchDataInRange(any(), any(), any(), any(), any(Granularity.class)))
                .thenAnswer(inv -> {
                    callCount.incrementAndGet();
                    Instant from = inv.getArgument(2);
                    LocalDate windowStart = from.atZone(ZoneOffset.UTC).toLocalDate();
                    // A month "has data" if any day in [windowStart, nextMonth) is >= firstAvailable.
                    // Whole-month window: if the month is the firstAvailable month or later, data exists.
                    LocalDate windowMonthStart = windowStart.withDayOfMonth(1);
                    LocalDate availMonthStart = firstAvailable.withDayOfMonth(1);
                    if (!windowMonthStart.isBefore(availMonthStart)) {
                        return List.of(priceBar(windowStart));
                    }
                    return List.of();
                });
    }

    private MarketEvent priceBar(LocalDate date) {
        return new MarketEvent(
                date.atStartOfDay().toInstant(ZoneOffset.UTC),
                EventType.PRICE, "alpaca", "BTC", Map.of("close", 100.0));
    }

    @Test
    void convergesToTheEarliestMonthWithData() {
        LocalDate d = LocalDate.of(2024, 7, 31);
        providerHasDataFrom(d);

        Optional<LocalDate> earliest = probeService.probeEarliest(BTC, EventType.PRICE);

        assertThat(earliest).contains(LocalDate.of(2024, 7, 1)); // first day of D's month
    }

    @Test
    void callCountIsLogarithmicOverFifteenYearSpan() {
        // Data starts ~15 years before now → ~180 months of range.
        LocalDate d = LocalDate.now(ZoneOffset.UTC).minusYears(15).withDayOfMonth(1);
        providerHasDataFrom(d);

        probeService.probeEarliest(BTC, EventType.PRICE);

        // Linear scan would be ~180 calls; logarithmic must be far fewer.
        assertThat(callCount.get()).isLessThan(25);
    }

    @Test
    void monthWindowNotFooledByWeekends() {
        // Provider returns a bar for ANY month with data regardless of which weekday
        // the window starts on — the whole-month window guarantees a hit if the month
        // has data. We assert the probe still converges (no false-negative).
        LocalDate d = LocalDate.of(2023, 1, 1);
        providerHasDataFrom(d);

        Optional<LocalDate> earliest = probeService.probeEarliest(BTC, EventType.PRICE);

        assertThat(earliest).contains(LocalDate.of(2023, 1, 1));
    }

    @Test
    void retryableExceptionAbortsToEmptyWithoutFalseFloor() {
        lenient().when(provider.fetchDataInRange(any(), any(), any(), any(), any(Granularity.class)))
                .thenThrow(MarketDataFetchException.rateLimited("alpaca"));

        Optional<LocalDate> earliest = probeService.probeEarliest(BTC, EventType.PRICE);

        assertThat(earliest).isEmpty();
    }

    @Test
    void nonRetryableExceptionAbortsToEmpty() {
        lenient().when(provider.fetchDataInRange(any(), any(), any(), any(), any(Granularity.class)))
                .thenThrow(MarketDataFetchException.authFailed("alpaca", 401));

        Optional<LocalDate> earliest = probeService.probeEarliest(BTC, EventType.PRICE);

        assertThat(earliest).isEmpty();
    }

    @Test
    void noProviderReturnsEmpty() {
        DataHorizonProbeService noProviders = new DataHorizonProbeService(List.of(), rateLimitTracker);

        Optional<LocalDate> earliest = noProviders.probeEarliest(BTC, EventType.PRICE);

        assertThat(earliest).isEmpty();
    }

    @Test
    void noDataAnywhereNearNowReturnsEmpty() {
        // Provider never returns data for any month.
        lenient().when(provider.fetchDataInRange(any(), any(), any(), any(), any(Granularity.class)))
                .thenAnswer(inv -> List.of());

        Optional<LocalDate> earliest = probeService.probeEarliest(BTC, EventType.PRICE);

        assertThat(earliest).isEmpty();
    }
}
