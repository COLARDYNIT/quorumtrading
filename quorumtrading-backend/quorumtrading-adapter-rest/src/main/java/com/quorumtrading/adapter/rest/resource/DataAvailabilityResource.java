package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.BackfillResponse;
import com.quorumtrading.adapter.rest.dto.response.DataAvailabilityResponse;
import com.quorumtrading.adapter.rest.dto.response.DataHorizonResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.application.service.DataHorizonService;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.QueryBackfillProgressUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.query.QueryBackfillProgressQuery;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.BackfillProgress;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.DataCoverageMetrics;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.valueobject.TimeRange;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST resource for market data availability analysis and backfill management.
 *
 * <p>Provides endpoints for:</p>
 * <ul>
 *   <li>Analyzing data coverage for symbols and time ranges</li>
 *   <li>Requesting backfill jobs for missing data</li>
 *   <li>Querying backfill job status and progress</li>
 * </ul>
 *
 * <p>This adapter follows hexagonal architecture:</p>
 * <ul>
 *   <li>Delegates to application layer use cases</li>
 *   <li>Converts DTOs to domain commands/queries</li>
 *   <li>Maps domain objects to response DTOs</li>
 * </ul>
 */
@Path("/api/v1/data-availability")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Data Availability", description = "Market data availability and backfill management")
@PermitAll
public class DataAvailabilityResource {

    @Inject
    AnalyzeDataAvailabilityUseCase analyzeService;

    @Inject
    RequestBackfillUseCase backfillService;

    @Inject
    QueryBackfillProgressUseCase progressService;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    DataHorizonService horizonService;

    @Inject
    com.quorumtrading.application.service.ClassifyGapsService classifyGapsService;

    @Inject
    com.quorumtrading.application.port.out.MarketDataHorizonRepository horizonRepository;

    @GET
    @Path("/coverage")
    @Operation(
        summary = "Analyze market data coverage",
        description = "Returns data availability metrics for specified symbols, data types, and time range"
    )
    @APIResponse(
        responseCode = "200",
        description = "Coverage analysis successful",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = DataAvailabilityResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid or missing parameters",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response analyzeCoverage(
        @QueryParam("symbols")
        @Parameter(
            description = "Comma-separated ticker symbols",
            example = "BTC,ETH",
            required = true
        )
        String symbols,

        @QueryParam("dataTypes")
        @Parameter(
            description = "Comma-separated data types",
            example = "PRICE,NEWS",
            required = true
        )
        String dataTypes,

        @QueryParam("from")
        @Parameter(
            description = "Start date (inclusive)",
            example = "2024-01-01",
            required = true
        )
        LocalDate from,

        @QueryParam("to")
        @Parameter(
            description = "End date (inclusive)",
            example = "2024-01-31",
            required = true
        )
        LocalDate to
        // No strategyId parameter: one was declared here and read by nothing, advertised in the
        // OpenAPI spec as "for future strategy-specific requirements". A parameter the server
        // accepts and ignores is indistinguishable from one it honours, so callers could tailor a
        // request that was never tailored. Add it back when something actually reads it.
    ) {
        // Validate required parameters
        if (symbols == null || symbols.isBlank()) {
            throw new BadRequestException("Parameter 'symbols' is required");
        }
        if (dataTypes == null || dataTypes.isBlank()) {
            throw new BadRequestException("Parameter 'dataTypes' is required");
        }
        if (from == null) {
            throw new BadRequestException("Parameter 'from' is required");
        }
        if (to == null) {
            throw new BadRequestException("Parameter 'to' is required");
        }

        // Parse symbols
        Set<Symbol> symbolSet = parseSymbols(symbols);

        // Parse data types
        Set<EventType> typeSet = parseDataTypes(dataTypes);

        // Create command
        AnalyzeDataAvailabilityCommand command = new AnalyzeDataAvailabilityCommand(
            TimeRange.of(from, to),
            symbolSet,
            typeSet
        );

        // Execute analysis
        DataAvailability availability = analyzeService.execute(command);

        // Convert to response DTO
        DataAvailabilityResponse response = toResponseDTO(availability);

        return Response.ok(response).build();
    }

    @POST
    @Path("/backfill")
    @Operation(
        summary = "Request market data backfill",
        description = "Creates a backfill job for missing market data. Returns 202 if backfill triggered, 200 if data is already sufficient."
    )
    @APIResponse(
        responseCode = "200",
        description = "No backfill needed - data is already sufficient",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BackfillResponse.class)
        )
    )
    @APIResponse(
        responseCode = "202",
        description = "Backfill job created and queued",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BackfillResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - validation failed",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @Authenticated
    public Response requestBackfill(
        @RequestBody(description = "Backfill request details", required = true)
        @Valid com.quorumtrading.adapter.rest.dto.request.BackfillRequest request
    ) {
        // Authentication check
        UserId userId = currentUser.getCurrentUserIdOptional()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));

        // Parse symbols with auto-detection of investment type
        Set<Symbol> symbolSet = request.symbols().stream()
            .map(SymbolParser::parseAutoType)
            .collect(Collectors.toSet());

        // Parse data types
        Set<EventType> typeSet = request.dataTypes().stream()
            .map(EventType::valueOf)
            .collect(Collectors.toSet());

        // Create command
        RequestBackfillCommand command = new RequestBackfillCommand(
            symbolSet,
            typeSet,
            TimeRange.of(request.from(), request.to()),
            request.forceBackfill()
        );

        // Execute backfill request
        RequestBackfillResult result = backfillService.execute(command);

        // Create response
        BackfillResponse response = new BackfillResponse(
            result.jobId() != null ? result.jobId().toString() : null,
            result.status() != null ? result.status().name() : null,
            result.backfillTriggered(),
            result.message(),
            0, // Initial progress
            result.createdAt()
        );

        // Return appropriate status code
        if (result.backfillTriggered()) {
            return Response.status(Response.Status.ACCEPTED).entity(response).build();
        } else {
            return Response.ok(response).build();
        }
    }

    @GET
    @Path("/backfill/{jobId}")
    @Operation(
        summary = "Get backfill job status",
        description = "Returns the current status and progress of a backfill job"
    )
    @APIResponse(
        responseCode = "200",
        description = "Job status retrieved",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BackfillResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Job not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Invalid job ID format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getBackfillStatus(
        @PathParam("jobId")
        @Parameter(
            description = "Backfill job unique identifier",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true
        )
        String jobId
    ) {
        // Parse and validate job ID
        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid job ID format: " + jobId);
        }

        // Query progress
        QueryBackfillProgressQuery query = new QueryBackfillProgressQuery(jobUuid);
        BackfillProgress progress = progressService.execute(query);

        if (progress == null) {
            throw new NotFoundException("Backfill job not found with ID: " + jobId);
        }

        // Create response
        BackfillResponse response = new BackfillResponse(
            jobId,
            progress.status().name(),
            true, // Was triggered (job exists)
            progress.errorMessage() != null ? progress.errorMessage() : "In progress",
            progress.progressPercentage(),
            progress.startedAt()
        );

        return Response.ok(response).build();
    }

    @GET
    @Path("/horizon")
    @Operation(
        summary = "Measured provider data horizons",
        description = "Returns the measured earliest-available date each provider has for the requested " +
            "(symbol, data type) pairs at daily granularity. Values are measured by a month-window probe, " +
            "cached with a TTL, and self-corrected from runtime feedback — never assumed. A probe failure " +
            "for a single symbol yields hasData=false / earliestAvailable=null rather than an error."
    )
    @APIResponse(
        responseCode = "200",
        description = "Horizon lookup successful",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = DataHorizonResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid or missing parameters",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getDataHorizon(
        @QueryParam("symbols")
        @Parameter(description = "Comma-separated ticker symbols", example = "BTC,ETH", required = true)
        String symbols,

        @QueryParam("dataTypes")
        @Parameter(description = "Comma-separated data types", example = "PRICE")
        String dataTypes
    ) {
        if (symbols == null || symbols.isBlank()) {
            throw new BadRequestException("Parameter 'symbols' is required");
        }

        // Parse symbols with auto-detection of investment type (same as backfill).
        Set<Symbol> symbolSet = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(SymbolParser::parseAutoType)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // Default to PRICE when not specified.
        Set<EventType> typeSet = (dataTypes == null || dataTypes.isBlank())
            ? Set.of(EventType.PRICE)
            : Arrays.stream(dataTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(EventType::valueOf)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        List<DataHorizonResponse.Entry> entries = new ArrayList<>();
        for (Symbol symbol : symbolSet) {
            for (EventType type : typeSet) {
                entries.add(resolveHorizonEntry(symbol, type));
            }
        }

        return Response.ok(new DataHorizonResponse(entries)).build();
    }

    /**
     * Resolve a single horizon entry. Any failure (probe error, etc.) degrades to
     * hasData=false / earliestAvailable=null — never a 500.
     */
    private DataHorizonResponse.Entry resolveHorizonEntry(Symbol symbol, EventType type) {
        try {
            Optional<LocalDate> earliest = horizonService.earliest(symbol, type);
            // Read back the cached row to surface the probe timestamp.
            Optional<com.quorumtrading.model.marketdata.DataHorizon> cached = horizonRepository.find(
                "probe", symbol, type, com.quorumtrading.model.enums.GranularityBucket.DAILY);
            String probedAt = cached.map(h -> h.probedAt().toString()).orElse(null);

            return new DataHorizonResponse.Entry(
                symbol.ticker(),
                type.name(),
                earliest.map(LocalDate::toString).orElse(null),
                earliest.isPresent(),
                probedAt
            );
        } catch (RuntimeException e) {
            return new DataHorizonResponse.Entry(symbol.ticker(), type.name(), null, false, null);
        }
    }

    /**
     * Parse comma-separated symbols into Symbol value objects, classifying each
     * ticker's asset class via the canonical heuristic. (Previously hardcoded
     * CRYPTO, which made equity weekends look like missing data rather than a
     * closed market.)
     */
    private Set<Symbol> parseSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(SymbolParser::parseAutoType)
            .collect(Collectors.toSet());
    }

    /**
     * Parse comma-separated data types into EventType enums.
     * Defaults to all types (PRICE, NEWS, SOCIAL) if not specified.
     */
    private Set<EventType> parseDataTypes(String dataTypes) {
        if (dataTypes == null || dataTypes.isBlank()) {
            return Set.of(EventType.PRICE, EventType.NEWS, EventType.SOCIAL);
        }
        return Arrays.stream(dataTypes.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(EventType::valueOf)
            .collect(Collectors.toSet());
    }

    /**
     * Convert DataAvailability domain object to response DTO.
     */
    private DataAvailabilityResponse toResponseDTO(DataAvailability availability) {
        // Calculate overall coverage percentage
        java.math.BigDecimal overallCoveragePercent = availability.getOverallCoverage();

        // Calculate aggregate per-type and per-symbol coverage from daily metrics
        Map<EventType, java.math.BigDecimal> aggregatePerType = calculateAggregatePerTypeCoverage(availability);
        Map<Symbol, java.math.BigDecimal> aggregatePerSymbol = calculateAggregatePerSymbolCoverage(availability);

        // Convert per-type coverage (EventType -> String)
        Map<String, java.math.BigDecimal> perTypeCoverage = aggregatePerType.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().name(),
                Map.Entry::getValue
            ));

        // Convert per-symbol coverage (Symbol -> String)
        Map<String, java.math.BigDecimal> perSymbolCoverage = aggregatePerSymbol.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().ticker(),
                Map.Entry::getValue
            ));

        // Convert daily coverage
        List<DataAvailabilityResponse.DailyCoverageDTO> dailyCoverage =
            availability.getDailyCoverage().entrySet().stream()
                .map(entry -> {
                    Map<String, java.math.BigDecimal> dailyPerType = entry.getValue().perTypePercentage().entrySet().stream()
                        .collect(Collectors.toMap(
                            e -> e.getKey().name(),
                            Map.Entry::getValue
                        ));

                    Map<String, java.math.BigDecimal> dailyPerSymbol = entry.getValue().perSymbolPercentage().entrySet().stream()
                        .collect(Collectors.toMap(
                            e -> e.getKey().ticker(),
                            Map.Entry::getValue
                        ));

                    return new DataAvailabilityResponse.DailyCoverageDTO(
                        entry.getKey(),
                        entry.getValue().overallPercentage(),
                        dailyPerType,
                        dailyPerSymbol
                    );
                })
                .sorted(Comparator.comparing(DataAvailabilityResponse.DailyCoverageDTO::date))
                .toList();

        // Convert gaps (symbol can be null for market-wide gaps like NEWS), enriched
        // with actionability classification + provider + estimated record count.
        List<DataAvailabilityResponse.GapDTO> gaps =
            classifyGapsService.classify(availability.identifyGaps()).stream()
                .map(cg -> {
                    var gap = cg.gap();
                    return new DataAvailabilityResponse.GapDTO(
                        gap.symbol() != null ? gap.symbol().ticker() : "MARKET",
                        gap.dataType().name(),
                        gap.period().getStart(),
                        gap.period().getEnd(),
                        cg.classification().name(),
                        cg.provider(),
                        cg.estimatedRecords()
                    );
                })
                .toList();

        return new DataAvailabilityResponse(
            overallCoveragePercent,
            perTypeCoverage,
            perSymbolCoverage,
            dailyCoverage,
            gaps,
            availability.isSufficient()
        );
    }

    /**
     * Calculate aggregate per-type coverage by averaging across all days.
     */
    private Map<EventType, java.math.BigDecimal> calculateAggregatePerTypeCoverage(DataAvailability availability) {
        Map<EventType, java.math.BigDecimal> result = new HashMap<>();

        for (EventType type : availability.getDataTypes()) {
            java.math.BigDecimal sum = availability.getDailyCoverage().values().stream()
                .map(metrics -> metrics.perTypePercentage().getOrDefault(type, java.math.BigDecimal.ZERO))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            int count = availability.getDailyCoverage().size();
            java.math.BigDecimal average = count > 0
                ? sum.divide(new java.math.BigDecimal(count), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

            result.put(type, average);
        }

        return result;
    }

    /**
     * Calculate aggregate per-symbol coverage by averaging across all days.
     */
    private Map<Symbol, java.math.BigDecimal> calculateAggregatePerSymbolCoverage(DataAvailability availability) {
        Map<Symbol, java.math.BigDecimal> result = new HashMap<>();

        for (Symbol symbol : availability.getSymbols()) {
            java.math.BigDecimal sum = availability.getDailyCoverage().values().stream()
                .map(metrics -> metrics.perSymbolPercentage().getOrDefault(symbol, java.math.BigDecimal.ZERO))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            int count = availability.getDailyCoverage().size();
            java.math.BigDecimal average = count > 0
                ? sum.divide(new java.math.BigDecimal(count), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

            result.put(symbol, average);
        }

        return result;
    }

}
