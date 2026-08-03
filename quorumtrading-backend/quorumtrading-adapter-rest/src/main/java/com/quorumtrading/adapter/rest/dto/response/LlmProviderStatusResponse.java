package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.model.enums.LlmProvider;

import java.time.Instant;
import java.util.List;

/**
 * JSON view of the LLM provider selection state (self-host edition).
 *
 * @param selected     id of the active provider ({@code LOCAL}/{@code OPENROUTER}), or {@code null}
 * @param configuredAt when the active selection was made, or {@code null}
 * @param providers    every selectable provider with live availability
 */
public record LlmProviderStatusResponse(
        String selected,
        Instant configuredAt,
        List<ProviderEntry> providers
) {

    /**
     * @param id        stable provider id ({@code LOCAL}/{@code OPENROUTER})
     * @param label     human-readable name
     * @param available whether it could serve a call right now
     * @param selected  whether it is the active provider
     * @param detail    one-line explanation of the availability state
     * @param telemetry call statistics since process start, null when the provider was never
     *                  called — "available" says it answered a probe; the telemetry says whether
     *                  it answers within the timeouts
     */
    public record ProviderEntry(String id, String label, boolean available, boolean selected, String detail,
                                TelemetryEntry telemetry) {}

    /** Per-provider call statistics; latencies in milliseconds. */
    public record TelemetryEntry(long completedCalls, long successes, long errors, long timeouts,
                                 Long avgLatencyMs, Long lastLatencyMs, int inFlight,
                                 String lastError, Instant lastErrorAt, Instant lastSuccessAt) {}

    public static LlmProviderStatusResponse from(LlmProviderStatus status) {
        List<ProviderEntry> entries = status.providers().stream()
                .map(p -> new ProviderEntry(
                        p.provider().name(),
                        label(p.provider()),
                        p.available(),
                        p.provider() == status.selected(),
                        detail(p.provider(), p.available()),
                        telemetry(p.telemetry())))
                .toList();
        return new LlmProviderStatusResponse(
                status.selected() == null ? null : status.selected().name(),
                status.configuredAt(),
                entries);
    }

    private static TelemetryEntry telemetry(LlmProviderStatus.ProviderTelemetry t) {
        if (t == null || (t.completedCalls() == 0 && t.timeouts() == 0 && t.inFlight() == 0)) {
            return null;
        }
        return new TelemetryEntry(t.completedCalls(), t.successes(), t.errors(), t.timeouts(),
                t.avgLatencyMs(), t.lastLatencyMs(), t.inFlight(),
                t.lastError(), t.lastErrorAt(), t.lastSuccessAt());
    }

    private static String label(LlmProvider provider) {
        return switch (provider) {
            case LOCAL -> "Local (claude CLI)";
            case LOCAL_HTTP -> "Local server (Ollama / OpenAI-compatible)";
            case OPENROUTER -> "OpenRouter";
        };
    }

    private static String detail(LlmProvider provider, boolean available) {
        return switch (provider) {
            case LOCAL -> available
                    ? "A supported CLI tool was detected on this machine."
                    : "No supported CLI tool (claude, codex, gemini or qwen) found on the PATH.";
            case LOCAL_HTTP -> available
                    ? "A local OpenAI-compatible server responded to a live probe."
                    : "No local model server (Ollama, LM Studio, …) responded at the configured URL.";
            case OPENROUTER -> available
                    ? "API key configured."
                    : "No API key configured.";
        };
    }
}
