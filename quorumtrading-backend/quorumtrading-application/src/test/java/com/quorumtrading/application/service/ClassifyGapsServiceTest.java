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
        // Default: usable (enabled + credentialed) — the pre-keyless-fix behaviour.
        lenient().when(provider.isEnabled()).thenReturn(true);
        lenient().when(provider.isConfigured()).thenReturn(true);

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
    void noCredentialedProviderWhenMatchingProviderLacksCredentials() {
        // A keyless install: the stock-capable provider exists but holds no key.
        when(provider.isConfigured()).thenReturn(false);

        MarketDataGap gap = priceGap(TSLA, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).classification())
            .isEqualTo(GapClassification.NO_CREDENTIALED_PROVIDER);
        // Never name a provider that would be skipped.
        assertThat(result.get(0).provider()).isNull();
    }

    @Test
    void noCredentialedProviderWhenMatchingProviderIsDisabled() {
        when(provider.isEnabled()).thenReturn(false);

        MarketDataGap gap = priceGap(TSLA, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).classification())
            .isEqualTo(GapClassification.NO_CREDENTIALED_PROVIDER);
        assertThat(result.get(0).provider()).isNull();
    }

    @Test
    void missingCredentialsBeatBeforeHorizonSoAKeyProblemNeverLooksPermanent() {
        // The horizon cache may hold a floor poisoned by an earlier keyless backfill.
        // With no credentialed provider, the gap must classify as fixable-by-key,
        // never as "before provider coverage — permanently unavailable".
        when(provider.isConfigured()).thenReturn(false);
        lenient().when(dataHorizonService.isReachable(any(), any(), any())).thenReturn(false);

        MarketDataGap gap = priceGap(TSLA, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).classification())
            .isEqualTo(GapClassification.NO_CREDENTIALED_PROVIDER);
    }

    @Test
    void providerNameIsTheUsableOneNotTheFirstDiscovered() {
        // Keyless install: alpaca is discovered first but has no key; kraken is
        // keyless-usable and actually serves crypto. The row must say kraken.
        MarketDataPort kraken = mock(MarketDataPort.class);
        lenient().when(kraken.getProviderName()).thenReturn("kraken");
        lenient().when(kraken.getSupportedFeatures()).thenReturn(Set.of(
            MarketDataFeature.BACKFILL, MarketDataFeature.CRYPTO, MarketDataFeature.CANDLES));
        lenient().when(kraken.hasFeature(any())).thenAnswer(inv ->
            kraken.getSupportedFeatures().contains((MarketDataFeature) inv.getArgument(0)));
        lenient().when(kraken.isEnabled()).thenReturn(true);
        lenient().when(kraken.isConfigured()).thenReturn(true);
        when(provider.isConfigured()).thenReturn(false);

        service = new ClassifyGapsService(List.of(provider, kraken), dataHorizonService);

        MarketDataGap gap = priceGap(BTC, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        List<ClassifiedGap> result = service.classify(List.of(gap));

        assertThat(result.get(0).classification()).isEqualTo(GapClassification.ACTIONABLE);
        assertThat(result.get(0).provider()).isEqualTo("kraken");
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
