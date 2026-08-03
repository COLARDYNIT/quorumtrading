package com.quorumtrading.adapter.llm.router;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.orchestration.agents.LlmProviderNotSelectedException;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort.LlmProviderPreference;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.LLMUsage;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavior spec for the router's strict runtime-selection mode
 * ({@code quorumtrading.llm.selection.enabled=true}, self-host edition):
 * unselected → actionable failure; selected → exactly that provider, never a silent fallback.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LLMRouterStrictSelectionTest {

    @Mock
    Instance<LLMPort> allProviders;

    @Mock
    Instance<ManagedExecutor> executorInstance;

    @Mock
    Instance<LlmProviderPreferencePort> preferencePortInstance;

    @Mock
    LlmProviderPreferencePort preferencePort;

    @Mock
    LLMPort localProvider;

    @Mock
    LLMPort openRouterProvider;

    LLMRouterAdapter router;

    private static final LLMConfig CONFIG = new LLMConfig("test-model", 0.7, 1000);

    @BeforeEach
    void setUp() {
        when(localProvider.getProviderName()).thenReturn("local-llm");
        when(openRouterProvider.getProviderName()).thenReturn("openrouter");
        when(allProviders.iterator())
                .thenAnswer(inv -> List.of(localProvider, openRouterProvider).iterator());
        when(executorInstance.isUnsatisfied()).thenReturn(true);
        when(preferencePortInstance.isResolvable()).thenReturn(true);
        when(preferencePortInstance.get()).thenReturn(preferencePort);

        router = new LLMRouterAdapter();
        router.allProviders = allProviders;
        router.llmExecutorInstance = executorInstance;
        router.preferencePort = preferencePortInstance;
        router.metricsLogger = new MetricsLogger();
        router.globalProviderOrder = List.of("local-llm", "openrouter");
        router.callTimeoutSeconds = 5;
        router.selectionEnabled = true;
        router.setupUrl = "http://localhost:8085/api/v1/llm/setup";
        router.init();
    }

    @Test
    void unselectedFailsFastWithActionableMessage() {
        when(preferencePort.current()).thenReturn(Optional.empty());
        when(localProvider.isAvailable()).thenReturn(true);
        when(openRouterProvider.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> router.queryWithUsage("prompt", CONFIG))
                .isInstanceOf(LlmProviderNotSelectedException.class)
                .hasMessageContaining("http://localhost:8085/api/v1/llm/setup")
                .hasMessageContaining("choose_llm_provider")
                .hasMessageContaining("local=yes")
                .hasMessageContaining("openrouter=no");

        verify(localProvider, never()).queryWithUsage(anyString(), any());
        verify(openRouterProvider, never()).queryWithUsage(anyString(), any());
    }

    @Test
    void selectedProviderServesTheCall() {
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(true);
        when(localProvider.queryWithUsage(anyString(), any()))
                .thenReturn(new LLMQueryResult("local says hi", LLMUsage.ZERO, "test-model"));

        LLMQueryResult result = router.queryWithUsage("prompt", CONFIG);

        assertThat(result.content()).isEqualTo("local says hi");
        verify(openRouterProvider, never()).queryWithUsage(anyString(), any());
    }

    @Test
    void noSilentFallbackToOtherProviderWhenSelectedOneFails() {
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(true);
        when(openRouterProvider.isAvailable()).thenReturn(true);
        when(localProvider.queryWithUsage(anyString(), any()))
                .thenThrow(new LLMQueryException("CLI crashed"));

        assertThatThrownBy(() -> router.queryWithUsage("prompt", CONFIG))
                .isInstanceOf(LLMQueryException.class);

        // The user chose the free local engine — a failure must NOT silently spend money.
        verify(openRouterProvider, never()).queryWithUsage(anyString(), any());
    }

    @Test
    void selectedButUnavailableProviderFailsWithFixInstructions() {
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> router.queryWithUsage("prompt", CONFIG))
                .isInstanceOf(LLMQueryException.class)
                .hasMessageContaining("LOCAL")
                .hasMessageContaining("CLI");

        verify(localProvider, never()).queryWithUsage(anyString(), any());
    }

    @Test
    void perCallPreferredProviderCannotOverrideTheUserSelection() {
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(true);
        when(localProvider.queryWithUsage(anyString(), any()))
                .thenReturn(new LLMQueryResult("local", LLMUsage.ZERO, "test-model"));

        LLMConfig preferOpenRouter = new LLMConfig(
                "test-model", 0.7, 1000, "openrouter", null, false);

        router.queryWithUsage("prompt", preferOpenRouter);

        verify(localProvider).queryWithUsage(anyString(), any());
        verify(openRouterProvider, never()).queryWithUsage(anyString(), any());
    }

    @Test
    void selectionDisabledKeepsLegacyOrderedFallback() {
        router.selectionEnabled = false;
        when(localProvider.isAvailable()).thenReturn(true);
        when(openRouterProvider.isAvailable()).thenReturn(true);
        when(localProvider.queryWithUsage(anyString(), any()))
                .thenThrow(new LLMQueryException("CLI crashed"));
        when(openRouterProvider.queryWithUsage(anyString(), any()))
                .thenReturn(new LLMQueryResult("openrouter took over", LLMUsage.ZERO, "test-model"));

        LLMQueryResult result = router.queryWithUsage("prompt", CONFIG);

        assertThat(result.content()).isEqualTo("openrouter took over");
    }

    @Test
    void selectedProviderCatalogueWinsForModelListing() {
        // The selected engine's calls are the only ones that will run, so its catalogue is
        // the truth. Preferring OpenRouter here rejected every real local model id with a
        // suggestion to use anthropic/claude-3.5-sonnet.
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(true);
        when(localProvider.supportsModelListing()).thenReturn(true);
        when(localProvider.getModelInfo("llama3.2")).thenReturn(Optional.of(
                com.quorumtrading.model.valueobject.LLMModelInfo.of(
                        "llama3.2", "llama3.2", com.quorumtrading.model.valueobject.LLMPricing.FREE)));
        when(openRouterProvider.supportsModelListing()).thenReturn(true);

        assertThat(router.getModelInfo("llama3.2")).isPresent();
        verify(openRouterProvider, never()).getModelInfo(anyString());
    }

    @Test
    void selectedProviderWithoutCatalogueDisablesListingInsteadOfBorrowingOpenRouters() {
        // The CLI engine advertises nothing. Reporting supportsModelListing=false makes the
        // strategy llm_model validation skip — strictly better than validating against a
        // catalogue of models that will never serve a call on this deployment.
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(true);
        when(localProvider.supportsModelListing()).thenReturn(false);
        when(openRouterProvider.supportsModelListing()).thenReturn(true);

        assertThat(router.supportsModelListing()).isFalse();
    }

    @Test
    void telemetryIsReadableAfterCalls() {
        // ProviderMetrics existed for years as a write-only map whose sole consumer was a
        // 5-minute log line; the telemetry port is what makes "the engine answers, but at
        // 294s per call" diagnosable before a run is created.
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(true);
        when(localProvider.queryWithUsage(anyString(), any()))
                .thenReturn(new LLMQueryResult("local says hi", LLMUsage.ZERO, "test-model"));

        router.queryWithUsage("prompt", CONFIG);

        var telemetry = router.telemetryFor(LlmProvider.LOCAL);
        assertThat(telemetry).isPresent();
        assertThat(telemetry.get().invocations()).isEqualTo(1);
        assertThat(telemetry.get().successes()).isEqualTo(1);
        assertThat(telemetry.get().avgLatencyMs()).isNotNull();
        assertThat(telemetry.get().inFlight()).isZero();
        assertThat(telemetry.get().lastSuccessAt()).isNotNull();
        assertThat(router.telemetrySnapshot()).isNotEmpty();
    }

    @Test
    void telemetryKeepsTheLastErrorReadable() {
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));
        when(localProvider.isAvailable()).thenReturn(true);
        when(localProvider.queryWithUsage(anyString(), any()))
                .thenThrow(new LLMQueryException("CLI crashed hard"));

        assertThatThrownBy(() -> router.queryWithUsage("prompt", CONFIG))
                .isInstanceOf(LLMQueryException.class);

        var telemetry = router.telemetryFor(LlmProvider.LOCAL);
        assertThat(telemetry).isPresent();
        assertThat(telemetry.get().errors()).isEqualTo(1);
        assertThat(telemetry.get().lastError()).contains("CLI crashed hard");
        assertThat(telemetry.get().lastErrorAt()).isNotNull();
    }
}
