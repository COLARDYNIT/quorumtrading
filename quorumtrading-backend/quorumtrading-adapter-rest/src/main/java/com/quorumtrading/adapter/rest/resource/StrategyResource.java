package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateStrategyRequest;
import com.quorumtrading.adapter.rest.dto.request.ToggleVisibilityRequest;
import com.quorumtrading.adapter.rest.dto.request.UpdateStrategyRequest;
import com.quorumtrading.adapter.rest.dto.response.StrategyPerformanceResponse;
import com.quorumtrading.adapter.rest.dto.response.StrategyResponse;
import com.quorumtrading.adapter.rest.dto.response.StrategySchemaResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.mapper.StrategyMapper;
import com.quorumtrading.adapter.rest.schema.StrategySchemaProvider;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyPerformance;
import com.quorumtrading.application.usecase.CalculateStrategyPerformanceUseCase;
import com.quorumtrading.application.usecase.CloneStrategyUseCase;
import com.quorumtrading.application.usecase.CreateStrategyUseCase;
import com.quorumtrading.application.usecase.DeleteAllStrategiesUseCase;
import com.quorumtrading.application.usecase.DeleteStrategyUseCase;
import com.quorumtrading.application.usecase.GetStrategyUseCase;
import com.quorumtrading.application.usecase.ListStrategiesUseCase;
import com.quorumtrading.application.usecase.ToggleStrategyVisibilityUseCase;
import com.quorumtrading.application.usecase.UpdateStrategyUseCase;
import com.quorumtrading.application.usecase.query.CalculateStrategyPerformanceQuery;
import com.quorumtrading.application.usecase.command.CloneStrategyCommand;
import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.application.usecase.command.DeleteStrategyCommand;
import com.quorumtrading.application.usecase.command.ToggleStrategyVisibilityCommand;
import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.query.GetStrategyQuery;
import com.quorumtrading.application.usecase.query.ListStrategiesQuery;
import com.quorumtrading.model.valueobject.StrategyId;
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

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST resource for strategy management.
 * Provides endpoints for creating, listing, and managing trading strategies.
 *
 * This adapter delegates to application layer use cases following hexagonal architecture.
 */
@Path("/api/v1/strategies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@io.quarkus.security.Authenticated
@Tag(name = "Strategies", description = "Trading strategy version management")
public class StrategyResource {

    @Inject
    CreateStrategyUseCase createStrategyUseCase;

    @Inject
    ListStrategiesUseCase listStrategiesUseCase;

    @Inject
    GetStrategyUseCase getStrategyUseCase;

    @Inject
    UpdateStrategyUseCase updateStrategyUseCase;

    @Inject
    DeleteStrategyUseCase deleteStrategyUseCase;

    @Inject
    DeleteAllStrategiesUseCase deleteAllStrategiesUseCase;

    @Inject
    CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase;

    @Inject
    CloneStrategyUseCase cloneStrategyUseCase;

    @Inject
    ToggleStrategyVisibilityUseCase toggleStrategyVisibilityUseCase;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    StrategyMapper mapper;

    @Inject
    StrategySchemaProvider schemaProvider;

    @POST
    @Operation(
        summary = "Create new strategy",
        description = "Creates a new trading strategy with markdown content"
    )
    @APIResponse(
        responseCode = "201",
        description = "Strategy created successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse.class)
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
    public Response createStrategy(
            @RequestBody(description = "Strategy creation request", required = true)
            @Valid CreateStrategyRequest request,
            @Context UriInfo uriInfo) {

        UserId userId = currentUser.getCurrentUserId();

        // Map DTO to command
        CreateStrategyCommand command = new CreateStrategyCommand(
            request.name(),
            request.description(),
            request.markdownContent(),
            userId
        );

        // Execute use case
        StrategyId strategyId = createStrategyUseCase.execute(command);

        // Fetch created strategy for response
        Strategy strategy = getStrategyUseCase.execute(new GetStrategyQuery(strategyId, userId));

        // Build Location header
        URI location = uriInfo.getAbsolutePathBuilder()
            .path(strategyId.value().toString())
            .build();

        return Response.created(location)
            .entity(mapper.toResponse(strategy))
            .build();
    }

    @GET
    @Operation(
        summary = "List all strategies",
        description = "Retrieves all trading strategies"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of strategies retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse.class, type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    public Response listStrategies() {
        UserId userId = currentUser.getCurrentUserId();

        // Execute use case
        ListStrategiesQuery query = new ListStrategiesQuery(
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            userId
        );
        List<Strategy> strategies = listStrategiesUseCase.execute(query);

        return Response.ok(mapper.toResponseList(strategies)).build();
    }

    @GET
    @Path("/schema")
    @Operation(
        summary = "Get strategy configuration schema",
        description = "Returns the JSON schema for strategy YAML frontmatter validation. " +
                     "Use this to implement client-side linting and autocomplete."
    )
    @APIResponse(
        responseCode = "200",
        description = "Schema retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategySchemaResponse.class)
        )
    )
    public Response getSchema() {
        return Response.ok(schemaProvider.getSchema())
            .header("Cache-Control", "public, max-age=3600") // Cache for 1 hour
            .build();
    }

    @GET
    @Path("/{id: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}")
    @Operation(
        summary = "Get strategy by ID",
        description = "Retrieves a specific strategy by its unique identifier"
    )
    @APIResponse(
        responseCode = "200",
        description = "Strategy retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Strategy not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getStrategy(
            @Parameter(description = "Strategy unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            StrategyId strategyId = new StrategyId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            GetStrategyQuery query = new GetStrategyQuery(strategyId, userId);
            Strategy strategy = getStrategyUseCase.execute(query);

            return Response.ok(mapper.toResponse(strategy)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid strategy ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Strategy not found with ID: " + id);
        }
    }

    @GET
    @Path("/{id: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}/performance")
    @Operation(
        summary = "Get strategy performance metrics",
        description = "Calculates aggregate performance metrics for all completed simulations of this strategy"
    )
    @APIResponse(
        responseCode = "200",
        description = "Performance metrics calculated successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyPerformanceResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Strategy not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getStrategyPerformance(
            @Parameter(description = "Strategy unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            StrategyId strategyId = new StrategyId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            CalculateStrategyPerformanceQuery query = new CalculateStrategyPerformanceQuery(strategyId, userId);
            StrategyPerformance performance = calculateStrategyPerformanceUseCase.execute(query);

            // Map to DTO
            StrategyPerformanceResponse response = new StrategyPerformanceResponse(
                performance.totalSimulations(),
                performance.completedSimulations(),
                performance.avgReturn(),
                performance.winRate(),
                performance.avgSharpeRatio(),
                performance.bestReturn(),
                performance.worstReturn(),
                performance.recentTrend(),
                performance.emptyRuns()
            );

            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid strategy ID format: " + id);
        }
    }

    @PUT
    @Path("/{id: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}")
    @Operation(
        summary = "Update strategy",
        description = "Updates an existing strategy's content"
    )
    @APIResponse(
        responseCode = "200",
        description = "Strategy updated successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse.class)
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
    @APIResponse(
        responseCode = "404",
        description = "Strategy not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response updateStrategy(
            @Parameter(description = "Strategy unique identifier", required = true)
            @PathParam("id") String id,
            @RequestBody(description = "Strategy update request", required = true)
            @Valid UpdateStrategyRequest request) {

        try {
            StrategyId strategyId = new StrategyId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            UpdateStrategyCommand command = new UpdateStrategyCommand(
                strategyId,
                request.name(),
                request.description(),
                request.markdownContent(),
                userId
            );
            updateStrategyUseCase.execute(command);

            Strategy strategy = getStrategyUseCase.execute(new GetStrategyQuery(strategyId, userId));

            return Response.ok(mapper.toResponse(strategy)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Strategy not found with ID: " + id);
        }
    }

    @DELETE
    @Path("/{id: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}")
    @Operation(
        summary = "Delete strategy",
        description = "Deletes a strategy and cascades deletion to all associated simulations and their data."
    )
    @APIResponse(
        responseCode = "204",
        description = "Strategy deleted successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "Strategy not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "409",
        description = "Conflict - strategy is referenced by simulations",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response deleteStrategy(
            @Parameter(description = "Strategy unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            StrategyId strategyId = new StrategyId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            DeleteStrategyCommand command = new DeleteStrategyCommand(strategyId, userId);
            deleteStrategyUseCase.execute(command);

            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid strategy ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Strategy not found with ID: " + id);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @DELETE
    @Operation(
        summary = "Delete all strategies",
        description = "Deletes all strategies and cascades deletion to all associated simulations and their data (llm_decisions, consensus_decisions, portfolio_snapshots)."
    )
    @APIResponse(
        responseCode = "200",
        description = "All strategies deleted successfully. Returns count of strategies deleted.",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Integer.class)
        )
    )
    public Response deleteAllStrategies() {
        UserId userId = currentUser.getCurrentUserId();

        int deletedCount = deleteAllStrategiesUseCase.execute(userId);

        return Response.ok()
            .entity(java.util.Map.of(
                "deletedCount", deletedCount,
                "message", "Successfully deleted " + deletedCount + " strategies and all associated data"
            ))
            .build();
    }

    @POST
    @Path("/{id: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}/clone")
    @Operation(summary = "Clone a public strategy", description = "Creates a private copy of a public strategy in the caller's account.")
    @APIResponse(responseCode = "201", description = "Strategy cloned successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse.class)))
    @APIResponse(responseCode = "403", description = "Strategy is not public")
    @APIResponse(responseCode = "404", description = "Strategy not found")
    public Response cloneStrategy(@PathParam("id") UUID id, @Context UriInfo uriInfo) {
        UserId userId = currentUser.getCurrentUserId();

        CloneStrategyCommand command = new CloneStrategyCommand(new StrategyId(id), userId);
        StrategyId clonedId = cloneStrategyUseCase.execute(command);

        Strategy cloned = strategyRepository.findById(clonedId)
            .orElseThrow(() -> new NotFoundException("Cloned strategy not found"));

        URI location = uriInfo.getBaseUriBuilder()
            .path("/api/v1/strategies/{id}")
            .build(clonedId.value());
        return Response.created(location).entity(mapper.toResponse(cloned)).build();
    }

    @PUT
    @Path("/{id: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}/visibility")
    @Operation(summary = "Toggle strategy visibility", description = "Make a strategy public or private. Admin + owner only.")
    @APIResponse(responseCode = "200", description = "Visibility updated",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = StrategyResponse.class)))
    @APIResponse(responseCode = "403", description = "Not admin or not owner")
    @APIResponse(responseCode = "404", description = "Strategy not found")
    public Response toggleVisibility(@PathParam("id") UUID id, @Valid ToggleVisibilityRequest request) {
        UserId userId = currentUser.getCurrentUserId();

        ToggleStrategyVisibilityCommand command = new ToggleStrategyVisibilityCommand(
            new StrategyId(id), request.publicVisible(), userId
        );
        toggleStrategyVisibilityUseCase.execute(command);

        Strategy updated = strategyRepository.findById(new StrategyId(id))
            .orElseThrow(() -> new NotFoundException("Strategy not found"));

        return Response.ok(mapper.toResponse(updated)).build();
    }
}
