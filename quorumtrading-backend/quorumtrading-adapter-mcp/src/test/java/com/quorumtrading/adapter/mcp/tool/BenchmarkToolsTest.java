package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase;
import com.quorumtrading.application.usecase.CreateBenchmarkUseCase;
import com.quorumtrading.application.usecase.DeleteBenchmarkUseCase;
import com.quorumtrading.application.usecase.command.CreateBenchmarkCommand;
import com.quorumtrading.application.usecase.command.DeleteBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.BenchmarkLeaderboard;
import com.quorumtrading.application.usecase.CalculateBenchmarkOverlaysUseCase;
import com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse;
import com.quorumtrading.model.valueobject.BuyAndHoldDataPoint;
import com.quorumtrading.model.valueobject.OverlayResult;
import com.quorumtrading.application.usecase.query.BuildBenchmarkLeaderboardQuery;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.*;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class BenchmarkToolsTest {

    @Inject
    BenchmarkTools benchmarkTools;

    @InjectMock
    CreateBenchmarkUseCase createBenchmarkUseCase;

    @InjectMock
    com.quorumtrading.application.usecase.ComposeBenchmarkUseCase composeBenchmarkUseCase;

    @InjectMock
    DeleteBenchmarkUseCase deleteBenchmarkUseCase;

    @InjectMock
    BenchmarkRepository benchmarkRepository;

    @InjectMock
    SimulationRepository simulationRepository;

    @InjectMock
    StrategyRepository strategyRepository;

    @InjectMock
    PortfolioRepository portfolioRepository;

    @InjectMock
    BrokerAccountRepository brokerAccountRepository;

    @InjectMock
    BuildBenchmarkLeaderboardUseCase buildBenchmarkLeaderboardUseCase;

    @InjectMock
    CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID STRATEGY_ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STRATEGY_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BENCHMARK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SIM_ID_1 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SIM_ID_2 = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void createBenchmark_createsWithStrategiesAndReturnsBenchmarkId() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(createBenchmarkUseCase.execute(any(CreateBenchmarkCommand.class)))
            .thenReturn(new CreateBenchmarkResponse(
                BENCHMARK_ID,
                List.of(SIM_ID_1, SIM_ID_2),
                "Backtest: Jan 1, 2024 - Dec 31, 2024"
            ));

        ToolResponse response = benchmarkTools.createBenchmark(
            STRATEGY_ID_1 + "," + STRATEGY_ID_2,
            "2024-01-01T00:00:00Z",
            Optional.of("2024-12-31T23:59:59Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("true")
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(BENCHMARK_ID.toString());
        assertThat(json.get("uiUrl").asText()).isEqualTo("http://localhost:3000/benchmarks/" + BENCHMARK_ID);
        assertThat(json.get("message").asText()).contains("2 simulations");
        assertThat(json.has("simulationIds")).isTrue();
        assertThat(json.get("simulationIds").size()).isEqualTo(2);
        assertThat(json.get("simulationCount").asInt()).isEqualTo(2);

        verify(createBenchmarkUseCase).execute(any(CreateBenchmarkCommand.class));
    }

    /**
     * A single strategy is a supported case, not an error: there is nothing to rank, so the service
     * starts the simulation and creates no benchmark, returning a null benchmarkId. Dereferencing
     * it raised a raw Java NullPointerException at the MCP boundary — while the simulation it had
     * already started ran on, billing agents, with the caller holding no id for it. Two independent
     * newcomer runs hit it on the most natural first call in the benchmarks workflow.
     */
    @Test
    void createBenchmark_singleStrategy_reportsTheStandaloneRunInsteadOfThrowing() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(createBenchmarkUseCase.execute(any(CreateBenchmarkCommand.class)))
            .thenReturn(new CreateBenchmarkResponse(null, List.of(SIM_ID_1), null));

        ToolResponse response = benchmarkTools.createBenchmark(
            STRATEGY_ID_1.toString(),
            "2024-01-01T00:00:00Z",
            Optional.of("2024-01-08T00:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("true")
        );

        assertThat(response.isError()).as("a supported case must not surface as an error").isFalse();
        JsonNode json = objectMapper.readTree(response.firstContent().asText().text());

        assertThat(json.get("id").isNull()).as("no benchmark was created").isTrue();
        assertThat(json.has("uiUrl")).as("no benchmark page exists to link to").isFalse();
        // The caller must leave holding the id of the run that is now spending their budget.
        assertThat(json.get("simulationIds").get(0).asText()).isEqualTo(SIM_ID_1.toString());
        assertThat(json.get("message").asText())
            .contains("no benchmark was created")
            .contains("compose_benchmark");
    }

    @Test
    void composeBenchmark_reportsTheBenchmarkAndItsPage() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(composeBenchmarkUseCase.execute(any(com.quorumtrading.application.usecase.command.ComposeBenchmarkCommand.class)))
            .thenReturn(new com.quorumtrading.application.usecase.dto.ComposeBenchmarkResponse(
                BENCHMARK_ID, "Composed", 2, 2));

        ToolResponse response = benchmarkTools.composeBenchmark(
            SIM_ID_1 + "," + SIM_ID_2, Optional.empty(), Optional.of("Composed"));

        assertThat(response.isError()).isFalse();
        JsonNode json = objectMapper.readTree(response.firstContent().asText().text());
        assertThat(json.get("benchmarkId").asText()).isEqualTo(BENCHMARK_ID.toString());
        assertThat(json.get("uiUrl").asText()).isEqualTo("http://localhost:3000/benchmarks/" + BENCHMARK_ID);
        assertThat(json.get("totalSimulations").asInt()).isEqualTo(2);
        assertThat(json.get("addedSimulations").asInt()).isEqualTo(2);
    }

    @Test
    void createBenchmark_returnsAuthErrorWhenNoToken() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = benchmarkTools.createBenchmark(
            STRATEGY_ID_1 + "," + STRATEGY_ID_2,
            "2024-01-01T00:00:00Z",
            Optional.of("2024-12-31T23:59:59Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("true")
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Authentication required");
    }

    @Test
    void createBenchmark_returnsErrorForInvalidStrategyId() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(createBenchmarkUseCase.execute(any(CreateBenchmarkCommand.class)))
            .thenThrow(new IllegalArgumentException("Strategy not found: " + STRATEGY_ID_1));

        ToolResponse response = benchmarkTools.createBenchmark(
            STRATEGY_ID_1.toString(),
            "2024-01-01T00:00:00Z",
            Optional.of("2024-12-31T23:59:59Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("true")
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Strategy not found");
    }

    @Test
    void createBenchmark_longDuration_withoutCostFlag_returnsError() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));

        ToolResponse response = benchmarkTools.createBenchmark(
            STRATEGY_ID_1 + "," + STRATEGY_ID_2,
            "2024-01-01T00:00:00Z",
            Optional.of("2024-03-02T00:00:00Z"),   // 61 days > 31
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("userInformedOfCost");
        assertThat(errorText).containsIgnoringCase("cost");
    }

    @Test
    void createBenchmark_longDuration_withCostFlagTrue_succeeds() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(createBenchmarkUseCase.execute(any(CreateBenchmarkCommand.class)))
            .thenReturn(new CreateBenchmarkResponse(
                BENCHMARK_ID,
                List.of(SIM_ID_1, SIM_ID_2),
                "Backtest: Jan 1, 2024 - Mar 2, 2024"
            ));

        ToolResponse response = benchmarkTools.createBenchmark(
            STRATEGY_ID_1 + "," + STRATEGY_ID_2,
            "2024-01-01T00:00:00Z",
            Optional.of("2024-03-02T00:00:00Z"),   // 61 days > 31
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("true")
        );

        assertThat(response.isError()).isFalse();
        verify(createBenchmarkUseCase).execute(any(CreateBenchmarkCommand.class));
    }

    @Test
    void createBenchmark_liveMode_noCostFlagNeeded() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(createBenchmarkUseCase.execute(any(CreateBenchmarkCommand.class)))
            .thenReturn(new CreateBenchmarkResponse(
                BENCHMARK_ID,
                List.of(SIM_ID_1, SIM_ID_2),
                "Live benchmark"
            ));

        // Live mode: no endDate → skip cost check
        ToolResponse response = benchmarkTools.createBenchmark(
            STRATEGY_ID_1 + "," + STRATEGY_ID_2,
            "2024-01-01T00:00:00Z",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        verify(createBenchmarkUseCase).execute(any(CreateBenchmarkCommand.class));
    }

    @Test
    void listBenchmarks_returnsEmptyListWhenNoBenchmarks() throws Exception {
        when(benchmarkRepository.findAllSorted(21)).thenReturn(List.of());

        ToolResponse response = benchmarkTools.listBenchmarks(Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(0);
        assertThat(json.get("items").size()).isEqualTo(0);
        assertThat(json.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    void listBenchmarks_returnsBenchmarksWithSimulationIds() throws Exception {
        BenchmarkId benchmarkId = new BenchmarkId(BENCHMARK_ID);
        Benchmark benchmark = new Benchmark(
            benchmarkId,
            "Test Benchmark",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-12-31T23:59:59Z"),
            Instant.now()
        );
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(benchmarkRepository.findByOwnerId(new UserId(USER_ID), 21)).thenReturn(List.of(benchmark));

        Simulation sim1 = createSimulation(SIM_ID_1, STRATEGY_ID_1, benchmarkId);
        Simulation sim2 = createSimulation(SIM_ID_2, STRATEGY_ID_2, benchmarkId);
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(sim1, sim2));

        // Mock strategy lookups for strategy names
        Strategy strategy1 = createStrategy(STRATEGY_ID_1, "Momentum Strategy");
        Strategy strategy2 = createStrategy(STRATEGY_ID_2, "Mean Reversion");
        when(strategyRepository.findById(new StrategyId(STRATEGY_ID_1))).thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(STRATEGY_ID_2))).thenReturn(Optional.of(strategy2));

        ToolResponse response = benchmarkTools.listBenchmarks(Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);

        JsonNode item = json.get("items").get(0);
        assertThat(item.get("id").asText()).isEqualTo(BENCHMARK_ID.toString());
        assertThat(item.get("name").asText()).isEqualTo("Test Benchmark");
        assertThat(item.get("isLive").asBoolean()).isFalse();
        assertThat(item.get("simulationIds").size()).isEqualTo(2);
        assertThat(item.get("strategyNames").size()).isEqualTo(2);
        assertThat(item.get("strategyNames").get(0).asText()).isEqualTo("Momentum Strategy");
        assertThat(item.get("strategyNames").get(1).asText()).isEqualTo("Mean Reversion");
        assertThat(item.get("totalSimulations").asInt()).isEqualTo(2);
        assertThat(item.get("completedSimulations").asInt()).isEqualTo(0);
    }

    @Test
    void getBenchmark_rejectsBenchmarkOwnedByAnotherUser() throws Exception {
        // get_benchmark was a bare findById with no viewer at all. Because /mcp is permitted at
        // the HTTP layer, that made any user's benchmark readable without even a token.
        BenchmarkId benchmarkId = new BenchmarkId(BENCHMARK_ID);
        UserId otherOwner = new UserId(UUID.fromString("88888888-8888-8888-8888-888888888888"));
        Benchmark someoneElses = new Benchmark(
            benchmarkId,
            "Not Yours",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-12-31T23:59:59Z"),
            Instant.now(),
            otherOwner
        );
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(someoneElses));

        ToolResponse response = benchmarkTools.getBenchmark(BENCHMARK_ID.toString());

        assertThat(response.isError()).isTrue();
    }

    @Test
    void getBenchmark_returnsBenchmarkWithRankedEntries() throws Exception {
        BenchmarkId benchmarkId = new BenchmarkId(BENCHMARK_ID);
        Benchmark benchmark = new Benchmark(
            benchmarkId,
            "Test Benchmark",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-12-31T23:59:59Z"),
            Instant.now()
        );
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));

        Simulation sim1 = createSimulation(SIM_ID_1, STRATEGY_ID_1, benchmarkId);
        Simulation sim2 = createSimulation(SIM_ID_2, STRATEGY_ID_2, benchmarkId);
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(sim1, sim2));

        // Strategy names, for the benchmark summary block
        Strategy strategy1 = createStrategy(STRATEGY_ID_1, "Momentum Strategy");
        Strategy strategy2 = createStrategy(STRATEGY_ID_2, "Mean Reversion");
        when(strategyRepository.findById(new StrategyId(STRATEGY_ID_1))).thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(STRATEGY_ID_2))).thenReturn(Optional.of(strategy2));

        // Ranking and returns come from the shared use case — the tool no longer computes
        // them, which is what stopped it from disagreeing with the REST leaderboard.
        // sim1 grew to 120k (+20%), sim2 dropped to 90k (-10%).
        when(buildBenchmarkLeaderboardUseCase.execute(any(BuildBenchmarkLeaderboardQuery.class)))
            .thenReturn(new BenchmarkLeaderboard(List.of(
                leaderboardEntry(1, 0, SIM_ID_1, "Momentum Strategy",
                    new BigDecimal("120000.00"), new BigDecimal("20.0000")),
                leaderboardEntry(2, 1, SIM_ID_2, "Mean Reversion",
                    new BigDecimal("90000.00"), new BigDecimal("-10.0000")))));

        ToolResponse response = benchmarkTools.getBenchmark(BENCHMARK_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);

        // The link sits at the TOP level, like every other tool's response — a client must
        // not have to know this composite also nests it under benchmark.uiUrl.
        assertThat(json.get("uiUrl").asText())
            .isEqualTo("http://localhost:3000/benchmarks/" + BENCHMARK_ID);

        // Benchmark details
        JsonNode benchmarkNode = json.get("benchmark");
        assertThat(benchmarkNode.get("id").asText()).isEqualTo(BENCHMARK_ID.toString());
        assertThat(benchmarkNode.get("name").asText()).isEqualTo("Test Benchmark");

        // Entries sorted by return descending
        JsonNode entries = json.get("entries");
        assertThat(entries.size()).isEqualTo(2);
        assertThat(entries.get(0).get("strategyName").asText()).isEqualTo("Momentum Strategy");
        assertThat(new BigDecimal(entries.get(0).get("returnPercent").asText())).isPositive();
        assertThat(entries.get(0).get("initialCash")).isNotNull();
        assertThat(entries.get(0).get("tradingMode").asText()).isEqualTo("BACKTEST");
        assertThat(entries.get(1).get("strategyName").asText()).isEqualTo("Mean Reversion");
        assertThat(new BigDecimal(entries.get(1).get("returnPercent").asText())).isNegative();
        assertThat(entries.get(1).get("initialCash")).isNotNull();
        assertThat(entries.get(1).get("tradingMode").asText()).isEqualTo("BACKTEST");
    }

    @Test
    void getBenchmark_returnsErrorWhenNotFound() {
        when(benchmarkRepository.findById(any(BenchmarkId.class))).thenReturn(Optional.empty());

        ToolResponse response = benchmarkTools.getBenchmark(BENCHMARK_ID.toString());

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Benchmark not found");
    }

    @Test
    void deleteBenchmark_deletesSuccessfully() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));

        ToolResponse response = benchmarkTools.deleteBenchmark(BENCHMARK_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(BENCHMARK_ID.toString());
        assertThat(json.get("message").asText()).contains("deleted");

        verify(deleteBenchmarkUseCase).execute(any(DeleteBenchmarkCommand.class));
    }

    @Test
    void deleteBenchmark_returnsErrorWhenNotFound() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        org.mockito.Mockito.doThrow(new NoSuchElementException("Benchmark not found: " + BENCHMARK_ID))
            .when(deleteBenchmarkUseCase).execute(any(DeleteBenchmarkCommand.class));

        ToolResponse response = benchmarkTools.deleteBenchmark(BENCHMARK_ID.toString());

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Benchmark not found");
    }

    // --- Test helpers ---

    private Simulation createSimulation(UUID simId, UUID strategyId, BenchmarkId benchmarkId) {
        return new Simulation(
            new SimulationId(simId),
            "Sim " + simId.toString().substring(0, 8),
            new StrategyId(strategyId),
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-12-31T23:59:59Z"),
            Money.usd(new BigDecimal("100000.00")),
            benchmarkId,
            null,
            null
        );
    }

    private Strategy createStrategy(UUID strategyId, String name) {
        return new Strategy(
            new StrategyId(strategyId),
            name,
            "---\nconsensus_method: MAJORITY\n---\n# " + name,
            new UserId(USER_ID)
        );
    }

    private BenchmarkLeaderboard.Entry leaderboardEntry(int rank, int ordinal, UUID simId,
                                                        String strategyName, BigDecimal currentValue,
                                                        BigDecimal returnPercent) {
        return new BenchmarkLeaderboard.Entry(
            rank, ordinal, new SimulationId(simId),
            "Sim " + simId.toString().substring(0, 8), strategyName, "BACKTEST",
            Money.usd(new BigDecimal("100000.00")), Money.usd(currentValue), true,
            returnPercent, SimulationStatus.COMPLETED, 7L);
    }

    private PortfolioSnapshot createSnapshot(UUID simId, BigDecimal totalValue) {
        Money total = new Money(totalValue, Money.Currency.USD);
        return new PortfolioSnapshot(
            new SimulationId(simId),
            Instant.now(),
            Map.of(),
            total,
            total
        );
    }

    /**
     * The overlay answers "did the council beat just buying the thing?", so the caller needs the
     * curve's value, not merely its length. The tool used to return only returnPercent and a point
     * count — and returnPercent is scale-invariant, so passing initialCapital and omitting it
     * produced byte-identical output. The documented parameter was unobservable, which reads as a
     * no-op, and there was no number to put beside the run's own currentValue.
     */
    @Test
    void getBenchmarkOverlays_reportsTheCurveEndpointsNotJustItsLength() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(calculateBenchmarkOverlaysUseCase.execute(any())).thenReturn(List.of(
            OverlayResult.ready("BTC", "CRYPTO",
                List.of(
                    new BuyAndHoldDataPoint(
                        java.time.Instant.parse("2026-06-01T00:00:00Z"), new java.math.BigDecimal("10000.00")),
                    new BuyAndHoldDataPoint(
                        java.time.Instant.parse("2026-07-13T00:00:00Z"), new java.math.BigDecimal("8731.00"))),
                new java.math.BigDecimal("-12.6900"))));

        ToolResponse response = benchmarkTools.getBenchmarkOverlays(
            BENCHMARK_ID.toString(), "BTC", Optional.empty());

        assertThat(response.isError()).isFalse();
        JsonNode overlay = objectMapper.readTree(response.firstContent().asText().text()).get(0);

        assertThat(overlay.get("returnPercent").asText()).isEqualTo("-12.6900");
        assertThat(overlay.get("dataPointCount").asInt()).isEqualTo(2);
        assertThat(overlay.get("initialValue").asText()).isEqualTo("10000.00");
        assertThat(overlay.get("finalValue").asText()).isEqualTo("8731.00");
        assertThat(overlay.get("firstPointAt").asText()).isEqualTo("2026-06-01T00:00:00Z");
        assertThat(overlay.get("lastPointAt").asText()).isEqualTo("2026-07-13T00:00:00Z");
        assertThat(overlay.get("fullSeries").asText())
            .as("the full series is too large for MCP; the caller must be told where it lives")
            .contains("/api/v1/benchmarks/" + BENCHMARK_ID + "/overlays");
    }

    @Test
    void getBenchmarkOverlays_reportsNoDataWithoutInventingACurve() throws Exception {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        when(calculateBenchmarkOverlaysUseCase.execute(any())).thenReturn(List.of(
            OverlayResult.noData("ZZZQQQ", "STOCK", "No price bars stored for this ticker")));

        JsonNode overlay = objectMapper.readTree(benchmarkTools.getBenchmarkOverlays(
            BENCHMARK_ID.toString(), "ZZZQQQ", Optional.empty())
            .firstContent().asText().text()).get(0);

        assertThat(overlay.get("status").asText()).isEqualTo("NO_DATA");
        assertThat(overlay.get("dataPointCount").asInt()).isZero();
        assertThat(overlay.has("initialValue")).as("no curve, so no endpoints to report").isFalse();
        assertThat(overlay.get("errorMessage").asText()).contains("No price bars");
    }
}
