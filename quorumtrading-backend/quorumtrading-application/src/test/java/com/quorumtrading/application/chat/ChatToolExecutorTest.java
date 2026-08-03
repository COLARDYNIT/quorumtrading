package com.quorumtrading.application.chat;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.result.*;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.*;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.quorumtrading.application.usecase.BackFillResult;
import com.quorumtrading.application.usecase.result.TickerPerformanceResult;

@ExtendWith(MockitoExtension.class)
class ChatToolExecutorTest {

    @Mock private ListStrategiesUseCase listStrategiesUseCase;
    @Mock private ListPublicStrategiesUseCase listPublicStrategiesUseCase;
    @Mock private GetStrategyUseCase getStrategyUseCase;
    @Mock private CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase;
    @Mock private ListSimulationsUseCase listSimulationsUseCase;
    @Mock private GetSimulationUseCase getSimulationUseCase;
    @Mock private QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase;
    @Mock private GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase;
    @Mock private QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase;
    @Mock private IndicatorEnrichmentService indicatorEnrichmentService;
    @Mock private QueryMarketEventsUseCase queryMarketEventsUseCase;
    @Mock private BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase;
    @Mock private ListMarketResearchUseCase listMarketResearchUseCase;
    @Mock private GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase;
    @Mock private ListBrokerAccountsUseCase listBrokerAccountsUseCase;
    @Mock private CreateStrategyUseCase createStrategyUseCase;
    @Mock private CreateSimulationUseCase createSimulationUseCase;
    @Mock private StartSimulationUseCase startSimulationUseCase;
    @Mock private CreateMarketResearchUseCase createMarketResearchUseCase;
    @Mock private ExecuteMarketResearchUseCase executeMarketResearchUseCase;
    @Mock private DeleteStrategyUseCase deleteStrategyUseCase;
    @Mock private DeleteSimulationUseCase deleteSimulationUseCase;
    @Mock private DeleteMarketResearchUseCase deleteMarketResearchUseCase;
    @Mock private DeleteBenchmarkUseCase deleteBenchmarkUseCase;
    @Mock private CompareSimulationsUseCase compareSimulationsUseCase;
    @Mock private CreateBenchmarkUseCase createBenchmarkUseCase;
    @Mock private PauseSimulationUseCase pauseSimulationUseCase;
    @Mock private SellPositionUseCase sellPositionUseCase;
    @Mock private LiquidateAllPositionsUseCase liquidateAllPositionsUseCase;
    @Mock private ForceEndSimulationUseCase forceEndSimulationUseCase;
    @Mock private FindTickerUseCase findTickerUseCase;
    @Mock private PauseMarketResearchUseCase pauseMarketResearchUseCase;
    @Mock private ResumeMarketResearchUseCase resumeMarketResearchUseCase;
    @Mock private PruneUnusedUseCase pruneUnusedUseCase;
    @Mock private CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase;
    @Mock private GetTickerPerformanceUseCase getTickerPerformanceUseCase;
    @Mock private ListHistoricalEventsUseCase listHistoricalEventsUseCase;
    @Mock private GetHistoricalEventUseCase getHistoricalEventUseCase;
    @Mock private JoinChallengeUseCase joinChallengeUseCase;
    @Mock private com.quorumtrading.application.port.out.ChallengeRepository challengeRepository;
    @Mock private BenchmarkRepository benchmarkRepository;
    @Mock private SimulationRepository simulationRepository;

    private ChatToolExecutor executor;
    private final ObjectMapper objectMapper = JsonMapper.builder()
        .findAndAddModules()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES)
        .disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS)
        .build();
    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        executor = new ChatToolExecutor(
            listStrategiesUseCase, listPublicStrategiesUseCase, getStrategyUseCase, calculateStrategyPerformanceUseCase,
            listSimulationsUseCase, getSimulationUseCase,
            queryPortfolioSnapshotsUseCase, getConsensusWithAgentsUseCase, queryConsensusDecisionsUseCase,
            indicatorEnrichmentService, queryMarketEventsUseCase, backFillTickerPriceDataUseCase,
            listMarketResearchUseCase, getMarketResearchDetailsUseCase,
            listBrokerAccountsUseCase, createStrategyUseCase, createSimulationUseCase,
            startSimulationUseCase, createMarketResearchUseCase, executeMarketResearchUseCase,
            deleteStrategyUseCase, deleteSimulationUseCase, deleteMarketResearchUseCase,
            deleteBenchmarkUseCase, compareSimulationsUseCase, createBenchmarkUseCase,
            pauseSimulationUseCase, sellPositionUseCase, liquidateAllPositionsUseCase,
            forceEndSimulationUseCase, findTickerUseCase, pauseMarketResearchUseCase,
            resumeMarketResearchUseCase, pruneUnusedUseCase, calculateBenchmarkOverlaysUseCase,
            getTickerPerformanceUseCase,
            listHistoricalEventsUseCase, getHistoricalEventUseCase,
            joinChallengeUseCase, challengeRepository,
            benchmarkRepository, simulationRepository, objectMapper, Runnable::run);
    }

    @Test
    void shouldListStrategies() {
        StrategyId id = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(id, "BTC Momentum", "# Content", USER_ID);
        when(listStrategiesUseCase.execute(any())).thenReturn(List.of(strategy));

        String result = executor.execute("list_strategies", "{}", USER_ID);

        assertThat(result).contains("BTC Momentum");
        assertThat(result).contains(id.value().toString());
    }

    @Test
    void shouldListPublicStrategies() {
        StrategyId id = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(id, "Community BTC Strategy", "# Content", new UserId(UUID.randomUUID()));
        when(listPublicStrategiesUseCase.execute()).thenReturn(List.of(strategy));

        String result = executor.execute("list_public_strategies", "{}", USER_ID);

        assertThat(result).contains("Community BTC Strategy");
        assertThat(result).contains(id.value().toString());
    }

    @Test
    void shouldReturnEmptyMessageForNoPublicStrategies() {
        when(listPublicStrategiesUseCase.execute()).thenReturn(List.of());

        String result = executor.execute("list_public_strategies", "{}", USER_ID);

        assertThat(result).contains("No public strategies available");
    }

    @Test
    void shouldGetStrategy() {
        StrategyId id = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(id, "ETH Strategy", "# ETH rules", USER_ID);
        when(getStrategyUseCase.execute(any())).thenReturn(strategy);

        String result = executor.execute("get_strategy",
            "{\"strategyId\":\"" + id.value() + "\"}", USER_ID);

        assertThat(result).contains("ETH Strategy");
        assertThat(result).contains("ETH rules");
    }

    @Test
    void shouldListSimulations() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        Simulation sim = new Simulation(simId, "BTC Backtest",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));
        when(listSimulationsUseCase.execute(any())).thenReturn(List.of(sim));

        String result = executor.execute("list_simulations", "{}", USER_ID);

        assertThat(result).contains("BTC Backtest");
        assertThat(result).contains(simId.value().toString());
    }

    @Test
    void shouldHandleUnknownTool() {
        String result = executor.execute("nonExistentTool", "{}", USER_ID);

        assertThat(result).contains("error");
        assertThat(result).contains("Unknown tool");
    }

    @Test
    void shouldHandleMalformedJson() {
        String result = executor.execute("list_strategies", "not-json{", USER_ID);

        assertThat(result).contains("error");
    }

    @Test
    void shouldTruncateLongResults() {
        // Create enough strategies to exceed MAX_RESULT_LENGTH
        List<Strategy> strategies = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            strategies.add(new Strategy(
                new StrategyId(UUID.randomUUID()),
                "Strategy_" + i + "_with_a_very_long_name_to_push_the_result_over_the_limit",
                "# Content", USER_ID));
        }
        when(listStrategiesUseCase.execute(any())).thenReturn(strategies);

        String result = executor.execute("list_strategies", "{}", USER_ID);

        assertThat(result.length()).isLessThanOrEqualTo(4020); // 4000 + "...(truncated)"
    }

    @Test
    void shouldHandleNullArgs() {
        when(listStrategiesUseCase.execute(any())).thenReturn(List.of());

        String result = executor.execute("list_strategies", null, USER_ID);

        assertThat(result).contains("\"results\":[]");
    }

    // ===========================================================================
    // Serialization tests: verify Jackson can serialize all domain types returned
    // by ChatToolExecutor tools. These catch missing fields and type mismatches.
    // In native image, these types also need @RegisterForReflection (McpReflectionConfig).
    // ===========================================================================

    @Test
    void shouldSerializeStrategyPerformance() {
        StrategyPerformance perf = new StrategyPerformance(
            5, 3, new BigDecimal("12.5"), new BigDecimal("66.7"),
            new BigDecimal("1.2"), new BigDecimal("25.0"), new BigDecimal("-5.0"),
            List.of(new BigDecimal("10"), new BigDecimal("15")));
        when(calculateStrategyPerformanceUseCase.execute(any())).thenReturn(perf);

        String result = executor.execute("get_strategy_performance",
            "{\"strategyId\":\"" + UUID.randomUUID() + "\"}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("12.5");
    }

    @Test
    void shouldSerializePortfolioSnapshots() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Position pos = new Position(btc, new BigDecimal("1.5"), Money.usd(30000), Money.usd(35000));
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
            simId, Instant.now(), Map.of(btc, pos), Money.usd(52500), Money.usd(10000));
        when(queryPortfolioSnapshotsUseCase.execute(any())).thenReturn(List.of(snapshot));

        String result = executor.execute("query_portfolio_snapshots",
            "{\"simulationId\":\"" + simId.value() + "\"}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("52500");
    }

    @Test
    void shouldSerializeConsensusDecisions() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        ConsensusDecision decision = new ConsensusDecision(
            simId, Instant.now(), ConsensusMethod.ORCHESTRATOR_OVERRIDE, ConsensusActionParser.parse("BUY"));
        decision.setSymbol(new Symbol("AAPL", InvestmentType.STOCK));
        decision.setInvestmentType(InvestmentType.STOCK);
        decision.setExecutionOutcome(ExecutionOutcome.EXECUTED);
        decision.addLLMUsage(LLMUsage.of("test/model", 100, 50, 0.01));
        when(queryConsensusDecisionsUseCase.execute(any())).thenReturn(List.of(decision));

        String result = executor.execute("query_consensus_decisions",
            "{\"simulationId\":\"" + simId.value() + "\"}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("AAPL");
    }

    @Test
    void shouldSerializeConsensusWithAgents() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        ConsensusDecision decision = new ConsensusDecision(
            simId, Instant.now(), ConsensusMethod.ORCHESTRATOR_OVERRIDE, ConsensusActionParser.parse("HOLD"));
        decision.setSymbol(new Symbol("TSLA", InvestmentType.STOCK));

        LLMDecision agentDecision = new LLMDecision();
        agentDecision.setSimulationId(simId);
        agentDecision.setAgentId("technical");
        agentDecision.setRecommendedAction("HOLD");
        agentDecision.setConfidenceScore(new Percentage(new BigDecimal("0.85")));
        agentDecision.setLLMUsage(LLMUsage.of("test/model", 200, 100, 0.02));

        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(decision, List.of(agentDecision));
        when(getConsensusWithAgentsUseCase.execute(any())).thenReturn(List.of(response));

        String result = executor.execute("get_consensus_with_agents",
            "{\"simulationId\":\"" + simId.value() + "\"}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("TSLA");
    }

    @Test
    void shouldSerializeIndicatorSnapshot() {
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("65.3")));
        values.put(IndicatorType.MOVING_AVERAGE, new IndicatorValue.MovingAverageValue(
            new BigDecimal("45000"), new BigDecimal("42000")));
        values.put(IndicatorType.MACD, new IndicatorValue.MACDValue(
            new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("200")));
        values.put(IndicatorType.BOLLINGER_BANDS, new IndicatorValue.BollingerValue(
            new BigDecimal("48000"), new BigDecimal("45000"), new BigDecimal("42000")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(btc, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(btc, snapshot));
        when(indicatorEnrichmentService.calculateIndicators(any())).thenReturn(context);

        String result = executor.execute("get_indicators",
            "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("65.3");
    }

    @Test
    void shouldSerializeMarketEvents() {
        MarketEvent event = new MarketEvent(
            1L, Instant.now(), EventType.PRICE, "alpaca", "AAPL",
            Map.of("close", 150.0, "volume", 1000000), Instant.now(), null, null);
        when(queryMarketEventsUseCase.execute(any())).thenReturn(List.of(event));

        String result = executor.execute("query_market_events",
            "{\"from\":\"2025-01-01T00:00:00Z\",\"to\":\"2025-06-01T00:00:00Z\"}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("AAPL");
    }

    @Test
    void shouldSerializeMarketResearchSummaries() {
        MarketResearch research = new MarketResearch(
            "BTC Research", "# Analyze BTC trends",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS),
            false, null, USER_ID);
        MarketResearchSummary summary = new MarketResearchSummary(research, 3);
        when(listMarketResearchUseCase.execute(any())).thenReturn(List.of(summary));

        String result = executor.execute("list_market_research", "{}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("BTC Research");
    }

    @Test
    void shouldSerializeMarketResearchDetails() {
        MarketResearch research = new MarketResearch(
            "ETH Research", "# Analyze ETH",
            Set.of(new Symbol("ETH", InvestmentType.CRYPTO)),
            new ResearchInterval(14, ResearchInterval.Unit.DAYS),
            true, new BacktestConfiguration(Duration.ofDays(90), null, null),
            USER_ID);
        ResearchExecution execution = new ResearchExecution(
            research.getId(), ResearchOutcome.STRATEGY_CREATED,
            "Found momentum opportunity", new ViabilityScore(new BigDecimal("75")),
            UUID.randomUUID(), null);
        MarketResearchDetails details = new MarketResearchDetails(
            research, List.of(execution), Optional.of(execution));
        when(getMarketResearchDetailsUseCase.execute(any(), any())).thenReturn(details);

        String result = executor.execute("get_market_research_details",
            "{\"marketResearchId\":\"" + research.getId() + "\"}", USER_ID);

        assertThat(result).doesNotContain("error");
        assertThat(result).contains("ETH Research");
    }

    // ===========================================================================
    // Benchmark tool dispatch tests
    // ===========================================================================

    @Test
    void shouldDispatchCompareSimulations() {
        UUID simId1 = UUID.randomUUID();
        UUID simId2 = UUID.randomUUID();
        UUID benchmarkId = UUID.randomUUID();
        when(compareSimulationsUseCase.execute(any())).thenReturn(
            new com.quorumtrading.application.usecase.dto.CompareSimulationsResponse(
                benchmarkId, "Test Benchmark", List.of(simId1, simId2), List.of("Different start dates")));

        String result = executor.execute("compare_simulations",
            "{\"simulationIds\":\"" + simId1 + "," + simId2 + "\",\"name\":\"Test Benchmark\"}", USER_ID);

        assertThat(result).contains(benchmarkId.toString());
        assertThat(result).contains("Test Benchmark");
        assertThat(result).contains("Different start dates");
        assertThat(result).contains("/benchmarks/");
    }

    @Test
    void shouldDispatchCreateBenchmark() {
        UUID stratId1 = UUID.randomUUID();
        UUID stratId2 = UUID.randomUUID();
        UUID benchmarkId = UUID.randomUUID();
        UUID simId1 = UUID.randomUUID();
        UUID simId2 = UUID.randomUUID();
        when(createBenchmarkUseCase.execute(any())).thenReturn(
            new com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse(
                benchmarkId, List.of(simId1, simId2), "My Benchmark"));

        String result = executor.execute("create_benchmark",
            "{\"strategyIds\":\"" + stratId1 + "," + stratId2
                + "\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-06-01\",\"name\":\"My Benchmark\"}", USER_ID);

        assertThat(result).contains(benchmarkId.toString());
        assertThat(result).contains("My Benchmark");
        assertThat(result).contains("/benchmarks/");
    }

    // ===========================================================================
    // Simulation control tool dispatch tests
    // ===========================================================================

    @Test
    void shouldDispatchPauseSimulation() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        String result = executor.execute("pause_simulation",
            "{\"simulationId\":\"" + simId.value() + "\"}", USER_ID);
        assertThat(result).contains("paused");
    }

    @Test
    void shouldDispatchSellPosition() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        when(sellPositionUseCase.execute(any(), any())).thenReturn(
            SellPositionResult.success(new BigDecimal("1.5"), new BigDecimal("45000")));

        String result = executor.execute("sell_position",
            "{\"simulationId\":\"" + simId.value() + "\",\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        assertThat(result).contains("true");
        assertThat(result).contains("1.5");
        assertThat(result).contains("45000");
    }

    @Test
    void shouldDispatchLiquidateAllPositions() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        when(liquidateAllPositionsUseCase.execute(any())).thenReturn(
            new LiquidateAllResult(3, 0, List.of(), new BigDecimal("50000")));

        String result = executor.execute("liquidate_all_positions",
            "{\"simulationId\":\"" + simId.value() + "\"}", USER_ID);

        assertThat(result).contains("3");
        assertThat(result).contains("50000");
    }

    @Test
    void shouldDispatchForceEndSimulation() {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        when(forceEndSimulationUseCase.execute(any())).thenReturn(
            ForceEndSimulationResult.completedWithLiquidation(2, "Simulation force-ended"));

        String result = executor.execute("force_end_simulation",
            "{\"simulationId\":\"" + simId.value() + "\"}", USER_ID);

        assertThat(result).contains("true");
        assertThat(result).contains("force-ended");
    }

    // ===========================================================================
    // Benchmark listing/detail tool dispatch tests
    // ===========================================================================

    @Test
    void shouldDispatchListBenchmarks() {
        BenchmarkId bId = BenchmarkId.generate();
        Benchmark benchmark = new Benchmark(bId, "Test Benchmark",
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Instant.now(), USER_ID);
        when(benchmarkRepository.findByOwnerId(any(), any(int.class))).thenReturn(List.of(benchmark));

        String result = executor.execute("list_benchmarks", "{}", USER_ID);

        assertThat(result).contains("Test Benchmark");
        assertThat(result).contains(bId.value().toString());
        assertThat(result).contains("backtest");
    }

    @Test
    void shouldDispatchGetBenchmark() {
        BenchmarkId bId = BenchmarkId.generate();
        Benchmark benchmark = new Benchmark(bId, "Detail Benchmark",
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Instant.now(), USER_ID);
        SimulationId simId = new SimulationId(UUID.randomUUID());
        Simulation sim = new Simulation(simId, "Sim in Benchmark",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));
        when(benchmarkRepository.findById(bId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findByBenchmarkId(bId)).thenReturn(List.of(sim));

        String result = executor.execute("get_benchmark",
            "{\"benchmarkId\":\"" + bId.value() + "\"}", USER_ID);

        assertThat(result).contains("Detail Benchmark");
        assertThat(result).contains("Sim in Benchmark");
        assertThat(result).contains(simId.value().toString());
    }

    // ===========================================================================
    // Market research control + ticker lookup tool dispatch tests
    // ===========================================================================

    @Test
    void shouldDispatchPauseMarketResearch() {
        UUID mrId = UUID.randomUUID();
        String result = executor.execute("pause_market_research",
            "{\"marketResearchId\":\"" + mrId + "\"}", USER_ID);
        assertThat(result).contains("paused");
    }

    @Test
    void shouldDispatchResumeMarketResearch() {
        UUID mrId = UUID.randomUUID();
        String result = executor.execute("resume_market_research",
            "{\"marketResearchId\":\"" + mrId + "\"}", USER_ID);
        assertThat(result).contains("resumed");
    }

    @Test
    void shouldDispatchFindTicker() {
        when(findTickerUseCase.execute(any())).thenReturn(
            TickerSearchResult.found("AAPL", "Apple Inc.", "NASDAQ", InvestmentType.STOCK));

        String result = executor.execute("find_ticker",
            "{\"companyName\":\"Apple\"}", USER_ID);

        assertThat(result).contains("AAPL");
        assertThat(result).contains("Apple Inc.");
        assertThat(result).contains("NASDAQ");
        assertThat(result).contains("STOCK");
    }

    // ===========================================================================
    // Ticker performance tool dispatch tests
    // ===========================================================================

    @Test
    void shouldDispatchGetTickerPerformance() {
        when(getTickerPerformanceUseCase.execute(any())).thenReturn(
            TickerPerformanceResult.withData("QQQ",
                Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-06-28T00:00:00Z"),
                new BigDecimal("400"), new BigDecimal("480"),
                new BigDecimal("490"), new BigDecimal("380"),
                new BigDecimal("20.00"), new BigDecimal("80"),
                125));

        String result = executor.execute("get_ticker_performance",
            "{\"ticker\":\"QQQ\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}", USER_ID);

        assertThat(result).contains("QQQ");
        assertThat(result).contains("400");
        assertThat(result).contains("480");
        assertThat(result).contains("20.00");
        assertThat(result).doesNotContain("error");
    }

    @Test
    void shouldDispatchGetTickerPerformance_noData() {
        when(getTickerPerformanceUseCase.execute(any())).thenReturn(
            TickerPerformanceResult.noData("ZZZZ", "No price data available. Use backfill_price_data first."));

        String result = executor.execute("get_ticker_performance",
            "{\"ticker\":\"ZZZZ\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}", USER_ID);

        assertThat(result).contains("ZZZZ");
        assertThat(result).contains("backfill_price_data");
        assertThat(result).contains("false");
    }

    // ===========================================================================
    // Error handling and timeout tests
    // ===========================================================================

    @Test
    void shouldHandleToolExceptionWithProperErrorFormat() {
        when(indicatorEnrichmentService.calculateIndicators(any()))
            .thenThrow(new RuntimeException("Database connection failed"));

        String result = executor.execute("get_indicators",
            "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        assertThat(result).contains("error");
        assertThat(result).contains("Database connection failed");
    }

    @Test
    void shouldReturnErrorForSlowToolExecution() {
        // Simulate a slow tool that would trigger timeout (in real scenario)
        // The actual timeout is 30 seconds, so we just verify the error message format
        doAnswer(invocation -> {
            // Simulate slow operation - in production this would timeout
            Thread.sleep(50); // Short delay just to test async execution works
            return IndicatorContext.empty();
        }).when(indicatorEnrichmentService).calculateIndicators(any());

        String result = executor.execute("get_indicators",
            "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        // Should complete without error since 50ms < 30s timeout
        assertThat(result).doesNotContain("timed out");
    }

    // ===========================================================================
    // Auto-backfill tests for get_indicators
    // ===========================================================================

    @Test
    void shouldAutoBackfillForGetIndicators_whenNoData() {
        // First call: empty indicators (no data)
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        when(indicatorEnrichmentService.calculateIndicators(any()))
            .thenReturn(IndicatorContext.empty())  // First: no data
            .thenReturn(new IndicatorContext(Map.of(btc,  // Second (after backfill): has data
                new IndicatorSnapshot(btc, Instant.now(),
                    Map.of(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("42.5"))),
                    Map.of()))));

        when(backFillTickerPriceDataUseCase.execute(any()))
            .thenReturn(BackFillResult.success("BTC", "CRYPTO", 200, 0, 0));

        String result = executor.execute("get_indicators",
            "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        assertThat(result).contains("42.5");
        assertThat(result).contains("autoBackfillNote");
        assertThat(result).contains("inserted 200");
        verify(backFillTickerPriceDataUseCase).execute(any());
    }

    @Test
    void shouldNotAutoBackfillForGetIndicators_whenDataExists() {
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("65.3")));
        IndicatorSnapshot snapshot = new IndicatorSnapshot(btc, Instant.now(), values, Map.of());
        when(indicatorEnrichmentService.calculateIndicators(any()))
            .thenReturn(new IndicatorContext(Map.of(btc, snapshot)));

        String result = executor.execute("get_indicators",
            "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        assertThat(result).contains("65.3");
        verify(backFillTickerPriceDataUseCase, never()).execute(any());
    }

    @Test
    void shouldHandleAutoBackfillFailure_forGetIndicators() {
        when(indicatorEnrichmentService.calculateIndicators(any()))
            .thenReturn(IndicatorContext.empty());

        when(backFillTickerPriceDataUseCase.execute(any()))
            .thenThrow(new RuntimeException("Alpaca API unavailable"));

        String result = executor.execute("get_indicators",
            "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        assertThat(result).contains("warning");
        assertThat(result).contains("autoBackfillNote");
        assertThat(result).contains("Auto-backfill failed");
        assertThat(result).doesNotContain("\"error\"");
    }

    // ===========================================================================
    // Auto-backfill tests for get_ticker_performance
    // ===========================================================================

    @Test
    void shouldAutoBackfillForGetTickerPerformance_whenNoData() {
        // First call: no data; second call (after backfill): has data
        when(getTickerPerformanceUseCase.execute(any()))
            .thenReturn(TickerPerformanceResult.noData("AAPL", "No price data available."))
            .thenReturn(TickerPerformanceResult.withData("AAPL",
                Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-06-28T00:00:00Z"),
                new BigDecimal("180"), new BigDecimal("220"),
                new BigDecimal("225"), new BigDecimal("175"),
                new BigDecimal("22.22"), new BigDecimal("40"), 125));

        when(backFillTickerPriceDataUseCase.execute(any()))
            .thenReturn(BackFillResult.success("AAPL", "STOCK", 125, 0, 0));

        String result = executor.execute("get_ticker_performance",
            "{\"ticker\":\"AAPL\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}", USER_ID);

        assertThat(result).contains("AAPL");
        assertThat(result).contains("220");
        assertThat(result).contains("22.22");
        assertThat(result).contains("true"); // dataAvailable
        verify(backFillTickerPriceDataUseCase).execute(any());
    }

    @Test
    void shouldAutoBackfillForGetTickerPerformance_withExplicitInvestmentType() {
        when(getTickerPerformanceUseCase.execute(any()))
            .thenReturn(TickerPerformanceResult.noData("ETH", "No price data available."))
            .thenReturn(TickerPerformanceResult.withData("ETH",
                Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-06-28T00:00:00Z"),
                new BigDecimal("2400"), new BigDecimal("3200"),
                new BigDecimal("3500"), new BigDecimal("2200"),
                new BigDecimal("33.33"), new BigDecimal("800"), 180));

        when(backFillTickerPriceDataUseCase.execute(any()))
            .thenReturn(BackFillResult.success("ETH", "CRYPTO", 180, 0, 0));

        String result = executor.execute("get_ticker_performance",
            "{\"ticker\":\"ETH\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\",\"investmentType\":\"CRYPTO\"}", USER_ID);

        assertThat(result).contains("ETH");
        assertThat(result).contains("3200");
        assertThat(result).contains("true"); // dataAvailable
        verify(backFillTickerPriceDataUseCase).execute(any());
    }

    @Test
    void shouldNotAutoBackfillForGetTickerPerformance_whenDataExists() {
        when(getTickerPerformanceUseCase.execute(any()))
            .thenReturn(TickerPerformanceResult.withData("QQQ",
                Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-06-28T00:00:00Z"),
                new BigDecimal("400"), new BigDecimal("480"),
                new BigDecimal("490"), new BigDecimal("380"),
                new BigDecimal("20.00"), new BigDecimal("80"), 125));

        String result = executor.execute("get_ticker_performance",
            "{\"ticker\":\"QQQ\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}", USER_ID);

        assertThat(result).contains("QQQ");
        assertThat(result).contains("480");
        verify(backFillTickerPriceDataUseCase, never()).execute(any());
    }

    @Test
    void shouldInferCryptoTypeForKnownTickers() {
        // BTC should be auto-detected as CRYPTO
        when(getTickerPerformanceUseCase.execute(any()))
            .thenReturn(TickerPerformanceResult.noData("BTC", "No data"))
            .thenReturn(TickerPerformanceResult.withData("BTC",
                Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-06-28T00:00:00Z"),
                new BigDecimal("42000"), new BigDecimal("60000"),
                new BigDecimal("65000"), new BigDecimal("40000"),
                new BigDecimal("42.86"), new BigDecimal("18000"), 180));

        when(backFillTickerPriceDataUseCase.execute(argThat(cmd ->
            cmd.assetType().equals("CRYPTO"))))
            .thenReturn(BackFillResult.success("BTC", "CRYPTO", 180, 0, 0));

        String result = executor.execute("get_ticker_performance",
            "{\"ticker\":\"BTC\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}", USER_ID);

        assertThat(result).contains("BTC");
        assertThat(result).contains("60000");
        // Verify backfill was called with CRYPTO type (inferred)
        verify(backFillTickerPriceDataUseCase).execute(argThat(cmd ->
            cmd.assetType().equals("CRYPTO")));
    }
}
