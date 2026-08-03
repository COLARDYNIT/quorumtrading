package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.model.valueobject.LLMUsage;

/**
 * Record representing an LLM query result with usage metrics.
 *
 * @param content The text content of the LLM result
 * @param usage   Token usage and cost information
 * @param model   The actual model used (may differ from requested due to fallback)
 */
public record LLMQueryResult(
    String content,
    LLMUsage usage,
    String model
) {

    public LLMQueryResult {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        if (usage == null) {
            usage = LLMUsage.ZERO;
        }
    }

    /**
     * Create a result with zero usage (for backward compatibility or fallback scenarios).
     */
    public static LLMQueryResult withoutUsage(String content, String model) {
        return new LLMQueryResult(content, LLMUsage.ZERO, model);
    }

    /**
     * Create a result with usage metrics.
     */
    public static LLMQueryResult of(String content, LLMUsage usage, String model) {
        return new LLMQueryResult(content, usage, model);
    }
}
