package com.quorumtrading.adapter.mcp.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.dto.*;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.application.usecase.BackFillResult;
import com.quorumtrading.application.usecase.SimulationEvaluationResult;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.*;
import com.quorumtrading.model.marketdata.BackfillProgress;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class McpDtoMapperTest {

    private McpDtoMapper mapper;

    private static final UUID SIM_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STRATEGY_UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OWNER_UUID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID RESEARCH_UUID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID ACCOUNT_UUID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID BENCHMARK_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @BeforeEach
    void setUp() {
        mapper = new McpDtoMapper();
        // Inject dependencies via reflection since this is a unit test
        UiLinks uiLinks = new UiLinks();
        uiLinks.frontendBaseUrl = "http://localhost:3000";
        uiLinks.frontendEnabled = true;
        try {
            var field = McpDtoMapper.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(mapper, new ObjectMapper());
            var linksField = McpDtoMapper.class.getDeclaredField("uiLinks");
            linksField.setAccessible(true);
            linksField.set(mapper, uiLinks);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject dependencies into mapper", e);
        }
    }

    /**
     * The ticker a tool reports back is the user's only evidence of which instrument it answered
     * about. Labelling a venue-qualified symbol with its bare root claims the answer is about the
     * US listing of the same company — and get_indicators is the tool users are told to run as a
     * pre-flight check, so a wrong label there is trusted.
     */
    @Test
    void toIndicatorSnapshotDto_keepsTheVenueOfANonUsListing() {
        Symbol asmlXams = Symbol.parse("ASML:XAMS");

        McpIndicatorSnapshotDto dto = mapper.toIndicatorSnapshotDto(
            IndicatorSnapshot.empty(asmlXams, "No price data available"));

        assertThat(dto.ticker())
            .as("the bare root names a different instrument than the one asked about")
            .isEqualTo("ASML:XAMS");
    }

    @Test
    void toIndicatorSnapshotDto_leavesAUsTickerBare() {
        McpIndicatorSnapshotDto dto = mapper.toIndicatorSnapshotDto(
            IndicatorSnapshot.empty(new Symbol("AAPL", InvestmentType.STOCK), "No price data"));

        assertThat(dto.ticker()).isEqualTo("AAPL");
    }

    @Test
    void toSimulationDto_mapsAllFields() {
        Simulation sim = new Simulation(
            new SimulationId(SIM_UUID),
            "Backtest BTC Q1",
            new StrategyId(STRATEGY_UUID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-04-01T00:00:00Z"),
            Money.usd(new BigDecimal("50000"))
        );

        McpSimulationDto dto = mapper.toSimulationDto(sim);

        assertThat(dto.id()).isEqualTo(SIM_UUID.toString());
        assertThat(dto.name()).isEqualTo("Backtest BTC Q1");
        assertThat(dto.strategyId()).isEqualTo(STRATEGY_UUID.toString());
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.progress()).isEqualTo(0);
        assertThat(dto.startDate()).isEqualTo("2025-01-01T00:00:00Z");
        assertThat(dto.endDate()).isEqualTo("2025-04-01T00:00:00Z");
        assertThat(dto.initialCash()).isEqualTo("50000");
        assertThat(dto.currency()).isEqualTo("USD");
        assertThat(dto.strategyName()).isNull();
        assertThat(dto.brokerAccountId()).isNull();
        assertThat(dto.tradingMode()).isNull();
        assertThat(dto.symbols()).isNull();
        assertThat(dto.currentValue()).isNull();
        assertThat(dto.returnPercent()).isNull();
        assertThat(dto.startedAt()).isNull();
        assertThat(dto.completedAt()).isNull();
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void toSimulationDto_mapsStrategyNameAndTradingMode() {
        Simulation sim = new Simulation(
            new SimulationId(SIM_UUID),
            "Backtest BTC Q1",
            new StrategyId(STRATEGY_UUID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-04-01T00:00:00Z"),
            Money.usd(new BigDecimal("50000"))
        );

        McpSimulationDto dto = mapper.toSimulationDto(sim, "Momentum Strategy", "BACKTEST");

        assertThat(dto.strategyName()).isEqualTo("Momentum Strategy");
        assertThat(dto.tradingMode()).isEqualTo("BACKTEST");
        assertThat(dto.symbols()).isNull();
        assertThat(dto.currentValue()).isNull();
        assertThat(dto.returnPercent()).isNull();
    }

    @Test
    void toSimulationDto_mapsBrokerAccountId() {
        Simulation sim = new Simulation(
            new SimulationId(SIM_UUID),
            "Live BTC",
            new StrategyId(STRATEGY_UUID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-04-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000"))
        );
        sim.setBrokerAccountId(new BrokerAccountId(ACCOUNT_UUID));

        McpSimulationDto dto = mapper.toSimulationDto(sim);

        assertThat(dto.brokerAccountId()).isEqualTo(ACCOUNT_UUID.toString());
    }

    @Test
    void toStrategyDto_includesContent() {
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID),
            "Momentum Strategy",
            "## Consensus Method\nWEIGHTED",
            new UserId(OWNER_UUID)
        );

        McpStrategyDto dto = mapper.toStrategyDto(strategy, true);

        assertThat(dto.id()).isEqualTo(STRATEGY_UUID.toString());
        assertThat(dto.name()).isEqualTo("Momentum Strategy");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.content()).isEqualTo("## Consensus Method\nWEIGHTED");
    }

    @Test
    void toStrategyDto_excludesContent() {
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID),
            "Momentum Strategy",
            "## Consensus Method\nWEIGHTED",
            new UserId(OWNER_UUID)
        );

        McpStrategyDto dto = mapper.toStrategyDto(strategy, false);

        assertThat(dto.content()).isNull();
    }

    /**
     * An MCP client has no way to know the web app exists, let alone where a simulation is
     * rendered — "show it in the UI" was unanswerable. Every entity DTO with a detail page
     * carries the page's URL.
     */
    @Test
    void dtosCarryTheirWebAppDetailPage() {
        Simulation sim = new Simulation(
            new SimulationId(SIM_UUID),
            "Backtest BTC Q1",
            new StrategyId(STRATEGY_UUID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-04-01T00:00:00Z"),
            Money.usd(new BigDecimal("50000"))
        );
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID), "Momentum", "content", new UserId(OWNER_UUID));

        assertThat(mapper.toSimulationDto(sim).uiUrl())
            .isEqualTo("http://localhost:3000/simulations/" + SIM_UUID);
        assertThat(mapper.toStrategyDto(strategy).uiUrl())
            .isEqualTo("http://localhost:3000/strategies/" + STRATEGY_UUID);
    }

    /**
     * A headless instance (frontend disabled) has no page to link to; the field must be
     * absent from the JSON, not a link to a connection error.
     */
    @Test
    void uiUrlIsOmittedFromJsonWhenTheFrontendIsDisabled() throws Exception {
        UiLinks headless = new UiLinks();
        headless.frontendBaseUrl = "http://localhost:3000";
        headless.frontendEnabled = false;
        var linksField = McpDtoMapper.class.getDeclaredField("uiLinks");
        linksField.setAccessible(true);
        linksField.set(mapper, headless);

        Simulation sim = new Simulation(
            new SimulationId(SIM_UUID),
            "Backtest BTC Q1",
            new StrategyId(STRATEGY_UUID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-04-01T00:00:00Z"),
            Money.usd(new BigDecimal("50000"))
        );

        McpSimulationDto dto = mapper.toSimulationDto(sim);

        assertThat(dto.uiUrl()).isNull();
        assertThat(new ObjectMapper().writeValueAsString(dto)).doesNotContain("uiUrl");
    }

    @Test
    void toConsensusDecisionDto_mapsAllFields() {
        ConsensusDecision cd = new ConsensusDecision(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T10:00:00Z"),
            ConsensusMethod.MAJORITY,
            ConsensusActionParser.parse("{\"action\":\"BUY\"}")
        );
        cd.setId(42L);
        cd.setSymbol(new Symbol("AAPL", InvestmentType.STOCK));
        cd.setInvestmentType(InvestmentType.STOCK);
        cd.setQuantity(new BigDecimal("10"));
        cd.setExecutionOutcome(ExecutionOutcome.EXECUTED);
        cd.setExecuted(true);
        cd.setExecutionPrice(Money.usd(new BigDecimal("175.50")));

        McpConsensusDecisionDto dto = mapper.toConsensusDecisionDto(cd);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.simulationId()).isEqualTo(SIM_UUID.toString());
        assertThat(dto.timestamp()).isEqualTo("2025-03-15T10:00:00Z");
        assertThat(dto.consensusMethod()).isEqualTo("MAJORITY");
        assertThat(dto.finalAction().action()).isEqualTo("BUY");
        assertThat(dto.executed()).isTrue();
        assertThat(dto.executionOutcome()).isEqualTo("EXECUTED");
        assertThat(dto.executionPrice()).isEqualTo("175.50");
        assertThat(dto.quantity()).isEqualTo("10");
        assertThat(dto.symbol()).isEqualTo("AAPL");
        assertThat(dto.investmentType()).isEqualTo("STOCK");
    }

    @Test
    void toPortfolioSnapshotDto_mapsPositions() {
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Position position = new Position(btc, new BigDecimal("0.5"), Money.usd(new BigDecimal("40000")), Money.usd(new BigDecimal("45000")));

        PortfolioSnapshot snap = new PortfolioSnapshot(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T12:00:00Z"),
            Map.of(btc, position),
            Money.usd(new BigDecimal("32500")),
            Money.usd(new BigDecimal("10000"))
        );

        McpPortfolioSnapshotDto dto = mapper.toPortfolioSnapshotDto(snap);

        assertThat(dto.simulationId()).isEqualTo(SIM_UUID.toString());
        assertThat(dto.timestamp()).isEqualTo("2025-03-15T12:00:00Z");
        assertThat(dto.totalValue()).isEqualTo("32500");
        assertThat(dto.cashAvailable()).isEqualTo("10000");
        assertThat(dto.currency()).isEqualTo("USD");
        assertThat(dto.positions()).hasSize(1);
        assertThat(dto.positions().get(0).ticker()).isEqualTo("BTC");
        assertThat(dto.positions().get(0).quantity()).isEqualTo("0.5");
        assertThat(dto.positions().get(0).averagePrice()).isEqualTo("40000");
        assertThat(dto.positions().get(0).currentPrice()).isEqualTo("45000");
        // marketValue = 0.5 * 45000 = 22500
        assertThat(dto.positions().get(0).marketValue()).isEqualTo("22500.0");
        // unrealizedPnl = (45000 - 40000) * 0.5 = 2500
        assertThat(dto.positions().get(0).unrealizedPnl()).isEqualTo("2500.0");
    }

    @Test
    void toStrategyPerformanceDto_mapsMetrics() {
        StrategyPerformance perf = new StrategyPerformance(
            10, 7,
            new BigDecimal("15.3"),
            new BigDecimal("71.4"),
            new BigDecimal("1.5"),
            new BigDecimal("30.0"),
            new BigDecimal("-8.2"),
            List.of(new BigDecimal("12.0"), new BigDecimal("18.0"))
        );

        McpStrategyPerformanceDto dto = mapper.toStrategyPerformanceDto(
            new StrategyId(STRATEGY_UUID), "Test Strategy", perf
        );

        assertThat(dto.strategyId()).isEqualTo(STRATEGY_UUID.toString());
        assertThat(dto.strategyName()).isEqualTo("Test Strategy");
        assertThat(dto.totalSimulations()).isEqualTo(10);
        assertThat(dto.completedSimulations()).isEqualTo(7);
        assertThat(dto.avgReturnPercent()).isEqualTo("15.3");
        assertThat(dto.winRatePercent()).isEqualTo("71.4");
        assertThat(dto.avgSharpeRatio()).isEqualTo("1.5");
        assertThat(dto.bestReturnPercent()).isEqualTo("30.0");
        assertThat(dto.worstReturnPercent()).isEqualTo("-8.2");
        assertThat(dto.recentReturnPercents()).containsExactly("12.0", "18.0");
    }

    // --- Market Research mapper tests ---

    @Test
    void toMarketResearchDto_mapsSummary() {
        MarketResearch research = new MarketResearch(
            "Test Research",
            "## Instructions",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS),
            false,
            null,
            new UserId(OWNER_UUID)
        );
        MarketResearchSummary summary = new MarketResearchSummary(research, 5);

        McpMarketResearchDto dto = mapper.toMarketResearchDto(summary);

        assertThat(dto.name()).isEqualTo("Test Research");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.intervalAmount()).isEqualTo(7);
        assertThat(dto.intervalUnit()).isEqualTo("DAYS");
        assertThat(dto.strategyCount()).isEqualTo(5);
        assertThat(dto.targetAssets()).hasSize(1);
        assertThat(dto.targetAssets().get(0)).isEqualTo("BTC:CRYPTO");
        assertThat(dto.instructions()).isEqualTo("## Instructions");
        assertThat(dto.totalExecutions()).isNull();
        assertThat(dto.lastOutcome()).isNull();
        assertThat(dto.uiUrl()).isEqualTo("http://localhost:3000/research/" + dto.id());
    }

    @Test
    void toMarketResearchDto_includesInstructionsAndExecutionStats() {
        MarketResearch research = new MarketResearch(
            "Stats Research",
            "## Custom Instructions",
            Set.of(new Symbol("AAPL", InvestmentType.STOCK)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            new UserId(OWNER_UUID)
        );

        McpMarketResearchDto dto = mapper.toMarketResearchDto(research, 3, 10, "STRATEGY_CREATED");

        assertThat(dto.name()).isEqualTo("Stats Research");
        assertThat(dto.instructions()).isEqualTo("## Custom Instructions");
        assertThat(dto.strategyCount()).isEqualTo(3);
        assertThat(dto.totalExecutions()).isEqualTo(10);
        assertThat(dto.lastOutcome()).isEqualTo("STRATEGY_CREATED");
    }

    @Test
    void toResearchAgentDecisionDto_mapsAllFields() {
        UUID execId = UUID.randomUUID();
        ResearchAgentDecision decision = new ResearchAgentDecision(
            UUID.randomUUID(),
            execId,
            ResearchAgentType.NEWS_SENTIMENT_ANALYST,
            "Buy signal detected",
            "Strong positive sentiment in news",
            Instant.parse("2025-03-15T10:00:00Z"),
            "claude-3.5-sonnet",
            100,
            200,
            300,
            new BigDecimal("0.015"),
            null
        );

        McpResearchAgentDecisionDto dto = mapper.toResearchAgentDecisionDto(decision);

        assertThat(dto.agentType()).isEqualTo("NEWS_SENTIMENT_ANALYST");
        assertThat(dto.advice()).isEqualTo("Buy signal detected");
        assertThat(dto.reasoning()).isEqualTo("Strong positive sentiment in news");
        assertThat(dto.modelUsed()).isEqualTo("claude-3.5-sonnet");
        assertThat(dto.totalTokens()).isEqualTo(300);
        assertThat(dto.llmCostUsd()).isEqualTo("0.015");
    }

    @Test
    void resolveOutcomeDisplay_returnsRawOutcomeWhenStrategyStillLinked() {
        ResearchExecution exec = new ResearchExecution(
            UUID.randomUUID(),
            ResearchOutcome.STRATEGY_CREATED,
            "Created strategy",
            new ViabilityScore(new BigDecimal("80.00")),
            UUID.randomUUID(),
            null
        );

        assertThat(McpDtoMapper.resolveOutcomeDisplay(exec)).isEqualTo("STRATEGY_CREATED");
    }

    @Test
    void resolveOutcomeDisplay_surfacesDeletedStateWhenStrategyIdNull() {
        // Simulates the ON DELETE SET NULL aftermath: outcome stays STRATEGY_CREATED but strategy was deleted.
        ResearchExecution exec = new ResearchExecution(
            UUID.randomUUID(),
            ResearchOutcome.STRATEGY_CREATED,
            "Created strategy, later deleted",
            new ViabilityScore(new BigDecimal("80.00")),
            null,
            null
        );

        assertThat(McpDtoMapper.resolveOutcomeDisplay(exec)).isEqualTo("STRATEGY_CREATED_DELETED");
    }

    @Test
    void resolveOutcomeDisplay_passesThroughNonStrategyOutcomes() {
        ResearchExecution noOpp = new ResearchExecution(
            UUID.randomUUID(),
            ResearchOutcome.NO_OPPORTUNITY,
            "Nothing actionable",
            null,
            null,
            null
        );
        ResearchExecution failed = new ResearchExecution(
            UUID.randomUUID(),
            ResearchOutcome.FAILED,
            "Boom",
            null,
            null,
            null
        );

        assertThat(McpDtoMapper.resolveOutcomeDisplay(noOpp)).isEqualTo("NO_OPPORTUNITY");
        assertThat(McpDtoMapper.resolveOutcomeDisplay(failed)).isEqualTo("FAILED");
    }

    @Test
    void toMarketResearchDetailsDto_mapsDetailsWithExecutions() {
        MarketResearch research = new MarketResearch(
            "Detail Research",
            "## Instructions",
            Set.of(),
            new ResearchInterval(14, ResearchInterval.Unit.DAYS),
            false,
            null,
            new UserId(OWNER_UUID)
        );
        MarketResearchDetails details = new MarketResearchDetails(research, List.of(), Optional.empty());

        McpMarketResearchDetailsDto dto = mapper.toMarketResearchDetailsDto(details);

        assertThat(dto.research().name()).isEqualTo("Detail Research");
        assertThat(dto.totalExecutions()).isEqualTo(0);
        assertThat(dto.successfulExecutions()).isEqualTo(0);
        assertThat(dto.latestExecution()).isNull();
        assertThat(dto.executions()).isEmpty();
    }

    // --- Market Data mapper tests ---

    @Test
    void toMarketEventDto_mapsAllFields() {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.PRICE,
            "alpaca",
            "AAPL",
            Map.of("close", 175.50)
        );

        McpMarketEventDto dto = mapper.toMarketEventDto(event);

        assertThat(dto.timestamp()).isEqualTo("2025-03-15T10:00:00Z");
        assertThat(dto.eventType()).isEqualTo("PRICE");
        assertThat(dto.source()).isEqualTo("alpaca");
        assertThat(dto.symbol()).isEqualTo("AAPL");
        assertThat(dto.data()).contains("175.5");
        assertThat(dto.closePrice()).isEqualTo("175.5");
    }

    @Test
    void toMarketEventDto_excludeRawDataStillHasClosePrice() {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.PRICE,
            "alpaca",
            "AAPL",
            Map.of("close", 175.50)
        );

        McpMarketEventDto dto = mapper.toMarketEventDto(event, true);

        assertThat(dto.data()).isNull();
        assertThat(dto.closePrice()).isEqualTo("175.5");
        assertThat(dto.headline()).isNull();
    }

    @Test
    void toMarketEventDto_extractsHeadlineForNewsEvents() {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.NEWS,
            "finnhub",
            "AAPL",
            Map.of("headline", "Apple announces record Q1 earnings", "category", "company")
        );

        McpMarketEventDto dto = mapper.toMarketEventDto(event, true);

        assertThat(dto.data()).isNull();
        assertThat(dto.headline()).isEqualTo("Apple announces record Q1 earnings");
        assertThat(dto.closePrice()).isNull();
    }

    @Test
    void toMarketEventDto_extractsTitleAsFallbackForNewsEvents() {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.NEWS,
            "rss",
            "BTC",
            Map.of("title", "Bitcoin surges past $100K", "url", "https://example.com")
        );

        McpMarketEventDto dto = mapper.toMarketEventDto(event, true);

        assertThat(dto.headline()).isEqualTo("Bitcoin surges past $100K");
    }

    @Test
    void toMarketEventDto_extractsHeadlineForSocialEvents() {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.SOCIAL,
            "twitter",
            "TSLA",
            Map.of("headline", "Elon Musk tweets about Tesla production")
        );

        McpMarketEventDto dto = mapper.toMarketEventDto(event, true);

        assertThat(dto.headline()).isEqualTo("Elon Musk tweets about Tesla production");
    }

    @Test
    void toMarketEventDto_priceEventHasNoHeadline() {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.PRICE,
            "alpaca",
            "AAPL",
            Map.of("close", 175.50, "headline", "should not extract this")
        );

        McpMarketEventDto dto = mapper.toMarketEventDto(event, true);

        assertThat(dto.closePrice()).isEqualTo("175.5");
        assertThat(dto.headline()).isNull();
    }

    @Test
    void toIndicatorSnapshotDto_mapsAllIndicators() {
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        Instant now = Instant.now();

        IndicatorSnapshot snapshot = new IndicatorSnapshot(
            symbol,
            now,
            Map.of(
                IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("55.0")),
                IndicatorType.MOVING_AVERAGE, new IndicatorValue.MovingAverageValue(
                    new BigDecimal("180.00"), new BigDecimal("170.00")
                )
            ),
            Map.of(IndicatorType.MACD, "Insufficient data for MACD")
        );

        McpIndicatorSnapshotDto dto = mapper.toIndicatorSnapshotDto(snapshot);

        assertThat(dto.ticker()).isEqualTo("AAPL");
        assertThat(dto.rsi().rsi14()).isEqualTo("55.0");
        assertThat(dto.movingAverage().sma50()).isEqualTo("180.00");
        assertThat(dto.movingAverage().sma200()).isEqualTo("170.00");
        assertThat(dto.macd()).isNull();
        assertThat(dto.bollingerBands()).isNull();
        assertThat(dto.warnings()).containsExactly("Insufficient data for MACD");
    }

    /**
     * {@code IndicatorSnapshot.empty} stamps one reason onto every IndicatorType, and the mapper
     * used to flatten the map straight to a list — so a single finding was reported as four
     * identical sentences with nothing saying which indicator each concerned. Say it once, and
     * name who it applies to.
     */
    @Test
    void toIndicatorSnapshotDto_saysASharedWarningOnceAndNamesTheIndicators() {
        Symbol symbol = new Symbol("BTC", InvestmentType.CRYPTO);
        IndicatorSnapshot snapshot = IndicatorSnapshot.empty(symbol, "No price data available");

        McpIndicatorSnapshotDto dto = mapper.toIndicatorSnapshotDto(snapshot);

        assertThat(dto.warnings())
            .as("four repetitions of one sentence read as four separate findings")
            .hasSize(1);
        assertThat(dto.warnings().get(0))
            .contains("No price data available")
            .contains("RSI")
            .contains("MACD");
    }

    // --- Backfill mapper tests ---

    @Test
    void toBackFillResultDto_mapsSuccess() {
        BackFillResult result = BackFillResult.success("AAPL", "STOCK", 250, 10, 0);

        McpBackFillResultDto dto = mapper.toBackFillResultDto(result);

        assertThat(dto.ticker()).isEqualTo("AAPL");
        assertThat(dto.assetType()).isEqualTo("STOCK");
        assertThat(dto.recordsInserted()).isEqualTo(250);
        assertThat(dto.recordsSkipped()).isEqualTo(10);
        assertThat(dto.recordsFailed()).isEqualTo(0);
        assertThat(dto.success()).isTrue();
        assertThat(dto.errorMessage()).isNull();
        assertThat(dto.startDate()).isNull();
        assertThat(dto.endDate()).isNull();
        assertThat(dto.granularity()).isNull();
    }

    @Test
    void toBackFillResultDto_mapsDateRange() {
        BackFillResult result = BackFillResult.success("AAPL", "STOCK", 250, 10, 0);

        McpBackFillResultDto dto = mapper.toBackFillResultDto(result, "2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z", "1Day");

        assertThat(dto.ticker()).isEqualTo("AAPL");
        assertThat(dto.assetType()).isEqualTo("STOCK");
        assertThat(dto.startDate()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(dto.endDate()).isEqualTo("2025-01-01T00:00:00Z");
        assertThat(dto.granularity()).isEqualTo("1Day");
        assertThat(dto.recordsInserted()).isEqualTo(250);
        assertThat(dto.success()).isTrue();
    }

    @Test
    void toBackfillProgressDto_mapsProgress() {
        UUID jobId = UUID.randomUUID();
        BackfillProgress progress = new BackfillProgress(
            jobId,
            BackfillStatus.COMPLETED,
            100,
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:05:00Z"),
            null,
            500,
            500
        );

        McpBackfillProgressDto dto = mapper.toBackfillProgressDto(progress);

        assertThat(dto.jobId()).isEqualTo(jobId.toString());
        assertThat(dto.status()).isEqualTo("COMPLETED");
        assertThat(dto.progressPercentage()).isEqualTo(100);
        assertThat(dto.recordsProcessed()).isEqualTo(500);
        assertThat(dto.ticker()).isNull();
        assertThat(dto.assetType()).isNull();
    }

    @Test
    void toBackfillProgressDto_mapsTickerAndAssetType() {
        UUID jobId = UUID.randomUUID();
        BackfillProgress progress = new BackfillProgress(
            jobId,
            BackfillStatus.IN_PROGRESS,
            50,
            Instant.parse("2025-01-01T10:00:00Z"),
            null,
            null,
            500,
            250
        );

        McpBackfillProgressDto dto = mapper.toBackfillProgressDto(progress, "AAPL", "STOCK");

        assertThat(dto.jobId()).isEqualTo(jobId.toString());
        assertThat(dto.status()).isEqualTo("IN_PROGRESS");
        assertThat(dto.ticker()).isEqualTo("AAPL");
        assertThat(dto.assetType()).isEqualTo("STOCK");
    }

    // --- Broker Account mapper tests ---

    @Test
    void toBrokerAccountDto_mapsMockAccount() {
        MockBrokerConfig config = MockBrokerConfig.fromPreset(
            Money.usd(new BigDecimal("100000")),
            CostPreset.ZERO_FEES
        );
        BrokerAccount account = BrokerAccount.createMock(new UserId(OWNER_UUID), "My Mock", config);

        McpBrokerAccountDto dto = mapper.toBrokerAccountDto(account);

        assertThat(dto.name()).isEqualTo("My Mock");
        assertThat(dto.brokerType()).isEqualTo("MOCK");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.totalBalance()).isEqualTo("100000");
        assertThat(dto.currency()).isEqualTo("USD");
    }

    // --- Portfolio Evaluation mapper test ---

    @Test
    void toPortfolioEvaluationDto_mapsSuccess() {
        SimulationEvaluationResult result = SimulationEvaluationResult.success(new SimulationId(SIM_UUID));

        McpPortfolioEvaluationDto dto = mapper.toPortfolioEvaluationDto(result);

        assertThat(dto.simulationId()).isEqualTo(SIM_UUID.toString());
        assertThat(dto.success()).isTrue();
        assertThat(dto.skipped()).isFalse();
        assertThat(dto.errorMessage()).isNull();
        assertThat(dto.latestSnapshot()).isNull();
        assertThat(dto.returnPercent()).isNull();
    }

    @Test
    void toPortfolioEvaluationDto_mapsSkipped() {
        SimulationEvaluationResult result = SimulationEvaluationResult.skipped(
            new SimulationId(SIM_UUID), "Portfolio value unchanged"
        );

        McpPortfolioEvaluationDto dto = mapper.toPortfolioEvaluationDto(result);

        assertThat(dto.simulationId()).isEqualTo(SIM_UUID.toString());
        assertThat(dto.success()).isFalse();
        assertThat(dto.skipped()).isTrue();
        assertThat(dto.errorMessage()).isEqualTo("Portfolio value unchanged");
        assertThat(dto.latestSnapshot()).isNull();
        assertThat(dto.returnPercent()).isNull();
    }

    @Test
    void toPortfolioEvaluationDto_includesSnapshotAndReturnPercent() {
        SimulationEvaluationResult result = SimulationEvaluationResult.success(new SimulationId(SIM_UUID));

        PortfolioSnapshot snap = new PortfolioSnapshot(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T12:00:00Z"),
            Map.of(),
            Money.usd(new BigDecimal("12000")),
            Money.usd(new BigDecimal("12000"))
        );
        McpPortfolioSnapshotDto snapshotDto = mapper.toPortfolioSnapshotDto(snap);

        McpPortfolioEvaluationDto dto = mapper.toPortfolioEvaluationDto(result, snapshotDto, "20.0000");

        assertThat(dto.success()).isTrue();
        assertThat(dto.latestSnapshot()).isNotNull();
        assertThat(dto.latestSnapshot().totalValue()).isEqualTo("12000");
        assertThat(dto.returnPercent()).isEqualTo("20.0000");
    }

    @Test
    void toStrategyDto_parsesFrontmatterMetadata() {
        String content = "---\nconsensus_method: WEIGHTED\nstock_tickers:\n  - AAPL\n  - TSLA\ncrypto_tickers:\n  - BTC\n---\n# My Strategy";
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID),
            "Parsed Strategy",
            content,
            new UserId(OWNER_UUID)
        );

        McpStrategyDto dto = mapper.toStrategyDto(strategy, false);

        assertThat(dto.consensusMethod()).isEqualTo("MAJORITY");
        assertThat(dto.stockTickers()).containsExactly("AAPL", "TSLA");
        assertThat(dto.cryptoTickers()).containsExactly("BTC");
        assertThat(dto.content()).isNull();
        assertThat(dto.simulationCount()).isNull();
    }

    @Test
    void toStrategyDto_includesSimulationCount() {
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID),
            "Counted Strategy",
            "## Some content",
            new UserId(OWNER_UUID)
        );

        McpStrategyDto dto = mapper.toStrategyDto(strategy, false, 5);

        assertThat(dto.simulationCount()).isEqualTo(5);
        assertThat(dto.content()).isNull();
    }

    @Test
    void toStrategyDto_absentConsensusMethodResolvesToTheEffectiveDefault() {
        // Omitting consensus_method selects ORCHESTRATOR_OVERRIDE at runtime (+1 model call
        // per round). Reporting null read as "nothing configured" — resolve it like
        // effectiveAgents already resolves an absent agents key.
        String content = "---\ncrypto_tickers:\n  - BTC\n---\n# My Strategy";
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID),
            "Defaulted Strategy",
            content,
            new UserId(OWNER_UUID)
        );

        McpStrategyDto dto = mapper.toStrategyDto(strategy, false);

        assertThat(dto.consensusMethod()).isEqualTo("ORCHESTRATOR_OVERRIDE");
    }

    @Test
    void toStrategyDto_gracefullyHandlesInvalidFrontmatter() {
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID),
            "No Frontmatter",
            "just plain markdown without YAML",
            new UserId(OWNER_UUID)
        );

        McpStrategyDto dto = mapper.toStrategyDto(strategy, false);

        // Should not throw, metadata fields are null
        assertThat(dto.stockTickers()).isNull();
        assertThat(dto.cryptoTickers()).isNull();
        assertThat(dto.consensusMethod()).isNull();
        assertThat(dto.tradingMode()).isNull();
    }

    // --- extractSymbols tests ---

    @Test
    void extractSymbols_returnsSortedCombinedTickers() {
        String content = "---\nstock_tickers:\n  - AAPL\n  - TSLA\ncrypto_tickers:\n  - BTC\n---\n# Strategy";
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_UUID),
            "Symbol Strategy",
            content,
            new UserId(OWNER_UUID)
        );

        List<String> symbols = mapper.extractSymbols(strategy);

        // Stock tickers come first (sorted), then crypto tickers (sorted)
        assertThat(symbols).containsExactly("AAPL", "TSLA", "BTC");
    }

    // --- Benchmark mapper tests ---

    // --- Consensus agent ticker fallback tests ---

    @Test
    void toConsensusWithAgentsDto_agentTickerFallsBackToConsensusSymbol() {
        ConsensusDecision cd = new ConsensusDecision(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T10:00:00Z"),
            ConsensusMethod.MAJORITY,
            ConsensusActionParser.parse("{\"action\":\"BUY\"}")
        );
        cd.setId(1L);
        cd.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));

        LLMDecision ld = new LLMDecision(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T10:00:00Z"),
            "agent-technical-1",
            "TECHNICAL",
            "qwen/qwen3"
        );
        ld.setId(100L);
        // Ticker is null (not persisted in DB)
        ld.setRecommendation(RecommendationType.BUY);
        ld.setConfidenceScore(new Percentage(new BigDecimal("0.85")));

        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(cd, List.of(ld));
        McpConsensusWithAgentsDto dto = mapper.toConsensusWithAgentsDto(response, true);

        // Agent ticker should fall back to consensus symbol
        assertThat(dto.agentDecisions().get(0).ticker()).isEqualTo("BTC");
    }

    @Test
    void toConsensusWithAgentsDto_agentTickerUsesOwnWhenPresent() {
        ConsensusDecision cd = new ConsensusDecision(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T10:00:00Z"),
            ConsensusMethod.MAJORITY,
            ConsensusActionParser.parse("{\"action\":\"BUY\"}")
        );
        cd.setId(1L);
        cd.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));

        LLMDecision ld = new LLMDecision(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T10:00:00Z"),
            "agent-technical-1",
            "TECHNICAL",
            "qwen/qwen3"
        );
        ld.setId(100L);
        ld.setTicker("ETH");  // Agent has its own ticker
        ld.setRecommendation(RecommendationType.SELL);
        ld.setConfidenceScore(new Percentage(new BigDecimal("0.70")));

        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(cd, List.of(ld));
        McpConsensusWithAgentsDto dto = mapper.toConsensusWithAgentsDto(response, true);

        // Agent ticker should be used when present
        assertThat(dto.agentDecisions().get(0).ticker()).isEqualTo("ETH");
    }

    @Test
    void toBenchmarkDto_computesCompletionCounts() {
        Benchmark benchmark = new Benchmark(
            new BenchmarkId(BENCHMARK_UUID),
            "Test Benchmark",
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-06-01T00:00:00Z"),
            Instant.now()
        );

        UUID simUuid1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID simUuid2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        Simulation sim1 = new Simulation(
            new SimulationId(simUuid1),
            "Sim 1",
            new StrategyId(STRATEGY_UUID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000"))
        );
        sim1.setStatus(SimulationStatus.COMPLETED);

        Simulation sim2 = new Simulation(
            new SimulationId(simUuid2),
            "Sim 2",
            new StrategyId(STRATEGY_UUID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000"))
        );
        // sim2 stays PENDING (default)

        McpBenchmarkDto dto = mapper.toBenchmarkDto(benchmark, List.of(sim1, sim2));

        assertThat(dto.totalSimulations()).isEqualTo(2);
        assertThat(dto.completedSimulations()).isEqualTo(1);
        assertThat(dto.simulationIds()).containsExactly(simUuid1.toString(), simUuid2.toString());
        assertThat(dto.uiUrl()).isEqualTo("http://localhost:3000/benchmarks/" + BENCHMARK_UUID);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the documented map ticker form renders as a symbol, not a Java map dump")
    void mapFormTickerRendersAsPlainSymbol() {
        // "- ticker: SIEGY / currency: USD" is the currency-pinning form the strategy
        // guide documents. Rendering it with toString() produced
        // "{ticker=SIEGY, currency=USD}" wherever a ticker was shown, which made a
        // correctly-parsed strategy look corrupt and led auditors to believe the map
        // form was slipping past ticker validation.
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("stock_tickers", List.of(
            Map.of("ticker", "SIEGY", "currency", "USD"),
            "AAPL"));

        List<String> tickers = mapper.extractTickerList(frontmatter, "stock_tickers");

        assertThat(tickers).containsExactly("AAPL", "SIEGY");
        assertThat(tickers).noneMatch(t -> t.contains("{") || t.contains("="));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("an entry with no ticker field is dropped rather than rendered")
    void mapFormWithoutTickerIsDropped() {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("stock_tickers", new ArrayList<>(List.of(
            Map.of("currency", "USD"),
            Map.of("symbol", "MSFT"))));

        // "symbol" is accepted as an alias, matching the domain parser's precedence.
        assertThat(mapper.extractTickerList(frontmatter, "stock_tickers"))
            .containsExactly("MSFT");
    }

    // --- Action payload: structured, and reasoning gated by the one flag ---

    private static final String AGENT_ACTION_JSON =
        "{\"action\": \"BUY\", \"symbol\": \"AAPL\", \"quantity\": 3, \"confidence\": 0.82,"
            + " \"reasoning\": \"RSI-14 is oversold at 27\"}";

    private static ConsensusDecision decisionWith(String actionJson) {
        ConsensusDecision cd = new ConsensusDecision(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T10:00:00Z"),
            ConsensusMethod.MAJORITY,
            ConsensusActionParser.parse(actionJson)
        );
        cd.setId(1L);
        cd.setSymbol(new Symbol("AAPL", InvestmentType.STOCK));
        return cd;
    }

    private static LLMDecision agentWith(String actionJson, String reasoning) {
        LLMDecision ld = new LLMDecision(
            new SimulationId(SIM_UUID),
            Instant.parse("2025-03-15T10:00:00Z"),
            "agent-technical-1",
            "TECHNICAL",
            "qwen/qwen3"
        );
        ld.setId(100L);
        ld.setRecommendation(RecommendationType.BUY);
        ld.setRecommendedAction(actionJson);
        if (reasoning != null) {
            ld.setReasoning(reasoning);
        }
        return ld;
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the action payload is structured fields, not an embedded JSON string")
    void actionPayloadIsParsedNotEmbedded() {
        McpConsensusDecisionDto dto = mapper.toConsensusDecisionDto(decisionWith(AGENT_ACTION_JSON), true);

        assertThat(dto.finalAction().action()).isEqualTo("BUY");
        assertThat(dto.finalAction().symbol()).isEqualTo("AAPL");
        assertThat(dto.finalAction().quantity()).isEqualTo("3");
        assertThat(dto.finalAction().confidence()).isEqualTo("0.82");
        assertThat(dto.finalAction().reasoning()).isEqualTo("RSI-14 is oversold at 27");
        // Nothing to salvage: the payload is fully represented by the fields above.
        assertThat(dto.finalAction().raw()).isNull();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("unparseable LLM output keeps its raw text so it stays diagnosable")
    void unparseableActionKeepsRawText() {
        McpConsensusDecisionDto dto = mapper.toConsensusDecisionDto(decisionWith("not json at all"), true);

        assertThat(dto.finalAction().action()).isNull();
        assertThat(dto.finalAction().raw()).isEqualTo("not json at all");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("includeReasoning=false strips reasoning from the action payload too, not just the clean field")
    void compactModeStripsReasoningFromEveryCarrier() {
        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(
            decisionWith(AGENT_ACTION_JSON),
            List.of(agentWith(AGENT_ACTION_JSON, "RSI-14 is oversold at 27")));

        McpConsensusWithAgentsDto dto = mapper.toConsensusWithAgentsDto(response, false);

        // This is the defect: the flag used to null only the clean field while shipping the
        // raw action JSON — carrying the same paragraph — unconditionally.
        assertThat(dto.consensus().finalAction().reasoning()).isNull();
        assertThat(dto.agentDecisions().get(0).reasoning()).isNull();
        assertThat(dto.agentDecisions().get(0).recommendedAction().reasoning()).isNull();
        assertThat(dto.agentDecisions().get(0).recommendedAction().raw()).isNull();
        // The action itself still comes through — compact means less text, not less meaning.
        assertThat(dto.agentDecisions().get(0).recommendedAction().action()).isEqualTo("BUY");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("an agent's reasoning is not repeated in its own sibling action payload")
    void reasoningIsNotDuplicatedWithinAnAgentDecision() {
        // NOTE: this asserts dedup WITHIN one agent record only. Its DisplayName used to claim
        // the broader property — "each reasoning exactly once, never repeated across sibling
        // fields" — which four independent audit agents then falsified: consensus.finalAction
        // .reasoning still came back byte-identical to the winning agent's. The fixture even
        // reproduced the duplication and asserted nothing about it. A green test whose name
        // overstates its assertions is worse than no test, because it retires the question.
        // The cross-object property is now asserted separately, below.
        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(
            decisionWith("{\"action\": \"BUY\", \"reasoning\": \"orchestrator synthesis\"}"),
            List.of(agentWith(AGENT_ACTION_JSON, "RSI-14 is oversold at 27")));

        McpConsensusWithAgentsDto dto = mapper.toConsensusWithAgentsDto(response, true);
        McpAgentDecisionDto agent = dto.agentDecisions().get(0);

        assertThat(agent.reasoning()).isEqualTo("RSI-14 is oversold at 27");
        assertThat(agent.recommendedAction().reasoning()).isNull();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the winning agent's reasoning is not repeated verbatim on the consensus")
    void reasoningIsNotDuplicatedAcrossConsensusAndAgent() {
        // Under MAJORITY the consensus action is built from the winning agent's own action, so
        // the same paragraph occupies two DB rows and both used to ship in full. At limit=50
        // that doubled the largest text field fifty times.
        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(
            decisionWith(AGENT_ACTION_JSON),
            List.of(agentWith(AGENT_ACTION_JSON, "RSI-14 is oversold at 27")));

        McpConsensusWithAgentsDto dto = mapper.toConsensusWithAgentsDto(response, true);
        McpAgentDecisionDto agent = dto.agentDecisions().get(0);

        assertThat(dto.consensus().finalAction().reasoning()).isEqualTo("RSI-14 is oversold at 27");
        assertThat(agent.reasoning())
            .isNotEqualTo(dto.consensus().finalAction().reasoning())
            .isEqualTo(McpDtoMapper.REASONING_SAME_AS_CONSENSUS);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("an agent that lost the vote keeps its own distinct reasoning in full")
    void dissentingAgentReasoningIsNeverCollapsed() {
        // The dedup must key on the text, not on the agent — collapsing a dissenter would
        // destroy the only record of why the minority disagreed.
        LLMDecision dissenter = agentWith(
            "{\"action\": \"SELL\", \"reasoning\": \"RSI-14 is overbought at 71\"}",
            "RSI-14 is overbought at 71");
        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(
            decisionWith(AGENT_ACTION_JSON), List.of(dissenter));

        McpConsensusWithAgentsDto dto = mapper.toConsensusWithAgentsDto(response, true);

        assertThat(dto.agentDecisions().get(0).reasoning()).isEqualTo("RSI-14 is overbought at 71");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("an agent with no stored reasoning falls back to the reasoning inside its action payload")
    void agentReasoningFallsBackToActionPayload() {
        // Distinct consensus text so the cross-object dedup stays out of the way — this test
        // is about the fallback from the action payload, nothing else.
        ConsensusWithAgentsResponse response = new ConsensusWithAgentsResponse(
            decisionWith("{\"action\": \"BUY\", \"reasoning\": \"orchestrator synthesis\"}"),
            List.of(agentWith(AGENT_ACTION_JSON, null)));

        McpConsensusWithAgentsDto dto = mapper.toConsensusWithAgentsDto(response, true);

        assertThat(dto.agentDecisions().get(0).reasoning()).isEqualTo("RSI-14 is oversold at 27");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("emptyRuns survives to the MCP wire, because a guide tells the user to read it")
    void emptyRunsReachesTheWire() {
        // The blocker this closes: emptyRuns was computed, validated, unit-tested AND documented
        // in platform-overview's "Reading Your Results" — and dropped at both adapter boundaries.
        // A missing key reads as "zero empty runs", so the guide turned a diligent reader into a
        // confidently wrong one. The same change threaded noDecisionsReason through this mapper
        // correctly; the boundary work was done for one field and not the other.
        StrategyPerformance perf = new StrategyPerformance(
            5, 4,
            new BigDecimal("2.95"), new BigDecimal("100.00"), new BigDecimal("0.5"),
            new BigDecimal("2.95"), new BigDecimal("2.95"),
            List.of(new BigDecimal("2.95")),
            3);

        McpStrategyPerformanceDto dto = mapper.toStrategyPerformanceDto(
            new StrategyId(STRATEGY_UUID), "Test Strategy", perf);

        assertThat(dto.emptyRuns())
            .as("three of the four completed runs decided nothing and were excluded from the "
                + "averages — a caller cannot interpret those averages without knowing that")
            .isEqualTo(3);
    }

    // --- price coverage ---

    @Test
    void toPriceCoverageDto_emptyCoverageSaysWhatToDoNext() {
        McpPriceCoverageDto dto = mapper.toPriceCoverageDto(PriceCoverage.empty("ZZZQQQ"));

        assertThat(dto.barCount()).isZero();
        assertThat(dto.note()).contains("backfill_price_data");
        assertThat(dto.window()).as("no window was requested, so none is invented").isNull();
    }

    /**
     * A venue-qualified symbol cannot simply be "backfilled": whether that can succeed depends on a
     * provider that is off by default. Answering it with the same sentence as an ordinary
     * not-yet-fetched US ticker sends the user round a loop that cannot terminate.
     */
    @Test
    void toPriceCoverageDto_venueQualifiedSymbolGetsItsOwnAnswer() {
        McpPriceCoverageDto dto = mapper.toPriceCoverageDto(PriceCoverage.empty("ASML:XAMS"));

        assertThat(dto.note()).contains("get_market_data_status");
    }

    @Test
    void toPriceCoverageDto_flagsDuplicateOrIntradayBars() {
        McpPriceCoverageDto dto = mapper.toPriceCoverageDto(new PriceCoverage(
            "AAPL", 100, 20, Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-20T00:00:00Z"), List.of("alpaca")));

        assertThat(dto.note()).contains("duplicate or intraday");
    }

    /**
     * The whole point of the window: first/last bar reads as continuous coverage and is not.
     */
    @Test
    void toPriceCoverageDto_incompleteWindowSaysSoRatherThanLookingHealthy() {
        PriceCoverage coverage = new PriceCoverage(
            "BTC", 2, 2, Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-05T00:00:00Z"), List.of("kraken"))
            .withWindow(PriceCoverage.buildWindow(
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 5),
                InvestmentType.CRYPTO,
                Set.of(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 5))));

        McpPriceCoverageDto dto = mapper.toPriceCoverageDto(coverage);

        assertThat(dto.window()).isNotNull();
        assertThat(dto.window().expectedDays()).isEqualTo(5);
        assertThat(dto.window().coveredDays()).isEqualTo(2);
        assertThat(dto.window().missingDays()).isEqualTo(3);
        assertThat(dto.window().gaps()).hasSize(1);
        assertThat(dto.window().gaps().get(0).from()).isEqualTo("2026-01-02");
        assertThat(dto.window().calendar()).isNotBlank();
        assertThat(dto.note())
            .as("a caller reading only the note must still learn the window is not usable")
            .contains("do not cover the requested window");
    }

    @Test
    void toPriceCoverageDto_completeWindowDoesNotWarn() {
        PriceCoverage coverage = new PriceCoverage(
            "BTC", 3, 3, Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z"), List.of("kraken"))
            .withWindow(PriceCoverage.buildWindow(
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 3),
                InvestmentType.CRYPTO,
                Set.of(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 2),
                       java.time.LocalDate.of(2026, 1, 3))));

        assertThat(mapper.toPriceCoverageDto(coverage).note()).isNull();
    }
}
