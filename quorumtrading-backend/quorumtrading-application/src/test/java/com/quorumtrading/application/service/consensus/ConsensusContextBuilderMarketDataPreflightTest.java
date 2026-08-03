package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.FinancialDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConsensusContextBuilder#validateMarketDataAvailability} — the coarse pre-flight
 * that stops a consensus round when a ticker has no price data at all.
 *
 * <p>Its failure mode is expensive rather than merely noisy: a failed pre-flight writes a skipped
 * consensus decision and feeds the data-gap circuit breaker, which pauses the simulation. So a
 * false "no data" verdict does not just lose one evaluation — it stops the run.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConsensusContextBuilderMarketDataPreflightTest {

    @Mock private MarketEventRepository marketEventRepository;
    @Mock private IndicatorEnrichmentService indicatorEnrichmentService;
    @Mock private FinancialDataPort financialDataPort;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private UserBudgetRepository userBudgetRepository;
    @Mock private SystemModelDefaultsPort systemModelDefaultsPort;

    private ConsensusContextBuilder contextBuilder;

    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);

    @BeforeEach
    void setUp() {
        contextBuilder = new ConsensusContextBuilder(
            marketEventRepository, indicatorEnrichmentService, financialDataPort,
            portfolioRepository, consensusDecisionRepository, userBudgetRepository,
            systemModelDefaultsPort);
    }

    @Test
    @DisplayName("First evaluation (analysis timestamp == start date) still looks back over a real window")
    void firstEvaluation_windowIsNotZeroWidth() {
        // Regression: a backtest's first evaluation runs at its own start date, so the raw
        // [startDate, analysisTimestamp] window was zero-width and matched only a bar sitting on
        // that exact instant. A 14:00Z start against 4-hourly bars (12:00, 16:00, …) reported
        // "no price data", skipped evaluation #0 and tripped the data-gap circuit breaker at a
        // 100% skip rate — the run was paused before it ever traded.
        Instant startDate = Instant.parse("2026-07-15T14:00:00Z");

        contextBuilder.validateMarketDataAvailability(Set.of(BTC), startDate, startDate);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(marketEventRepository).countEventsInRange(
            any(Symbol.class), eq(EventType.PRICE), from.capture(), to.capture());

        assertThat(to.getValue()).isEqualTo(startDate);
        assertThat(Duration.between(from.getValue(), to.getValue()))
            .as("window must span real time so a nearby bar can satisfy it")
            .isGreaterThanOrEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("A bar shortly before a first-tick evaluation satisfies the pre-flight")
    void firstEvaluation_nearbyBarPasses() {
        Instant startDate = Instant.parse("2026-07-15T14:00:00Z");
        Instant nearestBar = Instant.parse("2026-07-15T12:00:00Z");

        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenAnswer(invocation -> {
                Instant start = invocation.getArgument(2);
                Instant end = invocation.getArgument(3);
                return !nearestBar.isBefore(start) && !nearestBar.isAfter(end) ? 1L : 0L;
            });

        assertThat(contextBuilder.validateMarketDataAvailability(Set.of(BTC), startDate, startDate))
            .isTrue();
    }

    @Test
    @DisplayName("An already-wide window is left alone")
    void laterEvaluation_keepsTheSimulationWindow() {
        Instant startDate = Instant.parse("2026-06-01T00:00:00Z");
        Instant analysisTimestamp = Instant.parse("2026-07-15T14:00:00Z");

        contextBuilder.validateMarketDataAvailability(Set.of(BTC), startDate, analysisTimestamp);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(marketEventRepository).countEventsInRange(
            any(Symbol.class), eq(EventType.PRICE), from.capture(), any());

        assertThat(from.getValue())
            .as("widening must never narrow an existing window")
            .isEqualTo(startDate);
    }

    @Test
    @DisplayName("Genuinely absent price data still fails the pre-flight")
    void noDataAnywhere_stillFails() {
        Instant startDate = Instant.parse("2026-07-15T14:00:00Z");
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(0L);

        assertThat(contextBuilder.validateMarketDataAvailability(Set.of(BTC), startDate, startDate))
            .isFalse();
    }
}
