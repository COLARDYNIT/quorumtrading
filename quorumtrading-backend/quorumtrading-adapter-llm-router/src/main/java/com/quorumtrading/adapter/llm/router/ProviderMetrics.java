package com.quorumtrading.adapter.llm.router;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics tracking for LLM provider invocations.
 * Tracks invocation counts, errors, response times, in-flight calls and the last error
 * in memory. Readable through {@code LlmTelemetryPort} (the router implements it) —
 * these numbers used to be write-only, visible solely as a log line every five minutes.
 */
public class ProviderMetrics {

    private final String providerName;

    private final AtomicLong totalInvocations = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);
    /**
     * Calls currently executing against this provider. On a serialized local engine this is
     * the honest "queue depth": a router-level timeout does NOT decrement it, because the
     * underlying call is still occupying the engine until it actually returns.
     */
    private final AtomicInteger inFlight = new AtomicInteger(0);

    private volatile Long lastResponseTimeMs;
    private volatile String lastError;
    private volatile Instant lastErrorAt;
    private volatile Instant lastSuccessAt;

    public ProviderMetrics(String providerName) {
        this.providerName = providerName;
    }

    /**
     * Record a provider invocation with response time and success status.
     *
     * @param responseTimeMs Time taken for the invocation in milliseconds
     * @param success Whether the invocation succeeded
     */
    public void recordInvocation(long responseTimeMs, boolean success) {
        totalInvocations.incrementAndGet();
        totalResponseTimeMs.addAndGet(responseTimeMs);
        lastResponseTimeMs = responseTimeMs;

        if (success) {
            successCount.incrementAndGet();
            lastSuccessAt = Instant.now();
        } else {
            errorCount.incrementAndGet();
        }
    }

    /** Record a failed invocation together with its cause, keeping the last error readable. */
    public void recordFailure(long responseTimeMs, String errorMessage) {
        recordInvocation(responseTimeMs, false);
        lastError = errorMessage != null ? errorMessage : "unknown error";
        lastErrorAt = Instant.now();
    }

    /**
     * Record that the router's per-call cap fired while this provider was answering. The call
     * itself is still running (see {@link #inFlight}), so no latency is recorded.
     */
    public void recordTimeout(String message) {
        timeoutCount.incrementAndGet();
        lastError = message;
        lastErrorAt = Instant.now();
    }

    /** Record an attempt refused by the provider's open circuit breaker (no call was made). */
    public void recordCircuitOpen() {
        lastError = "circuit breaker open — provider temporarily disabled after repeated failures";
        lastErrorAt = Instant.now();
    }

    /** Mark a call as started; pair with {@link #endCall()} in a finally block. */
    public void beginCall() {
        inFlight.incrementAndGet();
    }

    /** Mark a call as finished (however it ended). */
    public void endCall() {
        inFlight.decrementAndGet();
    }

    public long getTotalInvocations() {
        return totalInvocations.get();
    }

    public long getSuccessCount() {
        return successCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Calculate error rate (0.0 to 1.0).
     *
     * @return Error rate, or 0.0 if no invocations
     */
    public double getErrorRate() {
        long total = totalInvocations.get();
        return total > 0 ? (double) errorCount.get() / total : 0.0;
    }

    /**
     * Calculate average response time in milliseconds.
     *
     * @return Average response time, or 0.0 if no invocations
     */
    public double getAverageResponseTimeMs() {
        long total = totalInvocations.get();
        return total > 0 ? (double) totalResponseTimeMs.get() / total : 0.0;
    }

    public String getProviderName() {
        return providerName;
    }

    public long getTimeoutCount() {
        return timeoutCount.get();
    }

    public int getInFlight() {
        return inFlight.get();
    }

    /** Latency of the most recent completed call, or null when none completed yet. */
    public Long getLastResponseTimeMs() {
        return lastResponseTimeMs;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLastErrorAt() {
        return lastErrorAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }
}
