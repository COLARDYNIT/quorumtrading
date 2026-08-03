package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase;
import com.quorumtrading.application.usecase.marketdata.MarketDataStatus;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * The market-data counterpart to {@code get_llm_status}. Its whole job is to make a configuration
 * problem legible as a configuration problem, so the assertions are about what the payload lets a
 * newcomer conclude — not about its shape.
 */
@QuarkusTest
class MarketDataStatusToolsTest {

    @Inject
    MarketDataStatusTools tools;

    @InjectMock
    GetMarketDataStatusUseCase getMarketDataStatusUseCase;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void authenticated() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(UUID.randomUUID()));
    }

    private static MarketDataStatus.ProviderStatus provider(
            String name, boolean enabled, boolean credentialed, MarketDataStatus.Probe probe) {
        return new MarketDataStatus.ProviderStatus(
            name, enabled, credentialed, List.of("STOCK"), List.of("BACKFILL", "CANDLES"), probe);
    }

    private JsonNode invoke(MarketDataStatus status) throws Exception {
        when(getMarketDataStatusUseCase.get(anyBoolean())).thenReturn(status);
        ToolResponse response = tools.getMarketDataStatus(java.util.Optional.of(true));
        assertThat(response.isError()).isFalse();
        return objectMapper.readTree(response.firstContent().asText().text());
    }

    @Test
    void namesASwitchedOffProviderAsSwitchedOff() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(true, false,
            List.of(provider("saxo", false, true, null))));

        JsonNode saxo = json.get("providers").get(0);
        assertThat(saxo.get("enabled").asBoolean()).isFalse();
        assertThat(saxo.get("usable").asBoolean()).isFalse();
        assertThat(saxo.get("detail").asText()).contains("Switched off");
    }

    @Test
    void namesAMissingKeyAsAMissingKey() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(true, false,
            List.of(provider("alpaca", true, false, null))));

        assertThat(json.get("providers").get(0).get("detail").asText())
            .contains("no credentials")
            .contains("restart the backend");
    }

    @Test
    void reportsTheLiveProbeResult() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(false, true,
            List.of(provider("alpaca", true, true,
                new MarketDataStatus.Probe("AAPL", true, "Returned 7 event(s) for AAPL over the last 10 days.")))));

        JsonNode probe = json.get("providers").get(0).get("probe");
        assertThat(probe.get("ok").asBoolean()).isTrue();
        assertThat(probe.get("symbol").asText()).isEqualTo("AAPL");
    }

    @Test
    void omitsTheProbeBlockWhenNothingWasProbed() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(true, true,
            List.of(provider("kraken", true, true, null))));

        assertThat(json.get("providers").get(0).has("probe"))
            .as("an absent probe must not be renderable as a failed one")
            .isFalse();
    }

    /**
     * The cold-start case: every backfill returns nothing and the failure looks like a coverage
     * problem, so the user goes looking for a different ticker. Say plainly that it is not.
     */
    @Test
    void aDeploymentWithNoUsableProviderSaysItIsAConfigurationProblem() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(false, false,
            List.of(provider("alpaca", true, false, null))));

        assertThat(json.get("hint").asText())
            .contains("configuration problem")
            .contains("do not go looking for a different ticker");
    }

    @Test
    void cryptoOnlyDeploymentSaysStockBackfillsWillReturnNothing() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(true, false,
            List.of(provider("kraken", true, true, null))));

        assertThat(json.get("cryptoUsable").asBoolean()).isTrue();
        assertThat(json.get("stockUsable").asBoolean()).isFalse();
        assertThat(json.get("hint").asText()).contains("No usable stock provider");
    }

    @Test
    void aFullyConfiguredDeploymentSaysSo() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(true, true,
            List.of(provider("alpaca", true, true, null))));

        assertThat(json.get("hint").asText()).contains("Both crypto and stock");
    }

    @Test
    void stockOnlyDeploymentSaysSo() throws Exception {
        JsonNode json = invoke(new MarketDataStatus(false, true,
            List.of(provider("alpaca", true, true, null))));

        assertThat(json.get("hint").asText()).contains("No usable crypto provider");
    }

    @Test
    void withoutAValidTokenItRefuses() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = tools.getMarketDataStatus(java.util.Optional.empty());

        assertThat(response.isError()).isTrue();
    }
}
