package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.usecase.result.ClassifiedGap;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GapClassification;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassifyGapsServiceTest {

    @Mock
    DataHorizonService dataHorizonService;

    private ClassifyGapsService service;

    private MarketDataPort provider;

    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);
    private static final Symbol TSLA = new Symbol("TSLA", InvestmentType.STOCK);

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataPort.class);
        lenient().when(provider.getProviderName()).thenReturn("alpaca");
        lenient().when(provider.getSupportedFeatures()).thenReturn(Set.of(
            MarketDataFeature.BACKFILL,
            MarketDataFeature.CRYPTO,
            MarketDataFeature.STOCK,
            MarketDataFeature.CANDLES,
            MarketDataFeature.NEWS,
            MarketDataFeature.SOCIAL_MEDIA));
        lenient().when(provider.hasFeature(any())).thenAnswer(inv ->
            provider.getSupportedFeatures().contains((MarketDataFeature) inv.getArgument(0)));

        // Default: every date reachable (no horizon floor) → not BEFORE_HORIZON.
        lenient().when(dataHorizonService.isReachable(any(), any(), any())).thenReturn(true);

        service = new ClassifyGapsService(List.of(provider), dataHorizonService);
    }

    private MarketDataGap priceGap(Symbol symbol, LocalDate from, LocalDate to) {
        return new MarketDataGap(symbol, EventType.PRICE, TimeRange.of(from, to));
    }

    @Test
    void noSourceWhenNoProviderMatches() {
        // A provider with only NEWS support cannot fill a crypto PRICE gap.
        when(provider.getSupportedFeatures()).thenReturn(Set.of(
            MarketDataFeature.BACKFILL, MarketDataFeature.NEWS));

        MarketDataGap gap = priceGap(BTC, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).classification()).isEqualTo(GapClassification.NO_SOURCE);
        assertThat(result.get(0).provider()).isNull();
    }

    @Test
    void futureWhenRangeStartsAfterToday() {
        LocalDate start = LocalDate.now().plusDays(5);
        MarketDataGap gap = priceGap(BTC, start, start.plusDays(3));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).classification()).isEqualTo(GapClassification.FUTURE);
        assertThat(result.get(0).provider()).isEqualTo("alpaca");
    }

    @Test
    void beforeHorizonWhenUnreachablePastProviderBackedGap() {
        when(dataHorizonService.isReachable(any(), any(), any())).thenReturn(false);

        MarketDataGap gap = priceGap(BTC, LocalDate.of(2010, 1, 1), LocalDate.of(2010, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).classification()).isEqualTo(GapClassification.BEFORE_HORIZON);
        assertThat(result.get(0).provider()).isEqualTo("alpaca");
    }

    @Test
    void actionableOtherwiseWithProviderName() {
        MarketDataGap gap = priceGap(BTC, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).classification()).isEqualTo(GapClassification.ACTIONABLE);
        assertThat(result.get(0).provider()).isEqualTo("alpaca");
    }

    @Test
    void cryptoPriceGapEstimatesEveryDay() {
        // 2024-01-01 .. 2024-01-07 inclusive = 7 calendar days.
        MarketDataGap gap = priceGap(BTC, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).estimatedRecords()).isEqualTo(7);
    }

    @Test
    void stockPriceGapEstimatesWeekdaysOnly() {
        // Mon 2024-01-01 .. Sun 2024-01-07 = 5 weekdays (Mon–Fri).
        MarketDataGap gap = priceGap(TSLA, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).estimatedRecords()).isEqualTo(5);
    }

    @Test
    void horizonLookupThrowingDegradesToActionable() {
        when(dataHorizonService.isReachable(any(), any(), any()))
            .thenThrow(new RuntimeException("probe blew up"));

        MarketDataGap gap = priceGap(BTC, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        // No exception propagates; gap degrades to ACTIONABLE.
        assertThat(result.get(0).classification()).isEqualTo(GapClassification.ACTIONABLE);
    }
}
