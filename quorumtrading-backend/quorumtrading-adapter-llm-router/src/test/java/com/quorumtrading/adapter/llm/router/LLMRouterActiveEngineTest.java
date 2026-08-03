package com.quorumtrading.adapter.llm.router;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort.LlmProviderPreference;
import com.quorumtrading.model.enums.LlmProvider;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;

/**
 * The router answers "which engine would a call land on right now?" — the question every
 * engine-scoped model default depends on. Getting it wrong hands one engine's default to another,
 * which is precisely the defect the unification work removes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LLMRouterAdapter — the active engine")
class LLMRouterActiveEngineTest {

    @Mock Instance<LLMPort> allProviders;
    @Mock Instance<ManagedExecutor> executorInstance;
    @Mock Instance<LlmProviderPreferencePort> preferencePortInstance;
    @Mock LlmProviderPreferencePort preferencePort;
    @Mock LLMPort localProvider;
    @Mock LLMPort openRouterProvider;

    private LLMRouterAdapter router;

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
        router.setupUrl = "http://localhost:8085/api/v1/llm/setup";
        router.init();
    }

    @Test
    @DisplayName("with runtime selection, the active engine is the persisted choice")
    void selectionWins() {
        router.selectionEnabled = true;
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL_HTTP, Instant.now())));

        assertThat(router.activeEngine()).contains(LlmProvider.LOCAL_HTTP);
    }

    @Test
    @DisplayName("a momentarily unreachable engine is still the engine the user selected")
    void selectionIsNotFilteredByAvailability() {
        router.selectionEnabled = true;
        when(localProvider.isAvailable()).thenReturn(false);
        when(openRouterProvider.isAvailable()).thenReturn(true);
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));

        assertThat(router.activeEngine()).contains(LlmProvider.LOCAL);
    }

    @Test
    @DisplayName("no selection yet means unknown, never a guessed default")
    void unselectedIsUnknown() {
        router.selectionEnabled = true;
        when(preferencePort.current()).thenReturn(Optional.empty());

        assertThat(router.activeEngine()).isEmpty();
    }

    @Test
    @DisplayName("without selection, the first available provider in the configured order wins")
    void staticOrderPrefersAnAvailableProvider() {
        router.selectionEnabled = false;
        when(localProvider.isAvailable()).thenReturn(false);
        when(openRouterProvider.isAvailable()).thenReturn(true);

        assertThat(router.activeEngine()).contains(LlmProvider.OPENROUTER);
    }

    @Test
    @DisplayName("without selection and nothing available, the first deployed provider is reported")
    void staticOrderFallsBackToTheFirstDeployedProvider() {
        router.selectionEnabled = false;
        when(localProvider.isAvailable()).thenReturn(false);
        when(openRouterProvider.isAvailable()).thenReturn(false);

        assertThat(router.activeEngine()).contains(LlmProvider.LOCAL);
    }
}
