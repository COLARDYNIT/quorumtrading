package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.usecase.llm.UnservablePinReport;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Switching engines is the realistic route to an unservable pin: a preference saved when OpenRouter
 * was selected is still there, still displayed as honoured, and unrunnable the moment a local
 * server becomes the engine. Nothing used to revalidate it, so under a fail-hard policy the
 * discovery would happen mid-backtest.
 */
@DisplayName("RevalidateStoredModelPinsService — what an engine switch just broke")
class RevalidateStoredModelPinsServiceTest {

    private static final String SERVABLE = "llama3.2";
    private static final String STALE = "anthropic/claude-sonnet-4.6";

    private static final String STRATEGY_TEMPLATE = """
            ---
            version: v1.0
            consensus_method: MAJORITY_VOTE
            consensus_threshold: 0.66
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1w
            position_limits:
              BTC: 0.8
            reserve_cash_percentage: 0.2
            %s
            ---

            # Pinned Strategy

            ## Entry Rules
            - RSI-14 drops below 30

            ## Exit Rules
            - Take profit: +8%%
            """;

    private static ModelPinValidator validator(List<String> catalogue) {
        LLMPort llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenReturn(ModelListingCapability.ENUMERABLE);
        when(llmPort.getAvailableModels())
                .thenReturn(catalogue.stream().map(id -> LLMModelInfo.of(id, id, LLMPricing.FREE)).toList());
        when(llmPort.getModelInfo(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return catalogue.contains(id)
                    ? Optional.of(LLMModelInfo.of(id, id, LLMPricing.FREE))
                    : Optional.empty();
        });
        return new ModelPinValidator(llmPort, () -> Optional.of(LlmProvider.LOCAL_HTTP));
    }

    private static UserBudget budgetWith(TradingContext context, AgentRole role, String model) {
        UserBudget budget = UserBudget.createUnlimited(new UserId(UUID.randomUUID()));
        if (role == null) {
            budget.setModelPreference(context, ModelId.of(model));
        } else {
            budget.setAgentModelPreference(context, role, ModelId.of(model));
        }
        return budget;
    }

    private static Strategy strategyPinning(String frontmatterLine) {
        return new Strategy(new StrategyId(UUID.randomUUID()), "Pinned Strategy",
                STRATEGY_TEMPLATE.formatted(frontmatterLine), new UserId(UUID.randomUUID()));
    }

    private static RevalidateStoredModelPinsService service(List<UserBudget> budgets, List<Strategy> strategies,
                                                            ModelPinValidator validator) {
        UserBudgetRepository budgetRepository = mock(UserBudgetRepository.class);
        when(budgetRepository.findAll()).thenReturn(budgets);
        StrategyRepository strategyRepository = mock(StrategyRepository.class);
        when(strategyRepository.findAll()).thenReturn(strategies);
        return new RevalidateStoredModelPinsService(budgetRepository, strategyRepository, validator);
    }

    @Test
    @DisplayName("everything servable reports clean")
    void allServableIsClean() {
        UnservablePinReport report = service(
                List.of(budgetWith(TradingContext.BACKTESTING, null, SERVABLE)),
                List.of(strategyPinning("llm_model: " + SERVABLE)),
                validator(List.of(SERVABLE))).revalidate();

        assertThat(report.isClean()).isTrue();
        assertThat(report.total()).isZero();
    }

    @Test
    @DisplayName("a stored preference the new engine cannot serve is reported, keyed by where it lives")
    void stalePreferenceIsReported() {
        UnservablePinReport report = service(
                List.of(budgetWith(TradingContext.BACKTESTING, AgentRole.TECHNICAL, STALE)),
                List.of(),
                validator(List.of(SERVABLE))).revalidate();

        assertThat(report.isClean()).isFalse();
        assertThat(report.preferences()).singleElement().satisfies(pin -> {
            assertThat(pin.modelId()).isEqualTo(STALE);
            assertThat(pin.where()).contains("BACKTESTING_TECHNICAL");
        });
    }

    @Test
    @DisplayName("a saved strategy's global pin is reported")
    void staleStrategyGlobalPinIsReported() {
        UnservablePinReport report = service(
                List.of(),
                List.of(strategyPinning("llm_model: " + STALE)),
                validator(List.of(SERVABLE))).revalidate();

        assertThat(report.strategyPins()).singleElement().satisfies(pin -> {
            assertThat(pin.modelId()).isEqualTo(STALE);
            assertThat(pin.where()).contains("Pinned Strategy").contains("llm_model");
        });
    }

    @Test
    @DisplayName("the report offers models the new engine WILL serve, so the fix is one step away")
    void reportCarriesCandidates() {
        UnservablePinReport report = service(
                List.of(budgetWith(TradingContext.BACKTESTING, null, STALE)),
                List.of(),
                validator(List.of(SERVABLE))).revalidate();

        assertThat(report.candidates()).extracting(c -> c.id()).containsExactly(SERVABLE);
    }

    @Test
    @DisplayName("nothing is repaired — repairing would be the silent substitution this design removes")
    void nothingIsRepaired() {
        UserBudgetRepository budgetRepository = mock(UserBudgetRepository.class);
        when(budgetRepository.findAll())
                .thenReturn(List.of(budgetWith(TradingContext.BACKTESTING, null, STALE)));
        StrategyRepository strategyRepository = mock(StrategyRepository.class);
        when(strategyRepository.findAll()).thenReturn(List.of());

        new RevalidateStoredModelPinsService(budgetRepository, strategyRepository, validator(List.of(SERVABLE)))
                .revalidate();

        verify(budgetRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(strategyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("an unparseable strategy is skipped, not allowed to fail the whole sweep")
    void unparseableStrategyDoesNotBreakTheSweep() {
        Strategy broken = new Strategy(new StrategyId(UUID.randomUUID()), "Broken",
                "this is not a strategy", new UserId(UUID.randomUUID()));

        UnservablePinReport report = service(
                List.of(budgetWith(TradingContext.BACKTESTING, null, STALE)),
                List.of(broken),
                validator(List.of(SERVABLE))).revalidate();

        assertThat(report.preferences()).hasSize(1);
        assertThat(report.strategyPins()).isEmpty();
    }
}
