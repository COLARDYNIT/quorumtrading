package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.model.enums.LlmProvider;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tools for runtime LLM provider selection (self-host edition) — lets the agent resolve the
 * "no LLM provider selected" condition in-band instead of sending the user to a browser.
 *
 * <p>Both tools are no-ops returning an explanatory error unless
 * {@code quorumtrading.llm.selection.enabled} is on (it is only on in the single-user self-host
 * edition; in the hosted SaaS the platform routes providers itself).</p>
 */
@Singleton
@ActivateRequestContext
public class LlmProviderTools extends McpToolBase {

    @Inject
    GetLlmProviderStatusUseCase getStatusUseCase;

    @Inject
    SelectLlmProviderUseCase selectUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @ConfigProperty(name = "quorumtrading.llm.selection.enabled", defaultValue = "false")
    boolean selectionEnabled;

    /**
     * Surfaced in the LOCAL_HTTP status detail. With both a CLI shim and Ollama installed, "a local
     * server answered a probe" is ambiguous — naming the endpoint and model is the only way to tell
     * which engine is actually serving the agents.
     */
    // Optional, not a defaulted String: an empty-string default fails Quarkus config validation,
    // and these properties are not defined in every module's test profile.
    @ConfigProperty(name = "quorumtrading.llm.localhttp.base-url")
    Optional<String> localHttpUrl;

    @ConfigProperty(name = "quorumtrading.llm.localhttp.model")
    Optional<String> localHttpModel;

    @ConfigProperty(name = "quorumtrading.llm.setup-url",
            defaultValue = "http://localhost:8085/api/v1/llm/setup")
    String setupUrl;

    @Tool(name = "get_llm_status", description = "Show which LLM engine powers AI features (local "
            + "CLI, a local OpenAI-compatible server, or OpenRouter), whether one is selected, and "
            + "what is available on this machine right now — including per-engine call telemetry "
            + "(avg/last latency, error and timeout counts, in-flight calls, last error). Check the "
            + "latency BEFORE creating a backtest on a local engine: 'available' only means it "
            + "answered a probe, not that it answers within the consensus timeouts. Self-host "
            + "edition only.")
    public ToolResponse getLlmStatus() {
        return execute(() -> {
            requireAuthenticated();
            if (!selectionEnabled) {
                throw new IllegalStateException(
                        "LLM provider selection is not enabled on this deployment — the platform "
                        + "routes LLM providers automatically.");
            }
            return toJson(statusPayload(getStatusUseCase.get()));
        });
    }

    @Tool(name = "choose_llm_provider", description = "Select the LLM engine for this deployment: "
            + "LOCAL (claude/codex/gemini/qwen CLI on this machine, free), LOCAL_HTTP (a local "
            + "OpenAI-compatible server such as Ollama or LM Studio, free and keyless), or OPENROUTER "
            + "(hosted models via your own API key). Takes effect on the next AI call — no restart. "
            + "Self-host edition only.")
    public ToolResponse chooseLlmProvider(
            @ToolArg(description = "Provider to activate: LOCAL, LOCAL_HTTP or OPENROUTER") String provider,
            @ToolArg(description = "OpenRouter API key to store (encrypted). Only needed for "
                    + "OPENROUTER when no key is configured yet.", required = false) String apiKey) {
        return execute(() -> {
            requireAuthenticated();
            if (!selectionEnabled) {
                throw new IllegalStateException(
                        "LLM provider selection is not enabled on this deployment — the platform "
                        + "routes LLM providers automatically.");
            }
            LlmProvider parsed;
            try {
                parsed = LlmProvider.valueOf(provider == null ? "" : provider.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown provider '" + provider + "'. Use LOCAL, LOCAL_HTTP or OPENROUTER.");
            }
            LlmProviderStatus status = selectUseCase.select(
                    new SelectLlmProviderUseCase.Command(parsed, apiKey));

            Map<String, Object> payload = statusPayload(status);
            payload.put("message", parsed + " is now the active LLM engine. "
                    + "It takes effect on the next AI call — no restart needed.");
            return toJson(payload);
        });
    }

    /** Tools mutate deployment-wide config — still require a valid MCP bearer identity. */
    private void requireAuthenticated() {
        mcpUserContext.getUserId();
    }

    private Map<String, Object> statusPayload(LlmProviderStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selected", status.selected() == null ? null : status.selected().name());
        payload.put("configuredAt", status.configuredAt() == null ? null : status.configuredAt().toString());
        payload.put("providers", status.providers().stream()
                .map(p -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", p.provider().name());
                    entry.put("available", p.available());
                    entry.put("detail", detail(p.provider(), p.available()));
                    // "Available" only says the engine answered a probe — the telemetry says
                    // whether it answers WITHIN the timeouts. A local model averaging 294s per
                    // call is 'available' and useless; surfacing latency here is what lets a
                    // caller catch that before creating a run instead of after it stalls.
                    putTelemetryIfMeaningful(entry, p.telemetry());
                    return entry;
                })
                .toList());
        if (status.selected() == null) {
            List<String> availableNow = status.providers().stream()
                    .filter(LlmProviderStatus.ProviderAvailability::available)
                    .map(p -> p.provider().name())
                    .toList();
            payload.put("hint", availableNow.isEmpty()
                    ? "No provider is usable yet: start a local OpenAI-compatible server (e.g. "
                      + "`ollama serve`) for LOCAL_HTTP, install the claude CLI on the host for LOCAL, "
                      + "or choose OPENROUTER with an apiKey. Setup page: " + setupUrl
                    : "No provider selected yet. Call choose_llm_provider with one of "
                      + availableNow + ", or open " + setupUrl);
        }
        return payload;
    }

    /** Attach flattened telemetry; a provider that was never called gets no block (no noise). */
    private static void putTelemetryIfMeaningful(Map<String, Object> entry,
            LlmProviderStatus.ProviderTelemetry t) {
        if (t == null || (t.completedCalls() == 0 && t.timeouts() == 0 && t.inFlight() == 0)) {
            return;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("completedCalls", t.completedCalls());
        map.put("successes", t.successes());
        map.put("errors", t.errors());
        map.put("timeouts", t.timeouts());
        map.put("avgLatencyMs", t.avgLatencyMs());
        map.put("lastLatencyMs", t.lastLatencyMs());
        map.put("inFlight", t.inFlight());
        map.put("lastError", t.lastError());
        map.put("lastErrorAt", t.lastErrorAt() == null ? null : t.lastErrorAt().toString());
        map.put("lastSuccessAt", t.lastSuccessAt() == null ? null : t.lastSuccessAt().toString());
        entry.put("telemetry", map);
    }

    private String detail(LlmProvider provider, boolean available) {
        return switch (provider) {
            case LOCAL -> available
                    ? "A supported CLI tool (claude/codex/gemini/qwen) was detected on this machine."
                    : "No supported CLI tool found on the PATH.";
            case LOCAL_HTTP -> available
                    ? "A local OpenAI-compatible server (Ollama/LM Studio/…) answered a live probe at "
                      + localHttpEndpoint() + "."
                    : "No local model server responded at " + localHttpEndpoint()
                      + " (set QT_LLM_LOCALHTTP_URL to point elsewhere).";
            case OPENROUTER -> available
                    ? "API key configured."
                    : "No API key configured — pass apiKey when choosing OPENROUTER.";
        };
    }

    /** e.g. {@code http://host.docker.internal:11434/v1 (model: llama3.2)}. */
    private String localHttpEndpoint() {
        String url = optionalOr(localHttpUrl, "the configured URL");
        String model = optionalOr(localHttpModel, "server default");
        return url + " (model: " + model + ")";
    }

    private static String optionalOr(Optional<String> value, String fallback) {
        return value == null ? fallback
                : value.filter(v -> !v.isBlank()).orElse(fallback);
    }
}
