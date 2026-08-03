package com.quorumtrading.application.orchestration.agents;

import java.util.UUID;

/**
 * Configuration for LLM queries.
 *
 * @param model Model identifier (e.g., "qwen/qwen3-coder:free")
 * @param temperature Sampling temperature (0.0 to 2.0)
 * @param tokens Maximum tokens in response
 * @param preferredProvider Optional preferred provider name (e.g., "openrouter", "local")
 * @param userId Optional user ID for cost attribution (for billing purposes)
 * @param noFallback When true, skip the fallback model chain - fail immediately if the model fails
 * @param effort Optional reasoning effort, a dimension separate from which model. Null means the
 *               caller expressed none, and an engine that supports effort must then omit the flag
 *               rather than pick a level nobody asked for.
 */
public record LLMConfig(String model, double temperature, int tokens, String preferredProvider, UUID userId, boolean noFallback,
                        com.quorumtrading.model.valueobject.ModelEffort effort) {

    /**
     * Backward compatibility constructor without effort.
     */
    public LLMConfig(String model, double temperature, int tokens, String preferredProvider, UUID userId, boolean noFallback) {
        this(model, temperature, tokens, preferredProvider, userId, noFallback, null);
    }

    /**
     * Backward compatibility constructor without preferred provider, userId, and noFallback.
     */
    public LLMConfig(String model, double temperature, int tokens) {
        this(model, temperature, tokens, null, null, false, null);
    }

    /**
     * Backward compatibility constructor without userId and noFallback.
     */
    public LLMConfig(String model, double temperature, int tokens, String preferredProvider) {
        this(model, temperature, tokens, preferredProvider, null, false);
    }

    /**
     * Backward compatibility constructor without noFallback.
     */
    public LLMConfig(String model, double temperature, int tokens, String preferredProvider, UUID userId) {
        this(model, temperature, tokens, preferredProvider, userId, false);
    }

    /**
     * Create a new LLMConfig with a user ID for cost attribution.
     */
    public LLMConfig withUserId(UUID userId) {
        return new LLMConfig(model, temperature, tokens, preferredProvider, userId, noFallback, effort);
    }

    /** The same config at a given reasoning effort. */
    public LLMConfig withEffort(com.quorumtrading.model.valueobject.ModelEffort effort) {
        return new LLMConfig(model, temperature, tokens, preferredProvider, userId, noFallback, effort);
    }

    /**
     * Create a new LLMConfig with noFallback enabled.
     * When noFallback is true, the adapter will not try fallback models if this model fails.
     */
    public LLMConfig withNoFallback() {
        return new LLMConfig(model, temperature, tokens, preferredProvider, userId, true, effort);
    }
}
