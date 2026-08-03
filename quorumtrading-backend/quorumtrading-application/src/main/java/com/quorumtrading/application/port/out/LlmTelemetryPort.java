package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.LlmProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only telemetry about LLM provider calls, implemented by the router adapter.
 *
 * <p>The router has always measured per-provider latency and errors; the numbers were
 * write-only — a private map whose sole consumer was a log line every five minutes. This
 * port makes them readable so the surfaces that need them (engine status, backtest time
 * estimates, stall diagnosis) can answer "how slow is the engine, and is it failing?"
 * before a run is created rather than after one has burned itself out on timeouts.</p>
 */
public interface LlmTelemetryPort {

    /** Telemetry for one provider, empty when the provider is not deployed. */
    Optional<ProviderTelemetry> telemetryFor(LlmProvider provider);

    /** Telemetry for every deployed provider, in enum order. */
    List<ProviderTelemetry> telemetrySnapshot();

    /**
     * Call statistics for one provider since process start.
     *
     * @param provider        which provider
     * @param invocations     completed calls (successes + failures; excludes timeouts still running)
     * @param successes       completed successfully
     * @param errors          completed with an error
     * @param timeouts        calls the router's per-call cap cut off while this provider was answering
     * @param avgLatencyMs    mean latency over completed calls, null when none completed
     * @param lastLatencyMs   latency of the most recent completed call, null when none completed
     * @param inFlight        calls currently executing against this provider — on a serialized
     *                        local engine this is the honest "queue depth"
     * @param lastError       message of the most recent failure/timeout/open-circuit, null when none
     * @param lastErrorAt     when that failure was observed, null when none
     * @param lastSuccessAt   when the provider last answered successfully, null when never
     */
    record ProviderTelemetry(
            LlmProvider provider,
            long invocations,
            long successes,
            long errors,
            long timeouts,
            Long avgLatencyMs,
            Long lastLatencyMs,
            int inFlight,
            String lastError,
            Instant lastErrorAt,
            Instant lastSuccessAt
    ) {
    }
}
