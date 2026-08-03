package com.quorumtrading.application.chat;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPromptBuilderTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private MarketResearchRepository marketResearchRepository;

    @Mock
    private UserBudgetRepository userBudgetRepository;

    private SystemPromptBuilder builder;

    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        builder = new SystemPromptBuilder(simulationRepository, strategyRepository, marketResearchRepository, userBudgetRepository, false);
    }

    @Test
    void shouldIncludeBaseSystemPrompt() {
        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of());

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("QuorumTrading assistant");
        assertThat(prompt).contains("Response Rules");
        assertThat(prompt).contains("Tool Tips");
        assertThat(prompt).contains("Disambiguation");
    }

    @Test
    void shouldExcludeA2UIWhenDisabled() {
        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of());

        String prompt = builder.build(USER_ID);

        assertThat(prompt).doesNotContain("Rich UI Components (A2UI)");
        assertThat(prompt).doesNotContain("<a2ui>");
    }

    @Test
    void shouldIncludeA2UIWhenEnabled() {
        var enabledBuilder = new SystemPromptBuilder(
            simulationRepository, strategyRepository, marketResearchRepository, userBudgetRepository, true);
        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of());

        String prompt = enabledBuilder.build(USER_ID);

        assertThat(prompt).contains("Rich UI Components (A2UI)");
        assertThat(prompt).contains("StatGroup");
    }

    @Test
    void shouldIncludeStrategyContext() {
        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()), "BTC Momentum", "# Strategy content",
            USER_ID);
        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of(strategy));
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of());

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("Strategies (1)");
        assertThat(prompt).contains("BTC Momentum");
        assertThat(prompt).contains("/strategies/");
    }

    @Test
    void shouldIncludeSimulationContext() {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation sim = new Simulation(
            new SimulationId(UUID.randomUUID()), "BTC Backtest", strategyId,
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));

        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of(sim));
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of());

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("Simulations (1)");
    }

    @Test
    void shouldIncludeMarketResearchContext() {
        MarketResearch research = new MarketResearch(
            "BTC Scanner",
            "Find BTC strategies",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS),
            false,
            null,
            USER_ID);
        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of(research));

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("Market Research (1 active)");
        assertThat(prompt).contains("BTC Scanner");
        assertThat(prompt).contains("BTC");
    }

    @Test
    void shouldHandleEmptyContext() {
        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of());

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("No strategies yet. The user must create a strategy before running simulations.");
        assertThat(prompt).contains("No simulations yet");
        assertThat(prompt).contains("No active market research");
    }

    @Test
    void shouldHandleRepositoryExceptions() {
        when(strategyRepository.findByOwnerId(USER_ID)).thenThrow(new RuntimeException("DB down"));

        String prompt = builder.build(USER_ID);

        // Should still include base prompt despite error
        assertThat(prompt).contains("QuorumTrading assistant");
    }

    // ===== Budget Context Tests =====

    @Test
    void shouldIncludeBudgetContextUnlimited() {
        stubEmptyContext();
        when(userBudgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
            UserBudget.createUnlimited(USER_ID)
        ));

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("## Budget");
        assertThat(prompt).contains("Budget: Unlimited");
    }

    @Test
    void shouldIncludeBudgetContextWithLimit() {
        stubEmptyContext();
        UserBudget budget = UserBudget.createWithLimit(USER_ID, BudgetLimit.usd(new BigDecimal("25.00")));
        when(userBudgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("## Budget");
        assertThat(prompt).contains("Remaining: $25.00");
        assertThat(prompt).contains("of $25.00");
        assertThat(prompt).contains("Status: AVAILABLE");
        assertThat(prompt).contains("Be mindful of cost");
    }

    @Test
    void shouldIncludeBudgetContextWhenDepleted() {
        stubEmptyContext();
        UserBudget budget = UserBudget.createWithLimit(USER_ID, BudgetLimit.usd(new BigDecimal("5.00")));
        budget.recordUsage(new BigDecimal("5.00"));
        when(userBudgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("Status: DEPLETED");
    }

    @Test
    void shouldShowUnlimitedWhenNoBudgetExists() {
        stubEmptyContext();
        when(userBudgetRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        String prompt = builder.build(USER_ID);

        assertThat(prompt).contains("Budget: Unlimited");
    }

    @Test
    void shouldHandleBudgetRepositoryException() {
        stubEmptyContext();
        when(userBudgetRepository.findByUserId(USER_ID)).thenThrow(new RuntimeException("Redis down"));

        String prompt = builder.build(USER_ID);

        // Should still include base prompt despite budget error
        assertThat(prompt).contains("QuorumTrading assistant");
    }

    private void stubEmptyContext() {
        when(strategyRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(simulationRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        when(marketResearchRepository.findActiveByOwnerId(USER_ID)).thenReturn(List.of());
    }
}
