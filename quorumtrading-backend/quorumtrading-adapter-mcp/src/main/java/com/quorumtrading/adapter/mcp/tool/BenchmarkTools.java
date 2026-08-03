package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpBenchmarkDetailDto;
import com.quorumtrading.adapter.mcp.dto.McpBenchmarkDto;
import com.quorumtrading.adapter.mcp.dto.McpBenchmarkEntryDto;
import com.quorumtrading.adapter.mcp.dto.McpCreatedResponse;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase;
import com.quorumtrading.application.usecase.CalculateBenchmarkOverlaysUseCase;
import com.quorumtrading.application.usecase.ComposeBenchmarkUseCase;
import com.quorumtrading.application.usecase.CreateBenchmarkUseCase;
import com.quorumtrading.application.usecase.DeleteBenchmarkUseCase;
import com.quorumtrading.application.usecase.command.CalculateBenchmarkOverlaysCommand;
import com.quorumtrading.application.usecase.command.ComposeBenchmarkCommand;
import com.quorumtrading.application.usecase.command.CreateBenchmarkCommand;
import com.quorumtrading.application.usecase.command.DeleteBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.ComposeBenchmarkResponse;
import com.quorumtrading.application.usecase.query.BuildBenchmarkLeaderboardQuery;
import com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.OverlayResult;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP tools for benchmark management.
 * A benchmark groups multiple simulations running over the same time period
 * with different strategies, enabling head-to-head strategy comparison.
 */
@Singleton
@ActivateRequestContext
public class BenchmarkTools extends McpToolBase {

    @Inject
    CreateBenchmarkUseCase createBenchmarkUseCase;

    @Inject
    DeleteBenchmarkUseCase deleteBenchmarkUseCase;

    @Inject
    ComposeBenchmarkUseCase composeBenchmarkUseCase;

    @Inject
    CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase;

    @Inject
    BuildBenchmarkLeaderboardUseCase buildBenchmarkLeaderboardUseCase;

    @Inject
    BenchmarkRepository benchmarkRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    com.quorumtrading.application.usecase.EstimateBacktestCostUseCase estimateBacktestCostUseCase;

    @Inject
    McpDtoMapper mapper;

    @Inject
    com.quorumtrading.adapter.mcp.mapper.UiLinks uiLinks;

    @Inject
    McpUserContext userContext;

    @Inject
    com.quorumtrading.application.service.AuthorizationService authorizationService;

    @Inject
    com.quorumtrading.model.valueobject.NewsDataAvailability newsDataAvailability;

    @Tool(name = "create_benchmark", description = "Create a benchmark to compare multiple strategies head-to-head. "
        + "A benchmark creates one simulation per strategy over the same time period, enabling fair comparison. "
        + "Simulations start automatically in the background. "
        + "For backtests, provide both startDate and endDate. For live mode, omit endDate. "
        + "By default uses mock broker with $100,000 initial capital per strategy. "
        + "COST WARNING: Backtests > 1 month consume significant AI budget (4+ agent calls per trading day per strategy). "
        + "Inform the user about cost before creating long backtests, then set userInformedOfCost=true.")
    public ToolResponse createBenchmark(
        @ToolArg(description = "Comma-separated list of strategy UUIDs to benchmark against each other") String strategyIds,
        @ToolArg(description = "Benchmark start date in ISO-8601 format (e.g., 2024-01-01T00:00:00Z)") String startDate,
        @ToolArg(description = "Benchmark end date in ISO-8601 format (e.g., 2024-12-31T23:59:59Z). Omit for live mode.") Optional<String> endDate,
        @ToolArg(description = "Optional display name for the benchmark") Optional<String> name,
        @ToolArg(description = "Initial capital per strategy in USD (default: 100000)") Optional<String> initialCapital,
        @ToolArg(description = "Cost preset: ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION (default: TYPICAL_RETAIL). " +
            "Costs are asset-type-aware: TYPICAL_RETAIL = 0% stocks, 0.25% crypto (Alpaca-aligned).") Optional<String> costPreset,
        @ToolArg(description = "Set to 'true' after informing the user about AI budget cost for backtests longer than 1 month. Required for backtests > 31 days.") Optional<String> userInformedOfCost
    ) {
        return execute(() -> {
            UserId userId = userContext.getUserId();

            // Parse strategy IDs
            List<UUID> parsedStrategyIds = new ArrayList<>();
            for (String id : strategyIds.split(",")) {
                parsedStrategyIds.add(UUID.fromString(id.trim()));
            }

            // Parse dates
            Instant start = parseDate(startDate);
            Instant end = endDate.map(this::parseDate).orElse(null);

            // Cost check: benchmarks multiply cost by strategy count
            if (end != null) {
                long durationDays = Duration.between(start, end).toDays();
                if (durationDays > 31 && !"true".equals(userInformedOfCost.orElse(null))) {
                    String costMessage = buildBenchmarkCostMessage(durationDays, parsedStrategyIds);
                    throw new IllegalArgumentException(costMessage);
                }
            }

            // Build mock broker config
            BigDecimal capital = parseBigDecimal(initialCapital.orElse("100000"), "initialCapital");
            CostPreset preset = parseEnum(CostPreset.class, costPreset.orElse("TYPICAL_RETAIL"), "costPreset");
            MockBrokerConfig mockConfig = MockBrokerConfig.fromPreset(
                new Money(capital, Money.Currency.USD),
                preset
            );

            CreateBenchmarkCommand command = new CreateBenchmarkCommand(
                parsedStrategyIds,
                start,
                end,
                name.orElse(null),
                mockConfig,
                userId
            );

            CreateBenchmarkResponse response = createBenchmarkUseCase.execute(command);

            logger.info("MCP: Created benchmark {} with {} simulations",
                response.benchmarkId(), response.simulationIds().size());

            // A single strategy is a supported case, not an error: there is nothing to rank, so the
            // service creates the simulation and no benchmark, and benchmarkId comes back null.
            // Dereferencing it raised a raw Java NullPointerException at the MCP boundary — while
            // the simulation it had already started ran on, billing agents, with the caller holding
            // no id for it. A failed call that silently spends money is the worst possible shape.
            boolean standalone = response.benchmarkId() == null;

            var responseMap = new LinkedHashMap<String, Object>();
            responseMap.put("id", standalone ? null : response.benchmarkId().toString());
            String benchmarkUiUrl = standalone ? null : uiLinks.benchmark(response.benchmarkId().toString());
            if (benchmarkUiUrl != null) {
                responseMap.put("uiUrl", benchmarkUiUrl);
            }
            responseMap.put("simulationIds", response.simulationIds().stream().map(UUID::toString).toList());
            responseMap.put("simulationCount", response.simulationIds().size());
            String message = standalone
                ? "Started " + response.simulationIds().size() + " standalone simulation(s) — no "
                    + "benchmark was created, because a benchmark ranks strategies against each "
                    + "other and one strategy has nothing to rank against. The simulation ids are "
                    + "above and the run is already starting. To compare it with something, either "
                    + "pass two or more strategyIds, or run the other strategy separately and group "
                    + "the finished simulations with compose_benchmark."
                : "Benchmark '" + response.benchmarkName() + "' created with "
                    + response.simulationIds().size() + " simulations. Simulations are starting automatically.";

            // Append cost estimate for backtests
            if (end != null) {
                try {
                    long days = Duration.between(start, end).toDays();
                    var estimate = estimateBacktestCostUseCase.estimateForBenchmark(
                        parsedStrategyIds, days, userId);
                    message += String.format(" Estimated total cost: %s (~%d LLM calls, %s)",
                        estimate.formattedCost(), estimate.totalLLMCalls(), estimate.modelName());
                } catch (Exception e) {
                    logger.debug("Cost estimation for benchmark response failed: {}", e.getMessage());
                }
            }

            // Warn about news data cutoff if any strategy uses news-dependent agents
            if (start != null && newsDataAvailability.isBeforeCutoff(start)) {
                message += buildNewsAvailabilityWarning(parsedStrategyIds, start);
            }

            responseMap.put("message", message);
            return toJson(responseMap);
        });
    }

    @Tool(name = "list_benchmarks", description = "List your benchmarks. Returns benchmark summaries including ID, name, mode (backtest/live), date range, and simulation count.")
    public ToolResponse listBenchmarks(
        @ToolArg(description = "Maximum number of results (default 20, max 50)") Optional<Integer> limit
    ) {
        return execute(() -> {
            int effectiveLimit = parseLimit(limit, 20, 50);
            int fetchLimit = effectiveLimit + 1;

            // Owner-scoped, and resolving the user is also what makes this tool require a token:
            // /mcp is permitted at the HTTP layer, so a tool that never calls getUserId() is
            // reachable anonymously. This previously used findAllSorted, returning every user's
            // benchmarks -- and their strategy names -- to any caller.
            Page<Benchmark> page = paginate(
                benchmarkRepository.findByOwnerId(userContext.getUserId(), fetchLimit), effectiveLimit);

            List<McpBenchmarkDto> dtos = page.items().stream()
                .map(benchmark -> {
                    List<Simulation> simulations = simulationRepository.findByBenchmarkId(
                        benchmark.getBenchmarkId()
                    );
                    List<String> strategyNames = simulations.stream()
                        .map(sim -> strategyRepository.findById(sim.getStrategyId())
                            .map(Strategy::getName).orElse("Unknown Strategy"))
                        .toList();
                    return mapper.toBenchmarkDto(benchmark, simulations, strategyNames);
                })
                .toList();

            return toJson(new McpPaginatedResponse<>(dtos, dtos.size(), page.hasMore()));
        });
    }

    @Tool(name = "get_benchmark", description = "Get detailed information about a specific benchmark including ranked strategy entries with return percentages. "
        + "Entries are sorted by return percentage descending (best performer first).")
    public ToolResponse getBenchmark(
        @ToolArg(description = "The benchmark UUID") String benchmarkId
    ) {
        return execute(() -> {
            BenchmarkId id = new BenchmarkId(UUID.fromString(benchmarkId));
            Benchmark benchmark = benchmarkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Benchmark not found: " + benchmarkId));
            // Resolving the user both requires a token (/mcp is open at the HTTP layer) and
            // gives us the viewer to authorize against -- this was a bare lookup by id.
            authorizationService.requireView(userContext.getUserId(), benchmark);

            List<Simulation> simulations = simulationRepository.findByBenchmarkId(id);
            // Resolve strategy names here too. This used the two-argument overload, which leaves
            // strategyNames null, so the same benchmark reported its strategies through
            // list_benchmarks but null through get_benchmark — the detail view being strictly
            // less informative than the list view it drills into.
            List<String> strategyNames = simulations.stream()
                .map(sim -> strategyRepository.findById(sim.getStrategyId())
                    .map(Strategy::getName).orElse("Unknown Strategy"))
                .toList();
            McpBenchmarkDto benchmarkDto = mapper.toBenchmarkDto(benchmark, simulations, strategyNames);

            // Return baseline and ranking live in the application layer, shared with the REST
            // leaderboard; the two used to compute them separately and disagreed on both.
            List<McpBenchmarkEntryDto> entries = buildBenchmarkLeaderboardUseCase
                .execute(new BuildBenchmarkLeaderboardQuery(id))
                .entries().stream()
                .map(mapper::toBenchmarkEntryDto)
                .toList();

            return toJson(new McpBenchmarkDetailDto(benchmarkDto, entries));
        });
    }

    @Tool(name = "delete_benchmark", description = "Delete a benchmark. Its member simulations are NOT deleted — "
        + "they survive as standalone simulations along with their portfolio snapshots and trade history, "
        + "and can be re-grouped with compose_benchmark. Delete them individually with delete_simulation if "
        + "that is what you want. This operation is permanent.")
    public ToolResponse deleteBenchmark(
        @ToolArg(description = "The benchmark UUID to delete") String benchmarkId
    ) {
        return execute(() -> {
            BenchmarkId id = new BenchmarkId(UUID.fromString(benchmarkId));
            deleteBenchmarkUseCase.execute(new DeleteBenchmarkCommand(id, userContext.getUserId()));
            logger.info("MCP: Deleted benchmark {}", benchmarkId);

            // Must match the tool description: V88 made the simulations FK ON DELETE SET NULL,
            // so members survive as standalone simulations.
            return toJson(new McpCreatedResponse(benchmarkId,
                "Benchmark deleted. Its simulations were kept as standalone simulations, "
                + "along with their portfolio snapshots and trade history."));
        });
    }

    @Tool(name = "compose_benchmark", description = "Compose a benchmark from existing simulations. "
        + "Can create a new benchmark or add simulations to an existing one. "
        + "Unlike create_benchmark, this does NOT require matching dates or capital — "
        + "dates are derived from the simulations. Re-adding a simulation that is already in THIS "
        + "benchmark is a no-op (idempotent), but a simulation belonging to a DIFFERENT benchmark is "
        + "rejected — remove it from that one first, or pass its benchmarkId to add to it instead.")
    public ToolResponse composeBenchmark(
        @ToolArg(description = "Comma-separated list of simulation UUIDs to include (at least one required)") String simulationIds,
        @ToolArg(description = "Optional benchmark UUID to add simulations to. Omit to create a new benchmark.") Optional<String> benchmarkId,
        @ToolArg(description = "Optional display name for a new benchmark (ignored when adding to existing)") Optional<String> name
    ) {
        return execute(() -> {
            UserId userId = userContext.getUserId();

            List<SimulationId> parsedIds = new ArrayList<>();
            for (String id : simulationIds.split(",")) {
                parsedIds.add(new SimulationId(UUID.fromString(id.trim())));
            }

            BenchmarkId bmId = benchmarkId
                .filter(s -> !s.isBlank())
                .map(s -> new BenchmarkId(UUID.fromString(s)))
                .orElse(null);

            ComposeBenchmarkCommand command = new ComposeBenchmarkCommand(
                parsedIds, bmId, name.orElse(null), userId);

            ComposeBenchmarkResponse response = composeBenchmarkUseCase.execute(command);

            logger.info("MCP: Composed benchmark {} with {} simulations ({} newly added)",
                response.benchmarkId(), response.totalCount(), response.addedCount());

            var responseMap = new LinkedHashMap<String, Object>();
            responseMap.put("benchmarkId", response.benchmarkId().toString());
            String benchmarkUiUrl = uiLinks.benchmark(response.benchmarkId().toString());
            if (benchmarkUiUrl != null) {
                responseMap.put("uiUrl", benchmarkUiUrl);
            }
            responseMap.put("name", response.name());
            responseMap.put("totalSimulations", response.totalCount());
            responseMap.put("addedSimulations", response.addedCount());
            String action = bmId == null ? "created" : "updated";
            responseMap.put("message", String.format("Benchmark '%s' %s — %d simulations total (%d newly added)",
                response.name(), action, response.totalCount(), response.addedCount()));
            return toJson(responseMap);
        });
    }

    @Tool(name = "get_benchmark_overlays",
        description = "Compute buy-and-hold reference overlays for a benchmark. "
            + "Shows what the portfolio would be worth if you simply bought and held a reference asset "
            + "(e.g., SPY for S&P 500, QQQ for Nasdaq, BTC for Bitcoin). "
            + "Use to answer 'Did my strategy beat the market?' — compare the overlay's returnPercent "
            + "against the benchmark entry's. Returns the curve's endpoints (initialValue, finalValue "
            + "and their timestamps) plus a point count; the full time series is on the REST endpoint "
            + "named in the response, because it would swamp an MCP result.")
    public ToolResponse getBenchmarkOverlays(
        @ToolArg(description = "The benchmark UUID") String benchmarkId,
        @ToolArg(description = "Comma-separated ticker symbols (e.g., SPY,QQQ,BTC)") String symbols,
        @ToolArg(description = "Optional initial capital in USD (defaults to first simulation's initial cash)") Optional<String> initialCapital
    ) {
        return execute(() -> {
            BenchmarkId id = new BenchmarkId(UUID.fromString(benchmarkId));

            List<Symbol> symbolList = java.util.Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> SymbolParser.parse(s, InvestmentType.fromTicker(s)))
                .toList();

            Money capital = initialCapital
                .filter(s -> !s.isBlank())
                .map(s -> Money.usd(parseBigDecimal(s, "initialCapital")))
                .orElse(null);

            CalculateBenchmarkOverlaysCommand command = new CalculateBenchmarkOverlaysCommand(
                id, symbolList, capital, userContext.getUserId());

            List<OverlayResult> results = calculateBenchmarkOverlaysUseCase.execute(command);

            logger.info("MCP: Computed {} overlays for benchmark {}", results.size(), benchmarkId);

            return toJson(results.stream().map(r -> {
                var map = new LinkedHashMap<String, Object>();
                map.put("symbol", r.symbol());
                map.put("assetType", r.assetType());
                map.put("status", r.status().name());
                if (r.returnPercent() != null) map.put("returnPercent", r.returnPercent().toPlainString());
                // The endpoints of the curve, not just its length. returnPercent is scale-invariant,
                // so with only a point count the initialCapital argument was unobservable — passing
                // it and omitting it produced byte-identical output, which reads as a no-op
                // parameter. These two values are what it actually moves, and they are what a
                // caller needs to put the reference asset beside their own run's currentValue.
                if (r.dataPoints() != null && !r.dataPoints().isEmpty()) {
                    var points = r.dataPoints();
                    var first = points.get(0);
                    var last = points.get(points.size() - 1);
                    map.put("dataPointCount", points.size());
                    map.put("initialValue", first.portfolioValue().toPlainString());
                    map.put("finalValue", last.portfolioValue().toPlainString());
                    map.put("firstPointAt", first.timestamp().toString());
                    map.put("lastPointAt", last.timestamp().toString());
                    // The full series would swamp an MCP result; it is on the REST endpoint.
                    map.put("fullSeries", "GET /api/v1/benchmarks/" + benchmarkId
                            + "/overlays?symbols=" + r.symbol());
                } else if (r.dataPoints() != null) {
                    map.put("dataPointCount", 0);
                }
                if (r.errorMessage() != null) map.put("errorMessage", r.errorMessage());
                return map;
            }).toList());
        });
    }



    /**
     * Build a cost-aware message for benchmark backtests > 31 days.
     * Falls back to the generic message if estimation fails.
     */
    private String buildBenchmarkCostMessage(long durationDays, List<UUID> strategyIds) {
        try {
            var estimate = estimateBacktestCostUseCase.estimateForBenchmark(
                strategyIds, durationDays, userContext.getUserId());
            return "This benchmark spans " + durationDays + " days across " + strategyIds.size() + " strategies. "
                + "Estimated cost: " + estimate.formattedCost()
                + " (" + estimate.formattedRange() + " range, ~" + estimate.totalLLMCalls()
                + " LLM calls using " + estimate.modelName() + "). "
                + "Please inform the user about the cost, "
                + "then retry with userInformedOfCost=true.";
        } catch (Exception e) {
            logger.warn("Cost estimation failed, using generic message: {}", e.getMessage());
            return "This benchmark spans " + durationDays + " days across " + strategyIds.size() + " strategies. "
                + "Each strategy triggers 4+ AI agent consensus calls per trading day — "
                + "that's ~" + (durationDays * strategyIds.size() * 4) + " total agent calls. "
                + "Please inform the user about the cost implications, "
                + "then retry with userInformedOfCost=true. Never refuse to create the benchmark — just ensure the user knows the cost.";
        }
    }


    /**
     * Build a news data availability warning for a benchmark if any strategy uses news-dependent agents.
     */
    private String buildNewsAvailabilityWarning(List<UUID> strategyIds, Instant startDate) {
        try {
            for (UUID sid : strategyIds) {
                Strategy strategy = strategyRepository.findById(
                    new com.quorumtrading.model.valueobject.StrategyId(sid)).orElse(null);
                if (strategy == null) continue;

                var config = new com.quorumtrading.model.orchestration.StrategyConfigParser().parse(strategy.getContent());
                var agents = config.getEffectiveEnabledAgents();
                if (agents.contains(com.quorumtrading.model.enums.AgentRole.FUNDAMENTAL)
                        || agents.contains(com.quorumtrading.model.enums.AgentRole.SENTIMENT)) {
                    return String.format(". WARNING: This benchmark starts before %s, but news data is only available from that date. " +
                        "One or more strategies use Fundamental/Sentiment agents that rely on news data — " +
                        "consensus decisions before %s will have no news context, potentially reducing accuracy",
                        newsDataAvailability.earliestNewsDate(), newsDataAvailability.earliestNewsDate());
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to check news availability for benchmark strategies: {}", e.getMessage());
        }
        return "";
    }
}
