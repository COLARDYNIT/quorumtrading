package com.quorumtrading.model.valueobject;

/**
 * Value object representing an LLM model identifier.
 * Format is typically "provider/model-name" (e.g., "anthropic/claude-sonnet-5.5").
 */
public record ModelId(String value) {

    public ModelId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Model ID cannot be null or blank");
        }
        value = value.trim();
    }

    /**
     * Create a ModelId from a string.
     */
    public static ModelId of(String value) {
        return new ModelId(value);
    }

    /**
     * Extract the provider from the model ID.
     * Returns null if no provider prefix found.
     */
    public String provider() {
        int slashIndex = value.indexOf('/');
        return slashIndex > 0 ? value.substring(0, slashIndex) : null;
    }

    /**
     * Extract the model name without provider prefix.
     */
    public String modelName() {
        int slashIndex = value.indexOf('/');
        return slashIndex > 0 ? value.substring(slashIndex + 1) : value;
    }

    @Override
    public String toString() {
        return value;
    }
}
