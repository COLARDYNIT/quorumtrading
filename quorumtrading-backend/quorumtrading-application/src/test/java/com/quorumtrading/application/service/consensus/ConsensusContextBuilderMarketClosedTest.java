package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.FinancialDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConsensusContextBuilder#checkMarketClosed} — the stock-backtest guard
 * that skips a ticker's evaluation when the market was closed (no price bar) that day,
 * e.g. the 2026-07-03 Independence Day observance, before any agent LLM call is made.
 */
@ExtendWith(MockitoExtension.class)
class ConsensusContextBuilderMarketClosedTest {

    @Mock private MarketEventRepository marketEventRepository;
    @Mock private IndicatorEnrichmentService indicatorEnrichmentService;
    @Mock private FinancialDataPort financialDataPort;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private UserBudgetRepository userBudgetRepository;
    @Mock private SystemModelDefaultsPort systemModelDefaultsPort;

    private ConsensusContextBuilder contextBuilder;

    private static final String STOCK_STRATEGY_MARKDOWN = """
        ---
        consensus_method: MAJORITY
        stock_tickers:
          - DXYZ
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00"]
        backtest_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, WEDNESDAY, FRIDAY]
          evaluation_times: ["10:00"]
        ---
        # Stock Test Strategy
        """;

    private static final String CRYPTO_STRATEGY_MARKDOWN = """
        ---
        consensus_method: MAJORITY
        crypto_tickers:
          - BTC
        backtest_schedule:
          interval: 1w
        ---
        # Crypto Test Strategy
        """;

    // Friday 2026-07-03 14:00Z = 10:00 America/New_York — NYSE closed (July 4th observed)
    private static final Instant HOLIDAY_EVAL = Instant.parse("2026-07-03T14:00:00Z");

    @BeforeEach
    void setUp() {
        contextBuilder = new ConsensusContextBuilder(
            marketEventRepository, indicatorEnrichmentService, financialDataPort,
            portfolioRepository, consensusDecisionRepository, userBudgetRepository,
            systemModelDefaultsPort);
    }

    @Test
    @DisplayName("Stock backtest on a day with no price bar → skip with explanatory note")
    void stockBacktestWithoutBar_skips() {
        Simulation backtest = backtestSimulation();
        StrategyConfig config = parse(STOCK_STRATEGY_MARKDOWN);
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(0L);

        Optional<ConsensusContextBuilder.MarketClosedSkip> skip =
            contextBuilder.checkMarketClosed(backtest, config, "DXYZ", HOLIDAY_EVAL);

        assertThat(skip).isPresent();
        assertThat(skip.get().note())
            .contains("DXYZ")
            .contains("2026-07-03")
            .contains("market closed");
    }

    @Test
    @DisplayName("Stock backtest on a day with a price bar → no skip")
    void stockBacktestWithBar_proceeds() {
        Simulation backtest = backtestSimulation();
        StrategyConfig config = parse(STOCK_STRATEGY_MARKDOWN);
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(1L);

        Optional<ConsensusContextBuilder.MarketClosedSkip> skip =
            contextBuilder.checkMarketClosed(backtest, config, "DXYZ", HOLIDAY_EVAL);

        assertThat(skip).isEmpty();
    }

    @Test
    @DisplayName("Lookup window is the UTC calendar day, not the exchange-local day")
    void stockBacktest_queriesUtcCalendarDay() {
        // Regression: the window was built from the exchange-local day (America/New_York), which in
        // summer starts at 04:00Z. Daily bars are stamped at UTC midnight, so the day's own bar fell
        // *before* the window and the NEXT day's bar fell inside it — every Friday and every
        // pre-holiday session was wrongly reported "market closed" and skipped.
        Simulation backtest = backtestSimulation();
        StrategyConfig config = parse(STOCK_STRATEGY_MARKDOWN);
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(1L);

        // Thursday 2026-07-02 14:00Z = 10:00 America/New_York — a normal open session
        contextBuilder.checkMarketClosed(
            backtest, config, "DXYZ", Instant.parse("2026-07-02T14:00:00Z"));

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(marketEventRepository).countEventsInRange(
            any(Symbol.class), eq(EventType.PRICE), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-07-02T00:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-07-03T00:00:00Z"));
    }

    @Test
    @DisplayName("A daily bar stamped at UTC midnight counts for its own trading day")
    void stockBacktest_utcMidnightBarCountsForItsOwnDay() {
        Simulation backtest = backtestSimulation();
        StrategyConfig config = parse(STOCK_STRATEGY_MARKDOWN);
        Instant fridayEval = Instant.parse("2026-07-10T14:00:00Z");
        Instant fridayBar = Instant.parse("2026-07-10T00:00:00Z");

        // Only the Friday bar exists — no Saturday bar. The pre-fix window (04:00Z Fri → 04:00Z Sat)
        // contained neither, so the session was skipped despite the market being open.
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenAnswer(invocation -> {
                Instant start = invocation.getArgument(2);
                Instant end = invocation.getArgument(3);
                return !fridayBar.isBefore(start) && fridayBar.isBefore(end) ? 1L : 0L;
            });

        Optional<ConsensusContextBuilder.MarketClosedSkip> skip =
            contextBuilder.checkMarketClosed(backtest, config, "DXYZ", fridayEval);

        assertThat(skip).isEmpty();
    }

    /** Identical schedule to STOCK_STRATEGY_MARKDOWN, written on a Tokyo clock. */
    private static final String TOKYO_CLOCK_STRATEGY_MARKDOWN = """
        ---
        consensus_method: MAJORITY
        stock_tickers:
          - DXYZ
        live_schedule:
          timezone: "Asia/Tokyo"
          trading_days: [SATURDAY]
          evaluation_times: ["02:00"]
          market_open: "01:00"
          market_close: "03:00"
        backtest_schedule:
          timezone: "Asia/Tokyo"
          trading_days: [SATURDAY]
          evaluation_times: ["02:00"]
          market_open: "01:00"
          market_close: "03:00"
        ---
        # Tokyo-clock Test Strategy
        """;

    @Test
    @DisplayName("The trading day is the venue's, not the strategy timezone's — a Tokyo clock must not skip a live US session")
    void stockBacktest_derivesTradingDayInVenueZoneNotStrategyZone() {
        // The schedule guide teaches this exact pattern: Tokyo SATURDAY 02:00 is Friday midday
        // in New York, and the venue validator accepts it. The engine then derived the trading
        // day in Asia/Tokyo, asked for Saturday's bar, found none, and skipped — so a strategy
        // the docs recommend skipped EVERY evaluation and still reported COMPLETED at 0.00%
        // with zero decisions. Same instant, same ticker, opposite outcome to an ET schedule.
        Simulation backtest = backtestSimulation();
        StrategyConfig config = parse(TOKYO_CLOCK_STRATEGY_MARKDOWN);

        // Saturday 2026-07-11 02:00 Asia/Tokyo == Friday 2026-07-10 17:00Z == Friday 13:00 ET.
        Instant tokyoSaturdayEval = Instant.parse("2026-07-10T17:00:00Z");
        Instant fridayBar = Instant.parse("2026-07-10T00:00:00Z");

        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenAnswer(invocation -> {
                Instant start = invocation.getArgument(2);
                Instant end = invocation.getArgument(3);
                return !fridayBar.isBefore(start) && fridayBar.isBefore(end) ? 1L : 0L;
            });

        Optional<ConsensusContextBuilder.MarketClosedSkip> skip =
            contextBuilder.checkMarketClosed(backtest, config, "DXYZ", tokyoSaturdayEval);

        assertThat(skip)
            .as("Friday's US session has a bar — a Tokyo-written schedule must resolve to the "
                + "same trading day as an Eastern-written one firing at the same instant")
            .isEmpty();
    }

    @Test
    @DisplayName("Two schedules firing at the same instant resolve to the same trading day whatever timezone they were written in")
    void stockBacktest_sameInstantSameTradingDayAcrossTimezones() {
        Simulation backtest = backtestSimulation();
        Instant sameInstant = Instant.parse("2026-07-10T17:00:00Z");

        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(1L);

        contextBuilder.checkMarketClosed(backtest, parse(STOCK_STRATEGY_MARKDOWN), "DXYZ", sameInstant);
        contextBuilder.checkMarketClosed(backtest, parse(TOKYO_CLOCK_STRATEGY_MARKDOWN), "DXYZ", sameInstant);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(marketEventRepository, org.mockito.Mockito.times(2)).countEventsInRange(
            any(Symbol.class), eq(EventType.PRICE), from.capture(), any());

        assertThat(from.getAllValues().get(0)).isEqualTo(from.getAllValues().get(1));
    }

    @Test
    @DisplayName("Crypto ticker → never skipped (trades 24/7), repository not queried")
    void cryptoTicker_neverSkipped() {
        Simulation backtest = backtestSimulation();
        StrategyConfig config = parse(CRYPTO_STRATEGY_MARKDOWN);

        Optional<ConsensusContextBuilder.MarketClosedSkip> skip =
            contextBuilder.checkMarketClosed(backtest, config, "BTC", HOLIDAY_EVAL);

        assertThat(skip).isEmpty();
        verifyNoInteractions(marketEventRepository);
    }

    @Test
    @DisplayName("Live/paper simulation (no end date) → never skipped (broker prices, not historical bars)")
    void liveSimulation_neverSkipped() {
        // isBacktest() == (endDate != null): a null end date marks an ongoing live simulation
        Simulation live = new Simulation(
            SimulationId.generate(),
            "Live test",
            StrategyId.generate(),
            Instant.now().minus(30, ChronoUnit.DAYS),
            null,
            new Money(new BigDecimal("10000"), Money.Currency.USD)
        );
        StrategyConfig config = parse(STOCK_STRATEGY_MARKDOWN);

        Optional<ConsensusContextBuilder.MarketClosedSkip> skip =
            contextBuilder.checkMarketClosed(live, config, "DXYZ", HOLIDAY_EVAL);

        assertThat(skip).isEmpty();
        verifyNoInteractions(marketEventRepository);
    }

    private static StrategyConfig parse(String markdown) {
        return new StrategyParser().parse(markdown);
    }

    private static Simulation backtestSimulation() {
        Instant now = Instant.now();
        return new Simulation(
            SimulationId.generate(),
            "Market-closed test",
            StrategyId.generate(),
            now.minus(30, ChronoUnit.DAYS),
            now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("10000"), Money.Currency.USD)
        );
    }
}
