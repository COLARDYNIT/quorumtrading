package com.quorumtrading.adapter.persistence.entity;

import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import com.quorumtrading.model.valueobject.ViabilityScore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for ResearchExecutionJpaEntity domain mapping.
 * Following hexagonal architecture: JPA entity must be separate from domain entity.
 */
class ResearchExecutionJpaEntityTest {

    @Test
    void fromDomain_createsCorrectJpaEntity() {
        // Given: A domain entity with all fields populated
        var marketResearchId = UUID.randomUUID();
        var strategyId = UUID.randomUUID();
        var simulationId = UUID.randomUUID();
        var viabilityScore = new ViabilityScore(new BigDecimal("85.50"));

        var domain = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.STRATEGY_CREATED,
            "Market conditions favorable for BTC momentum strategy",
            viabilityScore,
            strategyId,
            simulationId
        );

        // When: Converting to JPA entity
        var jpaEntity = ResearchExecutionJpaEntity.fromDomain(domain);

        // Then: All fields are correctly mapped
        assertEquals(domain.getId(), jpaEntity.getId());
        assertEquals(marketResearchId, jpaEntity.getMarketResearchId());
        assertEquals(ResearchOutcome.STRATEGY_CREATED.name(), jpaEntity.getOutcome());
        assertEquals("Market conditions favorable for BTC momentum strategy", jpaEntity.getOutcomeReasoning());
        assertEquals(new BigDecimal("85.50"), jpaEntity.getViabilityScore());
        assertEquals(strategyId, jpaEntity.getCreatedStrategyId());
        assertEquals(simulationId, jpaEntity.getSimulationId());
        assertNotNull(jpaEntity.getExecutedAt());
    }

    @Test
    void fromDomain_withNoOpportunity() {
        // Given: Domain entity with NO_OPPORTUNITY outcome (null strategy/simulation/score)
        var marketResearchId = UUID.randomUUID();

        var domain = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.NO_OPPORTUNITY,
            "No favorable market conditions found",
            null,
            null,
            null
        );

        // When: Converting to JPA entity
        var jpaEntity = ResearchExecutionJpaEntity.fromDomain(domain);

        // Then: Nullable fields are null
        assertEquals(ResearchOutcome.NO_OPPORTUNITY.name(), jpaEntity.getOutcome());
        assertNull(jpaEntity.getViabilityScore());
        assertNull(jpaEntity.getCreatedStrategyId());
        assertNull(jpaEntity.getSimulationId());
    }

    @Test
    void toDomain_reconstructsDomainEntity() {
        // Given: A JPA entity with all fields populated
        var jpaEntity = new ResearchExecutionJpaEntity();
        jpaEntity.setId(UUID.randomUUID());
        jpaEntity.setMarketResearchId(UUID.randomUUID());
        jpaEntity.setOutcome(ResearchOutcome.STRATEGY_CREATED.name());
        jpaEntity.setOutcomeReasoning("Strong bullish signals detected");
        jpaEntity.setViabilityScore(new BigDecimal("92.30"));
        jpaEntity.setCreatedStrategyId(UUID.randomUUID());
        jpaEntity.setSimulationId(UUID.randomUUID());
        jpaEntity.setExecutedAt(java.time.Instant.parse("2024-01-15T10:30:00Z"));

        // When: Converting to domain entity
        var domain = jpaEntity.toDomain();

        // Then: Domain entity is correctly reconstructed
        assertEquals(jpaEntity.getId(), domain.getId());
        assertEquals(jpaEntity.getMarketResearchId(), domain.getMarketResearchId());
        assertEquals(ResearchOutcome.STRATEGY_CREATED, domain.getOutcome());
        assertEquals("Strong bullish signals detected", domain.getResearcherConclusion());
        assertEquals(new BigDecimal("92.30"), domain.getViabilityScore().value());
        assertEquals(jpaEntity.getCreatedStrategyId(), domain.getStrategyId());
        assertEquals(jpaEntity.getSimulationId(), domain.getSimulationId());
        assertEquals(jpaEntity.getExecutedAt(), domain.getExecutedAt());
    }

    @Test
    void toDomain_withNullableFields() {
        // Given: JPA entity without viability score, strategy, or simulation
        var jpaEntity = new ResearchExecutionJpaEntity();
        jpaEntity.setId(UUID.randomUUID());
        jpaEntity.setMarketResearchId(UUID.randomUUID());
        jpaEntity.setOutcome(ResearchOutcome.FAILED.name());
        jpaEntity.setOutcomeReasoning("LLM service unavailable");
        jpaEntity.setExecutedAt(java.time.Instant.now());

        // When: Converting to domain
        var domain = jpaEntity.toDomain();

        // Then: Nullable fields handled correctly
        assertEquals(ResearchOutcome.FAILED, domain.getOutcome());
        assertNull(domain.getViabilityScore());
        assertNull(domain.getStrategyId());
        assertNull(domain.getSimulationId());
    }

    @Test
    void roundTrip_preservesAllData() {
        // Given: A complete domain entity
        var marketResearchId = UUID.randomUUID();
        var strategyId = UUID.randomUUID();
        var simulationId = UUID.randomUUID();
        var viabilityScore = new ViabilityScore(new BigDecimal("78.25"));

        var originalDomain = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.STRATEGY_CREATED,
            "ETH breakout pattern identified",
            viabilityScore,
            strategyId,
            simulationId
        );

        // When: Converting to JPA and back to domain
        var jpaEntity = ResearchExecutionJpaEntity.fromDomain(originalDomain);
        var reconstructedDomain = jpaEntity.toDomain();

        // Then: All data is preserved
        assertEquals(originalDomain.getId(), reconstructedDomain.getId());
        assertEquals(originalDomain.getMarketResearchId(), reconstructedDomain.getMarketResearchId());
        assertEquals(originalDomain.getOutcome(), reconstructedDomain.getOutcome());
        assertEquals(originalDomain.getResearcherConclusion(), reconstructedDomain.getResearcherConclusion());
        assertEquals(originalDomain.getViabilityScore(), reconstructedDomain.getViabilityScore());
        assertEquals(originalDomain.getStrategyId(), reconstructedDomain.getStrategyId());
        assertEquals(originalDomain.getSimulationId(), reconstructedDomain.getSimulationId());
        // Note: executedAt timestamp is set in constructor, so we just check it's not null
        assertNotNull(reconstructedDomain.getExecutedAt());
    }

    @Test
    void roundTrip_withMinimalData() {
        // Given: Minimal domain entity
        var marketResearchId = UUID.randomUUID();

        var originalDomain = new ResearchExecution(
            marketResearchId,
            ResearchOutcome.NO_OPPORTUNITY,
            "Market too volatile",
            null,
            null,
            null
        );

        // When: Round-trip conversion
        var jpaEntity = ResearchExecutionJpaEntity.fromDomain(originalDomain);
        var reconstructedDomain = jpaEntity.toDomain();

        // Then: Data preserved
        assertEquals(originalDomain.getId(), reconstructedDomain.getId());
        assertEquals(originalDomain.getMarketResearchId(), reconstructedDomain.getMarketResearchId());
        assertEquals(originalDomain.getOutcome(), reconstructedDomain.getOutcome());
        assertEquals(originalDomain.getResearcherConclusion(), reconstructedDomain.getResearcherConclusion());
        assertNull(reconstructedDomain.getViabilityScore());
        assertNull(reconstructedDomain.getStrategyId());
        assertNull(reconstructedDomain.getSimulationId());
    }
}
