package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.LLMDecisionJpaEntity;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Percentage;
import com.quorumtrading.model.valueobject.SimulationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Mapper between LLMDecision domain model and LLMDecisionJpaEntity.
 *
 * ARCHITECTURAL NOTE:
 * - Domain SimulationId (UUID wrapper) → Database simulationId (UUID)
 * - Domain Percentage (BigDecimal wrapper) → Database confidenceScore (BigDecimal)
 * - Domain RecommendationType (enum) → Database recommendation (String)
 * - Plain text strings → JSONB (wrapped in JSON object)
 *
 * This separation maintains hexagonal architecture by keeping JPA concerns out of the domain layer.
 */
@ApplicationScoped
@RegisterForReflection(targets = LLMDecisionMapper.TextWrapper.class)
public class LLMDecisionMapper {

    private static final Logger logger = LoggerFactory.getLogger(LLMDecisionMapper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert domain LLMDecision to JPA entity.
     *
     * Unwraps value objects:
     * - SimulationId.value() → UUID
     * - Percentage.value() → BigDecimal
     * - RecommendationType → String
     */
    public LLMDecisionJpaEntity toJpaEntity(LLMDecision decision) {
        LLMDecisionJpaEntity entity = new LLMDecisionJpaEntity();

        entity.id = decision.getId();
        entity.simulationId = decision.getSimulationId() != null ? decision.getSimulationId().value() : null;
        entity.decisionTimestamp = decision.getDecisionTimestamp();
        entity.agentId = decision.getAgentId();
        entity.role = decision.getRole();
        entity.llmModel = decision.getLlmModel();
        entity.requestedModel = decision.getRequestedModel();
        entity.promptUsed = decision.getPromptUsed();
        entity.reasoning = ensureValidJson(decision.getReasoning());
        entity.recommendation = decision.getRecommendation() != null ? decision.getRecommendation().name() : null;
        entity.recommendedAction = ensureValidJson(decision.getRecommendedAction());
        entity.confidenceScore = decision.getConfidenceScore() != null ? decision.getConfidenceScore().value() : null;
        entity.executionTimeMs = decision.getExecutionTimeMs();

        // Map LLM cost tracking fields
        LLMUsage usage = decision.getLLMUsage();
        if (usage != null) {
            entity.promptTokens = usage.promptTokens();
            entity.completionTokens = usage.completionTokens();
            entity.totalTokens = usage.totalTokens();
            entity.rawCostUsd = usage.rawCost();
        }

        // Map indicator snapshot — validate JSON before JSONB storage
        entity.indicatorSnapshot = ensureValidJson(decision.getIndicatorSnapshot());

        entity.uiPayload = ensureValidJson(decision.getUiPayload());

        return entity;
    }

    /**
     * Convert JPA entity to domain LLMDecision.
     *
     * Wraps database types into value objects:
     * - UUID → SimulationId
     * - BigDecimal → Percentage
     * - String → RecommendationType
     */
    public LLMDecision toDomain(LLMDecisionJpaEntity entity) {
        LLMDecision decision = new LLMDecision();

        decision.setId(entity.id);
        decision.setSimulationId(entity.simulationId != null ? new SimulationId(entity.simulationId) : null);
        decision.setDecisionTimestamp(entity.decisionTimestamp);
        decision.setAgentId(entity.agentId);
        decision.setRole(entity.role);
        decision.setLlmModel(entity.llmModel);
        decision.recordRequestedModel(entity.requestedModel);
        decision.setPromptUsed(entity.promptUsed);
        decision.setReasoning(extractTextFromJson(entity.reasoning));
        decision.setRecommendation(entity.recommendation != null ? RecommendationType.valueOf(entity.recommendation) : null);
        decision.setRecommendedAction(extractTextFromJson(entity.recommendedAction));
        decision.setConfidenceScore(entity.confidenceScore != null ? Percentage.fromDecimal(entity.confidenceScore) : null);
        decision.setExecutionTimeMs(entity.executionTimeMs);

        // Map LLM cost tracking fields (include model for complete tracking)
        LLMUsage usage = new LLMUsage(
            entity.llmModel,
            entity.promptTokens != null ? entity.promptTokens : 0,
            entity.completionTokens != null ? entity.completionTokens : 0,
            entity.totalTokens != null ? entity.totalTokens : 0,
            entity.rawCostUsd != null ? entity.rawCostUsd : BigDecimal.ZERO
        );
        decision.setLLMUsage(usage);

        // Map indicator snapshot
        decision.setIndicatorSnapshot(entity.indicatorSnapshot);

        decision.setUiPayload(entity.uiPayload);

        return decision;
    }

    /**
     * Ensure the given string is valid JSON for PostgreSQL JSONB column storage.
     * If the string is already valid JSON, return as-is.
     * If not, wrap it in a JSON object with a "text" field.
     *
     * Sanitizes characters that Jackson accepts but PostgreSQL JSONB rejects:
     * - NUL bytes (\u0000) are stripped — both literal NUL chars and JSON \u0000 escape sequences
     *   (PostgreSQL cannot store NUL in JSONB: "unsupported Unicode escape sequence")
     */
    private String ensureValidJson(String value) {
        if (value == null) {
            return "{\"text\": null}";
        }

        // Strip NUL bytes that PostgreSQL JSONB cannot store:
        // 1. Actual NUL byte chars (from decoded LLM text like reasoning)
        // 2. JSON escape sequence \u0000 (from raw LLM JSON like recommendedAction)
        String sanitized = sanitizeForPostgresJsonb(value);

        // Check if already valid JSON
        try {
            objectMapper.readTree(sanitized);
            return sanitized; // Already valid JSON
        } catch (JsonProcessingException e) {
            // Not valid JSON, wrap in object
            try {
                return objectMapper.writeValueAsString(new TextWrapper(sanitized));
            } catch (JsonProcessingException ex) {
                logger.error("Failed to wrap text in JSON (length={}): {}", sanitized.length(), ex.getMessage());
                // Last resort: escape the string manually
                String escaped = sanitized.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
                return "{\"text\": \"" + escaped + "\"}";
            }
        }
    }

    /**
     * Remove characters/sequences that PostgreSQL JSONB cannot store.
     * PostgreSQL rejects \u0000 (NUL) in any form within JSONB values.
     */
    private static String sanitizeForPostgresJsonb(String value) {
        // Fast path: no NUL content
        if (value.indexOf('\u0000') < 0 && !value.contains("\\u0000")) {
            return value;
        }
        return value
            .replace("\u0000", "")      // literal NUL byte
            .replace("\\u0000", "");    // JSON escape sequence
    }

    /**
     * Extract plain text from JSON stored in database.
     * If JSON contains a "text" wrapper field, extract it.
     * Otherwise, return the entire JSON as a string.
     */
    private String extractTextFromJson(String json) {
        if (json == null) {
            return null;
        }

        try {
            var node = objectMapper.readTree(json);
            if (node.has("text")) {
                return node.get("text").asText();
            }
            // If not wrapped, return entire JSON as string
            return json;
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse JSON, returning as-is: {}", e.getMessage());
            return json;
        }
    }

    /**
     * Simple wrapper class for plain text to JSON conversion.
     * Package-private for native image reflection registration.
     */
    static record TextWrapper(String text) {}
}
