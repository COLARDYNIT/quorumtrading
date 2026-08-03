package com.quorumtrading.application.port.out;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.ModelListingCapability;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface LLMPort {

    /**
     * Query the LLM and return full response with usage metrics.
     *
     * @param prompt The prompt to send to the LLM
     * @param config Configuration including model, temperature, tokens, and optional userId
     * @return LLMQueryResult containing content, usage metrics, and actual model used
     */
    LLMQueryResult queryWithUsage(String prompt, LLMConfig config);

    /**
     * Query the LLM and return only the content string.
     * <p>
     * This method exists for backward compatibility. For new code,
     * prefer {@link #queryWithUsage(String, LLMConfig)} to get usage metrics.
     *
     * @param prompt The prompt to send to the LLM
     * @param config Configuration including model, temperature, and tokens
     * @return The content string from the LLM response
     */
    default String query(String prompt, LLMConfig config) {
        return queryWithUsage(prompt, config).content();
    }

    /**
     * Async version of {@link #queryWithUsage(String, LLMConfig)}.
     * Default implementation wraps the sync call in a CompletableFuture.
     * Adapters that support non-blocking I/O (e.g., OpenRouter) should override
     * with a truly async implementation.
     */
    default CompletableFuture<LLMQueryResult> queryWithUsageAsync(String prompt, LLMConfig config) {
        return CompletableFuture.supplyAsync(() -> queryWithUsage(prompt, config));
    }

    String getProviderName();
    boolean isAvailable();

    // ========== Model Catalog Methods ==========

    /**
     * Check if this provider supports listing available models.
     * <p>
     * Providers that return true must implement {@link #getAvailableModels()}.
     *
     * @return true if model listing is supported
     */
    default boolean supportsModelListing() {
        return false;
    }

    /**
     * Get a list of all available models with their capabilities and pricing.
     * <p>
     * This method may cache results for performance. The cache is typically
     * refreshed every hour or when explicitly requested.
     *
     * @return List of available models, empty if not supported or unavailable
     */
    default List<LLMModelInfo> getAvailableModels() {
        return List.of();
    }

    /**
     * Get information about a specific model by its ID.
     *
     * @param modelId The model identifier (e.g., "openai/gpt-4o")
     * @return Model info if found, empty otherwise
     */
    default Optional<LLMModelInfo> getModelInfo(String modelId) {
        return getAvailableModels().stream()
            .filter(m -> m.id().equals(modelId))
            .findFirst();
    }

    /**
     * Force refresh of the model catalog cache.
     * <p>
     * Call this if you need the most up-to-date model information.
     */
    default void refreshModelCatalog() {
        // Default: no-op for providers without caching
    }

    /**
     * Why this provider can or cannot enumerate its models right now.
     *
     * <p>{@link #supportsModelListing()} conflates two states that must be told apart. A provider
     * that has no catalogue by design cannot be validated against, so skipping validation is
     * honest. A provider whose catalogue merely failed to load must <em>not</em> silently disable
     * validation — that is how a transient outage came to let any string through as a model
     * preference, which the fail-hard policy then crashes on at run time.</p>
     *
     * <p>The default preserves today's meaning for providers that have not been taught the
     * distinction.</p>
     */
    default ModelListingCapability modelListingCapability() {
        return supportsModelListing() ? ModelListingCapability.ENUMERABLE : ModelListingCapability.NOT_SUPPORTED;
    }
}
