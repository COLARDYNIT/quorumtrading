package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus.ProviderAvailability;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP tool tests for {@link LlmProviderTools} with the selection feature enabled
 * (self-host edition profile).
 */
@QuarkusTest
@TestProfile(LlmProviderToolsTest.SelectionEnabledProfile.class)
class LlmProviderToolsTest {

    public static class SelectionEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quorumtrading.llm.selection.enabled", "true");
        }
    }

    @Inject
    LlmProviderTools tools;

    @InjectMock
    GetLlmProviderStatusUseCase getStatusUseCase;

    @InjectMock
    SelectLlmProviderUseCase selectUseCase;

    @InjectMock
    McpUserContext mcpUserContext;

    @BeforeEach
    void authenticate() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(UUID.randomUUID()));
    }

    private static LlmProviderStatus unselected() {
        return new LlmProviderStatus(null, null, List.of(
                new ProviderAvailability(LlmProvider.LOCAL, true),
                new ProviderAvailability(LlmProvider.OPENROUTER, false)));
    }

    @Test
    void statusIncludesActionableHintWhenUnselected() {
        when(getStatusUseCase.get()).thenReturn(unselected());

        ToolResponse response = tools.getLlmStatus();

        assertThat(response.isError()).isFalse();
        String json = response.content().get(0).asText().text();
        assertThat(json).contains("\"selected\":null");
        assertThat(json).contains("choose_llm_provider");
        assertThat(json).contains("LOCAL");
    }

    @Test
    void statusRendersCallTelemetryForEnginesThatHaveBeenUsed() {
        // "Available" only says the engine answered a probe; the telemetry is what exposes an
        // engine that answers but takes 294s per call — BEFORE a backtest is created on it.
        when(getStatusUseCase.get()).thenReturn(new LlmProviderStatus(
                LlmProvider.LOCAL_HTTP, Instant.parse("2026-08-01T09:00:00Z"), List.of(
                new ProviderAvailability(LlmProvider.LOCAL, true),
                new ProviderAvailability(LlmProvider.LOCAL_HTTP, true,
                        new LlmProviderStatus.ProviderTelemetry(
                                6, 5, 1, 2, 294_000L, 301_000L, 3,
                                "router per-call cap fired",
                                Instant.parse("2026-08-01T10:00:00Z"),
                                Instant.parse("2026-08-01T09:55:00Z"))),
                new ProviderAvailability(LlmProvider.OPENROUTER, false))));

        ToolResponse response = tools.getLlmStatus();

        assertThat(response.isError()).isFalse();
        String json = response.content().get(0).asText().text();
        assertThat(json).contains("\"avgLatencyMs\":294000");
        assertThat(json).contains("\"inFlight\":3");
        assertThat(json).contains("\"timeouts\":2");
        assertThat(json).contains("router per-call cap fired");
        // Engines never called carry no telemetry block — absence must stay absence,
        // not a block of zeros that reads as a measured healthy engine.
        assertThat(json.split("\"telemetry\"")).hasSize(2);
    }

    @Test
    void chooseSelectsProviderAndConfirms() {
        when(selectUseCase.select(any())).thenReturn(new LlmProviderStatus(
                LlmProvider.LOCAL, Instant.parse("2026-06-10T08:00:00Z"),
                List.of(new ProviderAvailability(LlmProvider.LOCAL, true),
                        new ProviderAvailability(LlmProvider.OPENROUTER, false))));

        ToolResponse response = tools.chooseLlmProvider("local", null);

        assertThat(response.isError()).isFalse();
        ArgumentCaptor<SelectLlmProviderUseCase.Command> captor =
                ArgumentCaptor.forClass(SelectLlmProviderUseCase.Command.class);
        verify(selectUseCase).select(captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo(LlmProvider.LOCAL);
        assertThat(response.content().get(0).asText().text()).contains("no restart");
    }

    @Test
    void choosePassesApiKeyThrough() {
        when(selectUseCase.select(any())).thenReturn(new LlmProviderStatus(
                LlmProvider.OPENROUTER, Instant.now(),
                List.of(new ProviderAvailability(LlmProvider.LOCAL, false),
                        new ProviderAvailability(LlmProvider.OPENROUTER, true))));

        tools.chooseLlmProvider("OPENROUTER", "sk-or-v1-abc");

        ArgumentCaptor<SelectLlmProviderUseCase.Command> captor =
                ArgumentCaptor.forClass(SelectLlmProviderUseCase.Command.class);
        verify(selectUseCase).select(captor.capture());
        assertThat(captor.getValue().openRouterApiKey()).isEqualTo("sk-or-v1-abc");
    }

    @Test
    void chooseRejectsUnknownProvider() {
        ToolResponse response = tools.chooseLlmProvider("SKYNET", null);

        assertThat(response.isError()).isTrue();
        verify(selectUseCase, never()).select(any());
    }

    @Test
    void chooseSurfacesUnavailabilityAsToolError() {
        when(selectUseCase.select(any()))
                .thenThrow(new IllegalArgumentException("The local provider is not available"));

        ToolResponse response = tools.chooseLlmProvider("LOCAL", null);

        assertThat(response.isError()).isTrue();
    }

    @Test
    void toolsRequireAuthentication() {
        when(mcpUserContext.getUserId())
                .thenThrow(new SecurityException("Missing or invalid Authorization header"));

        assertThat(tools.getLlmStatus().isError()).isTrue();
        assertThat(tools.chooseLlmProvider("LOCAL", null).isError()).isTrue();
        verify(selectUseCase, never()).select(any());
    }
}
