package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateBenchmarkRequest;
import com.quorumtrading.adapter.rest.dto.response.BenchmarkChartDataResponse;
import com.quorumtrading.adapter.rest.dto.response.CreateBenchmarkResponse;
import com.quorumtrading.adapter.rest.dto.response.BenchmarkEntryResponse;
import com.quorumtrading.adapter.rest.dto.response.BenchmarkResponse;
import com.quorumtrading.adapter.rest.dto.response.OverlayDataPointResponse;
import com.quorumtrading.adapter.rest.dto.response.OverlayResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.mapper.BenchmarkMapper;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CalculateBenchmarkOverlaysUseCase;
import com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase;
import com.quorumtrading.application.usecase.CreateBenchmarkUseCase;
import com.quorumtrading.application.usecase.DeleteBenchmarkUseCase;
import com.quorumtrading.application.usecase.command.CalculateBenchmarkOverlaysCommand;
import com.quorumtrading.application.usecase.dto.BenchmarkLeaderboard;
import com.quorumtrading.application.usecase.query.BuildBenchmarkLeaderboardQuery;
import com.quorumtrading.application.usecase.command.CreateBenchmarkCommand;
import com.quorumtrading.application.usecase.command.DeleteBenchmarkCommand;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.OverlayResult;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.UUID;

/**
 * REST resource for benchmark management.
 * Provides endpoints for creating, listing, and retrieving benchmarks with simulations.
 *
 * <p>A benchmark groups multiple simulations running over the same time period
 * with different strategies, enabling competitive comparison and ranking.</p>
 *
 * <p>This adapter delegates to application layer use cases following hexagonal architecture.</p>
 */
@Path("/api/v1/benchmarks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@io.quarkus.security.Authenticated
@Tag(name = "Benchmarks", description = "Benchmark management for strategy competitions")
public class BenchmarkResource {

    private static final Logger LOG = Logger.getLogger(BenchmarkResource.class);

    @Inject
    CreateBenchmarkUseCase createBenchmarkUseCase;

    @Inject
    DeleteBenchmarkUseCase deleteBenchmarkUseCase;

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
    PortfolioRepository portfolioRepository;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    BenchmarkMapper mapper;

    // Chart colors matching frontend (quorumtrading-frontend/app/lib/utils/colors.ts)
    private static final String[] CHART_COLORS = {
        "#4AA3FF",  // Blue
        "#9D4EDD",  // Purple
        "#06FFA5",  // Green
        "#FF6B35",  // Orange
        "#FF006E",  // Red
        "#00F5FF",  // Cyan
        "#FFD60A"   // Yellow
    };

    @POST
    @Operation(
        summary = "Create benchmark with multiple strategies",
        description = "Creates a new benchmark and simulations for each strategy. " +
            "Simulations start automatically in the background. Returns 202 ACCEPTED since processing is asynchronous."
    )
    @APIResponse(
        responseCode = "202",
        description = "Benchmark created, simulations starting in background",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = CreateBenchmarkResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - validation failed or invalid strategy IDs",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "One or more strategies not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response createBenchmark(
            @RequestBody(description = "Benchmark creation request", required = true)
            @Valid CreateBenchmarkRequest request,
            @Context UriInfo uriInfo) {

        LOG.infof("Creating benchmark with %d strategies", request.strategyIds().size());

        try {
            // Get current user ID (required when mockConfig is provided)
            UserId userId = currentUser.getCurrentUserId();

            // Map DTO to command
            CreateBenchmarkCommand command = mapper.toCommand(request, userId);

            // Execute use case (creates benchmark + simulations, fires async events)
            com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse response =
                createBenchmarkUseCase.execute(command);

            // Convert to REST DTO
            CreateBenchmarkResponse dto = mapper.toCreateResponseDTO(response);

            // Build Location header
            Response.ResponseBuilder responseBuilder = Response.accepted(dto);
            if (dto.benchmarkId() != null) {
                URI location = uriInfo.getAbsolutePathBuilder()
                    .path(dto.benchmarkId())
                    .build();
                responseBuilder.location(location);
                LOG.infof("Created benchmark %s with %d simulations",
                    dto.benchmarkId(), dto.simulationIds().size());
            } else {
                LOG.infof("Created standalone simulation %s", dto.simulationIds().getFirst());
            }

            // Return 202 ACCEPTED (async processing)
            return responseBuilder.build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Failed to create benchmark: %s", e.getMessage());
            throw new BadRequestException(e.getMessage());
        }
    }

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Get benchmark by ID",
        description = "Retrieves a specific benchmark with its simulation IDs"
    )
    @APIResponse(
        responseCode = "200",
        description = "Benchmark retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BenchmarkResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid UUID format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Benchmark not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getBenchmark(
            @Parameter(description = "Benchmark unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse UUID and fetch benchmark
            BenchmarkId benchmarkId = new BenchmarkId(UUID.fromString(id));
            Benchmark benchmark = benchmarkRepository.findById(benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found with ID: " + id));

            // Fetch simulations for this benchmark
            List<Simulation> simulations = simulationRepository.findByBenchmarkId(benchmarkId);

            // Convert to DTO
            BenchmarkResponse response = mapper.toResponseDTO(benchmark, simulations);

            LOG.debugf("Retrieved benchmark %s with %d simulations", id, simulations.size());

            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid benchmark ID format: %s", id);
            throw new BadRequestException("Invalid UUID format: " + id);
        }
    }

    @GET
    @Operation(
        summary = "List all benchmarks",
        description = "Retrieves all benchmarks with their simulation IDs"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of benchmarks retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BenchmarkResponse.class,
                type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    public Response listBenchmarks() {
        LOG.debug("Listing all benchmarks");

        // Fetch all benchmarks
        List<Benchmark> benchmarks = benchmarkRepository.findAll();

        // Convert to DTOs with simulation IDs
        List<BenchmarkResponse> response = benchmarks.stream()
            .map(benchmark -> {
                // Fetch simulations for this benchmark
                List<Simulation> simulations = simulationRepository.findByBenchmarkId(
                    benchmark.getBenchmarkId()
                );
                return mapper.toResponseDTO(benchmark, simulations);
            })
            .collect(java.util.stream.Collectors.toList());

        LOG.debugf("Retrieved %d benchmarks", response.size());

        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}/entries")
    @Operation(
        summary = "Get benchmark entries",
        description = "Retrieves top N simulation entries sorted by return percentage descending"
    )
    @APIResponse(
        responseCode = "200",
        description = "Benchmark entries retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BenchmarkEntryResponse.class,
                type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid UUID format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Benchmark not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getBenchmarkEntries(
            @Parameter(description = "Benchmark unique identifier", required = true)
            @PathParam("id") String id,
            @Parameter(description = "Number of top entries to return", example = "3")
            @QueryParam("limit") @DefaultValue("3") int limit) {

        BenchmarkId benchmarkId;
        try {
            benchmarkId = new BenchmarkId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid benchmark ID format: %s", id);
            throw new BadRequestException("Invalid UUID format: " + id);
        }

        BenchmarkLeaderboard leaderboard;
        try {
            leaderboard = buildBenchmarkLeaderboardUseCase.execute(
                new BuildBenchmarkLeaderboardQuery(benchmarkId));
        } catch (NoSuchElementException e) {
            throw new NotFoundException("Benchmark not found with ID: " + id);
        }

        // Entries without a real snapshot carry a seed-cash placeholder, not a measurement;
        // a performance table shows nothing rather than a fabricated 0%.
        List<BenchmarkEntryResponse> entries = leaderboard.entries().stream()
            .filter(BenchmarkLeaderboard.Entry::measurable)
            .limit(limit)
            .map(this::toEntryResponse)
            .toList();

        LOG.debugf("Retrieved %d benchmark entries for benchmark %s (limit=%d)",
            Integer.valueOf(entries.size()), id, Integer.valueOf(limit));

        return Response.ok(entries).build();
    }

    private BenchmarkEntryResponse toEntryResponse(BenchmarkLeaderboard.Entry entry) {
        // Colour follows the simulation's position in the benchmark, not its rank, so a
        // simulation keeps its colour when the ranking shifts. Matches the frontend's
        // assignment in quorumtrading-frontend/app/lib/utils/colors.ts.
        String color = CHART_COLORS[entry.benchmarkOrdinal() % CHART_COLORS.length];
        return new BenchmarkEntryResponse(
            entry.simulationId().value().toString(),
            entry.simulationName(),
            entry.strategyName(),
            color,
            entry.currentValue() != null ? entry.currentValue().amount() : null,
            entry.returnPercent(),
            entry.status().name()
        );
    }

    @GET
    @Path("/{id}/chart-data")
    @Operation(
        summary = "Get compact chart data for a benchmark",
        description = "Returns a compact columnar format with daily total-value per simulation. "
            + "Replaces N separate snapshot fetches with a single query."
    )
    @APIResponse(
        responseCode = "200",
        description = "Chart data retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BenchmarkChartDataResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Benchmark not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getBenchmarkChartData(
            @Parameter(description = "Benchmark unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            BenchmarkId benchmarkId = new BenchmarkId(UUID.fromString(id));
            benchmarkRepository.findById(benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found with ID: " + id));

            // 1. Get simulations for this benchmark
            List<Simulation> simulations = simulationRepository.findByBenchmarkId(benchmarkId);
            if (simulations.isEmpty()) {
                return Response.ok(new BenchmarkChartDataResponse(List.of(), List.of())).build();
            }

            // 2. Build simulation info list and collect strategy IDs
            List<UUID> simulationUuids = new ArrayList<>();
            Map<UUID, Simulation> simMap = new LinkedHashMap<>();
            Map<UUID, String> strategyNames = new HashMap<>();

            for (Simulation sim : simulations) {
                UUID simUuid = sim.getSimulationId().value();
                simulationUuids.add(simUuid);
                simMap.put(simUuid, sim);
            }

            // 3. Batch-fetch strategy names
            simulations.stream()
                .map(Simulation::getStrategyId)
                .distinct()
                .forEach(stratId -> {
                    String name = strategyRepository.findById(stratId)
                        .map(Strategy::getName)
                        .orElse("Unknown Strategy");
                    strategyNames.put(stratId.value(), name);
                });

            // 4. Build SimulationInfo list (preserving order)
            List<BenchmarkChartDataResponse.SimulationInfo> simInfos = new ArrayList<>();
            for (UUID simUuid : simulationUuids) {
                Simulation sim = simMap.get(simUuid);
                simInfos.add(new BenchmarkChartDataResponse.SimulationInfo(
                    simUuid.toString(),
                    sim.getName(),
                    strategyNames.get(sim.getStrategyId().value()),
                    sim.getInitialCash() != null ? sim.getInitialCash().amount() : BigDecimal.ZERO
                ));
            }

            // 5. Single DB query for all daily snapshots
            List<PortfolioRepository.DailyPortfolioValue> dailyValues =
                portfolioRepository.findDailyTotalValueBySimulationIds(simulationUuids);

            // 6. Group by (date → simId → value) using a TreeMap for chronological ordering
            // Key: epoch millis of the day, Value: map of simId -> totalValue
            TreeMap<Long, Map<UUID, BigDecimal>> dateGrid = new TreeMap<>();

            for (PortfolioRepository.DailyPortfolioValue point : dailyValues) {
                dateGrid.computeIfAbsent(point.timestamp().toEpochMilli(), k -> new HashMap<>())
                    .put(point.simulationId(), point.totalValue());
            }

            // 7. Build columnar points
            List<List<Object>> points = new ArrayList<>(dateGrid.size());
            for (Map.Entry<Long, Map<UUID, BigDecimal>> entry : dateGrid.entrySet()) {
                List<Object> row = new ArrayList<>(simulationUuids.size() + 1);
                row.add(entry.getKey()); // timestamp epoch ms

                Map<UUID, BigDecimal> values = entry.getValue();
                for (UUID simUuid : simulationUuids) {
                    BigDecimal val = values.get(simUuid);
                    row.add(val); // null if this sim has no data at this date
                }
                points.add(row);
            }

            LOG.debugf("Returning chart data for benchmark %s: %d simulations, %d points",
                id, simInfos.size(), points.size());

            return Response.ok(new BenchmarkChartDataResponse(simInfos, points)).build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid benchmark ID format: %s", id);
            throw new BadRequestException("Invalid UUID format: " + id);
        }
    }

    @GET
    @Path("/{id}/overlays")
    @Operation(
        summary = "Get buy-and-hold overlays for a benchmark",
        description = "Computes buy-and-hold reference curves for the given symbols over the benchmark's date range. "
            + "Shows what the portfolio would be worth if you simply bought and held SPY, QQQ, BTC, etc."
    )
    @APIResponse(
        responseCode = "200",
        description = "Overlay results computed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = OverlayResponse.class,
                type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid parameters",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Benchmark not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getBenchmarkOverlays(
            @Parameter(description = "Benchmark unique identifier", required = true)
            @PathParam("id") String id,
            @Parameter(description = "Comma-separated ticker symbols (e.g., SPY,QQQ,BTC)", required = true)
            @QueryParam("symbols") String symbols,
            @Parameter(description = "Initial capital in USD (defaults to first simulation's initial cash)")
            @QueryParam("initialCapital") BigDecimal initialCapital,
            @Parameter(description = "Data resolution: 5m, 1h, 1d (default), 1w")
            @QueryParam("resolution") @DefaultValue("1d") String resolution) {

        try {
            if (symbols == null || symbols.isBlank()) {
                throw new BadRequestException("symbols query parameter is required");
            }

            BenchmarkId benchmarkId = new BenchmarkId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();

            List<Symbol> symbolList = java.util.Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> SymbolParser.parse(s, InvestmentType.fromTicker(s)))
                .toList();

            Money capital = initialCapital != null ? Money.usd(initialCapital) : null;

            CalculateBenchmarkOverlaysCommand command = new CalculateBenchmarkOverlaysCommand(
                benchmarkId, symbolList, capital, userId, resolution);

            List<OverlayResult> results = calculateBenchmarkOverlaysUseCase.execute(command);

            List<OverlayResponse> response = results.stream()
                .map(result -> new OverlayResponse(
                    result.symbol(),
                    result.assetType(),
                    result.status().name(),
                    result.dataPoints() != null
                        ? result.dataPoints().stream()
                            .map(dp -> new OverlayDataPointResponse(
                                dp.timestamp().toString(),
                                dp.portfolioValue()))
                            .toList()
                        : List.of(),
                    result.returnPercent(),
                    result.errorMessage()
                ))
                .toList();

            LOG.debugf("Computed %d overlays for benchmark %s", response.size(), id);

            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid overlay request for benchmark %s: %s", id, e.getMessage());
            throw new BadRequestException(e.getMessage());
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(
        summary = "Delete benchmark and all associated simulations",
        description = "Deletes a benchmark and cascades deletion to all simulations attached to it. " +
            "This operation uses database cascade constraints to ensure referential integrity."
    )
    @APIResponse(
        responseCode = "204",
        description = "Benchmark deleted successfully"
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid UUID format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Benchmark not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response deleteBenchmark(
            @Parameter(description = "Benchmark unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            BenchmarkId benchmarkId = new BenchmarkId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            deleteBenchmarkUseCase.execute(new DeleteBenchmarkCommand(benchmarkId, userId));

            LOG.infof("Deleted benchmark %s", id);

            return Response.noContent().build();

        } catch (NoSuchElementException e) {
            throw new NotFoundException("Benchmark not found with ID: " + id);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid benchmark ID format: %s", id);
            throw new BadRequestException("Invalid UUID format: " + id);
        }
    }
}
