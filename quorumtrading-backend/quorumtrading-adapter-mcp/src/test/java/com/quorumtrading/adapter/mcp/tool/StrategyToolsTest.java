package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.application.usecase.command.DeleteStrategyCommand;
import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;
import com.quorumtrading.application.usecase.query.CalculateStrategyPerformanceQuery;
import com.quorumtrading.application.usecase.query.GetStrategyQuery;
import com.quorumtrading.application.usecase.query.ListStrategiesQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.StrategyPerformance;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class StrategyToolsTest {

    @Inject
    StrategyTools strategyTools;

    @InjectMock
    ListStrategiesUseCase listStrategiesUseCase;

    @InjectMock
    GetStrategyUseCase getStrategyUseCase;

    @InjectMock
    CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase;

    @InjectMock
    CreateStrategyUseCase createStrategyUseCase;

    @InjectMock
    DeleteStrategyUseCase deleteStrategyUseCase;

    @InjectMock
    UpdateStrategyUseCase updateStrategyUseCase;

    @InjectMock
    SimulationRepository simulationRepository;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID STRATEGY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach
    void setUp() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(OWNER_ID));
    }

    /**
     * A newcomer could not find their own work. The tool's only parameter was `limit`: limit=100
     * exceeded the MCP result size outright on a library of ~100 strategies, and every smaller page
     * came back hasMore:true with no cursor to follow. The use case has always supported a
     * case-insensitive name filter — only this tool never passed one through.
     */
    @Test
    void listStrategies_forwardsTheNameFilterToTheUseCase() throws Exception {
        when(listStrategiesUseCase.execute(any(ListStrategiesQuery.class))).thenReturn(List.of());

        strategyTools.listStrategies(Optional.empty(), Optional.of("  R8-  "));

        org.mockito.ArgumentCaptor<ListStrategiesQuery> captor =
            org.mockito.ArgumentCaptor.forClass(ListStrategiesQuery.class);
        verify(listStrategiesUseCase).execute(captor.capture());
        assertThat(captor.getValue().name()).contains("R8-");
    }

    @Test
    void listStrategies_treatsABlankNameFilterAsNoFilter() throws Exception {
        when(listStrategiesUseCase.execute(any(ListStrategiesQuery.class))).thenReturn(List.of());

        strategyTools.listStrategies(Optional.empty(), Optional.of("   "));

        org.mockito.ArgumentCaptor<ListStrategiesQuery> captor =
            org.mockito.ArgumentCaptor.forClass(ListStrategiesQuery.class);
        verify(listStrategiesUseCase).execute(captor.capture());
        assertThat(captor.getValue().name()).isEmpty();
    }

    @Test
    void listStrategies_returnsJsonArray() throws Exception {
        Strategy strategy = createTestStrategy();
        when(listStrategiesUseCase.execute(any(ListStrategiesQuery.class)))
            .thenReturn(List.of(strategy));
        Simulation sim1 = createTestSimulation(UUID.randomUUID());
        Simulation sim2 = createTestSimulation(UUID.randomUUID());
        Simulation sim3 = createTestSimulation(UUID.randomUUID());
        when(simulationRepository.findByStrategyId(any())).thenReturn(List.of(sim1, sim2, sim3));

        ToolResponse response = strategyTools.listStrategies(Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("hasMore").asBoolean()).isFalse();
        assertThat(json.get("items").get(0).get("id").asText()).isEqualTo(STRATEGY_ID.toString());
        assertThat(json.get("items").get(0).get("name").asText()).isEqualTo("Test Strategy");
        assertThat(json.get("items").get(0).get("consensusMethod").asText()).isEqualTo("MAJORITY");
        assertThat(json.get("items").get(0).get("cryptoTickers").get(0).asText()).isEqualTo("BTC");
        assertThat(json.get("items").get(0).get("simulationCount").asInt()).isEqualTo(3);
        // Content excluded from list view
        assertThat(json.get("items").get(0).get("content").isNull()).isTrue();
    }

    @Test
    void getStrategy_includesContent() throws Exception {
        Strategy strategy = createTestStrategy();
        when(getStrategyUseCase.execute(any(GetStrategyQuery.class)))
            .thenReturn(strategy);

        ToolResponse response = strategyTools.getStrategy(STRATEGY_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(STRATEGY_ID.toString());
        assertThat(json.get("content").asText()).contains("## Consensus Method");
    }

    @Test
    void getStrategyPerformance_returnsMetrics() throws Exception {
        Strategy strategy = createTestStrategy();
        when(getStrategyUseCase.execute(any(GetStrategyQuery.class))).thenReturn(strategy);
        StrategyPerformance perf = new StrategyPerformance(
            5, 3,
            new BigDecimal("12.5"),
            new BigDecimal("66.7"),
            new BigDecimal("1.2"),
            new BigDecimal("25.0"),
            new BigDecimal("-5.0"),
            List.of(new BigDecimal("10.0"), new BigDecimal("15.0"), new BigDecimal("12.5"))
        );
        when(calculateStrategyPerformanceUseCase.execute(any(CalculateStrategyPerformanceQuery.class)))
            .thenReturn(perf);

        ToolResponse response = strategyTools.getStrategyPerformance(STRATEGY_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("strategyId").asText()).isEqualTo(STRATEGY_ID.toString());
        assertThat(json.get("strategyName").asText()).isEqualTo("Test Strategy");
        assertThat(json.get("totalSimulations").asInt()).isEqualTo(5);
        assertThat(json.get("completedSimulations").asInt()).isEqualTo(3);
        assertThat(json.get("avgReturnPercent").asText()).isEqualTo("12.5");
        assertThat(json.get("winRatePercent").asText()).isEqualTo("66.7");
    }

    @Test
    void createStrategy_returnsIdAndMessage() throws Exception {
        StrategyId newId = new StrategyId(STRATEGY_ID);
        when(createStrategyUseCase.execute(any(CreateStrategyCommand.class)))
            .thenReturn(newId);

        ToolResponse response = strategyTools.createStrategy("New Strategy", "## Consensus Method\nMAJORITY", Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(STRATEGY_ID.toString());
        assertThat(json.get("message").asText()).contains("New Strategy");
        assertThat(json.get("message").asText()).contains("created");
    }

    @Test
    void deleteStrategy_returnsConfirmation() throws Exception {
        doNothing().when(deleteStrategyUseCase).execute(any(DeleteStrategyCommand.class));

        ToolResponse response = strategyTools.deleteStrategy(STRATEGY_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(STRATEGY_ID.toString());
        assertThat(json.get("message").asText()).contains("deleted");

        verify(deleteStrategyUseCase).execute(any(DeleteStrategyCommand.class));
    }

    @Test
    void getStrategy_returnsErrorForInvalidUuid() {
        ToolResponse response = strategyTools.getStrategy("not-a-uuid");

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid UUID string");
    }

    @Test
    void updateStrategy_withNameAndContent_returnsUpdatedStrategy() throws Exception {
        Strategy updated = new Strategy(
            new StrategyId(STRATEGY_ID), "Renamed Strategy",
            "---\nconsensus_method: MAJORITY\ncrypto_tickers:\n  - BTC\n---\n## Updated content",
            new UserId(OWNER_ID)
        );
        doNothing().when(updateStrategyUseCase).execute(any(UpdateStrategyCommand.class));
        when(getStrategyUseCase.execute(any(GetStrategyQuery.class))).thenReturn(updated);

        ToolResponse response = strategyTools.updateStrategy(
            STRATEGY_ID.toString(), Optional.of("Renamed Strategy"), Optional.empty(), Optional.of("---\nnew content\n---\n"));

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(STRATEGY_ID.toString());
        assertThat(json.get("name").asText()).isEqualTo("Renamed Strategy");
        verify(updateStrategyUseCase).execute(any(UpdateStrategyCommand.class));
    }

    @Test
    void updateStrategy_withNameOnly_returnsUpdatedStrategy() throws Exception {
        Strategy updated = new Strategy(
            new StrategyId(STRATEGY_ID), "New Name",
            "---\nconsensus_method: MAJORITY\ncrypto_tickers:\n  - BTC\n---\n## Consensus Method\nMAJORITY",
            new UserId(OWNER_ID)
        );
        doNothing().when(updateStrategyUseCase).execute(any(UpdateStrategyCommand.class));
        when(getStrategyUseCase.execute(any(GetStrategyQuery.class))).thenReturn(updated);

        ToolResponse response = strategyTools.updateStrategy(
            STRATEGY_ID.toString(), Optional.of("New Name"), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("name").asText()).isEqualTo("New Name");
        verify(updateStrategyUseCase).execute(any(UpdateStrategyCommand.class));
    }

    @Test
    void updateStrategy_invalidUuid_returnsError() {
        ToolResponse response = strategyTools.updateStrategy(
            "not-a-uuid", Optional.of("Name"), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid UUID string");
    }

    private Strategy createTestStrategy() {
        return new Strategy(
            new StrategyId(STRATEGY_ID),
            "Test Strategy",
            "---\nconsensus_method: MAJORITY\ncrypto_tickers:\n  - BTC\n---\n## Consensus Method\nMAJORITY\n\n## Evaluation Interval\n1h",
            new UserId(OWNER_ID)
        );
    }

    private Simulation createTestSimulation(UUID simId) {
        return new Simulation(
            new SimulationId(simId),
            "Sim " + simId.toString().substring(0, 8),
            new StrategyId(STRATEGY_ID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000"))
        );
    }
}
