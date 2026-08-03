package com.quorumtrading.adapter.llm.openrouter;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OpenRouterAdapter.
 *
 * Integration tests with real API calls should be placed in a separate test class
 * with appropriate tagging to avoid accidental execution.
 */
@QuarkusTest
class OpenRouterAdapterTest {

    @ConfigProperty(name = "openrouter.api.key", defaultValue = "")
    String configuredApiKey;

    @Test
    void shouldReturnProviderName() {
        OpenRouterAdapter adapter = new OpenRouterAdapter();
        assertThat(adapter.getProviderName()).isEqualTo("openrouter");
    }

    @Test
    void shouldBeAvailableWhenApiKeyIsSet() {
        OpenRouterAdapter adapter = new OpenRouterAdapter();
        adapter.apiKey = "sk-or-v1-test-key";
        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void shouldNotBeAvailableWhenApiKeyIsBlank() {
        OpenRouterAdapter adapter = new OpenRouterAdapter();
        adapter.apiKey = "";
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void shouldNotBeAvailableWhenApiKeyIsNull() {
        OpenRouterAdapter adapter = new OpenRouterAdapter();
        adapter.apiKey = null;
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void shouldNotBeAvailableWhenApiKeyIsWhitespace() {
        OpenRouterAdapter adapter = new OpenRouterAdapter();
        adapter.apiKey = "   ";
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void shouldNotBeAvailableWhenApiKeyIsThePlaceholderDefault() {
        // The config default is the non-blank sentinel "not-configured" — it must NOT count as a
        // usable key, or the router selects this provider and fails at call time with a 401.
        OpenRouterAdapter adapter = new OpenRouterAdapter();
        adapter.apiKey = "not-configured";
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void runtimeStoredKeyMakesAdapterAvailableWithoutConfigKey() {
        // Self-host edition: a key submitted through the LLM setup flow (persisted via
        // LlmProviderPreferencePort) must take effect without restart.
        OpenRouterAdapter adapter = new OpenRouterAdapter();
        adapter.apiKey = "not-configured";

        jakarta.enterprise.inject.Instance<com.quorumtrading.application.port.out.LlmProviderPreferencePort> instance =
                org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
        com.quorumtrading.application.port.out.LlmProviderPreferencePort port =
                org.mockito.Mockito.mock(com.quorumtrading.application.port.out.LlmProviderPreferencePort.class);
        org.mockito.Mockito.when(instance.isResolvable()).thenReturn(true);
        org.mockito.Mockito.when(instance.get()).thenReturn(port);
        org.mockito.Mockito.when(port.openRouterApiKey()).thenReturn(java.util.Optional.of("sk-or-v1-stored"));
        adapter.preferencePort = instance;

        assertThat(adapter.isAvailable()).isTrue();
    }
}
