package com.quorumtrading.adapter.llm.openrouter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cache for OpenRouter model catalog.
 * <p>
 * Fetches and caches the list of available models from OpenRouter API.
 * Cache is automatically refreshed after TTL expires.
 * </p>
 */
@ApplicationScoped
public class ModelCatalogCache {

    private static final Logger LOG = Logger.getLogger(ModelCatalogCache.class);

    // Pattern to extract model size from name/id (e.g., "70b", "7b", "8b-instruct")
    private static final Pattern MODEL_SIZE_PATTERN = Pattern.compile("(\\d+)b(?:-|$|\\s|:)", Pattern.CASE_INSENSITIVE);

    private volatile HttpClient httpClient;

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        }
        return httpClient;
    }

    @ConfigProperty(name = "openrouter.api.key")
    String apiKey;

    @ConfigProperty(name = "openrouter.base.url", defaultValue = "https://openrouter.ai/api/v1")
    String baseUrl;

    @ConfigProperty(name = "quorumtrading.llm.openrouter.model-cache-ttl-minutes", defaultValue = "60")
    int cacheTtlMinutes;

    @Inject
    ObjectMapper objectMapper;

    private volatile List<LLMModelInfo> cachedModels = Collections.emptyList();
    private volatile Instant lastRefresh = Instant.EPOCH;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Get the cached list of available models.
     * Automatically refreshes if cache is stale.
     *
     * @return List of available models (never null)
     */
    public List<LLMModelInfo> getModels() {
        // Check if refresh needed (read lock for check)
        lock.readLock().lock();
        try {
            if (!shouldRefresh()) {
                return cachedModels;
            }
        } finally {
            lock.readLock().unlock();
        }

        // Need to refresh (acquire write lock)
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            if (shouldRefresh()) {
                refresh();
            }
            return cachedModels;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Force refresh the model catalog cache.
     */
    public void forceRefresh() {
        lock.writeLock().lock();
        try {
            refresh();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if cache needs refreshing.
     */
    private boolean shouldRefresh() {
        Duration elapsed = Duration.between(lastRefresh, Instant.now());
        return elapsed.toMinutes() >= cacheTtlMinutes;
    }

    /**
     * Fetch models from OpenRouter API and update cache.
     * Must be called with write lock held.
     */
    @SuppressWarnings("unchecked")
    private void refresh() {
        LOG.info("Refreshing OpenRouter model catalog...");

        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            LOG.warn("OpenRouter API key not configured, skipping model catalog refresh");
            return;
        }

        try {
            String targetUrl = baseUrl + "/models";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.errorf("Failed to fetch model catalog: HTTP %d", response.statusCode());
                return;
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> modelsData = (List<Map<String, Object>>) body.get("data");

            if (modelsData == null) {
                LOG.warn("No 'data' field in OpenRouter models response");
                return;
            }

            List<LLMModelInfo> models = new ArrayList<>();
            for (Map<String, Object> modelData : modelsData) {
                try {
                    LLMModelInfo model = parseModel(modelData);
                    if (model != null) {
                        models.add(model);
                    }
                } catch (Exception e) {
                    LOG.warnf("Failed to parse model: %s", e.getMessage());
                }
            }

            cachedModels = Collections.unmodifiableList(models);
            lastRefresh = Instant.now();

            LOG.infof("Model catalog refreshed: %d models loaded", models.size());

        } catch (Exception e) {
            LOG.errorf("Error fetching model catalog: %s", e.getMessage());
        }
    }

    /**
     * Parse a single model from OpenRouter API response.
     */
    @SuppressWarnings("unchecked")
    private LLMModelInfo parseModel(Map<String, Object> data) {
        String id = (String) data.get("id");
        if (id == null || id.isBlank()) {
            return null;
        }

        String name = (String) data.get("name");
        String description = (String) data.get("description");

        // Context length
        int contextLength = 0;
        Object contextObj = data.get("context_length");
        if (contextObj instanceof Number) {
            contextLength = ((Number) contextObj).intValue();
        }

        // Max output tokens from top_provider
        int maxOutputTokens = 0;
        Map<String, Object> topProvider = (Map<String, Object>) data.get("top_provider");
        if (topProvider != null) {
            Object maxCompletionObj = topProvider.get("max_completion_tokens");
            if (maxCompletionObj instanceof Number) {
                maxOutputTokens = ((Number) maxCompletionObj).intValue();
            }
        }

        // Pricing
        LLMPricing pricing = LLMPricing.FREE;
        Map<String, Object> pricingData = (Map<String, Object>) data.get("pricing");
        if (pricingData != null) {
            pricing = LLMPricing.fromStrings(
                toString(pricingData.get("prompt")),
                toString(pricingData.get("completion")),
                toString(pricingData.get("request")),
                toString(pricingData.get("image"))
            );
        }

        // Modalities from architecture
        List<String> modalities = List.of("text");
        Map<String, Object> architecture = (Map<String, Object>) data.get("architecture");
        if (architecture != null) {
            String modality = (String) architecture.get("modality");
            if (modality != null && !modality.isBlank()) {
                modalities = parseModalities(modality);
            }
        }

        // Created timestamp
        Instant createdAt = null;
        Object createdObj = data.get("created");
        if (createdObj instanceof Number) {
            createdAt = Instant.ofEpochSecond(((Number) createdObj).longValue());
        }

        // Extract provider from id
        String provider = null;
        int slashIndex = id.indexOf('/');
        if (slashIndex > 0) {
            provider = id.substring(0, slashIndex);
        }

        // Determine web search capability
        boolean supportsWebSearch = detectWebSearchCapability(id, name, data);

        // Parse parameter size from model id/name (e.g., "70b" -> 70)
        Integer parameterSizeB = parseParameterSize(id, name);

        return new LLMModelInfo(
            id,
            name,
            description,
            contextLength,
            maxOutputTokens,
            pricing,
            modalities,
            provider,
            createdAt,
            supportsWebSearch,
            parameterSizeB
        );
    }

    /**
     * Parse model size in billions from the model id or name.
     * Looks for patterns like "70b", "7b", "8b-instruct", "72b-chat".
     *
     * @return Size in billions, or null if not found
     */
    private Integer parseParameterSize(String id, String name) {
        // Try id first (more reliable), then name
        for (String text : List.of(id, name)) {
            if (text != null) {
                Matcher matcher = MODEL_SIZE_PATTERN.matcher(text);
                if (matcher.find()) {
                    try {
                        return Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException e) {
                        // Ignore and try next
                    }
                }
            }
        }
        return null; // Unknown size
    }

    /**
     * Detect if a model supports web search capability.
     * <p>
     * Web search models are detected by:
     * - Having "web_search_options" in supported_parameters (primary method)
     * - Having ":online" or "-online" suffix in model id
     * - Being from Perplexity provider (all their models support search)
     */
    @SuppressWarnings("unchecked")
    private boolean detectWebSearchCapability(String id, String name, Map<String, Object> data) {
        // Check for ":online" or "-online" suffix (common patterns for search-enabled models)
        if (id != null) {
            String lowerId = id.toLowerCase();
            if (lowerId.endsWith(":online") || lowerId.endsWith("-online")) {
                return true;
            }
        }
        if (name != null && name.toLowerCase().contains("online")) {
            return true;
        }

        // Check for Perplexity provider (all their models support search)
        if (id != null && id.toLowerCase().startsWith("perplexity/")) {
            return true;
        }

        // Check for web_search_options in supported_parameters (primary detection method)
        // This is the most reliable indicator of web search capability
        Object supportedParamsObj = data.get("supported_parameters");
        if (supportedParamsObj instanceof List) {
            List<String> paramNames = (List<String>) supportedParamsObj;
            if (paramNames.contains("web_search_options") || paramNames.contains("web_search")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Parse modalities string like "text+image->text" into list.
     */
    private List<String> parseModalities(String modality) {
        List<String> result = new ArrayList<>();
        result.add("text"); // Always include text

        String lower = modality.toLowerCase();
        if (lower.contains("image")) {
            result.add("image");
        }
        if (lower.contains("audio")) {
            result.add("audio");
        }
        if (lower.contains("video")) {
            result.add("video");
        }

        return result;
    }

    private String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    /**
     * Get the number of cached models.
     */
    public int getCachedModelCount() {
        lock.readLock().lock();
        try {
            return cachedModels.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the last refresh timestamp.
     */
    public Instant getLastRefreshTime() {
        lock.readLock().lock();
        try {
            return lastRefresh;
        } finally {
            lock.readLock().unlock();
        }
    }
}
