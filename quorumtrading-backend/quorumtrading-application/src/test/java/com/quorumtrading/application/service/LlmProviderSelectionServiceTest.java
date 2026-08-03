package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.LlmProviderAvailabilityPort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort.LlmProviderPreference;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.model.enums.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmProviderSelectionServiceTest {

    @Mock
    LlmProviderPreferencePort preferencePort;

    @Mock
    LlmProviderAvailabilityPort availabilityPort;

    LlmProviderSelectionService service;

    @BeforeEach
    void setUp() {
        service = new LlmProviderSelectionService(preferencePort, availabilityPort);
    }

    @Test
    void statusReportsUnselectedWithPerProviderAvailability() {
        when(preferencePort.current()).thenReturn(Optional.empty());
        when(availabilityPort.isAvailable(LlmProvider.LOCAL)).thenReturn(true);
        when(availabilityPort.isAvailable(LlmProvider.LOCAL_HTTP)).thenReturn(false);
        when(availabilityPort.isAvailable(LlmProvider.OPENROUTER)).thenReturn(false);

        LlmProviderStatus status = service.get();

        assertThat(status.isSelected()).isFalse();
        assertThat(status.selected()).isNull();
        assertThat(status.providers()).containsExactly(
                new LlmProviderStatus.ProviderAvailability(LlmProvider.LOCAL, true),
                new LlmProviderStatus.ProviderAvailability(LlmProvider.LOCAL_HTTP, false),
                new LlmProviderStatus.ProviderAvailability(LlmProvider.OPENROUTER, false));
    }

    @Test
    void statusReportsCurrentSelection() {
        Instant configuredAt = Instant.parse("2026-06-10T08:00:00Z");
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, configuredAt)));

        LlmProviderStatus status = service.get();

        assertThat(status.selected()).isEqualTo(LlmProvider.LOCAL);
        assertThat(status.configuredAt()).isEqualTo(configuredAt);
    }

    @Test
    void statusCarriesTelemetryWhenASourceIsWired() {
        // "Available" only says the engine answered a probe; the telemetry is what makes
        // "answers, but at 294s per call" visible before a run is created.
        com.quorumtrading.application.port.out.LlmTelemetryPort telemetryPort =
                org.mockito.Mockito.mock(com.quorumtrading.application.port.out.LlmTelemetryPort.class);
        var slowEngine = new com.quorumtrading.application.port.out.LlmTelemetryPort.ProviderTelemetry(
                LlmProvider.LOCAL_HTTP, 6, 5, 1, 2, 294_000L, 301_000L, 3,
                "router per-call cap fired", Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T09:55:00Z"));
        when(telemetryPort.telemetryFor(LlmProvider.LOCAL_HTTP)).thenReturn(Optional.of(slowEngine));
        when(telemetryPort.telemetryFor(LlmProvider.LOCAL)).thenReturn(Optional.empty());
        when(telemetryPort.telemetryFor(LlmProvider.OPENROUTER)).thenReturn(Optional.empty());
        when(preferencePort.current()).thenReturn(Optional.empty());

        LlmProviderSelectionService withTelemetry =
                new LlmProviderSelectionService(preferencePort, availabilityPort, telemetryPort);
        LlmProviderStatus status = withTelemetry.get();

        var localHttp = status.providers().stream()
                .filter(p -> p.provider() == LlmProvider.LOCAL_HTTP).findFirst().orElseThrow();
        assertThat(localHttp.telemetry()).isEqualTo(new LlmProviderStatus.ProviderTelemetry(
                6, 5, 1, 2, 294_000L, 301_000L, 3,
                "router per-call cap fired", Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T09:55:00Z")));
        var local = status.providers().stream()
                .filter(p -> p.provider() == LlmProvider.LOCAL).findFirst().orElseThrow();
        assertThat(local.telemetry()).isNull();
    }

    @Test
    void statusSurvivesATelemetrySourceThatThrows() {
        com.quorumtrading.application.port.out.LlmTelemetryPort telemetryPort =
                org.mockito.Mockito.mock(com.quorumtrading.application.port.out.LlmTelemetryPort.class);
        when(telemetryPort.telemetryFor(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("router not initialized"));
        when(preferencePort.current()).thenReturn(Optional.empty());

        LlmProviderSelectionService withTelemetry =
                new LlmProviderSelectionService(preferencePort, availabilityPort, telemetryPort);

        assertThat(withTelemetry.get().providers()).allSatisfy(p -> assertThat(p.telemetry()).isNull());
    }

    @Test
    void selectsAvailableLocalProvider() {
        when(availabilityPort.isAvailable(LlmProvider.LOCAL)).thenReturn(true);
        when(preferencePort.current())
                .thenReturn(Optional.of(new LlmProviderPreference(LlmProvider.LOCAL, Instant.now())));

        LlmProviderStatus status = service.select(
                new SelectLlmProviderUseCase.Command(LlmProvider.LOCAL, null));

        verify(preferencePort).select(LlmProvider.LOCAL);
        verify(preferencePort, never()).saveOpenRouterApiKey(org.mockito.ArgumentMatchers.anyString());
        assertThat(status.selected()).isEqualTo(LlmProvider.LOCAL);
    }

    @Test
    void rejectsUnavailableProviderWithActionableMessage() {
        when(availabilityPort.isAvailable(LlmProvider.LOCAL)).thenReturn(false);

        assertThatThrownBy(() -> service.select(new SelectLlmProviderUseCase.Command(LlmProvider.LOCAL, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claude");

        verify(preferencePort, never()).select(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void storesSubmittedKeyBeforeProbingOpenRouterAvailability() {
        when(availabilityPort.isAvailable(LlmProvider.OPENROUTER)).thenReturn(true);
        when(preferencePort.current()).thenReturn(
                Optional.of(new LlmProviderPreference(LlmProvider.OPENROUTER, Instant.now())));

        service.select(new SelectLlmProviderUseCase.Command(LlmProvider.OPENROUTER, "  sk-or-v1-abc  "));

        InOrder inOrder = inOrder(preferencePort, availabilityPort);
        inOrder.verify(preferencePort).saveOpenRouterApiKey("sk-or-v1-abc");
        inOrder.verify(availabilityPort).isAvailable(LlmProvider.OPENROUTER);
        inOrder.verify(preferencePort).select(LlmProvider.OPENROUTER);
    }

    @Test
    void rejectsOpenRouterWhenNoKeyUsable() {
        when(availabilityPort.isAvailable(LlmProvider.OPENROUTER)).thenReturn(false);

        assertThatThrownBy(() -> service.select(new SelectLlmProviderUseCase.Command(LlmProvider.OPENROUTER, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key");

        verify(preferencePort, never()).select(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresBlankSubmittedKey() {
        when(availabilityPort.isAvailable(LlmProvider.OPENROUTER)).thenReturn(true);
        when(preferencePort.current()).thenReturn(
                Optional.of(new LlmProviderPreference(LlmProvider.OPENROUTER, Instant.now())));

        service.select(new SelectLlmProviderUseCase.Command(LlmProvider.OPENROUTER, "   "));

        verify(preferencePort, never()).saveOpenRouterApiKey(org.mockito.ArgumentMatchers.anyString());
        verify(preferencePort).select(LlmProvider.OPENROUTER);
    }

    @Test
    void commandRequiresProvider() {
        assertThatThrownBy(() -> new SelectLlmProviderUseCase.Command(null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
