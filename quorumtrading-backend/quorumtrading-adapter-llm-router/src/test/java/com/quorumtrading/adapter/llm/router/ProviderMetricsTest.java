package com.quorumtrading.adapter.llm.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderMetricsTest {

    @Test
    void shouldRecordSuccessfulInvocation() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordInvocation(100, true);

        assertThat(metrics.getTotalInvocations()).isEqualTo(1);
        assertThat(metrics.getSuccessCount()).isEqualTo(1);
        assertThat(metrics.getErrorCount()).isEqualTo(0);
        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(100.0);
        assertThat(metrics.getErrorRate()).isEqualTo(0.0);
    }

    @Test
    void shouldRecordFailedInvocation() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordInvocation(200, false);

        assertThat(metrics.getTotalInvocations()).isEqualTo(1);
        assertThat(metrics.getSuccessCount()).isEqualTo(0);
        assertThat(metrics.getErrorCount()).isEqualTo(1);
        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(200.0);
        assertThat(metrics.getErrorRate()).isEqualTo(1.0);
    }

    @Test
    void shouldCalculateErrorRate() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordInvocation(100, true);
        metrics.recordInvocation(200, false);
        metrics.recordInvocation(150, false);
        metrics.recordInvocation(180, true);

        assertThat(metrics.getTotalInvocations()).isEqualTo(4);
        assertThat(metrics.getSuccessCount()).isEqualTo(2);
        assertThat(metrics.getErrorCount()).isEqualTo(2);
        assertThat(metrics.getErrorRate()).isEqualTo(0.5);
    }

    @Test
    void shouldCalculateAverageResponseTime() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordInvocation(100, true);
        metrics.recordInvocation(200, true);
        metrics.recordInvocation(300, true);

        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(200.0);
    }

    @Test
    void shouldHandleZeroInvocations() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        assertThat(metrics.getErrorRate()).isEqualTo(0.0);
        assertThat(metrics.getAverageResponseTimeMs()).isEqualTo(0.0);
        assertThat(metrics.getLastResponseTimeMs()).isNull();
        assertThat(metrics.getLastError()).isNull();
        assertThat(metrics.getLastSuccessAt()).isNull();
    }

    @Test
    void failureKeepsItsMessageReadable() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordFailure(150, "connection refused");

        assertThat(metrics.getErrorCount()).isEqualTo(1);
        assertThat(metrics.getLastError()).isEqualTo("connection refused");
        assertThat(metrics.getLastErrorAt()).isNotNull();
        assertThat(metrics.getLastResponseTimeMs()).isEqualTo(150);
    }

    @Test
    void timeoutCountsSeparatelyAndRecordsNoLatency() {
        // A router-cap timeout is not a completed call: the provider is still answering,
        // so it must not drag the average latency down with a synthetic figure.
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordTimeout("router per-call cap fired");

        assertThat(metrics.getTimeoutCount()).isEqualTo(1);
        assertThat(metrics.getTotalInvocations()).isZero();
        assertThat(metrics.getLastResponseTimeMs()).isNull();
        assertThat(metrics.getLastError()).contains("cap fired");
    }

    @Test
    void inFlightTracksBeginAndEnd() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.beginCall();
        metrics.beginCall();
        assertThat(metrics.getInFlight()).isEqualTo(2);

        metrics.endCall();
        assertThat(metrics.getInFlight()).isEqualTo(1);
    }

    @Test
    void successStampsLastSuccessAndLastLatency() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordInvocation(294_000, true);

        assertThat(metrics.getLastSuccessAt()).isNotNull();
        assertThat(metrics.getLastResponseTimeMs()).isEqualTo(294_000);
    }

    @Test
    void circuitOpenLeavesATraceWithoutCountingACall() {
        ProviderMetrics metrics = new ProviderMetrics("test-provider");

        metrics.recordCircuitOpen();

        assertThat(metrics.getTotalInvocations()).isZero();
        assertThat(metrics.getLastError()).contains("circuit breaker open");
        assertThat(metrics.getLastErrorAt()).isNotNull();
    }
}
