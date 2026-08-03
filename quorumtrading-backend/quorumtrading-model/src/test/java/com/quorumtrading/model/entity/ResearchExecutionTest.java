package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.ResearchAgentType;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import com.quorumtrading.model.valueobject.ViabilityScore;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ResearchExecutionTest {

    @Test
    void shouldCreateExecution() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        ResearchOutcome outcome = ResearchOutcome.STRATEGY_CREATED;
        String conclusion = "Created momentum strategy";
        ViabilityScore score = new ViabilityScore(new BigDecimal("85.50"));
        UUID strategyId = UUID.randomUUID();

        // When
        ResearchExecution execution = new ResearchExecution(
            marketResearchId, outcome, conclusion, score, strategyId, null
        );

        // Then
        assertThat(execution.getId()).isNotNull();
        assertThat(execution.getMarketResearchId()).isEqualTo(marketResearchId);
        assertThat(execution.getOutcome()).isEqualTo(outcome);
        assertThat(execution.getResearcherConclusion()).isEqualTo(conclusion);
        assertThat(execution.getViabilityScore()).isEqualTo(score);
        assertThat(execution.getStrategyId()).isEqualTo(strategyId);
        assertThat(execution.getSimulationId()).isNull();
        assertThat(execution.getExecutedAt()).isNotNull();
        assertThat(execution.getAgentDecisions()).isEmpty();
    }

    @Test
    void shouldCreateExecutionWithoutStrategy() {
        // Given
        UUID marketResearchId = UUID.randomUUID();
        ResearchOutcome outcome = ResearchOutcome.NO_OPPORTUNITY;
        String conclusion = "No viable opportunity found";

        // When
        ResearchExecution execution = new ResearchExecution(
            marketResearchId, outcome, conclusion, null, null, null
        );

        // Then
        assertThat(execution.getOutcome()).isEqualTo(ResearchOutcome.NO_OPPORTUNITY);
        assertThat(execution.getViabilityScore()).isNull();
        assertThat(execution.getStrategyId()).isNull();
    }

    @Test
    void shouldAddAgentDecision() {
        // Given
        ResearchExecution execution = new ResearchExecution(
            UUID.randomUUID(), ResearchOutcome.STRATEGY_CREATED, "conclusion",
            new ViabilityScore(new BigDecimal("80")), UUID.randomUUID(), null
        );

        // When
        execution.addAgentDecision(
            ResearchAgentType.NEWS_SENTIMENT_ANALYST,
            "Bullish sentiment",
            "Based on news analysis"
        );

        // Then
        assertThat(execution.getAgentDecisions()).hasSize(1);
        ResearchAgentDecision decision = execution.getAgentDecisions().get(0);
        assertThat(decision.getAgentType()).isEqualTo(ResearchAgentType.NEWS_SENTIMENT_ANALYST);
        assertThat(decision.getAdvice()).isEqualTo("Bullish sentiment");
    }

    @Test
    void shouldSetSimulationId() {
        // Given
        ResearchExecution execution = new ResearchExecution(
            UUID.randomUUID(), ResearchOutcome.STRATEGY_CREATED, "conclusion",
            new ViabilityScore(new BigDecimal("80")), UUID.randomUUID(), null
        );
        UUID simulationId = UUID.randomUUID();

        // When
        execution.setSimulationId(simulationId);

        // Then
        assertThat(execution.getSimulationId()).isEqualTo(simulationId);
    }

    @Test
    void shouldRejectNullMarketResearchId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecution(
            null, ResearchOutcome.STRATEGY_CREATED, "conclusion",
            new ViabilityScore(new BigDecimal("80")), UUID.randomUUID(), null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Market research ID cannot be null");
    }

    @Test
    void shouldRejectNullOutcome() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecution(
            UUID.randomUUID(), null, "conclusion",
            new ViabilityScore(new BigDecimal("80")), UUID.randomUUID(), null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Outcome cannot be null");
    }

    @Test
    void shouldRejectBlankConclusion() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecution(
            UUID.randomUUID(), ResearchOutcome.STRATEGY_CREATED, "  ",
            new ViabilityScore(new BigDecimal("80")), UUID.randomUUID(), null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Researcher conclusion cannot be blank");
    }
}
