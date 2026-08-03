package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.LLMDecisionJpaEntity;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.valueobject.Percentage;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LLMDecisionMapper — specifically JSONB sanitization.
 */
class LLMDecisionMapperTest {

    private final LLMDecisionMapper mapper = new LLMDecisionMapper();

    @Test
    void toJpaEntity_stripsNulByteFromReasoning() {
        LLMDecision decision = buildDecision();
        decision.recordReasoning("analysis\u0000text");

        LLMDecisionJpaEntity entity = mapper.toJpaEntity(decision);

        assertFalse(entity.reasoning.contains("\u0000"),
            "Reasoning must not contain NUL bytes");
        assertTrue(entity.reasoning.contains("analysistext"),
            "Reasoning text should be preserved minus the NUL byte");
    }

    @Test
    void toJpaEntity_stripsJsonNulEscapeFromRecommendedAction() {
        LLMDecision decision = buildDecision();
        decision.recordRecommendedAction("{\"action\":\"BUY\",\"reasoning\":\"test\\u0000value\"}");

        LLMDecisionJpaEntity entity = mapper.toJpaEntity(decision);

        assertFalse(entity.recommendedAction.contains("\\u0000"),
            "Recommended action must not contain \\u0000 escape sequence");
        assertFalse(entity.recommendedAction.contains("\u0000"),
            "Recommended action must not contain NUL bytes");
    }

    @Test
    void toJpaEntity_validJsonPassesThrough() {
        LLMDecision decision = buildDecision();
        String validJson = "{\"action\":\"BUY\",\"confidence\":0.85}";
        decision.recordRecommendedAction(validJson);

        LLMDecisionJpaEntity entity = mapper.toJpaEntity(decision);

        assertEquals(validJson, entity.recommendedAction);
    }

    @Test
    void toJpaEntity_plainTextWrappedInJsonObject() {
        LLMDecision decision = buildDecision();
        decision.recordReasoning("Simple reasoning text");

        LLMDecisionJpaEntity entity = mapper.toJpaEntity(decision);

        assertTrue(entity.reasoning.startsWith("{"),
            "Plain text reasoning should be wrapped in JSON object");
        assertTrue(entity.reasoning.contains("Simple reasoning text"),
            "Original text should be preserved");
    }

    @Test
    void toJpaEntity_nullIndicatorSnapshotProducesValidJson() {
        LLMDecision decision = buildDecision();
        // indicatorSnapshot is null by default

        LLMDecisionJpaEntity entity = mapper.toJpaEntity(decision);

        assertEquals("{\"text\": null}", entity.indicatorSnapshot);
    }

    private LLMDecision buildDecision() {
        LLMDecision decision = new LLMDecision(
            new SimulationId(UUID.randomUUID()),
            Instant.now(),
            UUID.randomUUID().toString(),
            "TECHNICAL",
            "test-model"
        );
        decision.recordRecommendation(RecommendationType.BUY);
        decision.recordConfidenceScore(new Percentage(new BigDecimal("0.85")));
        decision.recordReasoning("test reasoning");
        decision.recordRecommendedAction("{\"action\":\"BUY\"}");
        decision.recordExecutionTime(100);
        return decision;
    }
}
