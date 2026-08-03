package com.quorumtrading.model.service;

import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ModelSelectionServiceTest {

    private ModelSelectionService service;
    private UserId userId;

    @BeforeEach
    void setUp() {
        Map<TradingContext, ModelId> systemDefaults = Map.of(
            TradingContext.LIVE_TRADING, new ModelId("claude-3-opus"),
            TradingContext.PAPER_TRADING, new ModelId("claude-3-sonnet"),
            TradingContext.BACKTESTING, new ModelId("claude-3-haiku"),
            TradingContext.RESEARCH, new ModelId("claude-3-sonnet")
        );
        service = new ModelSelectionService(systemDefaults);
        userId = new UserId(UUID.randomUUID());
    }

    @Test
    void shouldReturnUserPreferenceWhenSet() {
        UserBudget budget = UserBudget.createUnlimited(userId);
        ModelId userPreference = new ModelId("gpt-4");
        budget.setModelPreference(TradingContext.LIVE_TRADING, userPreference);

        ModelId result = service.selectModel(budget, TradingContext.LIVE_TRADING);

        assertThat(result).isEqualTo(userPreference);
    }

    @Test
    void shouldReturnSystemDefaultWhenNoUserPreference() {
        UserBudget budget = UserBudget.createUnlimited(userId);

        ModelId result = service.selectModel(budget, TradingContext.LIVE_TRADING);

        assertThat(result).isEqualTo(new ModelId("claude-3-opus"));
    }

    @Test
    void shouldReturnDifferentDefaultsForDifferentContexts() {
        UserBudget budget = UserBudget.createUnlimited(userId);

        ModelId liveResult = service.selectModel(budget, TradingContext.LIVE_TRADING);
        ModelId backtestResult = service.selectModel(budget, TradingContext.BACKTESTING);

        assertThat(liveResult).isEqualTo(new ModelId("claude-3-opus"));
        assertThat(backtestResult).isEqualTo(new ModelId("claude-3-haiku"));
    }

    @Test
    void shouldThrowWhenNoDefaultConfigured() {
        Map<TradingContext, ModelId> incompleteDefaults = Map.of(
            TradingContext.LIVE_TRADING, new ModelId("claude-3-opus")
        );
        ModelSelectionService incompleteService = new ModelSelectionService(incompleteDefaults);
        UserBudget budget = UserBudget.createUnlimited(userId);

        assertThatThrownBy(() -> incompleteService.selectModel(budget, TradingContext.RESEARCH))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No model available for context");
    }

    @Test
    void shouldReturnSystemDefaultWhenBudgetIsNull() {
        // null budget is acceptable - just use system default
        ModelId result = service.selectModel(null, TradingContext.LIVE_TRADING);

        assertThat(result).isEqualTo(new ModelId("claude-3-opus"));
    }

    @Test
    void shouldThrowWhenContextIsNull() {
        UserBudget budget = UserBudget.createUnlimited(userId);

        assertThatThrownBy(() -> service.selectModel(budget, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldBuildServiceWithFluentApi() {
        ModelSelectionService builtService = ModelSelectionService.builder()
            .withDefault(TradingContext.LIVE_TRADING, "claude-3-opus")
            .withDefault(TradingContext.BACKTESTING, "claude-3-haiku")
            .build();

        UserBudget budget = UserBudget.createUnlimited(userId);

        assertThat(builtService.selectModel(budget, TradingContext.LIVE_TRADING))
            .isEqualTo(new ModelId("claude-3-opus"));
        assertThat(builtService.selectModel(budget, TradingContext.BACKTESTING))
            .isEqualTo(new ModelId("claude-3-haiku"));
    }

    @Test
    void shouldCheckIfSystemDefaultExists() {
        assertThat(service.hasSystemDefault(TradingContext.LIVE_TRADING)).isTrue();
        assertThat(service.getSystemDefault(TradingContext.LIVE_TRADING)).isPresent();
    }
}
