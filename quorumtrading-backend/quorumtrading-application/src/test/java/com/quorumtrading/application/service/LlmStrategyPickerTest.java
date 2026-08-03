package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmStrategyPickerTest {

    @Mock private LLMPort llmPort;
    @Mock private StrategyRepository strategyRepository;
    @Mock private RecordLLMUsageUseCase recordLLMUsageUseCase;

    private StrategyPickerPort fallback;
    private LlmStrategyPicker picker;

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final BrokerAccountId BROKER = BrokerAccountId.generate();

    private Strategy strategyA;
    private Strategy strategyB;
    private InvestmentPlan plan;

    @BeforeEach
    void setUp() {
        strategyA = new Strategy(StrategyId.generate(), "Momentum SPY", "tech-heavy momentum strategy", USER);
        strategyB = new Strategy(StrategyId.generate(), "Defensive Bonds", "conservative bond ladder", USER);
        // Heuristic fallback would pick whichever is most-recently-updated. Both are
        // freshly constructed so the order is effectively undefined — we'll mock the
        // fallback explicitly in tests that exercise the fallback path.
        fallback = new HeuristicStrategyPicker(strategyRepository);
        picker = new LlmStrategyPicker(llmPort, strategyRepository, fallback, recordLLMUsageUseCase,
                "openai/gpt-4o-mini", 0.2, 500);

        plan = InvestmentPlan.create(
                InvestmentPlanId.generate(), USER, BROKER,
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("100")),
                1, PickerPolicy.AI_PICK,
                InvestmentPlanPreferences.EMPTY,
                Instant.now());
    }

    @Test
    void picks_the_strategy_chosen_by_the_llm() {
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(strategyA, strategyB));
        String llmResponse = """
            {"chosenStrategyId":"%s","reasoning":"Best fit for moderate risk profile."}
            """.formatted(strategyB.getId().value());
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenReturn(new LLMQueryResult(llmResponse,
                        LLMUsage.of("openai/gpt-4o-mini", 100, 30, new BigDecimal("0.0005")),
                        "openai/gpt-4o-mini"));

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isTrue();
        assertThat(outcome.chosenStrategyId()).contains(strategyB.getId());
        assertThat(outcome.reasoning()).startsWith("LLM picker:");
        assertThat(outcome.reasoning()).contains("moderate risk profile");
        assertThat(outcome.llmCostUsd()).isGreaterThan(BigDecimal.ZERO);

        // Budget tracking must record the LLM spend.
        verify(recordLLMUsageUseCase).recordUsage(
                eq(USER), any(BigDecimal.class), any(BigDecimal.class), eq(130), eq(UsageSource.CHAT));
    }

    @Test
    void returns_none_when_user_has_no_active_strategies() {
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of());

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isFalse();
        assertThat(outcome.reasoning()).contains("No active strategies");
        verify(llmPort, never()).queryWithUsage(anyString(), any());
    }

    @Test
    void llm_decline_yields_none_with_llm_reasoning_and_cost() {
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(strategyA));
        String llmResponse = """
            {"chosenStrategyId":null,"reasoning":"All candidates exceed user risk tolerance."}
            """;
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenReturn(new LLMQueryResult(llmResponse,
                        LLMUsage.of("openai/gpt-4o-mini", 80, 20, new BigDecimal("0.0003")),
                        "openai/gpt-4o-mini"));

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isFalse();
        assertThat(outcome.reasoning()).contains("exceed user risk tolerance");
        assertThat(outcome.reasoning()).startsWith("LLM picker:");
        assertThat(outcome.llmCostUsd()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void llm_failure_falls_back_to_heuristic_with_annotation() {
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(strategyA));
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenThrow(new RuntimeException("network blip"));

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isTrue();
        assertThat(outcome.chosenStrategyId()).contains(strategyA.getId());
        assertThat(outcome.reasoning()).contains("LLM picker unavailable");
        assertThat(outcome.reasoning()).contains("network blip");
    }

    @Test
    void unknown_strategy_id_in_response_falls_back_to_heuristic() {
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(strategyA));
        String llmResponse = """
            {"chosenStrategyId":"%s","reasoning":"Hallucinated pick."}
            """.formatted(UUID.randomUUID());  // not in candidates
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenReturn(new LLMQueryResult(llmResponse, LLMUsage.ZERO, "openai/gpt-4o-mini"));

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isTrue();
        assertThat(outcome.chosenStrategyId()).contains(strategyA.getId());
        assertThat(outcome.reasoning()).contains("LLM picker unavailable");
        assertThat(outcome.reasoning()).contains("unknown strategy id");
    }

    @Test
    void unparseable_response_falls_back_to_heuristic() {
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(strategyA));
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenReturn(new LLMQueryResult("not json at all",
                        LLMUsage.ZERO, "openai/gpt-4o-mini"));

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isTrue();
        assertThat(outcome.chosenStrategyId()).contains(strategyA.getId());
        assertThat(outcome.reasoning()).contains("LLM picker unavailable");
    }

    @Test
    void ticker_policy_blocks_violating_strategy_before_llm_is_called() {
        Strategy aaplOnly = new Strategy(StrategyId.generate(), "AAPL bot",
                tickerStrategy("AAPL"), USER);
        Strategy tslaOnly = new Strategy(StrategyId.generate(), "TSLA bot",
                tickerStrategy("TSLA"), USER);
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(aaplOnly, tslaOnly));

        InvestmentPlan blockedTsla = InvestmentPlan.create(
                InvestmentPlanId.generate(), USER, BROKER,
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("100")),
                1, PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, List.of(), List.of("TSLA"), null, true),
                Instant.now());

        // LLM must be asked to pick from only AAPL — TSLA is filtered out.
        String response = """
            {"chosenStrategyId":"%s","reasoning":"Only allowed candidate."}
            """.formatted(aaplOnly.getId().value());
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenReturn(new LLMQueryResult(response, LLMUsage.ZERO, "test-model"));

        StrategyPickerPort.Outcome outcome = picker.pick(blockedTsla);

        assertThat(outcome.isChoice()).isTrue();
        assertThat(outcome.chosenStrategyId()).contains(aaplOnly.getId());

        // The prompt sent to the LLM must NOT contain TSLA's id.
        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llmPort).queryWithUsage(promptCaptor.capture(), any(LLMConfig.class));
        assertThat(promptCaptor.getValue()).doesNotContain(tslaOnly.getId().value().toString());
        assertThat(promptCaptor.getValue()).contains(aaplOnly.getId().value().toString());
    }

    @Test
    void ticker_policy_filtering_all_candidates_yields_none_without_calling_llm() {
        Strategy tsla = new Strategy(StrategyId.generate(), "TSLA bot",
                tickerStrategy("TSLA"), USER);
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(tsla));

        InvestmentPlan blockedTsla = InvestmentPlan.create(
                InvestmentPlanId.generate(), USER, BROKER,
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("100")),
                1, PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, List.of(), List.of("TSLA"), null, true),
                Instant.now());

        StrategyPickerPort.Outcome outcome = picker.pick(blockedTsla);

        assertThat(outcome.isChoice()).isFalse();
        assertThat(outcome.reasoning()).contains("ticker policy");
        verify(llmPort, never()).queryWithUsage(anyString(), any());
    }

    private static String tickerStrategy(String ticker) {
        return """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.66
            stock_tickers:
              - %s
            live_schedule:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00"]
              market_open: "09:30"
              market_close: "16:00"
            ---
            # Test
            Buy %s.
            """.formatted(ticker, ticker);
    }

    @Test
    void strips_markdown_fence_around_json() {
        when(strategyRepository.findByOwnerId(USER)).thenReturn(List.of(strategyA));
        String llmResponse = """
            ```json
            {"chosenStrategyId":"%s","reasoning":"Wrapped in a fence."}
            ```
            """.formatted(strategyA.getId().value());
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenReturn(new LLMQueryResult(llmResponse,
                        LLMUsage.of("openai/gpt-4o-mini", 50, 10, new BigDecimal("0.0001")),
                        "openai/gpt-4o-mini"));

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isTrue();
        assertThat(outcome.chosenStrategyId()).contains(strategyA.getId());
    }
}
