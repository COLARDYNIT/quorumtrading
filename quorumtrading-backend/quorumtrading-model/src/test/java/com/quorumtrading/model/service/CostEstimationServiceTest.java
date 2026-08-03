package com.quorumtrading.model.service;

import com.quorumtrading.model.valueobject.CostEstimate;
import com.quorumtrading.model.valueobject.LLMPricing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class CostEstimationServiceTest {

    private CostEstimationService service;

    // Gemini 2.5 Flash pricing: $0.15/M prompt, $0.60/M completion
    private static final LLMPricing GEMINI_FLASH_PRICING = LLMPricing.of(
        new BigDecimal("0.00000015"),  // $0.15 per million
        new BigDecimal("0.0000006")    // $0.60 per million
    );

    // Non-trivial rate so the markup multiplication stays observable in assertions
    private static final BigDecimal MARKUP_RATE = new BigDecimal("1.50");
    private static final int AVG_PROMPT_TOKENS = 2540;
    private static final int AVG_COMPLETION_TOKENS = 160;

    @BeforeEach
    void setUp() {
        PlatformMarkup.configure(MARKUP_RATE);
        service = new CostEstimationService();
    }

    @AfterEach
    void tearDown() {
        PlatformMarkup.reset();
    }

    @Test
    void shouldCalculateStockBacktestCost() {
        // 182 calendar days (~6 months) → 182 * 5/7 = 130 trading days
        // 1 eval/day, 16 tickers, 4 agents + 1 orchestrator = 5
        // 130 * 1 * 16 * 5 = 10,400 LLM calls
        CostEstimate estimate = service.estimate(
            182, false, 1, 16, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.tradingDays()).isEqualTo(130);
        assertThat(estimate.totalConsensusRounds()).isEqualTo(130);
        assertThat(estimate.tickerCount()).isEqualTo(16);
        assertThat(estimate.agentsPerRound()).isEqualTo(5);
        assertThat(estimate.totalLLMCalls()).isEqualTo(10_400);
        assertThat(estimate.modelId()).isEqualTo("google/gemini-2.5-flash");
        assertThat(estimate.modelName()).isEqualTo("Gemini 2.5 Flash");
        assertThat(estimate.estimatedCost()).isGreaterThan(BigDecimal.ZERO);

        // Verify cost per call: (2540 * 0.00000015 + 160 * 0.0000006) * 1.30
        BigDecimal rawCost = GEMINI_FLASH_PRICING.estimateCost(AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS);
        BigDecimal expectedCostPerCall = rawCost.multiply(MARKUP_RATE);
        assertThat(estimate.costPerCall()).isEqualByComparingTo(expectedCostPerCall.setScale(8, java.math.RoundingMode.HALF_UP));

        // Total: 10400 * costPerCall
        BigDecimal expectedTotal = expectedCostPerCall.multiply(BigDecimal.valueOf(10_400))
            .setScale(4, java.math.RoundingMode.HALF_UP);
        assertThat(estimate.estimatedCost()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void shouldCalculateCryptoBacktestCost() {
        // Crypto: all calendar days are trading days
        // 30 calendar days, 24 evals/day (hourly), 1 ticker, 4 agents
        CostEstimate estimate = service.estimate(
            30, true, 24, 1, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.tradingDays()).isEqualTo(30);
        assertThat(estimate.totalConsensusRounds()).isEqualTo(30 * 24); // 720
        assertThat(estimate.totalLLMCalls()).isEqualTo(720L * 1 * 5); // 3600
    }

    // =====================================================================
    // Short windows
    //
    // Every case above picks a span where calendarDays * 5/7 happens to land exactly (182 -> 130,
    // 7 -> 5), so the formula could never fail them — the tests encoded the implementation rather
    // than the requirement. Integer division makes it worst precisely where a newcomer starts: a
    // few days. And below one trading day the estimate collapses to zero calls and $0.00 while
    // the run still bills a full set of agents. On a local engine that is invisible; on a metered
    // provider it is someone's card.
    // =====================================================================

    @Test
    void shouldCountWeekdaysExactlyWhenGivenTheDates() {
        // Mon 2026-06-01 -> Fri 2026-06-05.
        CostEstimate estimate = service.estimate(
            java.time.LocalDate.parse("2026-06-01"), java.time.LocalDate.parse("2026-06-05"),
            false, 1, 1, 3, false,
            GEMINI_FLASH_PRICING, AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.tradingDays())
            .as("a Mon-Fri window is five trading days")
            .isEqualTo(5);
    }

    @Test
    void shouldExcludeTheWeekendWhenGivenTheDates() {
        // Fri 2026-06-05 -> Mon 2026-06-08 spans four calendar days, two of them a weekend.
        CostEstimate estimate = service.estimate(
            java.time.LocalDate.parse("2026-06-05"), java.time.LocalDate.parse("2026-06-08"),
            false, 1, 1, 3, false,
            GEMINI_FLASH_PRICING, AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.tradingDays()).isEqualTo(2);
    }

    @Test
    void shouldNotTruncateAShortStockWindowWithoutDates() {
        // Without a start date the exact weekday count is unknowable, but rounding down every
        // time is not: four calendar days is nearer three trading days than two.
        CostEstimate estimate = service.estimate(
            4, false, 1, 1, 3, false,
            GEMINI_FLASH_PRICING, AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.tradingDays()).isEqualTo(3);
    }

    @Test
    void shouldNeverEstimateZeroForANonEmptyStockWindow() {
        CostEstimate estimate = service.estimate(
            1, false, 1, 1, 3, false,
            GEMINI_FLASH_PRICING, AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.tradingDays())
            .as("a one-day backtest still runs a day of agents")
            .isGreaterThanOrEqualTo(1);
        assertThat(estimate.totalLLMCalls())
            .as("quoting zero LLM calls for a run that makes them is the expensive direction "
                + "to be wrong in")
            .isGreaterThan(0);
    }

    @Test
    void shouldNotQuoteFreeForAShortStockBacktest() {
        CostEstimate estimate = service.estimate(
            2, false, 1, 1, 3, false,
            GEMINI_FLASH_PRICING, AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.estimatedCost())
            .as("$0.00 tells the caller the run is free when it is not")
            .isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldApplyWeekdayReductionForStocks() {
        // 7 calendar days → 5 trading days (exact 5/7 ratio)
        CostEstimate estimate = service.estimate(
            7, false, 1, 1, 1, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        assertThat(estimate.tradingDays()).isEqualTo(5);
    }

    @Test
    void shouldNotApplyWeekdayReductionForCrypto() {
        // 7 calendar days → 7 trading days (crypto trades every day)
        CostEstimate estimate = service.estimate(
            7, true, 1, 1, 1, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        assertThat(estimate.tradingDays()).isEqualTo(7);
    }

    @Test
    void shouldComputeBoundsAt80And120Percent() {
        CostEstimate estimate = service.estimate(
            30, false, 1, 1, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        BigDecimal cost = estimate.estimatedCost();
        BigDecimal expectedLower = cost.multiply(new BigDecimal("0.80"))
            .setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedUpper = cost.multiply(new BigDecimal("1.20"))
            .setScale(4, java.math.RoundingMode.HALF_UP);

        assertThat(estimate.lowerBound()).isEqualByComparingTo(expectedLower);
        assertThat(estimate.upperBound()).isEqualByComparingTo(expectedUpper);
    }

    @Test
    void shouldReturnZeroCostForZeroTradingDays() {
        CostEstimate estimate = service.estimate(
            0, false, 1, 1, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        assertThat(estimate.tradingDays()).isEqualTo(0);
        assertThat(estimate.totalLLMCalls()).isEqualTo(0);
        assertThat(estimate.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnZeroCostForZeroTickers() {
        CostEstimate estimate = service.estimate(
            30, false, 1, 0, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        assertThat(estimate.totalLLMCalls()).isEqualTo(0);
        assertThat(estimate.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnZeroCostForZeroEvalsPerDay() {
        CostEstimate estimate = service.estimate(
            30, false, 0, 1, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        assertThat(estimate.totalLLMCalls()).isEqualTo(0);
        assertThat(estimate.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldNotBillAnOrchestratorCallUnderMajority() {
        // MAJORITY tallies agent votes in code and calls no orchestrator, so an estimate that
        // adds one overstates every MAJORITY backtest by a full agent's worth of calls.
        CostEstimate majority = service.estimate(
            182, false, 1, 16, 3, false,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );
        CostEstimate orchestrated = service.estimate(
            182, false, 1, 16, 3, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(majority.agentsPerRound()).isEqualTo(3);
        assertThat(orchestrated.agentsPerRound()).isEqualTo(4);
        assertThat(majority.totalLLMCalls())
            .as("MAJORITY must bill three agent calls per ticker-round, not four")
            .isEqualTo(orchestrated.totalLLMCalls() * 3 / 4);
        assertThat(majority.estimatedCost()).isLessThan(orchestrated.estimatedCost());
    }

    @Test
    void shouldReturnZeroCostForFreeModel() {
        CostEstimate estimate = service.estimate(
            30, false, 1, 1, 4, true,
            LLMPricing.FREE,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "free-model", "Free Model"
        );

        assertThat(estimate.totalLLMCalls()).isGreaterThan(0);
        assertThat(estimate.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(estimate.costPerCall()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldIncludeOrchestratorInAgentCount() {
        // 3 enabled agents → 4 agents per round (3 + orchestrator)
        CostEstimate estimate = service.estimate(
            7, false, 1, 1, 3, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        assertThat(estimate.agentsPerRound()).isEqualTo(4);
    }

    @Test
    void shouldScaleLinearlyWithMultipleEvalsPerDay() {
        CostEstimate singleEval = service.estimate(
            7, false, 1, 1, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        CostEstimate tripleEval = service.estimate(
            7, false, 3, 1, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "test-model", "Test"
        );

        assertThat(tripleEval.totalLLMCalls()).isEqualTo(singleEval.totalLLMCalls() * 3);
        assertThat(tripleEval.estimatedCost()).isEqualByComparingTo(
            singleEval.estimatedCost().multiply(BigDecimal.valueOf(3))
        );
    }

    @Test
    void shouldFormatCostAsDollarAmount() {
        CostEstimate estimate = service.estimate(
            182, false, 1, 16, 4, true,
            GEMINI_FLASH_PRICING,
            AVG_PROMPT_TOKENS, AVG_COMPLETION_TOKENS,
            "google/gemini-2.5-flash", "Gemini 2.5 Flash"
        );

        assertThat(estimate.formattedCost()).startsWith("$");
        assertThat(estimate.formattedRange()).contains("–");
    }
}
