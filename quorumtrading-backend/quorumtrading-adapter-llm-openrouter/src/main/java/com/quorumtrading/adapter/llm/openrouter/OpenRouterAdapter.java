package com.quorumtrading.adapter.llm.openrouter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ToolCall;
import com.quorumtrading.model.valueobject.ToolDefinition;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * OpenRouter LLM adapter with model fallback support.
 * <p>
 * Uses java.net.http.HttpClient for async non-blocking HTTP calls.
 * Supports both sync (queryWithUsage) and async (queryWithUsageAsync) modes.
 * </p>
 * <p>
 * Fallback Strategy:
 * <ul>
 *   <li>Primary: Free-tier models (e.g., qwen:free) - $0/month but rate limited</li>
 *   <li>Fallback: Paid models (e.g., Llama 3.3 70B) - ~$8-40/month with 10% fallback rate</li>
 * </ul>
 * </p>
 */
@ApplicationScoped
public class OpenRouterAdapter implements LLMPort, ConversationalLLMPort {

    private static final Logger LOG = Logger.getLogger(OpenRouterAdapter.class);

    private volatile HttpClient httpClient;

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        }
        return httpClient;
    }

    /** Sentinel default for an unconfigured key — non-blank so Quarkus config validation passes. */
    static final String KEY_NOT_CONFIGURED = "not-configured";

    @ConfigProperty(name = "openrouter.api.key")
    String apiKey;

    /**
     * Runtime-stored key (self-host edition: the user can submit a key through the LLM setup flow,
     * persisted encrypted). Consulted per call so a key submitted at runtime takes effect without
     * restart. Unsatisfied in isolated adapter tests — hence {@code Instance} plus a null guard for
     * plain {@code new OpenRouterAdapter()} instantiation.
     */
    @Inject
    Instance<LlmProviderPreferencePort> preferencePort;

    @ConfigProperty(name = "openrouter.base.url", defaultValue = "https://openrouter.ai/api/v1")
    String baseUrl;

    @ConfigProperty(name = "openrouter.timeout", defaultValue = "60s")
    Duration requestTimeout;

    @ConfigProperty(name = "quorumtrading.llm.openrouter.model.fallback")
    List<String> fallbackModels;

    @Inject
    ModelRateLimitCache rateLimitCache;

    @Inject
    ModelCatalogCache modelCatalogCache;

    @Inject
    ObjectMapper objectMapper;

    @jakarta.annotation.PostConstruct
    void init() {
        boolean hasKey = apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey);
        String maskedKey = hasKey ? apiKey.substring(0, Math.min(10, apiKey.length())) + "..." : "NOT_SET";
        LOG.infof("OpenRouter adapter initialized: apiKeyConfigured=%s, apiKeyPrefix=%s, baseUrl=%s, timeout=%s, fallbackModels=%s",
                  hasKey, maskedKey, baseUrl, requestTimeout, fallbackModels);
        if (!hasKey) {
            LOG.warn("OpenRouter API key is not configured! Set OPENROUTER_API_KEY environment variable.");
        }
    }

    @Override
    @Timed(value = "llm.openrouter.query", description = "OpenRouter queryWithUsage including fallback chain")
    @CircuitBreaker(
        requestVolumeThreshold = 20,
        failureRatio = 0.5,
        delay = 60000,
        successThreshold = 2
    )
    public LLMQueryResult queryWithUsage(String prompt, LLMConfig config) {
        return queryWithUsageAsync(prompt, config).join();
    }

    @Override
    public CompletableFuture<LLMQueryResult> queryWithUsageAsync(String prompt, LLMConfig config) {
        // When noFallback is set (strategy-pinned model), skip the entire fallback chain
        if (config.noFallback()) {
            LOG.infof("OpenRouter query (no-fallback): model=%s, promptLength=%d, userId=%s",
                      config.model(), prompt.length(),
                      config.userId() != null ? config.userId().toString().substring(0, 8) + "..." : "none");

            LLMConfig singleConfig = new LLMConfig(config.model(), config.temperature(), config.tokens(), null, config.userId());
            return querySingleModelAsync(prompt, singleConfig)
                .thenApply(response -> {
                    LOG.infof("OpenRouter query (no-fallback) successful: model=%s, responseLength=%d, tokens=%d, cost=$%.6f",
                             config.model(), response.content().length(), response.usage().totalTokens(),
                             response.usage().rawCost().doubleValue());
                    return response;
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof RateLimitException) {
                        rateLimitCache.recordRateLimit(config.model());
                        throw new LLMQueryException(String.format(
                            "Strategy-pinned model %s is rate-limited (no fallback allowed): %s",
                            config.model(), cause.getMessage()), cause);
                    }
                    if (cause instanceof LLMQueryException lqe) {
                        throw new LLMQueryException(String.format(
                            "Strategy-pinned model %s failed (no fallback allowed): %s",
                            config.model(), lqe.getMessage()), lqe);
                    }
                    throw new LLMQueryException(String.format(
                        "Strategy-pinned model %s failed (no fallback allowed): %s",
                        config.model(), cause.getMessage()), cause);
                });
        }

        // Build model fallback chain starting with requested model
        List<String> modelChain = buildModelChain(config.model());
        List<String> filteredChain = filterRateLimitedModels(modelChain);

        if (filteredChain.isEmpty()) {
            String errorMessage = String.format(
                "All models rate-limited for agent %s. Original chain: %s",
                config.model(), modelChain
            );
            LOG.error(errorMessage);
            return CompletableFuture.failedFuture(new LLMQueryException(errorMessage));
        }

        LOG.infof("OpenRouter query: requestedModel=%s, filteredChain=%s, promptLength=%d, userId=%s",
                  config.model(), filteredChain, prompt.length(),
                  config.userId() != null ? config.userId().toString().substring(0, 8) + "..." : "none");

        return tryNextModelAsync(prompt, config, filteredChain, 0, new ArrayList<>(), new ArrayList<>());
    }

    private CompletableFuture<LLMQueryResult> tryNextModelAsync(
            String prompt, LLMConfig config, List<String> chain,
            int index, List<String> attemptedModels, List<String> failureReasons) {

        if (index >= chain.size()) {
            String errorMessage = String.format(
                "All OpenRouter models failed. Attempted: %s. Failures: %s",
                attemptedModels, failureReasons
            );
            LOG.error(errorMessage);
            return CompletableFuture.failedFuture(new LLMQueryException(errorMessage));
        }

        String model = chain.get(index);
        attemptedModels.add(model);

        LOG.infof("Attempting OpenRouter query: model=%s, attempt=%d/%d",
                 model, index + 1, chain.size());

        LLMConfig modelConfig = new LLMConfig(model, config.temperature(), config.tokens(), null, config.userId());

        return querySingleModelAsync(prompt, modelConfig)
            .thenApply(response -> {
                LOG.infof("OpenRouter query successful: model=%s, responseLength=%d, tokens=%d, cost=$%.6f",
                         model, response.content().length(), response.usage().totalTokens(),
                         response.usage().rawCost().doubleValue());
                return response;
            })
            .exceptionallyCompose(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

                if (cause instanceof RateLimitException) {
                    failureReasons.add(model + " - " + cause.getMessage());
                    LOG.warnf("Model %s failed: %s - recording rate limit and trying next model", model, cause.getMessage());
                    rateLimitCache.recordRateLimit(model);
                } else if (cause instanceof LLMQueryException) {
                    failureReasons.add(model + " - " + cause.getMessage());
                    LOG.warnf("Model %s failed: %s", model, cause.getMessage());
                } else {
                    failureReasons.add(model + " - " + cause.getMessage());
                    LOG.warnf("Model %s failed unexpectedly: %s", model, cause.getMessage());
                }

                return tryNextModelAsync(prompt, config, chain, index + 1, attemptedModels, failureReasons);
            });
    }

    /**
     * Query a single model without fallback logic (async).
     * Package-private to allow CDI interception for @Timed metrics.
     */
    @Timed(value = "llm.openrouter.single_model", description = "Single OpenRouter model call")
    CompletableFuture<LLMQueryResult> querySingleModelAsync(String prompt, LLMConfig config) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.model());
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            requestBody.put("temperature", config.temperature());
            requestBody.put("max_tokens", config.tokens());
            requestBody.put("usage", Map.of("include", true));

            if (config.userId() != null) {
                requestBody.put("user", config.userId().toString());
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String targetUrl = baseUrl + "/chat/completions";

            LOG.debugf("OpenRouter API call: url=%s, model=%s, userId=%s",
                      targetUrl, config.model(),
                      config.userId() != null ? config.userId().toString().substring(0, 8) + "..." : "none");

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(requestTimeout)
                .build();

            return getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseQueryResponse(response, config));

        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                new LLMQueryException("Failed to build OpenRouter request: " + e.getMessage(), e));
        }
    }

    @SuppressWarnings("unchecked")
    private LLMQueryResult parseQueryResponse(HttpResponse<String> response, LLMConfig config) {
        LOG.debugf("OpenRouter API response: status=%d, model=%s", response.statusCode(), config.model());

        if (response.statusCode() == 429) {
            throw new RateLimitException("OpenRouter API rate limit exceeded (HTTP 429) for model: " + config.model());
        }
        if (response.statusCode() == 402) {
            throw new RateLimitException(
                String.format("OpenRouter API spend limit exceeded (HTTP 402) for model: %s - %s", config.model(), response.body()));
        }
        if (response.statusCode() == 503) {
            throw new RateLimitException(
                String.format("OpenRouter API service unavailable (HTTP 503) for model: %s - %s", config.model(), response.body()));
        }
        if (response.statusCode() != 200) {
            throw new LLMQueryException(
                String.format("OpenRouter API error: %d - %s", response.statusCode(), response.body()));
        }

        try {
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");

            if (choices == null || choices.isEmpty()) {
                throw new LLMQueryException("OpenRouter API returned empty choices array");
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                throw new LLMQueryException("OpenRouter API returned null message");
            }

            String content = (String) message.get("content");
            if (content == null) {
                throw new LLMQueryException("OpenRouter API returned null content");
            }

            String actualModel = config.model();
            if (body.containsKey("model")) {
                actualModel = (String) body.get("model");
            }

            LLMUsage usage = parseUsageFromResponse(body, actualModel);
            return LLMQueryResult.of(content, usage, actualModel);

        } catch (LLMQueryException | RateLimitException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMQueryException("Failed to parse OpenRouter response: " + e.getMessage(), e);
        }
    }

    // ========== Conversational LLM (Tool Use) ==========

    @Override
    public ChatCompletionResult chatCompletion(List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config) {
        LOG.infof("OpenRouter chatCompletion: model=%s, messages=%d, tools=%d, userId=%s",
                  config.model(), messages.size(), tools.size(),
                  config.userId() != null ? config.userId().toString().substring(0, 8) + "..." : "none");

        try {
            Map<String, Object> requestBody = buildChatCompletionRequest(messages, tools, config);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String targetUrl = baseUrl + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(requestTimeout)
                .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            handleHttpErrorResponse(response, config.model());

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            return parseChatCompletionResponse(body, config.model());

        } catch (LLMQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMQueryException("OpenRouter chatCompletion failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatCompletionResult chatCompletionStreaming(
            List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config,
            ChatCompletionStreamHandler handler) {

        LOG.infof("OpenRouter chatCompletionStreaming: model=%s, messages=%d, tools=%d, userId=%s",
                  config.model(), messages.size(), tools.size(),
                  config.userId() != null ? config.userId().toString().substring(0, 8) + "..." : "none");

        try {
            Map<String, Object> requestBody = buildChatCompletionRequest(messages, tools, config);
            requestBody.put("stream", true);
            requestBody.put("stream_options", Map.of("include_usage", true));

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String targetUrl = baseUrl + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(requestTimeout)
                .build();

            HttpResponse<InputStream> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                // Read the error body from the stream
                String errorBody;
                try (InputStream is = response.body()) {
                    errorBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                handleStreamingErrorStatus(response.statusCode(), errorBody, config.model());
            }

            StringBuilder contentAccumulator = new StringBuilder();
            Map<Integer, String> toolCallIds = new LinkedHashMap<>();
            Map<Integer, String> toolCallNames = new LinkedHashMap<>();
            Map<Integer, StringBuilder> toolCallArgs = new LinkedHashMap<>();
            LLMUsage[] streamUsage = { null };
            String[] actualModel = { config.model() };

            try (InputStream is = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith(":")) {
                        continue;
                    }
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    Map<String, Object> chunk;
                    try {
                        chunk = objectMapper.readValue(data, Map.class);
                    } catch (Exception e) {
                        LOG.debugf("Skipping unparseable SSE chunk: %s", data);
                        continue;
                    }

                    if (chunk.containsKey("model")) {
                        actualModel[0] = (String) chunk.get("model");
                    }

                    if (chunk.containsKey("usage") && chunk.get("usage") != null) {
                        streamUsage[0] = parseUsageFromResponse(chunk, actualModel[0]);
                    }

                    List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        continue;
                    }

                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                    if (delta == null) {
                        continue;
                    }

                    String contentDelta = (String) delta.get("content");
                    if (contentDelta != null && !contentDelta.isEmpty()) {
                        contentAccumulator.append(contentDelta);
                        handler.onTextDelta(contentDelta);
                    }

                    if (delta.containsKey("tool_calls")) {
                        List<Map<String, Object>> tcDeltas = (List<Map<String, Object>>) delta.get("tool_calls");
                        if (tcDeltas != null) {
                            for (Map<String, Object> tcDelta : tcDeltas) {
                                int idx = ((Number) tcDelta.get("index")).intValue();

                                if (tcDelta.containsKey("id") && tcDelta.get("id") != null) {
                                    String tcId = (String) tcDelta.get("id");
                                    if (!tcId.isBlank()) {
                                        toolCallIds.put(idx, tcId);
                                    }
                                }

                                Map<String, Object> function = (Map<String, Object>) tcDelta.get("function");
                                if (function != null) {
                                    if (function.containsKey("name") && function.get("name") != null) {
                                        toolCallNames.put(idx, (String) function.get("name"));
                                    }
                                    if (function.containsKey("arguments") && function.get("arguments") != null) {
                                        toolCallArgs.computeIfAbsent(idx, k -> new StringBuilder())
                                            .append((String) function.get("arguments"));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.errorf("Error reading SSE stream: model=%s, error=%s", config.model(), e.getMessage());
                handler.onError("Stream read error: " + e.getMessage());
                throw new LLMQueryException("Error reading SSE stream from OpenRouter: " + e.getMessage(), e);
            }

            List<ToolCall> assembledToolCalls = null;
            if (!toolCallNames.isEmpty()) {
                assembledToolCalls = new ArrayList<>();
                for (Integer idx : toolCallNames.keySet()) {
                    String id = toolCallIds.getOrDefault(idx, "call_" + UUID.randomUUID());
                    String name = toolCallNames.get(idx);
                    String args = toolCallArgs.containsKey(idx) ? toolCallArgs.get(idx).toString() : "{}";
                    assembledToolCalls.add(new ToolCall(id, name, args));
                }
                handler.onToolCalls(assembledToolCalls);
            }

            LLMUsage usage = streamUsage[0] != null ? streamUsage[0] : LLMUsage.ZERO;
            handler.onUsage(usage);

            String content = contentAccumulator.length() > 0 ? contentAccumulator.toString() : null;

            LOG.infof("OpenRouter chatCompletionStreaming completed: model=%s, hasContent=%s, toolCalls=%d, tokens=%d, cost=$%.6f",
                      actualModel[0], content != null, assembledToolCalls != null ? assembledToolCalls.size() : 0,
                      usage.totalTokens(), usage.rawCost().doubleValue());

            return new ChatCompletionResult(content, assembledToolCalls, usage, actualModel[0]);

        } catch (LLMQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMQueryException("OpenRouter chatCompletionStreaming failed: " + e.getMessage(), e);
        }
    }

    private void handleHttpErrorResponse(HttpResponse<String> response, String model) {
        if (response.statusCode() == 429) {
            rateLimitCache.recordRateLimit(model);
            throw new LLMQueryException("OpenRouter API rate limit exceeded (HTTP 429) for model: " + model);
        }
        if (response.statusCode() == 402) {
            rateLimitCache.recordRateLimit(model);
            throw new LLMQueryException(String.format("OpenRouter API spend limit exceeded (HTTP 402) for model: %s - %s", model, response.body()));
        }
        if (response.statusCode() != 200) {
            throw new LLMQueryException(String.format("OpenRouter API error: %d - %s", response.statusCode(), response.body()));
        }
    }

    private void handleStreamingErrorStatus(int statusCode, String errorBody, String model) {
        if (statusCode == 429) {
            rateLimitCache.recordRateLimit(model);
            throw new LLMQueryException("OpenRouter API rate limit exceeded (HTTP 429) for model: " + model);
        }
        if (statusCode == 402) {
            rateLimitCache.recordRateLimit(model);
            throw new LLMQueryException(String.format("OpenRouter API spend limit exceeded (HTTP 402) for model: %s - %s", model, errorBody));
        }
        throw new LLMQueryException(String.format("OpenRouter API error: %d - %s", statusCode, errorBody));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildChatCompletionRequest(List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.model());
        request.put("temperature", config.temperature());
        request.put("max_tokens", config.tokens());
        request.put("usage", Map.of("include", true));

        if (config.userId() != null) {
            request.put("user", config.userId().toString());
        }

        List<Map<String, Object>> messageArray = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("role", msg.role());

            if (msg.content() != null) {
                msgMap.put("content", msg.content());
            }

            if (msg.hasToolCalls()) {
                List<Map<String, Object>> toolCallsArray = new ArrayList<>();
                for (ToolCall tc : msg.toolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("type", "function");
                    tcMap.put("function", Map.of(
                        "name", tc.functionName(),
                        "arguments", tc.arguments()
                    ));
                    toolCallsArray.add(tcMap);
                }
                msgMap.put("tool_calls", toolCallsArray);
            }

            if (msg.toolCallId() != null) {
                msgMap.put("tool_call_id", msg.toolCallId());
            }

            messageArray.add(msgMap);
        }
        request.put("messages", messageArray);

        if (!tools.isEmpty()) {
            List<Map<String, Object>> toolsArray = new ArrayList<>();
            for (ToolDefinition tool : tools) {
                Map<String, Object> properties = new LinkedHashMap<>();
                for (var entry : tool.parameters().entrySet()) {
                    Map<String, String> paramDef = new LinkedHashMap<>();
                    paramDef.put("type", entry.getValue().type());
                    if (entry.getValue().description() != null) {
                        paramDef.put("description", entry.getValue().description());
                    }
                    properties.put(entry.getKey(), paramDef);
                }

                Map<String, Object> parametersSchema = new LinkedHashMap<>();
                parametersSchema.put("type", "object");
                parametersSchema.put("properties", properties);
                if (!tool.required().isEmpty()) {
                    parametersSchema.put("required", tool.required());
                }

                Map<String, Object> functionDef = new LinkedHashMap<>();
                functionDef.put("name", tool.name());
                functionDef.put("description", tool.description());
                functionDef.put("parameters", parametersSchema);

                toolsArray.add(Map.of("type", "function", "function", functionDef));
            }
            request.put("tools", toolsArray);
        }

        return request;
    }

    @SuppressWarnings("unchecked")
    private ChatCompletionResult parseChatCompletionResponse(Map<String, Object> body, String requestedModel) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LLMQueryException("OpenRouter API returned empty choices array");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new LLMQueryException("OpenRouter API returned null message");
        }

        String actualModel = body.containsKey("model") ? (String) body.get("model") : requestedModel;
        LLMUsage usage = parseUsageFromResponse(body, actualModel);

        String content = (String) message.get("content");

        List<ToolCall> toolCalls = null;
        if (message.containsKey("tool_calls")) {
            List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
                toolCalls = new ArrayList<>();
                for (Map<String, Object> rawTc : rawToolCalls) {
                    String tcId = (String) rawTc.get("id");
                    if (tcId == null || tcId.isBlank()) {
                        tcId = "call_" + UUID.randomUUID();
                    }
                    Map<String, Object> function = (Map<String, Object>) rawTc.get("function");
                    String functionName = (String) function.get("name");
                    String arguments = (String) function.get("arguments");
                    toolCalls.add(new ToolCall(tcId, functionName, arguments));
                }
            }
        }

        LOG.infof("OpenRouter chatCompletion successful: model=%s, hasContent=%s, toolCalls=%d, tokens=%d, cost=$%.6f",
                  actualModel, content != null, toolCalls != null ? toolCalls.size() : 0,
                  usage.totalTokens(), usage.rawCost().doubleValue());

        return new ChatCompletionResult(content, toolCalls, usage, actualModel);
    }

    // ========== Usage Parsing ==========

    @SuppressWarnings("unchecked")
    private LLMUsage parseUsageFromResponse(Map<String, Object> responseBody, String model) {
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        BigDecimal cost = BigDecimal.ZERO;

        try {
            if (responseBody.containsKey("usage")) {
                Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");

                if (usage != null) {
                    promptTokens = getIntValue(usage, "prompt_tokens");
                    completionTokens = getIntValue(usage, "completion_tokens");
                    totalTokens = getIntValue(usage, "total_tokens");

                    if (usage.containsKey("cost")) {
                        cost = getBigDecimalValue(usage, "cost");
                    }
                } else {
                    LOG.warnf("OpenRouter response contained null usage object for model=%s", model);
                }
            } else {
                LOG.warnf("OpenRouter response missing usage data for model=%s. Token/cost tracking will be 0.", model);
            }

            if (cost.equals(BigDecimal.ZERO) && responseBody.containsKey("cost")) {
                cost = getBigDecimalValue(responseBody, "cost");
            }

            if (totalTokens > 0 && cost.compareTo(BigDecimal.ZERO) == 0) {
                cost = estimateCostFromCatalog(model, promptTokens, completionTokens);
                if (cost.compareTo(BigDecimal.ZERO) > 0) {
                    LOG.infof("OpenRouter response missing cost for model=%s (%d tokens). Estimated from catalog pricing: $%.8f",
                              model, totalTokens, cost.doubleValue());
                } else {
                    LOG.warnf("OpenRouter response missing cost for model=%s (%d tokens) and no catalog pricing available. Cost will be 0.",
                              model, totalTokens);
                }
            }

            LOG.debugf("Parsed usage: model=%s, promptTokens=%d, completionTokens=%d, totalTokens=%d, cost=$%.8f",
                      model, promptTokens, completionTokens, totalTokens, cost.doubleValue());

        } catch (Exception e) {
            LOG.warnf("Failed to parse usage from response: %s. Using zero usage.", e.getMessage());
        }

        return new LLMUsage(model, promptTokens, completionTokens, totalTokens, cost);
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try { return new BigDecimal(value.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private BigDecimal estimateCostFromCatalog(String model, int promptTokens, int completionTokens) {
        if (model == null || (promptTokens == 0 && completionTokens == 0)) {
            return BigDecimal.ZERO;
        }
        try {
            List<LLMModelInfo> models = modelCatalogCache.getModels();
            for (LLMModelInfo modelInfo : models) {
                if (model.equals(modelInfo.id())) {
                    if (!modelInfo.pricing().isFree()) {
                        return modelInfo.pricing().estimateCost(promptTokens, completionTokens);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            LOG.debugf("Failed to estimate cost from catalog for model=%s: %s", model, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    // ========== Model Chain ==========

    private List<String> buildModelChain(String requestedModel) {
        List<String> chain = new ArrayList<>();
        chain.add(requestedModel);
        for (String fallback : fallbackModels) {
            if (!fallback.equals(requestedModel)) {
                chain.add(fallback);
            }
        }
        return chain;
    }

    private List<String> filterRateLimitedModels(List<String> modelChain) {
        return modelChain.stream()
            .filter(model -> !rateLimitCache.isModelRateLimited(model))
            .toList();
    }

    // ========== Provider Info ==========

    @Override
    public String getProviderName() {
        return "openrouter";
    }

    @Override
    public boolean isAvailable() {
        // A placeholder default must NOT report available — the router would select this provider
        // and fail at call time with a 401 instead of routing/erroring meaningfully up front.
        return isUsableKey(effectiveApiKey());
    }

    /**
     * The key used for requests: a runtime-stored key (user-submitted via the LLM setup flow)
     * wins over the env/config key, so submitting a key takes effect without restart.
     */
    private String effectiveApiKey() {
        if (preferencePort != null && preferencePort.isResolvable()) {
            Optional<String> stored = preferencePort.get().openRouterApiKey();
            if (stored.isPresent() && isUsableKey(stored.get())) {
                return stored.get();
            }
        }
        return apiKey;
    }

    private static boolean isUsableKey(String key) {
        return key != null && !key.isBlank() && !KEY_NOT_CONFIGURED.equals(key);
    }

    // ========== Model Catalog Methods ==========

    @Override
    public boolean supportsModelListing() {
        return true;
    }

    /**
     * Enumerable only when the key can actually fetch the catalogue.
     *
     * <p>{@link #supportsModelListing()} answers "by design, yes" and is deliberately unconditional.
     * But {@link #getAvailableModels()} returns an empty list whenever this adapter is unavailable,
     * so without this override the adapter claims ENUMERABLE while enumerating nothing — and a
     * caller that trusts the claim concludes that no model in the world is servable.</p>
     *
     * <p>That is exactly the distinction {@code ModelListingCapability} exists to draw, and this is
     * the adapter that most needed it: an install with no OpenRouter key still has this bean
     * deployed, so it is the provider whose catalogue the router falls back to.</p>
     */
    @Override
    public com.quorumtrading.model.valueobject.ModelListingCapability modelListingCapability() {
        return isAvailable()
                ? com.quorumtrading.model.valueobject.ModelListingCapability.ENUMERABLE
                : com.quorumtrading.model.valueobject.ModelListingCapability.UNAVAILABLE;
    }

    @Override
    public List<LLMModelInfo> getAvailableModels() {
        if (!isAvailable()) {
            LOG.warn("OpenRouter not available, cannot fetch model catalog");
            return List.of();
        }
        return modelCatalogCache.getModels();
    }

    @Override
    public Optional<LLMModelInfo> getModelInfo(String modelId) {
        return getAvailableModels().stream()
            .filter(m -> m.id().equals(modelId))
            .findFirst();
    }

    @Override
    public void refreshModelCatalog() {
        if (isAvailable()) {
            modelCatalogCache.forceRefresh();
        }
    }

    /**
     * Exception thrown when an OpenRouter model hits rate limits (HTTP 429/402/503).
     * Package-private to allow use in async fallback chain lambdas.
     */
    static class RateLimitException extends RuntimeException {
        RateLimitException(String message) {
            super(message);
        }
    }
}
