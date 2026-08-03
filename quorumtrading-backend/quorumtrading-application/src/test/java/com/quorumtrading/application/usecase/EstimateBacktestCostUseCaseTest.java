package com.quorumtrading.application.usecase;

import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort.LlmProviderPreference;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.service.CostEstimationService;
import com.quorumtrading.model.service.ModelSelectionService;
import com.quorumtrading.model.valueobject.CostEstimate;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cost estimation for backtests, with emphasis on schedules that evaluate less often than the
 * default. Narrowing {@code backtest_schedule} is the documented way to keep AI spend down, so an
 * estimate that ignores the narrowing is worse than no estimate at all.
 */
class EstimateBacktestCostUseCaseTest {

    /** Jan 1 2026 -> Jul 25 2026, the window used by the exploratory backtests. */
    private static final long CALENDAR_DAYS = 205;

    /** The same window as dates, for the overload {@code create_simulation} actually calls. */
    private static final Instant WINDOW_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant WINDOW_TO = Instant.parse("2026-07-25T00:00:00Z");

    private static final UserId USER = new UserId(UUID.randomUUID());

    private StrategyRepository strategyRepository;
    private EstimateBacktestCostUseCase useCase;

    @BeforeEach
    void setUp() {
        strategyRepository = mock(StrategyRepository.class);
        LLMPort llmPort = mock(LLMPort.class);
        UserBudgetRepository userBudgetRepository = mock(UserBudgetRepository.class);
        ModelSelectionService modelSelectionService = mock(ModelSelectionService.class);
        LlmProviderPreferencePort providerPreference = mock(LlmProviderPreferencePort.class);

        // A local engine short-circuits model resolution to the free tier, keeping this test about
        // round counting rather than pricing.
        when(providerPreference.current())
            .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL_HTTP, Instant.EPOCH)));

        useCase = new EstimateBacktestCostUseCase(
            strategyRepository, llmPort, userBudgetRepository, modelSelectionService,
            new StrategyParser(), new CostEstimationService(), 2000, 400, providerPreference
        );
    }

    private CostEstimate estimateFor(String markdown) {
        StrategyId id = new StrategyId(UUID.randomUUID());
        Strategy strategy = mock(Strategy.class);
        when(strategy.getContent()).thenReturn(markdown);
        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        return useCase.estimateForSimulation(id, CALENDAR_DAYS, USER);
    }

    /** The same estimate taken through the date-aware overload, over the same 205-day window. */
    private CostEstimate estimateForDates(String markdown) {
        StrategyId id = new StrategyId(UUID.randomUUID());
        Strategy strategy = mock(Strategy.class);
        when(strategy.getContent()).thenReturn(markdown);
        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        return useCase.estimateForSimulation(id, WINDOW_FROM, WINDOW_TO, USER);
    }

    /**
     * The estimator prices the schedule and used to know nothing about data: a window seven
     * years before the first stored bar produced byte-identical figures to a covered one.
     * Since create_simulation starts the run, the estimate is the last place to say so.
     */
    @Nested
    @DisplayName("Stored-price-coverage cross-check")
    class DataCoverageCrossCheck {

        private com.quorumtrading.application.port.out.HistoricalPricePort historicalPricePort;
        private EstimateBacktestCostUseCase useCaseWithCoverage;

        private static final String CRYPTO_STRATEGY = """
            ---
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1d
            ---

            # BTC Test Strategy

            Body.
            """;

        @BeforeEach
        void wireCoveragePort() {
            historicalPricePort = mock(com.quorumtrading.application.port.out.HistoricalPricePort.class);
            LlmProviderPreferencePort providerPreference = mock(LlmProviderPreferencePort.class);
            when(providerPreference.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL_HTTP, Instant.EPOCH)));
            useCaseWithCoverage = new EstimateBacktestCostUseCase(
                strategyRepository, mock(LLMPort.class), mock(UserBudgetRepository.class),
                mock(ModelSelectionService.class), new StrategyParser(), new CostEstimationService(),
                2000, 400, providerPreference, null, historicalPricePort);
        }

        private CostEstimate estimateWithCoverage(long expectedDays, long coveredDays) {
            var window = new com.quorumtrading.model.valueobject.PriceCoverage.Window(
                java.time.LocalDate.ofInstant(WINDOW_FROM, java.time.ZoneOffset.UTC),
                java.time.LocalDate.ofInstant(WINDOW_TO, java.time.ZoneOffset.UTC),
                expectedDays, coveredDays, java.util.List.of(), "CRYPTO — every day expects a bar");
            var coverage = new com.quorumtrading.model.valueobject.PriceCoverage(
                "BTC", coveredDays, coveredDays, WINDOW_FROM, WINDOW_TO, java.util.List.of("kraken"), window);
            when(historicalPricePort.getCoverage(
                    any(com.quorumtrading.model.valueobject.Symbol.class),
                    any(java.time.LocalDate.class), any(java.time.LocalDate.class)))
                .thenReturn(coverage);

            StrategyId id = new StrategyId(UUID.randomUUID());
            Strategy strategy = mock(Strategy.class);
            when(strategy.getContent()).thenReturn(CRYPTO_STRATEGY);
            when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
            return useCaseWithCoverage.estimateForSimulation(id, WINDOW_FROM, WINDOW_TO, USER);
        }

        @Test
        @DisplayName("a window with no stored bars at all carries a coverage warning")
        void emptyWindowWarns() {
            CostEstimate estimate = estimateWithCoverage(205, 0);

            assertThat(estimate.dataCoverageWarning())
                .isNotNull()
                .contains("no bars at all for BTC")
                .contains("prices the schedule, not the data");
        }

        @Test
        @DisplayName("a mostly-missing window warns with the measured counts")
        void mostlyMissingWindowWarnsWithCounts() {
            CostEstimate estimate = estimateWithCoverage(205, 70);

            assertThat(estimate.dataCoverageWarning())
                .isNotNull()
                .contains("only partial data for")
                .contains("BTC (70 of 205 expected days stored)");
        }

        @Test
        @DisplayName("holiday-sized gaps stay silent — a full stock month legitimately misses a day or two")
        void holidaySizedGapsDoNotWarn() {
            CostEstimate estimate = estimateWithCoverage(22, 21);

            assertThat(estimate.dataCoverageWarning()).isNull();
        }

        @Test
        @DisplayName("a fully covered window carries no warning")
        void coveredWindowStaysSilent() {
            CostEstimate estimate = estimateWithCoverage(205, 205);

            assertThat(estimate.dataCoverageWarning()).isNull();
        }

        @Test
        @DisplayName("wiring without the port (legacy constructors, unit tests) skips the check")
        void withoutPortTheCheckIsSkipped() {
            CostEstimate estimate = estimateForDates(CRYPTO_STRATEGY);

            assertThat(estimate.dataCoverageWarning()).isNull();
        }
    }

    private static String stockStrategy(String backtestTradingDays) {
        return """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            stock_tickers:
              - NVDA
            live_schedule:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00"]
            backtest_schedule:
              timezone: "America/New_York"
              trading_days: %s
              evaluation_times: ["10:00"]
            agents:
              - TECHNICAL
              - FUNDAMENTAL
              - SENTIMENT
            ---

            # NVDA Test Strategy

            Body.
            """.formatted(backtestTradingDays);
    }

    @Nested
    @DisplayName("Stock backtest_schedule narrowing")
    class StockScheduleNarrowing {

        @Test
        @DisplayName("a Monday-only backtest schedule costs a fifth of an all-weekday one")
        void mondayOnlyIsAFifthOfEveryWeekday() {
            CostEstimate everyWeekday = estimateFor(
                stockStrategy("[MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]"));
            CostEstimate mondayOnly = estimateFor(stockStrategy("[MONDAY]"));

            assertThat(mondayOnly.totalLLMCalls())
                .as("a one-day-a-week schedule must not be quoted at the five-day rate")
                .isLessThan(everyWeekday.totalLLMCalls());
            assertThat(mondayOnly.totalLLMCalls())
                .isCloseTo(everyWeekday.totalLLMCalls() / 5,
                    org.assertj.core.data.Offset.offset(everyWeekday.totalLLMCalls() / 20));
        }

        @Test
        @DisplayName("Monday-only over ~7 months matches the ~29 rounds actually executed")
        void mondayOnlyMatchesObservedRoundCount() {
            CostEstimate estimate = estimateFor(stockStrategy("[MONDAY]"));

            // 205 calendar days is 29 Mondays; one ticker, three agents plus the orchestrator.
            assertThat(estimate.totalConsensusRounds()).isBetween(27L, 31L);
            assertThat(estimate.agentsPerRound()).isEqualTo(4);
            assertThat(estimate.totalLLMCalls()).isBetween(108L, 124L);
        }

        @Test
        @DisplayName("two trading days a week land between the one- and five-day estimates")
        void twoDaysPerWeekScalesProportionally() {
            long oneDay = estimateFor(stockStrategy("[MONDAY]")).totalLLMCalls();
            long twoDays = estimateFor(stockStrategy("[MONDAY, THURSDAY]")).totalLLMCalls();
            long fiveDays = estimateFor(
                stockStrategy("[MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]")).totalLLMCalls();

            assertThat(twoDays).isGreaterThan(oneDay).isLessThan(fiveDays);
        }
    }

    /**
     * The date-aware overload is the one {@code create_simulation} calls, so it is the one a user
     * is quoted from. It refines the day-count approximation by counting real weekdays — but the
     * refinement must still respect {@code backtest_schedule}, or narrowing trading_days (the
     * documented way to control spend) is quoted at the full five-day rate.
     */
    @Nested
    @DisplayName("Date-aware overload honours backtest_schedule")
    class DateAwareNarrowing {

        @Test
        @DisplayName("a Monday-only schedule is not quoted at the every-weekday rate")
        void mondayOnlyIsNotQuotedAtTheFullWeekdayRate() {
            CostEstimate everyWeekday = estimateForDates(
                stockStrategy("[MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]"));
            CostEstimate mondayOnly = estimateForDates(stockStrategy("[MONDAY]"));

            assertThat(mondayOnly.totalLLMCalls())
                .as("counting exact weekdays must not discard the schedule narrowing")
                .isLessThan(everyWeekday.totalLLMCalls());
            assertThat(mondayOnly.totalLLMCalls())
                .isCloseTo(everyWeekday.totalLLMCalls() / 5,
                    org.assertj.core.data.Offset.offset(everyWeekday.totalLLMCalls() / 10));
        }

        /**
         * The two overloads are shown to the same user seconds apart — the pre-flight refusal
         * quotes one and the creation confirmation quotes the other. They disagreed by 5x
         * (~21 calls then ~120) for one unchanged strategy, which is worse than either being
         * wrong alone: it tells the user neither figure can be trusted.
         */
        @Test
        @DisplayName("both overloads agree on the same window and strategy")
        void bothOverloadsAgreeOnTheSameWindow() {
            for (String tradingDays : new String[]{
                    "[MONDAY]", "[MONDAY, THURSDAY]",
                    "[MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]"}) {
                CostEstimate byDayCount = estimateFor(stockStrategy(tradingDays));
                CostEstimate byDates = estimateForDates(stockStrategy(tradingDays));

                assertThat(byDates.totalLLMCalls())
                    .as("the two cost surfaces must not disagree for " + tradingDays)
                    .isCloseTo(byDayCount.totalLLMCalls(),
                        org.assertj.core.data.Offset.offset(
                            Math.max(4L, byDayCount.totalLLMCalls() / 5)));
            }
        }

        @Test
        @DisplayName("Monday-only over the window matches the ~29 rounds actually executed")
        void mondayOnlyMatchesObservedRoundCount() {
            CostEstimate estimate = estimateForDates(stockStrategy("[MONDAY]"));

            // Jan 1 -> Jul 25 2026 contains 29 Mondays; one ticker, three agents + orchestrator.
            assertThat(estimate.totalConsensusRounds()).isBetween(27L, 31L);
            assertThat(estimate.agentsPerRound()).isEqualTo(4);
            assertThat(estimate.totalLLMCalls()).isBetween(108L, 124L);
        }
    }

    @Nested
    @DisplayName("Crypto backtest interval")
    class CryptoInterval {

        private static final String WEEKLY_CRYPTO = """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            crypto_tickers:
              - ETH
            live_schedule:
              interval: 1d
            backtest_schedule:
              interval: 1w
            agents:
              - TECHNICAL
              - FUNDAMENTAL
              - SENTIMENT
            ---

            # ETH Test Strategy

            Body.
            """;

        @Test
        @DisplayName("a weekly crypto interval yields roughly one round per week")
        void weeklyIntervalYieldsWeeklyRounds() {
            CostEstimate estimate = estimateFor(WEEKLY_CRYPTO);

            // 205 days at one evaluation per week is ~29 rounds.
            assertThat(estimate.totalConsensusRounds()).isBetween(27L, 31L);
            assertThat(estimate.totalLLMCalls()).isBetween(108L, 124L);
        }

        /**
         * A schedule fires on the start date and every Nth day after, so a span of D days contains
         * floor(D/N) + 1 firings. The estimator computed plain floor(D/N) and so dropped the first
         * one: January 2026 on a weekly cadence was quoted at 4 rounds and 12 agent calls, and the
         * run executed 5 and 15. Two independent newcomer runs measured it.
         *
         * <p>This is the number the docs promise is an <em>upper</em> bound and that
         * create_simulation echoes back when confirming a run, so quoting under the truth is the
         * one direction this tool may not err in.</p>
         */
        @Test
        @DisplayName("a weekly cadence counts the firing on the start date too")
        void weeklyIntervalCountsTheInclusiveStartRound() {
            StrategyId id = new StrategyId(UUID.randomUUID());
            Strategy strategy = mock(Strategy.class);
            when(strategy.getContent()).thenReturn(WEEKLY_CRYPTO);
            when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));

            // 2026-01-01 .. 2026-01-31 fires on the 1st, 8th, 15th, 22nd and 29th.
            CostEstimate estimate = useCase.estimateForSimulation(
                id,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T00:00:00Z"),
                USER);

            assertThat(estimate.totalConsensusRounds())
                .as("floor(30/7) is 4; the run fires 5 times")
                .isEqualTo(5L);
        }

        @Test
        @DisplayName("a window shorter than one interval still quotes the start-date round")
        void windowShorterThanOneIntervalStillQuotesOneRound() {
            StrategyId id = new StrategyId(UUID.randomUUID());
            Strategy strategy = mock(Strategy.class);
            when(strategy.getContent()).thenReturn(WEEKLY_CRYPTO);
            when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));

            CostEstimate estimate = useCase.estimateForSimulation(
                id,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-04T00:00:00Z"),
                USER);

            assertThat(estimate.totalConsensusRounds()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("a window containing no scheduled day is quoted as zero, not floored to one")
    void windowWithNoScheduledDayQuotesZero() {
        // The one moment this tool can save a user from an empty run, and the one case it used to
        // discard: exactWeekdays == 0 fell through to the approximate path, which floors at 1, so
        // a Friday-only strategy over a Mon-Thu window was quoted "1 trading day, 1 round".
        StrategyId id = new StrategyId(UUID.randomUUID());
        Strategy strategy = mock(Strategy.class);
        when(strategy.getContent()).thenReturn(stockStrategy("[FRIDAY]"));
        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));

        // Monday 2026-06-01 through Thursday 2026-06-04 — contains no Friday.
        CostEstimate estimate = useCase.estimateForSimulation(
            id,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-06-04T23:59:59Z"),
            USER);

        assertThat(estimate.tradingDays()).isZero();
        assertThat(estimate.totalConsensusRounds()).isZero();
        assertThat(estimate.totalLLMCalls()).isZero();
    }

    @Nested
    @DisplayName("Wall-clock projection — for a free engine, time is the cost")
    class WallClockProjection {

        private com.quorumtrading.application.port.out.LlmTelemetryPort telemetryPort;

        private EstimateBacktestCostUseCase timedUseCase() {
            LlmProviderPreferencePort providerPreference = mock(LlmProviderPreferencePort.class);
            when(providerPreference.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL_HTTP, Instant.EPOCH)));
            telemetryPort = mock(com.quorumtrading.application.port.out.LlmTelemetryPort.class);
            return new EstimateBacktestCostUseCase(
                strategyRepository, mock(LLMPort.class), mock(UserBudgetRepository.class),
                mock(ModelSelectionService.class), new StrategyParser(), new CostEstimationService(),
                2000, 400, providerPreference, telemetryPort, null);
        }

        private EstimateBacktestCostUseCase.TimedCostEstimate timedEstimate(EstimateBacktestCostUseCase uc) {
            StrategyId id = new StrategyId(UUID.randomUUID());
            Strategy strategy = mock(Strategy.class);
            when(strategy.getContent()).thenReturn(stockStrategy("[MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]"));
            when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
            return uc.estimateForSimulationWithTime(id, WINDOW_FROM, WINDOW_TO, USER);
        }

        @Test
        @DisplayName("observed latency × calls, with the basis stated")
        void projectsFromObservedLatency() {
            EstimateBacktestCostUseCase uc = timedUseCase();
            when(telemetryPort.telemetryFor(LlmProvider.LOCAL_HTTP)).thenReturn(Optional.of(
                new com.quorumtrading.application.port.out.LlmTelemetryPort.ProviderTelemetry(
                    LlmProvider.LOCAL_HTTP, 5, 5, 0, 0, 30_000L, 29_000L, 0, null, null, Instant.EPOCH)));

            var timed = timedEstimate(uc);

            assertThat(timed.estimatedWallClockSeconds())
                .isEqualTo(timed.estimate().totalLLMCalls() * 30L);
            assertThat(timed.timeBasis()).contains("30000ms").contains("serialized");
        }

        @Test
        @DisplayName("too few samples → no number, and the basis says why")
        void hedgesWithoutEnoughSamples() {
            EstimateBacktestCostUseCase uc = timedUseCase();
            when(telemetryPort.telemetryFor(LlmProvider.LOCAL_HTTP)).thenReturn(Optional.of(
                new com.quorumtrading.application.port.out.LlmTelemetryPort.ProviderTelemetry(
                    LlmProvider.LOCAL_HTTP, 1, 1, 0, 0, 30_000L, 30_000L, 0, null, null, Instant.EPOCH)));

            var timed = timedEstimate(uc);

            assertThat(timed.estimatedWallClockSeconds()).isNull();
            assertThat(timed.timeBasis()).contains("cannot be projected");
        }

        @Test
        @DisplayName("no telemetry source wired → hedged, never a guess")
        void hedgesWithoutATelemetrySource() {
            var timed = timedEstimate(useCase); // the setUp instance has no telemetry port

            assertThat(timed.estimatedWallClockSeconds()).isNull();
            assertThat(timed.timeBasis()).isNotBlank();
        }
    }

}
