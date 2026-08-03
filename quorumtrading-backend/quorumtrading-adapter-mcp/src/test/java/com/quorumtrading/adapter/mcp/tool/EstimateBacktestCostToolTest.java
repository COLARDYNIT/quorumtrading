package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.EstimateBacktestCostUseCase;
import com.quorumtrading.model.valueobject.CostEstimate;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pricing a backtest without running it.
 *
 * <p>{@code create_simulation}'s own description tells the caller: "To price a backtest without
 * paying for it, ask about the cost first rather than creating one to find out." The setup guide
 * repeats it. But no MCP tool could answer — {@code POST /api/v1/config/cost-estimate} exists and
 * is well built, and is reachable only over REST. An agent driving this platform had to create a
 * simulation to discover what creating it would cost, which is the exact thing the instruction
 * tells it not to do. Creating a backtest starts it, so the discovery is not free.</p>
 */
@QuarkusTest
class EstimateBacktestCostToolTest {

    @Inject SimulationTools simulationTools;

    @InjectMock EstimateBacktestCostUseCase estimateBacktestCostUseCase;
    @InjectMock McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID STRATEGY_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private JsonNode textOf(ToolResponse response) throws Exception {
        String text = ((io.quarkiverse.mcp.server.TextContent) response.content().get(0)).text();
        return objectMapper.readTree(text);
    }

    @Test
    @DisplayName("prices a backtest from its dates without creating one — in dollars AND time")
    void pricesABacktestWithoutCreatingIt() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        CostEstimate estimate = CostEstimate.of(
                29, 29L, 1, 4, 116L,
                new BigDecimal("0.0002"), new BigDecimal("0.0232"),
                "anthropic/claude-sonnet-5", "Claude Sonnet 5");
        when(estimateBacktestCostUseCase.estimateForSimulationWithTime(
                any(StrategyId.class), any(Instant.class), any(Instant.class), any(UserId.class)))
                .thenReturn(new EstimateBacktestCostUseCase.TimedCostEstimate(
                        estimate, 3480L,
                        "116 calls x 30000ms — LOCAL_HTTP's observed average over 5 completed call(s)"));

        ToolResponse response = simulationTools.estimateBacktestCost(
                STRATEGY_ID.toString(), "2026-01-01", "2026-07-25");

        JsonNode json = textOf(response);
        assertThat(json.get("totalLLMCalls").asLong()).isEqualTo(116L);
        assertThat(json.get("estimatedCost").asText()).contains("0.0232");
        // The dollar figure is half the answer — on a free local engine it reads $0.00 while
        // the run costs hours, so the projection and its basis must reach the caller.
        assertThat(json.get("estimatedWallClockSeconds").asLong()).isEqualTo(3480L);
        assertThat(json.get("timeBasis").asText()).contains("observed average");

        // The dates must reach the date-aware overload — the day-count form cannot know how many
        // trading days a span holds, because that depends on which weekday it starts on.
        verify(estimateBacktestCostUseCase).estimateForSimulationWithTime(
                eq(new StrategyId(STRATEGY_ID)),
                eq(Instant.parse("2026-01-01T00:00:00Z")),
                eq(Instant.parse("2026-07-25T00:00:00Z")),
                eq(new UserId(USER_ID)));
    }

    @Test
    @DisplayName("refuses a range that ends before it starts, rather than quoting nonsense")
    void refusesAnInvertedRange() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));

        ToolResponse response = simulationTools.estimateBacktestCost(
                STRATEGY_ID.toString(), "2026-07-25", "2026-01-01");

        assertThat(response.isError()).isTrue();
    }
}
