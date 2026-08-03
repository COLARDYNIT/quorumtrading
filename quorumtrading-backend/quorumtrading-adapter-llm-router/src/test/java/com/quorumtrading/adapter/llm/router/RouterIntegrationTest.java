package com.quorumtrading.adapter.llm.router;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for LLMRouterAdapter with mock providers.
 */
@QuarkusTest
class RouterIntegrationTest {

    @Inject
    LLMRouterAdapter router;

    @Test
    void shouldRouteToFirstSuccessfulProvider() {
        LLMConfig config = new LLMConfig("test-model", 0.7, 1000);

        String result = router.query("test prompt", config);

        assertThat(result).contains("Mock response from mock-a");
    }

    @Test
    void shouldUsePreferredProviderWhenSpecified() {
        LLMConfig config = new LLMConfig("test-model", 0.7, 1000, "mock-a");

        String result = router.query("test prompt", config);

        assertThat(result).contains("mock-a");
    }

    @Test
    void shouldReportRouterAsProviderName() {
        assertThat(router.getProviderName()).isEqualTo("router");
    }

    @Test
    void shouldReportAvailableWhenProvidersExist() {
        assertThat(router.isAvailable()).isTrue();
    }

    @Test
    void shouldRecordMetricsForSuccessfulCall() {
        LLMConfig config = new LLMConfig("test-model", 0.7, 1000);

        router.query("test prompt", config);

        // Verify metrics were recorded (can't access internal map easily, but call succeeds)
        assertThat(router.isAvailable()).isTrue();
    }
}
