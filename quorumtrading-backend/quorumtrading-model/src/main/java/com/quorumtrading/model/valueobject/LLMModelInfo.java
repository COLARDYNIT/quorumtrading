package com.quorumtrading.model.valueobject;

import java.time.Instant;
import java.util.List;

/**
 * Value object representing LLM model information including capabilities and pricing.
 * This provides all information needed to make informed model selection decisions.
 *
 * @param id               Unique model identifier (e.g., "openai/gpt-4o", "anthropic/claude-sonnet-5.5")
 * @param name             Human-readable display name (e.g., "GPT-4o", "Claude 3.5 Sonnet")
 * @param description      Model description and capabilities overview
 * @param contextLength    Maximum context window in tokens
 * @param maxOutputTokens  Maximum completion/output tokens (0 if unknown)
 * @param pricing          Cost structure for using this model
 * @param modalities       Supported input/output modalities (e.g., "text", "image", "audio")
 * @param provider         The underlying provider (e.g., "OpenAI", "Anthropic")
 * @param createdAt        When the model was added/updated (null if unknown)
 * @param supportsWebSearch Whether this model supports web search capability (required for market research)
 * @param parameterSizeB   Model size in billions of parameters (e.g., 70 for "70b"), null if unknown
 */
public record LLMModelInfo(
    String id,
    String name,
    String description,
    int contextLength,
    int maxOutputTokens,
    LLMPricing pricing,
    List<String> modalities,
    String provider,
    Instant createdAt,
    boolean supportsWebSearch,
    Integer parameterSizeB
) {

    public LLMModelInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Model id cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            name = id; // Fallback to id if no name provided
        }
        if (pricing == null) {
            pricing = LLMPricing.FREE;
        }
        if (modalities == null) {
            modalities = List.of("text");
        }
        if (contextLength < 0) {
            contextLength = 0;
        }
        if (maxOutputTokens < 0) {
            maxOutputTokens = 0;
        }
    }

    /**
     * Create a minimal model info with just id, name, and pricing.
     */
    public static LLMModelInfo of(String id, String name, LLMPricing pricing) {
        return new LLMModelInfo(
            id,
            name,
            null,
            0,
            0,
            pricing,
            List.of("text"),
            extractProvider(id),
            null,
            false,
            null
        );
    }

    /**
     * Create a model info with common fields.
     */
    public static LLMModelInfo of(
        String id,
        String name,
        String description,
        int contextLength,
        LLMPricing pricing,
        List<String> modalities
    ) {
        return new LLMModelInfo(
            id,
            name,
            description,
            contextLength,
            0,
            pricing,
            modalities,
            extractProvider(id),
            null,
            false,
            null
        );
    }

    /**
     * Create a model info with web search capability.
     */
    public static LLMModelInfo ofWithWebSearch(
        String id,
        String name,
        String description,
        int contextLength,
        LLMPricing pricing,
        List<String> modalities,
        boolean supportsWebSearch
    ) {
        return new LLMModelInfo(
            id,
            name,
            description,
            contextLength,
            0,
            pricing,
            modalities,
            extractProvider(id),
            null,
            supportsWebSearch,
            null
        );
    }

    /**
     * Check if this model is free to use.
     */
    public boolean isFree() {
        return pricing.isFree();
    }

    /**
     * Check if this model supports a specific modality (e.g., "image", "audio").
     */
    public boolean supportsModality(String modality) {
        return modalities.stream()
            .anyMatch(m -> m.equalsIgnoreCase(modality));
    }

    /**
     * Check if this model supports images (vision capability).
     */
    public boolean supportsImages() {
        return supportsModality("image");
    }

    /**
     * Check if the context length is sufficient for a given requirement.
     */
    public boolean hasMinContextLength(int requiredLength) {
        return contextLength >= requiredLength;
    }

    /**
     * Extract provider name from model id (e.g., "openai/gpt-4o" -> "openai").
     */
    private static String extractProvider(String modelId) {
        if (modelId == null) return null;
        int slashIndex = modelId.indexOf('/');
        return slashIndex > 0 ? modelId.substring(0, slashIndex) : null;
    }

    /**
     * Get a short display name (without provider prefix if present in name).
     */
    public String shortName() {
        if (name.contains("/")) {
            return name.substring(name.indexOf('/') + 1);
        }
        return name;
    }
}
