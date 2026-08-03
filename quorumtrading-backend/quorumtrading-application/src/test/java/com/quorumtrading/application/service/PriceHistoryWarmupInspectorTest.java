package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The defect being guarded: a backtest whose indicators can never compute still
 * reports {@code COMPLETED}, progress 100, no failure reason, and a return of
 * exactly 0.00% — which is indistinguishable from a strategy that correctly
 * declined to trade. The user pays for every agent call and learns nothing.
 */
class PriceHistoryWarmupInspectorTest {

    private static final Instant START = Instant.parse("2026-07-13T00:00:00Z");
    private static final Symbol KO = new Symbol("KO", InvestmentType.STOCK);

    private MarketEventRepository marketEventRepository;
    private PriceHistoryWarmupInspector inspector;

    @BeforeEach
    void setUp() {
        marketEventRepository = mock(MarketEventRepository.class);
        inspector = new PriceHistoryWarmupInspector(marketEventRepository);
    }

    @Test
    @DisplayName("18 bars of history is reported as unable to feed the longer indicators")
    void reportsShortfallForTheObservedFailure() {
        // The exact case from a first-run audit: a month of data backfilled, a strategy
        // requiring SMA-50/MACD/Bollinger accepted in silence, 28 agent calls spent, and
        // a clean green result in which not one of those indicators ever evaluated.
        givenBarsBeforeStart(18);

        List<PriceHistoryWarmupInspector.TickerWarmup> shortfalls =
            inspector.inspect(configWith(KO), START);

        assertThat(shortfalls).hasSize(1);
        assertThat(shortfalls.get(0).barsAvailable()).isEqualTo(18);
        assertThat(shortfalls.get(0).uncomputable())
            .contains("Bollinger Bands (20)", "MACD (12/26/9)", "SMA-50 / EMA-50", "SMA-200 / EMA-200")
            .as("RSI-14 needs 15 bars and IS satisfied by 18 — reporting it would cry wolf")
            .doesNotContain("RSI-14");
    }

    @Test
    @DisplayName("the warning names the counts, so the user knows how much more to backfill")
    void warningIsActionable() {
        givenBarsBeforeStart(18);

        String warning = inspector.describe(inspector.inspect(configWith(KO), START));

        assertThat(warning)
            .contains("KO")
            .contains("18 bar")
            .contains("SMA-50")
            // "Insufficient data" is not actionable; a number and a target are.
            .contains("220");
    }

    @Test
    @DisplayName("ample history produces no warning at all")
    void staysSilentWhenEverythingComputes() {
        givenBarsBeforeStart(500);

        assertThat(inspector.inspect(configWith(KO), START)).isEmpty();
        assertThat(inspector.describe(List.of())).isEmpty();
    }

    @Test
    @DisplayName("only history BEFORE the start date counts as warm-up")
    void countsOnlyPriorHistory() {
        // Bars inside the backtest window are what is being traded, not warm-up. Counting
        // them would report history the indicators cannot use and hide the shortfall.
        givenBarsBeforeStart(0);

        inspector.inspect(configWith(KO), START);

        org.mockito.Mockito.verify(marketEventRepository).countEventsInRange(
            eq(KO), eq(EventType.PRICE), eq(Instant.EPOCH), eq(START.minusMillis(1)));
    }

    @Test
    @DisplayName("a repository failure never blocks simulation creation")
    void failsOpen() {
        when(marketEventRepository.countEventsInRange(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("database unavailable"));

        // A warning that cannot be produced must not become an error that stops the user.
        assertThat(inspector.inspect(configWith(KO), START)).isEmpty();
    }

    private void givenBarsBeforeStart(long bars) {
        when(marketEventRepository.countEventsInRange(any(), eq(EventType.PRICE), any(), any()))
            .thenReturn(bars);
    }

    private StrategyConfig configWith(Symbol symbol) {
        StrategyConfig config = new StrategyConfig();
        config.setStockTickers(Set.of(symbol));
        return config;
    }
}
