package com.quorumtrading.application.usecase.llm;

import com.quorumtrading.model.enums.LlmProvider;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of the deployment's LLM provider selection: what is chosen (if anything) and which
 * providers could currently serve calls.
 *
 * @param selected     the active provider, or {@code null} when none has been chosen yet
 * @param configuredAt when the active selection was made, or {@code null} when none
 * @param providers    availability of every selectable provider, in enum order
 * @param unservablePins stored preferences and strategy pins the selected engine will not serve.
 *                       Populated on selection, empty on a plain status read — switching engines
 *                       is the realistic way a valid pin becomes unservable, and saying nothing
 *                       means the user discovers it mid-backtest instead.
 */
public record LlmProviderStatus(
        LlmProvider selected,
        Instant configuredAt,
        List<ProviderAvailability> providers,
        UnservablePinReport unservablePins
) {

    public LlmProviderStatus {
        unservablePins = unservablePins == null ? UnservablePinReport.EMPTY : unservablePins;
    }

    /** Status without a pin sweep — the shape every plain read uses. */
    public LlmProviderStatus(LlmProvider selected, Instant configuredAt, List<ProviderAvailability> providers) {
        this(selected, configuredAt, providers, UnservablePinReport.EMPTY);
    }

    /** The same status, carrying what a just-completed engine switch left unservable. */
    public LlmProviderStatus withUnservablePins(UnservablePinReport report) {
        return new LlmProviderStatus(selected, configuredAt, providers, report);
    }

    /** Whether a provider has been chosen. */
    public boolean isSelected() {
        return selected != null;
    }

    /**
     * One provider's current usability.
     *
     * @param provider  the provider
     * @param available whether it could serve a call right now (CLI present / key configured)
     * @param telemetry call statistics since process start (latency, errors, timeouts,
     *                  in-flight count) — null when no telemetry source is wired. This is what
     *                  makes "the engine is up but takes 294s per call" visible <em>before</em>
     *                  a backtest is created rather than after it burned out on timeouts.
     */
    public record ProviderAvailability(
            LlmProvider provider,
            boolean available,
            ProviderTelemetry telemetry) {

        /** Availability without telemetry (no source wired). */
        public ProviderAvailability(LlmProvider provider, boolean available) {
            this(provider, available, null);
        }
    }

    /**
     * Call statistics for one provider since process start — the read-model twin of the
     * output-port record, so driving adapters render telemetry without touching
     * {@code port.out} (the ArchUnit rule that keeps orchestration out of adapters).
     * Latencies in milliseconds; a router-cap timeout is not a completed call.
     */
    public record ProviderTelemetry(
            long completedCalls,
            long successes,
            long errors,
            long timeouts,
            Long avgLatencyMs,
            Long lastLatencyMs,
            int inFlight,
            String lastError,
            Instant lastErrorAt,
            Instant lastSuccessAt) {
    }
}
