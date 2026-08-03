package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ResearchAgentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ResearchAgentDecisionTest {

    @Test
    void shouldCreateDecision() {
        // Given
        UUID executionId = UUID.randomUUID();
        ResearchAgentType agentType = ResearchAgentType.NEWS_SENTIMENT_ANALYST;
        String advice = "Current market shows bullish sentiment";
        String reasoning = "Based on news analysis...";

        // When
        ResearchAgentDecision decision = new ResearchAgentDecision(
            executionId, agentType, advice, reasoning
        );

        // Then
        assertThat(decision.getId()).isNotNull();
        assertThat(decision.getResearchExecutionId()).isEqualTo(executionId);
        assertThat(decision.getAgentType()).isEqualTo(agentType);
        assertThat(decision.getAdvice()).isEqualTo(advice);
        assertThat(decision.getReasoning()).isEqualTo(reasoning);
        assertThat(decision.getDecidedAt()).isNotNull();
    }

    @Test
    void shouldRejectNullExecutionId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentDecision(
            null, ResearchAgentType.NEWS_SENTIMENT_ANALYST, "advice", "reasoning"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Research execution ID cannot be null");
    }

    @Test
    void shouldRejectNullAgentType() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentDecision(
            UUID.randomUUID(), null, "advice", "reasoning"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Agent type cannot be null");
    }

    @Test
    void shouldRejectBlankAdvice() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentDecision(
            UUID.randomUUID(), ResearchAgentType.NEWS_SENTIMENT_ANALYST, "  ", "reasoning"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Advice cannot be blank");
    }

    @Test
    void shouldRejectBlankReasoning() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentDecision(
            UUID.randomUUID(), ResearchAgentType.NEWS_SENTIMENT_ANALYST, "advice", "  "
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reasoning cannot be blank");
    }

    @Test
    void shouldCreateDecisionWithLLMUsage() {
        // Given
        UUID executionId = UUID.randomUUID();
        ResearchAgentType agentType = ResearchAgentType.NEWS_SENTIMENT_ANALYST;
        String advice = "Current market shows bullish sentiment";
        String reasoning = "Based on news analysis...";
        LLMUsage llmUsage = LLMUsage.of("openai/gpt-4o", 500, 200, new BigDecimal("0.00350000"));

        // When
        ResearchAgentDecision decision = new ResearchAgentDecision(
            executionId, agentType, advice, reasoning, llmUsage
        );

        // Then
        assertThat(decision.getId()).isNotNull();
        assertThat(decision.getResearchExecutionId()).isEqualTo(executionId);
        assertThat(decision.getAgentType()).isEqualTo(agentType);
        assertThat(decision.getAdvice()).isEqualTo(advice);
        assertThat(decision.getReasoning()).isEqualTo(reasoning);
        assertThat(decision.getDecidedAt()).isNotNull();
        assertThat(decision.getModelUsed()).isEqualTo("openai/gpt-4o");
        assertThat(decision.getPromptTokens()).isEqualTo(500);
        assertThat(decision.getCompletionTokens()).isEqualTo(200);
        assertThat(decision.getTotalTokens()).isEqualTo(700);
        assertThat(decision.getRawCostUsd()).isEqualByComparingTo(new BigDecimal("0.00350000"));
    }

    @Test
    void shouldCreateDecisionWithNullLLMUsage() {
        // Given
        UUID executionId = UUID.randomUUID();

        // When
        ResearchAgentDecision decision = new ResearchAgentDecision(
            executionId,
            ResearchAgentType.STRATEGY_DESIGNER,
            "Create momentum strategy",
            "Based on technical analysis",
            null
        );

        // Then - should default to zero values
        assertThat(decision.getModelUsed()).isNull();
        assertThat(decision.getPromptTokens()).isEqualTo(0);
        assertThat(decision.getCompletionTokens()).isEqualTo(0);
        assertThat(decision.getTotalTokens()).isEqualTo(0);
        assertThat(decision.getRawCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
