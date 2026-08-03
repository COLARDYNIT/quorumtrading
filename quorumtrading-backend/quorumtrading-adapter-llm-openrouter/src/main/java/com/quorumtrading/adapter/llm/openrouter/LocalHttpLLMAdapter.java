package com.quorumtrading.adapter.llm.openrouter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.valueobject.LLMUsage;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Local HTTP LLM provider — an OpenAI-compatible model server the user already runs on their own
 * machine: <b>Ollama</b> ({@code :11434}), <b>LM Studio</b> ({@code :1234}), llama.cpp / vLLM /
 * LocalAI, etc. It speaks the same wire format as OpenRouter ({@code /chat/completions},
 * {@code /models}), so it lives in this module but points at a <em>local</em> base URL and needs no
 * API key. Free, keyless, and reachable from the Docker container via {@code host.docker.internal}
 * (see the {@code extra_hosts} mapping in {@code docker-compose.local.yml}).
 *
 * <p>Availability is a <b>live probe</b> — {@code GET {base}/models} must answer 200 — so unlike the
 * old always-optimistic LOCAL detection, the status never claims a provider the server can't back
 * up. The provider bean name is {@code "local-http"} (see
 * {@code LlmProviderAvailabilityAdapter.PROVIDER_BEAN_NAMES}).</p>
 *
 * <p>Model selection: the incoming per-agent model id is usually a hosted (OpenRouter-namespaced)
 * name the local server doesn't have, so this adapter honours the requested id when the server
 * advertises it, else falls back to the first model the server advertises via {@code /models}
 * (cached), else passes the request through. That makes it "just work" with whatever model the
 * user has pulled.</p>
 *
 * <p>{@code quorumtrading.llm.localhttp.model} ({@code QT_LLM_LOCALHTTP_MODEL}) is <b>not</b> read
 * here. It is system-level configuration and resolves in {@code SystemModelDefaultsProvider} as
 * this engine's system default, so a pin reaches this adapter as an ordinary requested model.
 * Reading it a second time inside the request path is what let a computed-and-reported model differ
 * from the one that ran.</p>
 *
 * <p>Gated by {@code quorumtrading.llm.localhttp.enabled} (default true only in the self-host
 * {@code %local} profile); invisible to CDI lookup otherwise, exactly like {@code LocalLLMAdapter}.</p>
 */
@ApplicationScoped
@LookupIfProperty(name = "quorumtrading.llm.localhttp.enabled", stringValue = "true", lookupIfMissing = false)
public class LocalHttpLLMAdapter implements LLMPort {

    private static final Logger LOG = Logger.getLogger(LocalHttpLLMAdapter.class);

    /** Bean name mapped to {@code LlmProvider.LOCAL_HTTP} in the availability adapter. */
    static final String PROVIDER_NAME = "local-http";

    private static final String KEY_NOT_CONFIGURED = "not-configured";

    /**
     * Loosest characters-per-token ratio still treated as a real prompt-token count.
     * Real tokenizers sit near 4; anything above 20 characters per reported token means the
     * server did not count the prompt.
     */
    private static final int MIN_CHARS_PER_PROMPT_TOKEN = 20;

    /** Cached set of model ids the server advertises; null until first successful probe. */
    private volatile java.util.Set<String> advertisedModelIds;

    /**
     * Per-model metadata from Ollama's {@code /api/show}, cached on success only.
     *
     * <p>A context window is not decoration: without it nothing can reason about whether a model
     * even fits the agent prompts, which run long once news and sentiment context are attached.
     * Reporting 0 is reporting "unknown" in a field that reads like a measurement.</p>
     */
    private final java.util.Map<String, LocalModelMetadata> metadataCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether this server implements {@code /api/show} at all.
     *
     * <p>Cached separately from the metadata because it answers a different question. A 404 is a
     * <em>stable</em> property of the server — LM Studio, llama.cpp and vLLM speak the
     * OpenAI-compatible surface but not Ollama's native one, and they never will — so caching it
     * costs one probe rather than one per model per call. A network failure is transient and is
     * deliberately NOT cached, exactly as {@link #advertisedModels()} does not cache a failed probe.
     * {@code null} means "not yet determined".</p>
     */
    private volatile Boolean showEndpointSupported;

    /** Substitutions already warned about, so the log records each once rather than per call. */
    private final java.util.Set<String> substitutionWarnings = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @ConfigProperty(name = "quorumtrading.llm.localhttp.base-url", defaultValue = "http://host.docker.internal:11434/v1")
    String baseUrl;

    @ConfigProperty(name = "quorumtrading.llm.localhttp.api-key", defaultValue = KEY_NOT_CONFIGURED)
    String apiKey;

    @ConfigProperty(name = "quorumtrading.llm.localhttp.timeout", defaultValue = "120s")
    Duration requestTimeout;

    @Inject
    ObjectMapper objectMapper;

    private volatile HttpClient httpClient;
    /** Lazily-discovered first model the server advertises; used when no model is pinned. */
    private volatile String discoveredModel;

    private HttpClient http() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        }
        return httpClient;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // Live round-trip: the model list must answer 200. A real probe means the status never
        // reports a local server that isn't actually up (the whole point of this provider).
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(3))
                    .GET())
                    .build();
            HttpResponse<Void> response = http().send(request, HttpResponse.BodyHandlers.discarding());
            boolean up = response.statusCode() == 200;
            if (!up) {
                LOG.debugf("Local HTTP LLM probe: %s/models returned %d", baseUrl, response.statusCode());
            }
            return up;
        } catch (Exception e) {
            LOG.debugf("Local HTTP LLM not reachable at %s: %s", baseUrl, e.getMessage());
            return false;
        }
    }

    @Override
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 60000, successThreshold = 2)
    public LLMQueryResult queryWithUsage(String prompt, LLMConfig config) {
        String model = resolveModel(config);
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", config.temperature(),
                    "max_tokens", config.tokens());
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = authorized(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(json)))
                    .build();

            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 400) {
                throw new LLMQueryException(String.format(
                        "Local LLM server rejected model '%s' (HTTP %d). Pull it (e.g. `ollama pull %s`) "
                        + "or set QT_LLM_LOCALHTTP_MODEL to a model your server has. Server said: %s",
                        model, response.statusCode(), model, truncate(response.body())));
            }
            if (response.statusCode() != 200) {
                throw new LLMQueryException(String.format(
                        "Local LLM server error: HTTP %d - %s", response.statusCode(), truncate(response.body())));
            }
            return parseResponse(response.body(), model, prompt.length());
        } catch (LLMQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMQueryException("Local HTTP LLM query failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private LLMQueryResult parseResponse(String rawBody, String requestedModel, int promptChars) {
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new LLMQueryException("Local LLM server returned no choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message == null ? null : (String) message.get("content");
            if (content == null) {
                throw new LLMQueryException("Local LLM server returned empty content");
            }
            String actualModel = body.get("model") instanceof String m ? m : requestedModel;
            // Local inference is free; record token counts when the server reports them, cost stays $0.
            int prompt = 0, completion = 0, total = 0;
            if (body.get("usage") instanceof Map<?, ?> usage) {
                prompt = intVal(usage.get("prompt_tokens"));
                completion = intVal(usage.get("completion_tokens"));
                total = intVal(usage.get("total_tokens"));
            }

            // Some local servers answer with a token count they never computed: observed
            // prompt_tokens=2 for a 4,543-character prompt, while completion_tokens was genuine.
            // Storing that 2 is worse than storing nothing — it reads as a measurement and is
            // small enough to make a simulation look almost free. Treat an implausible count as
            // unknown (0), which is the sentinel every consumer already understands.
            if (prompt > 0 && !isPlausiblePromptTokenCount(prompt, promptChars)) {
                LOG.warnf("Local LLM server reported %d prompt tokens for a %d-character prompt "
                        + "(model=%s); recording as unknown rather than as a count",
                        prompt, promptChars, actualModel);
                // total_tokens is derived from the same bad number — recompute from what is left.
                total = completion;
                prompt = 0;
            }

            LLMUsage usage = new LLMUsage(actualModel, prompt, completion, total, BigDecimal.ZERO);
            return LLMQueryResult.of(content, usage, actualModel);
        } catch (LLMQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMQueryException("Failed to parse local LLM response: " + e.getMessage(), e);
        }
    }

    /**
     * Whether a reported prompt-token count could plausibly correspond to the prompt sent.
     *
     * <p>Tokenizers land near one token per four characters across every model family in use.
     * The bound here is deliberately loose — one token per {@value #MIN_CHARS_PER_PROMPT_TOKEN}
     * characters — so it never rejects a genuine count from an unusual tokenizer, and only
     * catches servers that are not counting at all (single-digit tokens for a multi-thousand
     * character prompt).</p>
     */
    static boolean isPlausiblePromptTokenCount(int promptTokens, int promptChars) {
        if (promptChars <= 0) {
            return true; // nothing to compare against
        }
        return promptTokens >= promptChars / MIN_CHARS_PER_PROMPT_TOKEN;
    }

    /**
     * The model to actually request: the caller's id when this server has it, otherwise the
     * server's first advertised model, otherwise the request unchanged.
     *
     * <p>{@code QT_LLM_LOCALHTTP_MODEL} is deliberately <b>not</b> read here. It is system-level
     * configuration and now resolves in {@code SystemModelDefaultsProvider} as this engine's system
     * default, so a pin arrives through the normal 6-tier chain as {@code config.model()} and is
     * honoured by the branch below. Reading it here as well is what let the platform compute one
     * model, report it on every surface, and run another.</p>
     */
    String resolveModel(LLMConfig config) {
        // Honour the caller's model when this server actually has it. The per-agent id is
        // usually a hosted name the local server does not carry, which is why a local model is
        // preferred — but silently substituting one meant a user's model preference was
        // accepted, echoed back as "user_override" by get_model_preferences, and then ignored
        // on every single call with nothing anywhere saying so.
        String requested = config.model();
        if (requested != null && !requested.isBlank() && advertisedModels().contains(requested)) {
            return requested;
        }

        String discovered = firstAdvertisedModel();
        if (discovered != null) {
            warnIfRequestNotHonoured(config, discovered, "this server does not advertise the requested model");
            return discovered;
        }
        return config.model();
    }

    /**
     * Log, once per distinct substitution, that the model actually used is not the one asked
     * for. Silent substitution is what made a model preference look honoured when it was not.
     */
    private void warnIfRequestNotHonoured(LLMConfig config, String actual, String why) {
        String requested = config.model();
        if (requested == null || requested.isBlank() || requested.equals(actual)) {
            return;
        }
        if (substitutionWarnings.add(requested + "->" + actual)) {
            LOG.warnf("Local HTTP LLM: request for model '%s' is being served by '%s' (%s). "
                    + "Model preferences have no effect on this engine.", requested, actual, why);
        }
    }

    // ========== Model catalogue (LLMPort) ==========
    // The local server's /models list IS this deployment's catalogue. Without these
    // overrides the router fell through to the OpenRouter catalogue, so a strategy pinning
    // a real Ollama model (llm_model: qwen3:8b) was rejected with a message suggesting
    // anthropic/claude-3.5-sonnet — implying local models cannot be pinned at all.

    @Override
    public boolean supportsModelListing() {
        return !advertisedModels().isEmpty();
    }

    /**
     * A local server always <em>has</em> a catalogue — that is the whole point of the OpenAI-compatible
     * {@code /models} endpoint. So an empty list never means "no listing by design"; it means the
     * probe failed. Saying so stops a transient outage from silently disabling pin validation and
     * letting an unservable model be saved.
     */
    @Override
    public com.quorumtrading.model.valueobject.ModelListingCapability modelListingCapability() {
        return advertisedModels().isEmpty()
                ? com.quorumtrading.model.valueobject.ModelListingCapability.UNAVAILABLE
                : com.quorumtrading.model.valueobject.ModelListingCapability.ENUMERABLE;
    }

    @Override
    public List<com.quorumtrading.model.valueobject.LLMModelInfo> getAvailableModels() {
        return advertisedModels().stream()
                .sorted()
                .map(this::describeModel)
                .toList();
    }

    @Override
    public java.util.Optional<com.quorumtrading.model.valueobject.LLMModelInfo> getModelInfo(String modelId) {
        if (modelId == null || !advertisedModels().contains(modelId)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(describeModel(modelId));
    }

    /**
     * A model id plus whatever the server will tell us about it.
     *
     * <p>Context length and parameter size come from {@code /api/show} when the server implements
     * it. When it does not, they stay 0/null — which every consumer already reads as "unknown"
     * rather than "small": {@code ModelFilter} skips a null parameter size instead of failing it,
     * and now does the same for an unknown context length.</p>
     */
    private com.quorumtrading.model.valueobject.LLMModelInfo describeModel(String modelId) {
        LocalModelMetadata metadata = metadataFor(modelId);
        return new com.quorumtrading.model.valueobject.LLMModelInfo(
                modelId,
                modelId,
                metadata.description(),
                metadata.contextLength(),
                0,
                com.quorumtrading.model.valueobject.LLMPricing.FREE,
                List.of("text"),
                null,
                null,
                false,
                metadata.parameterSizeB());
    }

    @Override
    public void refreshModelCatalog() {
        advertisedModelIds = null; // next lookup re-probes the server
        metadataCache.clear();
        showEndpointSupported = null;
    }

    /** Model ids this server advertises, empty when it cannot be reached. Overridable in tests. */
    @SuppressWarnings("unchecked")
    java.util.Set<String> advertisedModels() {
        java.util.Set<String> cached = advertisedModelIds;
        if (cached != null) {
            return cached;
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(3))
                    .GET())
                    .build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (data != null) {
                    for (Map<String, Object> entry : data) {
                        if (entry.get("id") instanceof String id && !id.isBlank()) {
                            ids.add(id);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("Local HTTP LLM: could not list models from %s: %s", baseUrl, e.getMessage());
            return ids; // do not cache a failed probe
        }
        advertisedModelIds = ids;
        return ids;
    }

    // ========== Per-model metadata (Ollama /api/show) ==========

    /**
     * What we know about one model. All-unknown is a legitimate, common answer.
     *
     * @param contextLength  maximum context window in tokens, 0 when unknown
     * @param parameterSizeB size in billions of parameters, null when unknown
     * @param description    short human-readable summary, null when unknown
     */
    record LocalModelMetadata(int contextLength, Integer parameterSizeB, String description) {
        static final LocalModelMetadata UNKNOWN = new LocalModelMetadata(0, null, null);
    }

    /** Metadata for one model: cached on success, re-probed after a transient failure. */
    LocalModelMetadata metadataFor(String modelId) {
        LocalModelMetadata cached = metadataCache.get(modelId);
        if (cached != null) {
            return cached;
        }
        if (Boolean.FALSE.equals(showEndpointSupported)) {
            // Known not to implement /api/show — do not pay a round-trip per model per call.
            return LocalModelMetadata.UNKNOWN;
        }

        LocalModelMetadata probed;
        try {
            probed = probeModelMetadata(modelId);
        } catch (RuntimeException e) {
            // Belt and braces. probeModelMetadata already swallows its own failures, but the
            // guarantee that matters here is structural: model listing must not be breakable by a
            // metadata lookup, whatever a future probe implementation decides to throw.
            LOG.debugf("Local HTTP LLM: metadata probe for '%s' failed: %s", modelId, e.getMessage());
            return LocalModelMetadata.UNKNOWN;
        }
        if (probed != null) {
            metadataCache.put(modelId, probed);
            return probed;
        }
        return LocalModelMetadata.UNKNOWN;
    }

    /**
     * Ask Ollama's {@code /api/show} about one model.
     *
     * <p>{@code /api/show} is Ollama's <em>native</em> API, not part of the OpenAI-compatible
     * surface, so it lives at the server root rather than under {@code /v1}. Servers that speak only
     * the OpenAI-compatible subset — LM Studio, llama.cpp, vLLM — answer 404, which is a permanent
     * property of that server and is remembered so the cost is one probe rather than one per model.</p>
     *
     * @return the metadata, or {@code null} when it could not be determined — never an exception,
     *         because model listing must not fail over a metadata lookup
     */
    @SuppressWarnings("unchecked")
    LocalModelMetadata probeModelMetadata(String modelId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("model", modelId));
            HttpRequest request = authorized(HttpRequest.newBuilder()
                    .uri(URI.create(nativeApiRoot() + "/api/show"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(3))
                    .POST(HttpRequest.BodyPublishers.ofString(payload)))
                    .build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
            return interpretShowResponse(modelId, response.statusCode(), response.body());
        } catch (Exception e) {
            // Transient: deliberately not cached, mirroring advertisedModels().
            LOG.debugf("Local HTTP LLM: could not read metadata for '%s': %s", modelId, e.getMessage());
            return null;
        }
    }

    /**
     * Decide what an {@code /api/show} reply means. Separated from the HTTP call so the rules are
     * testable without a server: it is the status-code interpretation, not the plumbing, that
     * decides whether a deployment silently loses its model metadata.
     *
     * @return parsed metadata, or {@code null} when nothing could be determined
     */
    @SuppressWarnings("unchecked")
    LocalModelMetadata interpretShowResponse(String modelId, int statusCode, String body) throws java.io.IOException {
        if (statusCode == 404 || statusCode == 405 || statusCode == 501) {
            // Permanent: this server speaks only the OpenAI-compatible subset. Remember it, so the
            // cost of a non-Ollama server is one probe rather than one per model per call.
            LOG.debugf("Local HTTP LLM: %s does not implement /api/show (HTTP %d); "
                    + "model metadata will be reported as unknown", baseUrl, statusCode);
            showEndpointSupported = false;
            return null;
        }
        if (statusCode != 200) {
            // Could be transient (503 while a model loads). Conclude nothing permanent, and do not
            // cache — the next call gets another chance.
            LOG.debugf("Local HTTP LLM: /api/show for '%s' returned %d", modelId, statusCode);
            return null;
        }

        showEndpointSupported = true;
        return parseShowResponse(objectMapper.readValue(body, Map.class));
    }

    /**
     * Pull the two numbers worth having out of an {@code /api/show} body.
     *
     * <p>Ollama namespaces context length by architecture — {@code llama.context_length},
     * {@code qwen3.context_length}, {@code gemma3.context_length} — so the key is matched by suffix
     * rather than enumerated, which would go stale with every new model family.</p>
     */
    static LocalModelMetadata parseShowResponse(Map<String, Object> body) {
        int contextLength = 0;
        Integer parameterSizeB = null;
        String description = null;

        if (body.get("model_info") instanceof Map<?, ?> modelInfo) {
            for (Map.Entry<?, ?> entry : modelInfo.entrySet()) {
                if (entry.getKey() instanceof String key
                        && key.endsWith(".context_length")
                        && entry.getValue() instanceof Number value) {
                    contextLength = Math.max(contextLength, value.intValue());
                }
            }
            if (modelInfo.get("general.parameter_count") instanceof Number count) {
                // Billions, rounded to the nearest whole — the gate compares against 8B.
                parameterSizeB = (int) Math.round(count.doubleValue() / 1_000_000_000d);
            }
        }

        if (body.get("details") instanceof Map<?, ?> details) {
            if (parameterSizeB == null && details.get("parameter_size") instanceof String size) {
                parameterSizeB = parseParameterSize(size);
            }
            Object family = details.get("family");
            Object quantization = details.get("quantization_level");
            if (family instanceof String f && !f.isBlank()) {
                description = quantization instanceof String q && !q.isBlank()
                        ? f + ", " + q
                        : f;
            }
        }

        return new LocalModelMetadata(Math.max(contextLength, 0), parameterSizeB, description);
    }

    /** {@code "3.2B"} → 3, {@code "70B"} → 70, {@code "540M"} → 1. Null when unparseable. */
    static Integer parseParameterSize(String size) {
        String trimmed = size.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            if (trimmed.endsWith("B")) {
                return (int) Math.round(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)));
            }
            if (trimmed.endsWith("M")) {
                double millions = Double.parseDouble(trimmed.substring(0, trimmed.length() - 1));
                return (int) Math.round(millions / 1000d);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /**
     * The server root, with the OpenAI-compatible {@code /v1} suffix stripped.
     *
     * <p>{@code baseUrl} points at the OpenAI-compatible surface ({@code .../v1}); Ollama's native
     * endpoints sit beside it, not under it.</p>
     */
    String nativeApiRoot() {
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return root.endsWith("/v1") ? root.substring(0, root.length() - "/v1".length()) : root;
    }

    @SuppressWarnings("unchecked")
    String firstAdvertisedModel() {
        String cached = discoveredModel;
        if (cached != null) {
            return cached;
        }
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(3))
                    .GET())
                    .build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data != null && !data.isEmpty() && data.get(0).get("id") instanceof String id && !id.isBlank()) {
                discoveredModel = id;
                LOG.infof("Local HTTP LLM: no model pinned, using server's first model '%s' from %s", id, baseUrl);
                return id;
            }
        } catch (Exception e) {
            LOG.debugf("Local HTTP LLM: could not list models from %s: %s", baseUrl, e.getMessage());
        }
        return null;
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        if (apiKey != null && !apiKey.isBlank() && !KEY_NOT_CONFIGURED.equals(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private static int intVal(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }
}
