package com.quorumtrading.adapter.persistence.entity;

import com.quorumtrading.model.entity.ResearchAgentDecision;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ResearchAgentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for ResearchAgentDecisionJpaEntity domain mapping.
 * Following hexagonal architecture: JPA entity must be separate from domain entity.
 */
class ResearchAgentDecisionJpaEntityTest {

    @Test
    void fromDomain_createsCorrectJpaEntity() {
        // Given: A domain entity
        var researchExecutionId = UUID.randomUUID();

        var domain = new ResearchAgentDecision(
            researchExecutionId,
            ResearchAgentType.NEWS_SENTIMENT_ANALYST,
            "Positive sentiment on BTC, 80% bullish articles",
            "Analyzed 50 news sources, detected strong positive sentiment trend"
        );

        // When: Converting to JPA entity
        var jpaEntity = ResearchAgentDecisionJpaEntity.fromDomain(domain);

        // Then: All fields are correctly mapped
        assertEquals(domain.getId(), jpaEntity.getId());
        assertEquals(researchExecutionId, jpaEntity.getResearchExecutionId());
        assertEquals(ResearchAgentType.NEWS_SENTIMENT_ANALYST.name(), jpaEntity.getAgentType());
        assertEquals("Positive sentiment on BTC, 80% bullish articles", jpaEntity.getAdvice());
        assertEquals("Analyzed 50 news sources, detected strong positive sentiment trend", jpaEntity.getReasoning());
        assertNotNull(jpaEntity.getDecidedAt());
    }

    @Test
    void fromDomain_strategyDesigner() {
        // Given: Strategy designer decision
        var researchExecutionId = UUID.randomUUID();

        var domain = new ResearchAgentDecision(
            researchExecutionId,
            ResearchAgentType.STRATEGY_DESIGNER,
            "Recommend BTC momentum strategy with 50% allocation",
            "Strong uptrend detected, low volatility, ideal for momentum trading"
        );

        // When: Converting to JPA entity
        var jpaEntity = ResearchAgentDecisionJpaEntity.fromDomain(domain);

        // Then: Agent type is correctly mapped
        assertEquals(ResearchAgentType.STRATEGY_DESIGNER.name(), jpaEntity.getAgentType());
    }

    @Test
    void fromDomain_viabilityAssessor() {
        // Given: Viability assessor decision
        var researchExecutionId = UUID.randomUUID();

        var domain = new ResearchAgentDecision(
            researchExecutionId,
            ResearchAgentType.VIABILITY_ASSESSOR,
            "Viability score: 85/100, proceed with strategy creation",
            "Historical backtest shows 75% win rate, risk/reward ratio 2.5:1"
        );

        // When: Converting to JPA entity
        var jpaEntity = ResearchAgentDecisionJpaEntity.fromDomain(domain);

        // Then: Agent type is correctly mapped
        assertEquals(ResearchAgentType.VIABILITY_ASSESSOR.name(), jpaEntity.getAgentType());
    }

    @Test
    void toDomain_reconstructsDomainEntity() {
        // Given: A JPA entity with cost tracking
        var jpaEntity = new ResearchAgentDecisionJpaEntity();
        jpaEntity.setId(UUID.randomUUID());
        jpaEntity.setResearchExecutionId(UUID.randomUUID());
        jpaEntity.setAgentType(ResearchAgentType.NEWS_SENTIMENT_ANALYST.name());
        jpaEntity.setAdvice("Bearish trend detected");
        jpaEntity.setReasoning("Negative news flow, regulatory concerns");
        jpaEntity.setDecidedAt(java.time.Instant.parse("2024-01-20T14:15:00Z"));
        jpaEntity.setModelUsed("openai/gpt-4o");
        jpaEntity.setPromptTokens(500);
        jpaEntity.setCompletionTokens(200);
        jpaEntity.setTotalTokens(700);
        jpaEntity.setRawCostUsd(new BigDecimal("0.00350000"));

        // When: Converting to domain entity
        var domain = jpaEntity.toDomain();

        // Then: Domain entity is correctly reconstructed
        assertEquals(jpaEntity.getId(), domain.getId());
        assertEquals(jpaEntity.getResearchExecutionId(), domain.getResearchExecutionId());
        assertEquals(ResearchAgentType.NEWS_SENTIMENT_ANALYST, domain.getAgentType());
        assertEquals("Bearish trend detected", domain.getAdvice());
        assertEquals("Negative news flow, regulatory concerns", domain.getReasoning());
        assertEquals(jpaEntity.getDecidedAt(), domain.getDecidedAt());
        assertEquals("openai/gpt-4o", domain.getModelUsed());
        assertEquals(500, domain.getPromptTokens());
        assertEquals(200, domain.getCompletionTokens());
        assertEquals(700, domain.getTotalTokens());
        assertEquals(new BigDecimal("0.00350000"), domain.getRawCostUsd());
    }

    @Test
    void fromDomain_withLLMUsage_mapsAllCostFields() {
        // Given: A domain entity with LLM usage
        var researchExecutionId = UUID.randomUUID();
        var llmUsage = LLMUsage.of("openai/gpt-4o", 1000, 500, new BigDecimal("0.00750000"));

        var domain = new ResearchAgentDecision(
            researchExecutionId,
            ResearchAgentType.NEWS_SENTIMENT_ANALYST,
            "Positive sentiment detected",
            "Analyzed news sources",
            llmUsage
        );

        // When: Converting to JPA entity
        var jpaEntity = ResearchAgentDecisionJpaEntity.fromDomain(domain);

        // Then: Cost fields are correctly mapped
        assertEquals("openai/gpt-4o", jpaEntity.getModelUsed());
        assertEquals(1000, jpaEntity.getPromptTokens());
        assertEquals(500, jpaEntity.getCompletionTokens());
        assertEquals(1500, jpaEntity.getTotalTokens());
        assertEquals(new BigDecimal("0.00750000"), jpaEntity.getRawCostUsd());
    }

    @Test
    void roundTrip_preservesAllData() {
        // Given: A domain entity
        var researchExecutionId = UUID.randomUUID();

        var originalDomain = new ResearchAgentDecision(
            researchExecutionId,
            ResearchAgentType.STRATEGY_DESIGNER,
            "Create ETH swing trading strategy",
            "ETH showing consolidation pattern, expected breakout within 48 hours"
        );

        // When: Converting to JPA and back to domain
        var jpaEntity = ResearchAgentDecisionJpaEntity.fromDomain(originalDomain);
        var reconstructedDomain = jpaEntity.toDomain();

        // Then: All data is preserved
        assertEquals(originalDomain.getId(), reconstructedDomain.getId());
        assertEquals(originalDomain.getResearchExecutionId(), reconstructedDomain.getResearchExecutionId());
        assertEquals(originalDomain.getAgentType(), reconstructedDomain.getAgentType());
        assertEquals(originalDomain.getAdvice(), reconstructedDomain.getAdvice());
        assertEquals(originalDomain.getReasoning(), reconstructedDomain.getReasoning());
        // Note: decidedAt timestamp is set in constructor, so we just check it's not null
        assertNotNull(reconstructedDomain.getDecidedAt());
    }

    @Test
    void roundTrip_preservesCostData() {
        // Given: A domain entity with LLM usage
        var researchExecutionId = UUID.randomUUID();
        var llmUsage = LLMUsage.of("anthropic/claude-3-opus", 2000, 1000, new BigDecimal("0.09000000"));

        var originalDomain = new ResearchAgentDecision(
            researchExecutionId,
            ResearchAgentType.VIABILITY_ASSESSOR,
            "Viability score: 85/100",
            "Historical analysis supports strategy",
            llmUsage
        );

        // When: Converting to JPA and back to domain
        var jpaEntity = ResearchAgentDecisionJpaEntity.fromDomain(originalDomain);
        var reconstructedDomain = jpaEntity.toDomain();

        // Then: Cost data is preserved
        assertEquals("anthropic/claude-3-opus", reconstructedDomain.getModelUsed());
        assertEquals(2000, reconstructedDomain.getPromptTokens());
        assertEquals(1000, reconstructedDomain.getCompletionTokens());
        assertEquals(3000, reconstructedDomain.getTotalTokens());
        assertEquals(new BigDecimal("0.09000000"), reconstructedDomain.getRawCostUsd());
    }

    @Test
    void roundTrip_allAgentTypes() {
        // Test round-trip for all three agent types
        var executionId = UUID.randomUUID();

        for (ResearchAgentType agentType : ResearchAgentType.values()) {
            // Given: Domain entity for this agent type
            var originalDomain = new ResearchAgentDecision(
                executionId,
                agentType,
                "Advice from " + agentType.getDisplayName(),
                "Reasoning for " + agentType.name()
            );

            // When: Round-trip conversion
            var jpaEntity = ResearchAgentDecisionJpaEntity.fromDomain(originalDomain);
            var reconstructedDomain = jpaEntity.toDomain();

            // Then: Agent type is preserved
            assertEquals(agentType, reconstructedDomain.getAgentType(),
                "Agent type should be preserved for " + agentType);
        }
    }
}
