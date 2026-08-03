package com.quorumtrading.application.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.command.*;
import com.quorumtrading.application.usecase.result.*;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.application.usecase.query.*;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.*;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Dispatches tool calls from the chat LLM to the appropriate use cases.
 * Mirrors the MCP tool dispatch but returns JSON strings instead of ToolResponse.
 * <p>
 * <b>Native Image:</b> Any domain object passed directly to {@link #toJson(Object)}
 * must be registered for reflection in {@code McpReflectionConfig}. This includes
 * the top-level type AND all its transitive field types (value objects, enums).
 * Forgetting this causes "No serializer found" errors in production native image.
 */
public class ChatToolExecutor {

    private static final Logger LOG = Logger.getLogger(ChatToolExecutor.class);
    private static final int MAX_RESULT_LENGTH = 4000;
    private static final int TOOL_TIMEOUT_SECONDS = 30;

    /** Calendar days to auto-backfill for indicators (trading days * 7/5 + holiday buffer, capped at 365). */
    static final int AUTO_BACKFILL_CALENDAR_DAYS = Math.min(
        (int) Math.ceil(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS * 7.0 / 5) + 20, 365);

    /** Well-known crypto tickers for inferring investment type when not provided. */
    private static final Set<String> KNOWN_CRYPTO = Set.of(
        "BTC", "ETH", "SOL", "DOGE", "ADA", "XRP", "DOT", "AVAX", "MATIC", "LINK",
        "UNI", "ATOM", "LTC", "BCH", "NEAR", "APT", "ARB", "OP", "SHIB", "PEPE",
        "BTC-USD", "ETH-USD", "SOL-USD", "DOGE-USD", "ADA-USD", "XRP-USD");

    private final ListStrategiesUseCase listStrategiesUseCase;
    private final ListPublicStrategiesUseCase listPublicStrategiesUseCase;
    private final GetStrategyUseCase getStrategyUseCase;
    private final CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase;
    private final ListSimulationsUseCase listSimulationsUseCase;
    private final GetSimulationUseCase getSimulationUseCase;
    private final QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase;
    private final GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase;
    private final QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase;
    private final IndicatorEnrichmentService indicatorEnrichmentService;
    private final QueryMarketEventsUseCase queryMarketEventsUseCase;
    private final BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase;
    private final ListMarketResearchUseCase listMarketResearchUseCase;
    private final GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase;
    private final ListBrokerAccountsUseCase listBrokerAccountsUseCase;
    private final CreateStrategyUseCase createStrategyUseCase;
    private final CreateSimulationUseCase createSimulationUseCase;
    private final StartSimulationUseCase startSimulationUseCase;
    private final CreateMarketResearchUseCase createMarketResearchUseCase;
    private final ExecuteMarketResearchUseCase executeMarketResearchUseCase;
    private final DeleteStrategyUseCase deleteStrategyUseCase;
    private final DeleteSimulationUseCase deleteSimulationUseCase;
    private final DeleteMarketResearchUseCase deleteMarketResearchUseCase;
    private final DeleteBenchmarkUseCase deleteBenchmarkUseCase;
    private final CompareSimulationsUseCase compareSimulationsUseCase;
    private final CreateBenchmarkUseCase createBenchmarkUseCase;
    private final PauseSimulationUseCase pauseSimulationUseCase;
    private final SellPositionUseCase sellPositionUseCase;
    private final LiquidateAllPositionsUseCase liquidateAllPositionsUseCase;
    private final ForceEndSimulationUseCase forceEndSimulationUseCase;
    private final FindTickerUseCase findTickerUseCase;
    private final PauseMarketResearchUseCase pauseMarketResearchUseCase;
    private final ResumeMarketResearchUseCase resumeMarketResearchUseCase;
    private final PruneUnusedUseCase pruneUnusedUseCase;
    private final CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase;
    private final GetTickerPerformanceUseCase getTickerPerformanceUseCase;
    private final ListHistoricalEventsUseCase listHistoricalEventsUseCase;
    private final GetHistoricalEventUseCase getHistoricalEventUseCase;
    private final JoinChallengeUseCase joinChallengeUseCase;
    private final com.quorumtrading.application.port.out.ChallengeRepository challengeRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final SimulationRepository simulationRepository;
    private final ObjectMapper objectMapper;
    private final Executor workerExecutor;

    public ChatToolExecutor(
            ListStrategiesUseCase listStrategiesUseCase,
            ListPublicStrategiesUseCase listPublicStrategiesUseCase,
            GetStrategyUseCase getStrategyUseCase,
            CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase,
            ListSimulationsUseCase listSimulationsUseCase,
            GetSimulationUseCase getSimulationUseCase,
            QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase,
            GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase,
            QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase,
            IndicatorEnrichmentService indicatorEnrichmentService,
            QueryMarketEventsUseCase queryMarketEventsUseCase,
            BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase,
            ListMarketResearchUseCase listMarketResearchUseCase,
            GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase,
            ListBrokerAccountsUseCase listBrokerAccountsUseCase,
            CreateStrategyUseCase createStrategyUseCase,
            CreateSimulationUseCase createSimulationUseCase,
            StartSimulationUseCase startSimulationUseCase,
            CreateMarketResearchUseCase createMarketResearchUseCase,
            ExecuteMarketResearchUseCase executeMarketResearchUseCase,
            DeleteStrategyUseCase deleteStrategyUseCase,
            DeleteSimulationUseCase deleteSimulationUseCase,
            DeleteMarketResearchUseCase deleteMarketResearchUseCase,
            DeleteBenchmarkUseCase deleteBenchmarkUseCase,
            CompareSimulationsUseCase compareSimulationsUseCase,
            CreateBenchmarkUseCase createBenchmarkUseCase,
            PauseSimulationUseCase pauseSimulationUseCase,
            SellPositionUseCase sellPositionUseCase,
            LiquidateAllPositionsUseCase liquidateAllPositionsUseCase,
            ForceEndSimulationUseCase forceEndSimulationUseCase,
            FindTickerUseCase findTickerUseCase,
            PauseMarketResearchUseCase pauseMarketResearchUseCase,
            ResumeMarketResearchUseCase resumeMarketResearchUseCase,
            PruneUnusedUseCase pruneUnusedUseCase,
            CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase,
            GetTickerPerformanceUseCase getTickerPerformanceUseCase,
            ListHistoricalEventsUseCase listHistoricalEventsUseCase,
            GetHistoricalEventUseCase getHistoricalEventUseCase,
            JoinChallengeUseCase joinChallengeUseCase,
            com.quorumtrading.application.port.out.ChallengeRepository challengeRepository,
            BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            ObjectMapper objectMapper,
            Executor workerExecutor) {
        this.listStrategiesUseCase = listStrategiesUseCase;
        this.listPublicStrategiesUseCase = listPublicStrategiesUseCase;
        this.getStrategyUseCase = getStrategyUseCase;
        this.calculateStrategyPerformanceUseCase = calculateStrategyPerformanceUseCase;
        this.listSimulationsUseCase = listSimulationsUseCase;
        this.getSimulationUseCase = getSimulationUseCase;
        this.queryPortfolioSnapshotsUseCase = queryPortfolioSnapshotsUseCase;
        this.getConsensusWithAgentsUseCase = getConsensusWithAgentsUseCase;
        this.queryConsensusDecisionsUseCase = queryConsensusDecisionsUseCase;
        this.indicatorEnrichmentService = indicatorEnrichmentService;
        this.queryMarketEventsUseCase = queryMarketEventsUseCase;
        this.backFillTickerPriceDataUseCase = backFillTickerPriceDataUseCase;
        this.listMarketResearchUseCase = listMarketResearchUseCase;
        this.getMarketResearchDetailsUseCase = getMarketResearchDetailsUseCase;
        this.listBrokerAccountsUseCase = listBrokerAccountsUseCase;
        this.createStrategyUseCase = createStrategyUseCase;
        this.createSimulationUseCase = createSimulationUseCase;
        this.startSimulationUseCase = startSimulationUseCase;
        this.createMarketResearchUseCase = createMarketResearchUseCase;
        this.executeMarketResearchUseCase = executeMarketResearchUseCase;
        this.deleteStrategyUseCase = deleteStrategyUseCase;
        this.deleteSimulationUseCase = deleteSimulationUseCase;
        this.deleteMarketResearchUseCase = deleteMarketResearchUseCase;
        this.deleteBenchmarkUseCase = deleteBenchmarkUseCase;
        this.compareSimulationsUseCase = compareSimulationsUseCase;
        this.createBenchmarkUseCase = createBenchmarkUseCase;
        this.pauseSimulationUseCase = pauseSimulationUseCase;
        this.sellPositionUseCase = sellPositionUseCase;
        this.liquidateAllPositionsUseCase = liquidateAllPositionsUseCase;
        this.forceEndSimulationUseCase = forceEndSimulationUseCase;
        this.findTickerUseCase = findTickerUseCase;
        this.pauseMarketResearchUseCase = pauseMarketResearchUseCase;
        this.resumeMarketResearchUseCase = resumeMarketResearchUseCase;
        this.pruneUnusedUseCase = pruneUnusedUseCase;
        this.calculateBenchmarkOverlaysUseCase = calculateBenchmarkOverlaysUseCase;
        this.getTickerPerformanceUseCase = getTickerPerformanceUseCase;
        this.listHistoricalEventsUseCase = listHistoricalEventsUseCase;
        this.getHistoricalEventUseCase = getHistoricalEventUseCase;
        this.joinChallengeUseCase = joinChallengeUseCase;
        this.challengeRepository = challengeRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.simulationRepository = simulationRepository;
        this.objectMapper = objectMapper;
        this.workerExecutor = workerExecutor;
    }

    /**
     * Execute a tool call and return the result as a JSON string.
     * <p>
     * Tool execution is wrapped with a timeout to prevent hanging. If a tool
     * takes longer than {@link #TOOL_TIMEOUT_SECONDS}, a timeout error is returned
     * instead of blocking indefinitely.
     *
     * @param toolName The name of the tool to execute
     * @param argsJson The JSON arguments string from the LLM
     * @param userId   The current user's ID
     * @return JSON string result (truncated if necessary)
     */
    public String execute(String toolName, String argsJson, UserId userId) {
        try {
            JsonNode args = objectMapper.readTree(argsJson != null ? argsJson : "{}");

            LOG.infof("Chat tool call: tool=%s, args=%s, userId=%s", toolName, argsJson, userId.value());

            // Execute with timeout to prevent hanging.
            // Uses the injected workerExecutor (Quarkus-managed thread pool) instead of
            // ForkJoinPool.commonPool() to ensure proper CDI context propagation and
            // database connection pool access.
            String result = CompletableFuture.supplyAsync(() -> {
                    try {
                        return dispatch(toolName, args, userId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, workerExecutor)
                .orTimeout(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();

            String truncated = truncate(result);
            LOG.infof("Chat tool result: tool=%s, resultLength=%d, result=%s",
                toolName, truncated.length(), truncated.length() <= 500 ? truncated : truncated.substring(0, 500) + "...");

            return truncated;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException || e.getMessage() != null && e.getMessage().contains("timed out")) {
                LOG.warnf("Chat tool execution timed out after %ds: tool=%s", TOOL_TIMEOUT_SECONDS, toolName);
                return "{\"error\": \"Tool execution timed out after " + TOOL_TIMEOUT_SECONDS
                    + " seconds. The service may be busy - please try again.\", \"tool\": \"" + toolName + "\"}";
            }
            LOG.errorf("Chat tool execution failed: tool=%s, error=%s", toolName, cause.getMessage());
            return "{\"error\": \"" + escapeJson(cause.getMessage()) + "\"}";
        }
    }

    private String dispatch(String toolName, JsonNode args, UserId userId) throws JsonProcessingException {
        return switch (toolName) {
            case "list_strategies" -> {
                List<Strategy> strategies = listStrategiesUseCase.execute(
                    new ListStrategiesQuery(Optional.empty(), Optional.empty(), userId));
                if (strategies.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No strategies found. Create one with create_strategy."));
                }
                yield toJson(strategies.stream().map(s -> Map.of(
                    "id", s.getId().value().toString(),
                    "name", s.getName(),
                    "status", s.getStatus().name()
                )).toList());
            }
            case "list_public_strategies" -> {
                List<Strategy> publicStrategies = listPublicStrategiesUseCase.execute();
                if (publicStrategies.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No public strategies available yet."));
                }
                yield toJson(publicStrategies.stream().map(s -> Map.of(
                    "id", s.getId().value().toString(),
                    "name", s.getName(),
                    "description", s.getDescription() != null ? s.getDescription() : ""
                )).toList());
            }
            case "get_strategy" -> {
                StrategyId id = new StrategyId(UUID.fromString(args.get("strategyId").asText()));
                Strategy strategy = getStrategyUseCase.execute(new GetStrategyQuery(id, userId));
                yield toJson(Map.of(
                    "id", strategy.getId().value().toString(),
                    "name", strategy.getName(),
                    "status", strategy.getStatus().name(),
                    "content", strategy.getContent()
                ));
            }
            case "get_strategy_performance" -> {
                StrategyId id = new StrategyId(UUID.fromString(args.get("strategyId").asText()));
                StrategyPerformance perf = calculateStrategyPerformanceUseCase.execute(
                    new CalculateStrategyPerformanceQuery(id, userId));
                yield toJson(perf);
            }
            case "list_simulations" -> {
                int limit = args.has("limit") ? Math.min(args.get("limit").asInt(20), 100) : 20;
                Optional<String> status = args.has("status") ? Optional.of(args.get("status").asText()) : Optional.empty();
                List<Simulation> sims = listSimulationsUseCase.execute(
                    new ListSimulationsQuery(status, Optional.empty(), Optional.empty(), Optional.of(limit), userId));
                if (sims.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No simulations found. Create one with create_simulation."));
                }
                yield toJson(sims.stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getSimulationId().value().toString());
                    m.put("name", String.valueOf(s.getName()));
                    m.put("strategyId", s.getStrategyId().value().toString());
                    m.put("status", s.getStatus().name());
                    m.put("progress", s.getProgress());
                    if (s.getStartDate() != null) m.put("startDate", s.getStartDate().toString());
                    if (s.getEndDate() != null) m.put("endDate", s.getEndDate().toString());
                    return m;
                }).toList());
            }
            case "get_simulation" -> {
                SimulationId id = new SimulationId(UUID.fromString(args.get("simulationId").asText()));
                Simulation sim = getSimulationUseCase.execute(new GetSimulationQuery(id, userId));
                Map<String, Object> simMap = new LinkedHashMap<>();
                simMap.put("id", sim.getSimulationId().value().toString());
                simMap.put("name", String.valueOf(sim.getName()));
                simMap.put("strategyId", sim.getStrategyId().value().toString());
                simMap.put("status", sim.getStatus().name());
                simMap.put("progress", sim.getProgress());
                if (sim.getStartDate() != null) simMap.put("startDate", sim.getStartDate().toString());
                if (sim.getEndDate() != null) simMap.put("endDate", sim.getEndDate().toString());
                if (sim.getInitialCash() != null) {
                    simMap.put("initialCash", sim.getInitialCash().amount().toPlainString());
                    simMap.put("currency", sim.getInitialCash().currency().name());
                }
                if (sim.getStartedAt() != null) simMap.put("startedAt", sim.getStartedAt().toString());
                if (sim.getCompletedAt() != null) simMap.put("completedAt", sim.getCompletedAt().toString());
                yield toJson(simMap);
            }
            case "query_portfolio_snapshots" -> {
                int limit = args.has("limit") ? Math.min(args.get("limit").asInt(20), 200) : 20;
                QueryPortfolioSnapshotsQuery query = new QueryPortfolioSnapshotsQuery(
                    UUID.fromString(args.get("simulationId").asText()),
                    args.has("from") ? Instant.parse(args.get("from").asText()) : null,
                    args.has("to") ? Instant.parse(args.get("to").asText()) : null,
                    limit);
                List<PortfolioSnapshot> snapshots = queryPortfolioSnapshotsUseCase.execute(query);
                yield toJson(snapshots);
            }
            case "get_consensus_with_agents" -> {
                int limit = args.has("limit") ? Math.min(args.get("limit").asInt(5), 50) : 5;
                List<String> actionList = args.has("actions") && !args.get("actions").asText().isBlank()
                    ? List.of(args.get("actions").asText().split(",")) : null;
                GetConsensusWithAgentsCommand cmd = new GetConsensusWithAgentsCommand(
                    UUID.fromString(args.get("simulationId").asText()),
                    args.has("ticker") ? args.get("ticker").asText() : null,
                    actionList, limit, null, null, userId);
                List<ConsensusWithAgentsResponse> responses = getConsensusWithAgentsUseCase.execute(cmd);
                yield toJson(responses);
            }
            case "query_consensus_decisions" -> {
                int limit = args.has("limit") ? Math.min(args.get("limit").asInt(20), 200) : 20;
                List<String> actionList = args.has("actions") && !args.get("actions").asText().isBlank()
                    ? List.of(args.get("actions").asText().split(",")) : null;
                QueryConsensusDecisionsQuery query = new QueryConsensusDecisionsQuery(
                    UUID.fromString(args.get("simulationId").asText()),
                    args.has("from") ? Instant.parse(args.get("from").asText()) : null,
                    args.has("to") ? Instant.parse(args.get("to").asText()) : null,
                    null, null, actionList, limit, userId);
                List<ConsensusDecision> decisions = queryConsensusDecisionsUseCase.execute(query);
                yield toJson(decisions);
            }
            case "get_indicators" -> {
                Symbol sym = new Symbol(
                    args.get("ticker").asText().toUpperCase(),
                    InvestmentType.valueOf(args.get("investmentType").asText().toUpperCase()));
                IndicatorContext context = indicatorEnrichmentService.calculateIndicators(List.of(sym));
                IndicatorSnapshot snapshot = context.tickerIndicators().get(sym);

                // Auto-backfill if no indicator data available
                if (snapshot == null || snapshot.values().isEmpty()) {
                    String backfillNote = attemptAutoBackfill(sym);
                    if (backfillNote.contains("inserted")) {
                        IndicatorContext retryContext = indicatorEnrichmentService.calculateIndicators(List.of(sym));
                        IndicatorSnapshot retrySnapshot = retryContext.tickerIndicators().get(sym);
                        if (retrySnapshot != null && !retrySnapshot.values().isEmpty()) {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("indicators", retrySnapshot);
                            result.put("autoBackfillNote", backfillNote);
                            yield toJson(result);
                        }
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("warning", "No data available for " + sym.ticker());
                    result.put("autoBackfillNote", backfillNote);
                    yield toJson(result);
                }
                yield toJson(snapshot);
            }
            case "query_market_events" -> {
                int limit = args.has("limit") ? Math.min(args.get("limit").asInt(20), 200) : 20;
                QueryMarketEventsQuery query = new QueryMarketEventsQuery(
                    Instant.parse(args.get("from").asText()),
                    Instant.parse(args.get("to").asText()),
                    args.has("symbol") ? args.get("symbol").asText() : null,
                    null,
                    args.has("eventType") ? args.get("eventType").asText() : null,
                    limit);
                List<MarketEvent> events = queryMarketEventsUseCase.execute(query);
                if (events.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No market events found for the given time range. Try backfill_price_data to load historical data first."));
                }
                yield toJson(events);
            }
            case "backfill_price_data" -> {
                Granularity gran = Granularity.fromString(
                    args.has("granularity") ? args.get("granularity").asText() : "1Day");
                BackFillTickerPriceDataCommand cmd = new BackFillTickerPriceDataCommand(
                    args.get("ticker").asText().toUpperCase(),
                    args.get("assetType").asText().toUpperCase(),
                    parseDate(args.get("startDate").asText(), false),
                    parseDate(args.get("endDate").asText(), true),
                    gran);
                BackFillResult result = backFillTickerPriceDataUseCase.execute(cmd);
                yield toJson(Map.of(
                    "recordsInserted", result.recordsInserted(),
                    "recordsSkipped", result.recordsSkipped(),
                    "recordsFailed", result.recordsFailed()));
            }
            case "get_ticker_performance" -> {
                String perfTicker = args.get("ticker").asText().toUpperCase();
                Instant perfStart = parseDate(args.get("startDate").asText(), false);
                Instant perfEnd = parseDate(args.get("endDate").asText(), true);
                GetTickerPerformanceQuery query = new GetTickerPerformanceQuery(perfTicker, perfStart, perfEnd);
                TickerPerformanceResult perfResult = getTickerPerformanceUseCase.execute(query);

                // Auto-backfill if no data available
                if (!perfResult.dataAvailable()) {
                    InvestmentType perfType = args.has("investmentType")
                        ? InvestmentType.valueOf(args.get("investmentType").asText().toUpperCase())
                        : inferInvestmentType(perfTicker);
                    Symbol perfSym = new Symbol(perfTicker, perfType);
                    String backfillNote = attemptAutoBackfillForRange(perfSym, perfStart, perfEnd);
                    if (backfillNote.contains("inserted")) {
                        TickerPerformanceResult retryResult = getTickerPerformanceUseCase.execute(query);
                        if (retryResult.dataAvailable()) {
                            yield toJson(retryResult);
                        }
                    }
                    yield toJson(Map.of(
                        "ticker", perfTicker,
                        "dataAvailable", false,
                        "message", perfResult.message() != null ? perfResult.message() : "No data available",
                        "autoBackfillNote", backfillNote));
                }
                yield toJson(perfResult);
            }
            case "list_market_research" -> {
                List<MarketResearchSummary> summaries = listMarketResearchUseCase.execute(userId);
                if (summaries.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No market research found. Create one with create_market_research."));
                }
                yield toJson(summaries);
            }
            case "get_market_research_details" -> {
                UUID id = UUID.fromString(args.get("marketResearchId").asText());
                MarketResearchDetails details = getMarketResearchDetailsUseCase.execute(id, userId);
                yield toJson(details);
            }
            case "list_broker_accounts" -> {
                List<BrokerAccount> accounts = listBrokerAccountsUseCase.execute(
                    new ListBrokerAccountsQuery(userId));
                if (accounts.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No broker accounts connected. Set up a broker account in Settings."));
                }
                yield toJson(accounts.stream().map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId().value().toString());
                    m.put("name", a.getName());
                    m.put("type", a.getBrokerType().name());
                    m.put("status", a.getStatus().name());
                    if (a.getTotalBalance() != null) {
                        m.put("totalBalance", a.getTotalBalance().amount().toPlainString());
                        m.put("availableBalance", a.getAvailableBalance().amount().toPlainString());
                        m.put("currency", a.getTotalBalance().currency().name());
                    }
                    return m;
                }).toList());
            }
            case "create_strategy" -> {
                String missingParams = requireParams(args, "name", "markdownContent");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams
                        + ". Please provide: name and markdownContent (full strategy with YAML frontmatter)."));
                }
                String description = args.has("description") ? args.get("description").asText() : null;
                CreateStrategyCommand cmd = new CreateStrategyCommand(
                    args.get("name").asText(),
                    description,
                    args.get("markdownContent").asText(),
                    userId);
                StrategyId id = createStrategyUseCase.execute(cmd);
                yield toJson(Map.of("id", id.value().toString(), "message", "Strategy created successfully"));
            }
            case "create_simulation" -> {
                String missingParams = requireParams(args, "strategyId", "startDate", "endDate", "initialCash");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams
                        + ". Please provide: strategyId, startDate, endDate, initialCash."));
                }
                BrokerAccountId brokerId = args.has("brokerAccountId") && !args.get("brokerAccountId").asText().isBlank()
                    ? new BrokerAccountId(UUID.fromString(args.get("brokerAccountId").asText()))
                    : null;
                CreateSimulationCommand cmd = new CreateSimulationCommand(
                    args.has("name") ? args.get("name").asText() : null,
                    new StrategyId(UUID.fromString(args.get("strategyId").asText())),
                    parseDate(args.get("startDate").asText(), false),
                    parseDate(args.get("endDate").asText(), true),
                    Money.usd(new BigDecimal(args.get("initialCash").asText())),
                    brokerId, null, null, userId);
                SimulationId id = createSimulationUseCase.execute(cmd);
                yield toJson(Map.of("id", id.value().toString(), "message", "Simulation created successfully"));
            }
            case "start_simulation" -> {
                SimulationId id = new SimulationId(UUID.fromString(args.get("simulationId").asText()));
                startSimulationUseCase.execute(new StartSimulationCommand(id, userId));
                yield toJson(Map.of("id", id.value().toString(), "message", "Simulation started"));
            }
            case "create_market_research" -> {
                String missingParams = requireParams(args, "name", "markdownInstructions");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams
                        + ". Please call create_market_research with: name, markdownInstructions, intervalAmount, intervalUnit, and optionally targetAssets."));
                }
                Set<Symbol> symbols = Set.of();
                if (args.has("targetAssets") && !args.get("targetAssets").asText().isBlank()) {
                    symbols = parseTargetAssets(args.get("targetAssets").asText());
                }
                int intervalAmount = args.has("intervalAmount") ? args.get("intervalAmount").asInt() : 7;
                String intervalUnitStr = args.has("intervalUnit") && !args.get("intervalUnit").isNull()
                    ? args.get("intervalUnit").asText() : "DAYS";
                ResearchInterval interval = new ResearchInterval(
                    intervalAmount,
                    ResearchInterval.Unit.valueOf(intervalUnitStr.toUpperCase()));
                CreateMarketResearchCommand cmd = new CreateMarketResearchCommand(
                    args.get("name").asText(),
                    args.get("markdownInstructions").asText(),
                    symbols, interval, false, null, userId);
                MarketResearch created = createMarketResearchUseCase.execute(cmd);
                yield toJson(Map.of("id", created.getId().toString(), "message", "Market research created"));
            }
            case "execute_market_research" -> {
                UUID id = UUID.fromString(args.get("marketResearchId").asText());
                UUID executionId = executeMarketResearchUseCase.execute(id, userId);
                yield toJson(Map.of("executionId", executionId.toString(), "message", "Research execution started"));
            }
            case "delete_strategy" -> {
                StrategyId id = new StrategyId(UUID.fromString(args.get("strategyId").asText()));
                deleteStrategyUseCase.execute(new DeleteStrategyCommand(id, userId));
                yield toJson(Map.of("message", "Strategy deleted successfully"));
            }
            case "delete_simulation" -> {
                SimulationId id = new SimulationId(UUID.fromString(args.get("simulationId").asText()));
                deleteSimulationUseCase.execute(new DeleteSimulationCommand(id, userId));
                yield toJson(Map.of("message", "Simulation deleted successfully"));
            }
            case "delete_market_research" -> {
                UUID id = UUID.fromString(args.get("marketResearchId").asText());
                deleteMarketResearchUseCase.execute(id, userId);
                yield toJson(Map.of("message", "Market research deleted successfully"));
            }
            case "delete_benchmark" -> {
                BenchmarkId id = new BenchmarkId(UUID.fromString(args.get("benchmarkId").asText()));
                deleteBenchmarkUseCase.execute(new DeleteBenchmarkCommand(id, userId));
                yield toJson(Map.of("message", "Benchmark deleted successfully"));
            }
            case "compare_simulations" -> {
                String missingParams = requireParams(args, "simulationIds");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams
                        + ". Provide comma-separated simulation UUIDs (at least 1)."));
                }
                List<UUID> simIds = Arrays.stream(args.get("simulationIds").asText().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .toList();
                String name = args.has("name") && !args.get("name").asText().isBlank()
                    ? args.get("name").asText() : null;
                CompareSimulationsCommand cmd = new CompareSimulationsCommand(simIds, name, userId);
                com.quorumtrading.application.usecase.dto.CompareSimulationsResponse response =
                    compareSimulationsUseCase.execute(cmd);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("benchmarkId", response.benchmarkId().toString());
                result.put("benchmarkName", response.benchmarkName());
                result.put("simulationCount", response.simulationIds().size());
                result.put("warnings", response.warnings());
                result.put("message", "Benchmark created. View comparison at /benchmarks/" + response.benchmarkId());
                yield toJson(result);
            }
            case "create_benchmark" -> {
                String missingParams = requireParams(args, "strategyIds", "startDate");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams
                        + ". Provide strategyIds (comma-separated UUIDs) and startDate (YYYY-MM-DD)."));
                }
                List<UUID> strategyIds = Arrays.stream(args.get("strategyIds").asText().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .toList();
                Instant startDate = parseDate(args.get("startDate").asText(), false);
                Instant endDate = args.has("endDate") && !args.get("endDate").asText().isBlank()
                    ? parseDate(args.get("endDate").asText(), true) : null;
                String benchName = args.has("name") && !args.get("name").asText().isBlank()
                    ? args.get("name").asText() : null;
                BigDecimal capital = args.has("initialCapital") && !args.get("initialCapital").asText().isBlank()
                    ? new BigDecimal(args.get("initialCapital").asText()) : new BigDecimal("100000");
                String costPresetStr = args.has("costPreset") && !args.get("costPreset").asText().isBlank()
                    ? args.get("costPreset").asText() : "TYPICAL_RETAIL";
                MockBrokerConfig mockConfig = MockBrokerConfig.fromPreset(
                    Money.usd(capital),
                    com.quorumtrading.model.enums.CostPreset.valueOf(costPresetStr.toUpperCase()));
                CreateBenchmarkCommand cmd = new CreateBenchmarkCommand(
                    strategyIds, startDate, endDate, benchName, mockConfig, userId);
                com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse response =
                    createBenchmarkUseCase.execute(cmd);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("benchmarkId", response.benchmarkId().toString());
                result.put("benchmarkName", response.benchmarkName());
                result.put("simulationIds", response.simulationIds().stream()
                    .map(UUID::toString).toList());
                result.put("message", "Benchmark created with " + response.simulationIds().size()
                    + " simulations. View at /benchmarks/" + response.benchmarkId());
                yield toJson(result);
            }
            case "pause_simulation" -> {
                SimulationId id = new SimulationId(UUID.fromString(args.get("simulationId").asText()));
                pauseSimulationUseCase.execute(new PauseSimulationCommand(id, userId));
                yield toJson(Map.of("id", id.value().toString(), "message", "Simulation paused"));
            }
            case "sell_position" -> {
                String missingParams = requireParams(args, "simulationId", "ticker", "investmentType");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams));
                }
                SimulationId id = new SimulationId(UUID.fromString(args.get("simulationId").asText()));
                Symbol sym = new Symbol(
                    args.get("ticker").asText().toUpperCase(),
                    InvestmentType.valueOf(args.get("investmentType").asText().toUpperCase()));
                SellPositionResult sellResult = sellPositionUseCase.execute(id, sym, userId);
                Map<String, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("success", sellResult.success());
                resultMap.put("message", sellResult.message());
                if (sellResult.filledQuantity() != null) {
                    resultMap.put("filledQuantity", sellResult.filledQuantity().toPlainString());
                }
                if (sellResult.executionPrice() != null) {
                    resultMap.put("executionPrice", sellResult.executionPrice().toPlainString());
                }
                yield toJson(resultMap);
            }
            case "liquidate_all_positions" -> {
                SimulationId id = new SimulationId(UUID.fromString(args.get("simulationId").asText()));
                LiquidateAllResult liqResult = liquidateAllPositionsUseCase.execute(id, userId);
                Map<String, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("positionsLiquidated", liqResult.positionsLiquidated());
                resultMap.put("positionsFailed", liqResult.positionsFailed());
                if (!liqResult.errors().isEmpty()) {
                    resultMap.put("errors", liqResult.errors());
                }
                if (liqResult.cashAfterLiquidation() != null) {
                    resultMap.put("cashAfterLiquidation", liqResult.cashAfterLiquidation().toPlainString());
                }
                yield toJson(resultMap);
            }
            case "force_end_simulation" -> {
                SimulationId id = new SimulationId(UUID.fromString(args.get("simulationId").asText()));
                boolean liquidate = !args.has("liquidatePositions")
                    || !"false".equalsIgnoreCase(args.get("liquidatePositions").asText());
                ForceEndSimulationResult endResult = forceEndSimulationUseCase.execute(
                    new ForceEndSimulationCommand(id, liquidate, userId));
                Map<String, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("success", endResult.success());
                resultMap.put("liquidated", endResult.liquidated());
                resultMap.put("liquidatedCount", endResult.liquidatedCount());
                resultMap.put("message", endResult.message());
                yield toJson(resultMap);
            }
            case "list_benchmarks" -> {
                int limit = args.has("limit") ? Math.min(args.get("limit").asInt(20), 50) : 20;
                List<Benchmark> benchmarks = benchmarkRepository.findByOwnerId(userId, limit);
                if (benchmarks.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No benchmarks found. Create one with create_benchmark or compare_simulations."));
                }
                yield toJson(benchmarks.stream().map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getBenchmarkId().value().toString());
                    m.put("name", b.getName());
                    m.put("mode", b.isBacktest() ? "backtest" : "live");
                    m.put("startDate", b.getStartDate().toString());
                    if (b.getEndDate() != null) m.put("endDate", b.getEndDate().toString());
                    m.put("createdAt", b.getCreatedAt().toString());
                    return m;
                }).toList());
            }
            case "get_benchmark" -> {
                BenchmarkId id = new BenchmarkId(UUID.fromString(args.get("benchmarkId").asText()));
                Benchmark benchmark = benchmarkRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Benchmark not found: " + id.value()));
                List<Simulation> sims = simulationRepository.findByBenchmarkId(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", benchmark.getBenchmarkId().value().toString());
                result.put("name", benchmark.getName());
                result.put("mode", benchmark.isBacktest() ? "backtest" : "live");
                result.put("startDate", benchmark.getStartDate().toString());
                if (benchmark.getEndDate() != null) result.put("endDate", benchmark.getEndDate().toString());
                result.put("createdAt", benchmark.getCreatedAt().toString());
                result.put("simulations", sims.stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getSimulationId().value().toString());
                    m.put("name", String.valueOf(s.getName()));
                    m.put("strategyId", s.getStrategyId().value().toString());
                    m.put("status", s.getStatus().name());
                    if (s.getInitialCash() != null) {
                        m.put("initialCash", s.getInitialCash().amount().toPlainString());
                    }
                    return m;
                }).toList());
                yield toJson(result);
            }
            case "get_benchmark_overlays" -> {
                String missingParams = requireParams(args, "benchmarkId", "symbols");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams));
                }
                BenchmarkId overlayBenchmarkId = new BenchmarkId(UUID.fromString(args.get("benchmarkId").asText()));
                List<Symbol> symbolList = Arrays.stream(args.get("symbols").asText().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> new Symbol(s.toUpperCase(), InvestmentType.fromTicker(s)))
                    .toList();
                Money overlayCapital = args.has("initialCapital") && !args.get("initialCapital").asText().isBlank()
                    ? Money.usd(new BigDecimal(args.get("initialCapital").asText()))
                    : null;
                CalculateBenchmarkOverlaysCommand overlayCmd = new CalculateBenchmarkOverlaysCommand(
                    overlayBenchmarkId, symbolList, overlayCapital, userId);
                List<OverlayResult> overlayResults = calculateBenchmarkOverlaysUseCase.execute(overlayCmd);
                yield toJson(overlayResults.stream().map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("symbol", r.symbol());
                    m.put("assetType", r.assetType());
                    m.put("status", r.status().name());
                    if (r.returnPercent() != null) m.put("returnPercent", r.returnPercent().toPlainString());
                    if (r.dataPoints() != null) m.put("dataPointCount", r.dataPoints().size());
                    if (r.errorMessage() != null) m.put("errorMessage", r.errorMessage());
                    return m;
                }).toList());
            }
            case "pause_market_research" -> {
                UUID id = UUID.fromString(args.get("marketResearchId").asText());
                pauseMarketResearchUseCase.execute(id, userId);
                yield toJson(Map.of("message", "Market research paused"));
            }
            case "resume_market_research" -> {
                UUID id = UUID.fromString(args.get("marketResearchId").asText());
                resumeMarketResearchUseCase.execute(id, userId);
                yield toJson(Map.of("message", "Market research resumed"));
            }
            case "find_ticker" -> {
                String companyName = args.get("companyName").asText();
                BrokerAccountId findTickerBrokerId = args.has("brokerAccountId") && !args.get("brokerAccountId").asText().isBlank()
                    ? new BrokerAccountId(UUID.fromString(args.get("brokerAccountId").asText()))
                    : null;
                TickerSearchResult tickerResult = findTickerUseCase.execute(
                    new FindTickerQuery(companyName, userId, findTickerBrokerId));
                Map<String, Object> resultMap = new LinkedHashMap<>();
                if (tickerResult.ticker() != null) resultMap.put("ticker", tickerResult.ticker());
                resultMap.put("companyName", tickerResult.companyName());
                if (tickerResult.exchange() != null) resultMap.put("exchange", tickerResult.exchange());
                if (tickerResult.investmentType() != null) resultMap.put("investmentType", tickerResult.investmentType().name());
                resultMap.put("tradableOnBroker", tickerResult.tradableOnBroker());
                // resolution/confidence carry the distinctions the boolean cannot: whether a
                // negative means "does not exist" or "could not be checked", and whether a
                // positive was corroborated. Without them the caller can only guess.
                resultMap.put("resolution", tickerResult.resolution().name());
                resultMap.put("confidence", tickerResult.confidence().name());
                resultMap.put("listingType", tickerResult.listingType().name());
                if (!tickerResult.alternatives().isEmpty()) {
                    resultMap.put("alternatives", tickerResult.alternatives().stream()
                        .map(listing -> {
                            Map<String, Object> alt = new LinkedHashMap<>();
                            alt.put("ticker", listing.ticker());
                            if (listing.exchangeName() != null) alt.put("exchange", listing.exchangeName());
                            if (listing.currency() != null) alt.put("currency", listing.currency().name());
                            alt.put("type", listing.type().name());
                            alt.put("symbolSupported", listing.symbolSupported());
                            if (listing.tradableHere() != null) {
                                alt.put("tradableHere", listing.tradableHere());
                            }
                            if (listing.note() != null) alt.put("note", listing.note());
                            return alt;
                        })
                        .toList());
                }
                if (!tickerResult.warnings().isEmpty()) {
                    resultMap.put("warnings", tickerResult.warnings());
                }
                resultMap.put("message", tickerResult.message());
                yield toJson(resultMap);
            }
            case "prune_unused" -> {
                boolean removeStrategies = !args.has("removeStrategies")
                    || !"false".equalsIgnoreCase(args.get("removeStrategies").asText());
                boolean removeResearch = !args.has("removeResearch")
                    || !"false".equalsIgnoreCase(args.get("removeResearch").asText());
                PruneUnusedResult pruneResult = pruneUnusedUseCase.execute(
                    new PruneUnusedCommand(userId, removeStrategies, removeResearch));
                yield toJson(Map.of(
                    "deletedStrategies", pruneResult.deletedStrategies(),
                    "deletedResearch", pruneResult.deletedResearch(),
                    "message", "Pruned " + pruneResult.deletedStrategies() + " unused strategies and " + pruneResult.deletedResearch() + " unused research"
                ));
            }
            case "list_historical_events" -> {
                Optional<com.quorumtrading.model.enums.EventCategory> cat = args.has("category") && !args.get("category").asText().isBlank()
                    ? Optional.of(com.quorumtrading.model.enums.EventCategory.valueOf(args.get("category").asText().toUpperCase()))
                    : Optional.empty();
                Optional<com.quorumtrading.model.enums.AssetClass> ac = args.has("assetClass") && !args.get("assetClass").asText().isBlank()
                    ? Optional.of(com.quorumtrading.model.enums.AssetClass.valueOf(args.get("assetClass").asText().toUpperCase()))
                    : Optional.empty();
                Optional<com.quorumtrading.model.enums.MarketSentiment> sent = args.has("sentiment") && !args.get("sentiment").asText().isBlank()
                    ? Optional.of(com.quorumtrading.model.enums.MarketSentiment.valueOf(args.get("sentiment").asText().toUpperCase()))
                    : Optional.empty();
                List<com.quorumtrading.model.entity.HistoricalEvent> events = listHistoricalEventsUseCase.execute(
                    new com.quorumtrading.application.usecase.query.ListHistoricalEventsQuery(cat, ac, sent));
                if (events.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No historical events found matching the filters."));
                }
                yield toJson(events);
            }
            case "get_historical_event" -> {
                String slugOrKeyword = args.get("slugOrKeyword").asText();
                List<com.quorumtrading.model.entity.HistoricalEvent> events = getHistoricalEventUseCase.execute(
                    new com.quorumtrading.application.usecase.query.GetHistoricalEventQuery(slugOrKeyword));
                if (events.isEmpty()) {
                    yield toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No historical event found matching '" + slugOrKeyword + "'. Use list_historical_events to see all."));
                }
                yield toJson(events);
            }
            case "list_challenges" -> {
                var challenges = challengeRepository.findAllSorted(20);
                var result = challenges.stream().map(c -> {
                    var entries = simulationRepository.findByChallengeId(c.getChallengeId());
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", c.getChallengeId().value().toString());
                    m.put("name", c.getName());
                    m.put("ticker", c.getTicker().ticker());
                    m.put("initialCash", c.getInitialCash().amount());
                    m.put("simulationStartDate", c.getSimulationStartDate().toString());
                    m.put("simulationEndDate", c.getSimulationEndDate().toString());
                    m.put("participationClose", c.getParticipationClose().toString());
                    m.put("status", c.getStatus().name());
                    m.put("isBacktest", c.isBacktest());
                    m.put("acceptingEntries", c.isAcceptingEntries());
                    m.put("participantCount", entries.size());
                    return m;
                }).toList();
                yield toJson(result);
            }
            case "get_challenge" -> {
                ChallengeId id = new ChallengeId(UUID.fromString(args.get("challengeId").asText()));
                var challenge = challengeRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + id));
                var entries = simulationRepository.findByChallengeId(id);
                var m = new LinkedHashMap<String, Object>();
                m.put("id", challenge.getChallengeId().value().toString());
                m.put("name", challenge.getName());
                m.put("description", challenge.getDescription());
                m.put("ticker", challenge.getTicker().ticker());
                m.put("initialCash", challenge.getInitialCash().amount());
                m.put("simulationStartDate", challenge.getSimulationStartDate().toString());
                m.put("simulationEndDate", challenge.getSimulationEndDate().toString());
                m.put("participationOpen", challenge.getParticipationOpen().toString());
                m.put("participationClose", challenge.getParticipationClose().toString());
                m.put("status", challenge.getStatus().name());
                m.put("isBacktest", challenge.isBacktest());
                m.put("acceptingEntries", challenge.isAcceptingEntries());
                m.put("participantCount", entries.size());
                yield toJson(m);
            }
            case "join_challenge" -> {
                String missingParams = requireParams(args, "challengeId", "strategyId");
                if (missingParams != null) {
                    yield toJson(Map.of("error", "Missing required parameters: " + missingParams));
                }
                ChallengeId cid = new ChallengeId(UUID.fromString(args.get("challengeId").asText()));
                StrategyId sid = new StrategyId(UUID.fromString(args.get("strategyId").asText()));
                SimulationId simId = joinChallengeUseCase.execute(
                    new JoinChallengeCommand(cid, sid, userId));
                yield toJson(Map.of(
                    "message", "Successfully joined challenge. A simulation has been created and will start automatically.",
                    "simulationId", simId.value().toString()
                ));
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    private String toJson(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

    private String truncate(String result) {
        if (result != null && result.length() > MAX_RESULT_LENGTH) {
            return result.substring(0, MAX_RESULT_LENGTH) + "...(truncated)";
        }
        return result;
    }

    private String escapeJson(String text) {
        if (text == null) return "null";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /**
     * Check that all required parameters are present and non-null in the args.
     * @return comma-separated list of missing param names, or null if all present
     */
    private String requireParams(JsonNode args, String... paramNames) {
        List<String> missing = new ArrayList<>();
        for (String name : paramNames) {
            if (!args.has(name) || args.get(name).isNull()
                    || (args.get(name).isTextual() && args.get(name).asText().isBlank())) {
                missing.add(name);
            }
        }
        return missing.isEmpty() ? null : String.join(", ", missing);
    }

    private Set<Symbol> parseTargetAssets(String targetAssets) {
        Set<Symbol> symbols = new HashSet<>();
        for (String part : targetAssets.split(",")) {
            String[] split = part.trim().split(":");
            String ticker = split[0].trim().toUpperCase();
            InvestmentType type = split.length > 1
                ? InvestmentType.valueOf(split[1].trim().toUpperCase())
                : InvestmentType.STOCK;
            symbols.add(new Symbol(ticker, type));
        }
        return symbols;
    }

    /**
     * Parses a date string leniently, accepting both:
     * - Date only: "2026-01-01" → converted to start of day UTC
     * - Full ISO-8601: "2026-01-01T00:00:00Z" → parsed as-is
     *
     * @param dateStr The date string from the LLM
     * @param endOfDay If true, uses end of day (23:59:59) for date-only strings
     * @return Parsed Instant
     */
    private Instant parseDate(String dateStr, boolean endOfDay) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("Date cannot be empty");
        }

        String trimmed = dateStr.trim();

        // Try full ISO-8601 first
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException e) {
            // Fall through to date-only parsing
        }

        // Try date-only format (YYYY-MM-DD)
        try {
            LocalDate date = LocalDate.parse(trimmed);
            if (endOfDay) {
                return date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
            }
            return date.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Invalid date format: '" + dateStr + "'. Use YYYY-MM-DD or full ISO-8601 (e.g., 2026-01-01 or 2026-01-01T00:00:00Z)");
        }
    }

    /**
     * Attempt to auto-backfill recent price data for indicators (last ~300 calendar days).
     * Mirrors the logic in MarketDataTools.attemptAutoBackfill().
     */
    private String attemptAutoBackfill(Symbol sym) {
        try {
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofDays(AUTO_BACKFILL_CALENDAR_DAYS));
            BackFillTickerPriceDataCommand command = new BackFillTickerPriceDataCommand(
                sym.ticker(), sym.type().name(), start, end, Granularity.ONE_DAY);
            BackFillResult result = backFillTickerPriceDataUseCase.execute(command);
            LOG.infof("Chat: Auto-backfill for %s - inserted=%d, skipped=%d, failed=%d",
                sym.ticker(), result.recordsInserted(), result.recordsSkipped(), result.recordsFailed());
            if (result.recordsInserted() > 0) {
                return "Auto-backfill completed: inserted " + result.recordsInserted() + " daily bars for " + sym.ticker();
            }
            return "Auto-backfill completed but no data was available from the provider for " + sym.ticker();
        } catch (Exception e) {
            LOG.warnf("Chat: Auto-backfill failed for %s: %s", sym.ticker(), e.getMessage());
            return "Auto-backfill failed for " + sym.ticker() + ": " + e.getMessage();
        }
    }

    /**
     * Attempt to auto-backfill price data for a specific date range (for ticker performance).
     */
    private String attemptAutoBackfillForRange(Symbol sym, Instant start, Instant end) {
        try {
            BackFillTickerPriceDataCommand command = new BackFillTickerPriceDataCommand(
                sym.ticker(), sym.type().name(), start, end, Granularity.ONE_DAY);
            BackFillResult result = backFillTickerPriceDataUseCase.execute(command);
            LOG.infof("Chat: Auto-backfill for %s (%s to %s) - inserted=%d, skipped=%d, failed=%d",
                sym.ticker(), start, end, result.recordsInserted(), result.recordsSkipped(), result.recordsFailed());
            if (result.recordsInserted() > 0) {
                return "Auto-backfill completed: inserted " + result.recordsInserted() + " daily bars for " + sym.ticker();
            }
            return "Auto-backfill completed but no data was available from the provider for " + sym.ticker();
        } catch (Exception e) {
            LOG.warnf("Chat: Auto-backfill for %s (%s to %s) failed: %s", sym.ticker(), start, end, e.getMessage());
            return "Auto-backfill failed for " + sym.ticker() + ": " + e.getMessage();
        }
    }

    /**
     * Infer investment type from ticker name when not explicitly provided.
     * Falls back to STOCK for unknown tickers.
     */
    private static InvestmentType inferInvestmentType(String ticker) {
        return KNOWN_CRYPTO.contains(ticker.toUpperCase()) ? InvestmentType.CRYPTO : InvestmentType.STOCK;
    }
}
