package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateSimulationRequest;
import com.quorumtrading.adapter.rest.dto.request.ReorderQueueRequest;
import com.quorumtrading.adapter.rest.dto.request.UpdateSimulationNotificationPreferenceRequest;
import com.quorumtrading.adapter.rest.dto.response.SimulationHealthResponse;
import com.quorumtrading.adapter.rest.dto.response.SimulationResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.exception.NotFoundException;
import com.quorumtrading.adapter.rest.mapper.SimulationMapper;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.DeletePausedSimulationsUseCase;
import com.quorumtrading.application.usecase.DeleteSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationUseCase;
import com.quorumtrading.application.usecase.ListSimulationsUseCase;
import com.quorumtrading.application.usecase.PauseSimulationUseCase;
import com.quorumtrading.application.usecase.ReorderQueueUseCase;
import com.quorumtrading.application.usecase.ResumeSimulationUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.command.DeleteSimulationCommand;
import com.quorumtrading.application.usecase.command.PauseSimulationCommand;
import com.quorumtrading.application.usecase.command.ReorderQueueCommand;
import com.quorumtrading.application.usecase.command.ResumeSimulationCommand;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.application.usecase.query.ListSimulationsQuery;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
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

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST resource for simulation management.
 * Provides endpoints for creating, listing, and managing backtesting simulations.
 *
 * This adapter delegates to application layer use cases following hexagonal architecture.
 */
@Path("/api/v1/simulations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@io.quarkus.security.Authenticated
@Tag(name = "Simulations", description = "Backtesting simulation management")
public class SimulationResource {

    @Inject
    CreateSimulationUseCase createSimulationUseCase;

    @Inject
    ListSimulationsUseCase listSimulationsUseCase;

    @Inject
    GetSimulationUseCase getSimulationUseCase;

    @Inject
    com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase getSimulationHealthUseCase;

    @Inject
    DeleteSimulationUseCase deleteSimulationUseCase;

    @Inject
    DeletePausedSimulationsUseCase deletePausedSimulationsUseCase;

    @Inject
    StartSimulationUseCase startSimulationUseCase;

    @Inject
    PauseSimulationUseCase pauseSimulationUseCase;

    @Inject
    com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase updateSimulationNotificationPreferenceUseCase;

    @Inject
    ResumeSimulationUseCase resumeSimulationUseCase;

    @Inject
    ReorderQueueUseCase reorderQueueUseCase;

    @Inject
    com.quorumtrading.application.usecase.GetSimulationChartDataUseCase getSimulationChartDataUseCase;

    @Inject
    com.quorumtrading.application.usecase.SellPositionUseCase sellPositionUseCase;

    @Inject
    com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase liquidateAllPositionsUseCase;

    @Inject
    com.quorumtrading.application.usecase.ForceEndSimulationUseCase forceEndSimulationUseCase;

    @Inject
    com.quorumtrading.application.service.RestartSimulationFromService restartSimulationFromService;

    @Inject
    com.quorumtrading.application.usecase.ReachConsensusUseCase reachConsensusUseCase;

    @Inject
    com.quorumtrading.application.port.out.PortfolioRepository portfolioRepository;

    @Inject
    com.quorumtrading.application.service.BrokerRouter brokerRouter;

    @Inject
    org.eclipse.microprofile.context.ManagedExecutor managedExecutor;

    @Inject
    com.quorumtrading.application.usecase.CalculateSimulationOverlaysUseCase calculateSimulationOverlaysUseCase;

    @Inject
    com.quorumtrading.application.usecase.contribution.GetSimulationContributionsUseCase getSimulationContributionsUseCase;

    @Inject
    com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase recordSimulationContributionUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    SimulationMapper mapper;

    @POST
    @Operation(
        summary = "Create simulation",
        description = "Creates a new backtesting simulation with specified strategy and time range"
    )
    @APIResponse(
        responseCode = "201",
        description = "Simulation created successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class)
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
    public Response createSimulation(
            @RequestBody(description = "Simulation creation request", required = true)
            @Valid CreateSimulationRequest request,
            @Context UriInfo uriInfo) {

        UserId userId = currentUser.getCurrentUserId();

        // Map DTO to command
        // For LIVE mode, startDate is null - use current time
        Instant effectiveStartDate = request.startDate() != null
            ? request.startDate()
            : Instant.now();

        // The broker account was previously hardcoded to null here while the request DTO carried
        // no such field, so a brokerAccountId in the body was silently discarded by Jackson and
        // every REST-created simulation ran broker-less — with no capital ring-fenced and no
        // SimulationPortfolio ledger. Paper and live trading were unreachable over REST entirely.
        BrokerAccountId brokerAccountId = null;
        Money tradingLimit = null;
        if (request.brokerAccountId() != null && !request.brokerAccountId().isBlank()) {
            try {
                brokerAccountId = new BrokerAccountId(UUID.fromString(request.brokerAccountId()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                    "Invalid brokerAccountId format: " + request.brokerAccountId());
            }
            tradingLimit = new Money(
                BigDecimal.valueOf(request.tradingLimit()), Money.Currency.USD);
        }

        CreateSimulationCommand command = new CreateSimulationCommand(
            request.name(), // Pass the name from request (null if not provided)
            new StrategyId(UUID.fromString(request.strategyId())),
            effectiveStartDate,
            request.endDate(),
            new Money(BigDecimal.valueOf(request.initialCash()), Money.Currency.USD),
            brokerAccountId, tradingLimit, null,
            userId,
            request.confirmLive()
        );

        // Execute use case
        SimulationId simulationId = createSimulationUseCase.execute(command);

        // Fetch created simulation for response
        Simulation simulation = getSimulationUseCase.execute(new GetSimulationQuery(simulationId, userId));

        // Build Location header
        URI location = uriInfo.getAbsolutePathBuilder()
            .path(simulationId.value().toString())
            .build();

        return Response.created(location)
            .entity(mapper.toResponse(simulation))
            .build();
    }

    @GET
    @Operation(
        summary = "List simulations",
        description = "Retrieves all simulations, optionally filtered by status and/or start time"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of simulations retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class, type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid status or since parameter",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response listSimulations(
            @Parameter(description = "Filter by status (PENDING, SIMULATING, LIVE, COMPLETED, FAILED)")
            @QueryParam("status") String status,
            @Parameter(description = "Filter by startedAt >= since (ISO-8601 timestamp, e.g., 2025-11-08T14:00:00Z)")
            @QueryParam("since") String since) {

        // Parse since parameter if provided
        java.util.Optional<java.time.Instant> sinceInstant = java.util.Optional.empty();
        if (since != null && !since.isEmpty()) {
            try {
                sinceInstant = java.util.Optional.of(java.time.Instant.parse(since));
            } catch (java.time.format.DateTimeParseException e) {
                throw new BadRequestException("Invalid since parameter format. Expected ISO-8601 timestamp (e.g., 2025-11-08T14:00:00Z)");
            }
        }

        UserId userId = currentUser.getCurrentUserId();

        // Execute use case
        ListSimulationsQuery query = new ListSimulationsQuery(
            java.util.Optional.ofNullable(status),
            java.util.Optional.empty(),
            sinceInstant,
            java.util.Optional.empty(),
            userId
        );
        List<Simulation> simulations = listSimulationsUseCase.execute(query);

        return Response.ok(mapper.toResponseList(simulations)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Get simulation by ID",
        description = "Retrieves a specific simulation by its unique identifier"
    )
    @APIResponse(
        responseCode = "200",
        description = "Simulation retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getSimulation(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            GetSimulationQuery query = new GetSimulationQuery(simulationId, userId);
            Simulation simulation = getSimulationUseCase.execute(query);

            // Enrich with contributions + TWR/MWR. The current portfolio value
            // for MWR comes from the latest snapshot when available.
            var contributions = getSimulationContributionsUseCase.execute(
                simulationId, currentUser.getCurrentUserId());
            var snapshots = portfolioRepository.findAllBySimulationId(simulationId);
            Money currentValue = portfolioRepository.findLatestBySimulationId(simulationId)
                    .map(snap -> snap.getTotalValue())
                    .orElse(simulation.getInitialCash());
            return Response.ok(mapper.toResponseWithContributions(
                    simulation, contributions, snapshots, currentValue)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(
        summary = "Delete simulation",
        description = "Deletes a simulation. Cannot delete a running simulation."
    )
    @APIResponse(
        responseCode = "204",
        description = "Simulation deleted successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "409",
        description = "Conflict - cannot delete running simulation",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response deleteSimulation(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            DeleteSimulationCommand command = new DeleteSimulationCommand(simulationId, userId);
            deleteSimulationUseCase.execute(command);

            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @DELETE
    @Path("/paused")
    @Operation(
        summary = "Delete all paused simulations",
        description = "Deletes all simulations with PAUSED status. Returns the count of deleted simulations."
    )
    @APIResponse(
        responseCode = "200",
        description = "Paused simulations deleted successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Integer.class)
        )
    )
    public Response deletePausedSimulations() {
        UserId userId = currentUser.getCurrentUserIdOptional()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));
        int deletedCount = deletePausedSimulationsUseCase.execute(userId);
        return Response.ok(deletedCount).build();
    }

    @POST
    @Path("/queue/start-next")
    @Operation(
        summary = "Start next queued simulation",
        description = "Starts execution of the first pending simulation in the queue (ordered by creation time)"
    )
    @APIResponse(
        responseCode = "200",
        description = "Next simulation started successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "No pending simulations in queue",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response startNextQueuedSimulation() {
        UserId userId = currentUser.getCurrentUserId();

        // Query for first PENDING simulation (ordered by createdAt)
        ListSimulationsQuery query = new ListSimulationsQuery(
            java.util.Optional.of("PENDING"),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.of(1),
            userId
        );
        List<Simulation> pendingSimulations = listSimulationsUseCase.execute(query);

        // If no pending simulations, return 404
        if (pendingSimulations.isEmpty()) {
            throw new NotFoundException("No pending simulations in queue");
        }

        // Start the first pending simulation
        Simulation nextSimulation = pendingSimulations.get(0);
        StartSimulationCommand command = new StartSimulationCommand(nextSimulation.getSimulationId(), userId);
        startSimulationUseCase.execute(command);

        // Fetch updated simulation
        Simulation updatedSimulation = getSimulationUseCase.execute(new GetSimulationQuery(nextSimulation.getSimulationId(), userId));

        return Response.ok(mapper.toResponse(updatedSimulation)).build();
    }

    @PATCH
    @Path("/queue/reorder")
    @Operation(
        summary = "Reorder simulation queue",
        description = "Updates the execution order of pending simulations. " +
                      "Provide a list of simulation IDs in desired execution order (first ID = next to run). " +
                      "Only PENDING simulations can be reordered."
    )
    @APIResponse(
        responseCode = "200",
        description = "Queue reordered successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class, type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid simulation IDs or empty list",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "One or more simulations not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "409",
        description = "Conflict - one or more simulations not in PENDING status",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response reorderQueue(
            @RequestBody(description = "Ordered list of simulation IDs", required = true)
            @Valid ReorderQueueRequest request) {

        try {
            // Map string IDs to SimulationId value objects
            List<SimulationId> orderedIds = request.orderedSimulationIds().stream()
                .map(id -> new SimulationId(UUID.fromString(id)))
                .collect(java.util.stream.Collectors.toList());

            // Execute use case
            UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));
            ReorderQueueCommand command = new ReorderQueueCommand(orderedIds, userId);
            List<Simulation> reorderedSimulations = reorderQueueUseCase.execute(command);

            return Response.ok(mapper.toResponseList(reorderedSimulations)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + e.getMessage());
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @POST
    @Path("/{id}/start")
    @Operation(
        summary = "Start simulation execution",
        description = "Starts execution of a pending simulation"
    )
    @APIResponse(
        responseCode = "200",
        description = "Simulation started successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "409",
        description = "Conflict - simulation is not in PENDING status",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response startSimulation(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            StartSimulationCommand command = new StartSimulationCommand(simulationId, userId);
            startSimulationUseCase.execute(command);

            // Fetch updated simulation
            Simulation simulation = getSimulationUseCase.execute(new GetSimulationQuery(simulationId, userId));

            return Response.ok(mapper.toResponse(simulation)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @POST
    @Path("/{id}/pause")
    @Operation(
        summary = "Pause simulation execution",
        description = "Pauses a running simulation"
    )
    @APIResponse(
        responseCode = "200",
        description = "Simulation paused successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response pauseSimulation(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            PauseSimulationCommand command = new PauseSimulationCommand(simulationId, userId);
            pauseSimulationUseCase.execute(command);

            // Fetch updated simulation
            Simulation simulation = getSimulationUseCase.execute(new GetSimulationQuery(simulationId, userId));

            return Response.ok(mapper.toResponse(simulation)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        }
    }

    @PATCH
    @Path("/{id}/notification-preferences")
    @Operation(
        summary = "Update per-simulation notification preferences",
        description = "Toggle whether the owner receives BUY/SELL decision notifications for this simulation. " +
                      "HOLDs never trigger notifications regardless of this setting. " +
                      "Channel routing (in-app vs email) is controlled separately via /api/v1/notification-preferences."
    )
    @APIResponse(
        responseCode = "200",
        description = "Updated simulation",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class)
        )
    )
    @APIResponse(
        responseCode = "401",
        description = "Caller is not authenticated"
    )
    @APIResponse(
        responseCode = "403",
        description = "Caller does not own this simulation"
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found"
    )
    public Response updateSimulationNotificationPreferences(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id,
            @Valid UpdateSimulationNotificationPreferenceRequest request) {

        UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new jakarta.ws.rs.NotAuthorizedException("Authentication required"));

        try {
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            updateSimulationNotificationPreferenceUseCase.execute(
                new com.quorumtrading.application.usecase.command.UpdateSimulationNotificationPreferenceCommand(
                    simulationId,
                    userId,
                    Boolean.TRUE.equals(request.notifyDecisions())
                )
            );
            Simulation simulation = getSimulationUseCase.execute(new GetSimulationQuery(simulationId, userId));
            return Response.ok(mapper.toResponse(simulation)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        } catch (SecurityException e) {
            throw new jakarta.ws.rs.ForbiddenException("Simulation does not belong to caller");
        }
    }

    @POST
    @Path("/{id}/resume")
    @Operation(
        summary = "Resume simulation execution",
        description = "Resumes or restarts a simulation from its last checkpoint. " +
                      "This endpoint is idempotent and handles multiple scenarios:\n" +
                      "- If simulation is already running: no effect (returns immediately)\n" +
                      "- If simulation is complete: no effect (returns immediately)\n" +
                      "- If simulation has checkpoints: resumes from last portfolio snapshot\n" +
                      "- If simulation has no checkpoints: starts from beginning\n" +
                      "- If simulation crashed/stalled: restarts from last known state\n\n" +
                      "Use this endpoint to recover from server restarts, crashes, or to manually restart stalled simulations."
    )
    @APIResponse(
        responseCode = "200",
        description = "Simulation resume triggered successfully (execution may be async)",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response resumeSimulation(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            // Parse ID and execute use case
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            ResumeSimulationCommand command = new ResumeSimulationCommand(simulationId, userId);
            resumeSimulationUseCase.execute(command);

            // Fetch updated simulation
            Simulation simulation = getSimulationUseCase.execute(new GetSimulationQuery(simulationId, userId));

            return Response.ok(mapper.toResponse(simulation)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        }
    }

    @GET
    @Path("/{id}/status")
    @Operation(
        summary = "Get simulation execution health",
        description = "Returns the live execution health of the run: a verdict (HEALTHY, IDLE, STALLED, "
                    + "WAITING_FOR_DATA, PAUSED_BY_USER, PAUSED_EXTERNAL, NOT_STARTED, COMPLETED, FAILED), "
                    + "a human-readable explanation, when the run last decided and last attempted anything, "
                    + "skip counts and streak, and the dominant skip reasons — the endpoint to call when a "
                    + "simulation does not seem to progress."
    )
    @APIResponse(
        responseCode = "200",
        description = "Health computed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = SimulationHealthResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getSimulationStatus(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            var health = getSimulationHealthUseCase.execute(new GetSimulationQuery(simulationId, userId));
            return Response.ok(new SimulationHealthResponse(
                health.status(),
                health.verdict().name(),
                health.detail(),
                health.lastDecisionAt(),
                health.lastRoundAt(),
                health.recentRows(),
                health.recentSkippedRows(),
                health.consecutiveSkippedRows(),
                health.totalEvaluations(),
                health.totalSkippedEvaluations(),
                health.topSkipReasons().stream()
                    .map(r -> new SimulationHealthResponse.SkipReasonResponse(r.reason(), r.count()))
                    .toList(),
                health.externalPauseReason(),
                health.failureReason()
            )).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        }
    }

    @GET
    @Path("/{id}/chart-data")
    @Operation(
        summary = "Get decimated chart data for simulation",
        description = "Returns portfolio value data points optimized for chart rendering. " +
                "Large datasets (thousands of snapshots) are automatically decimated to the specified " +
                "resolution (default: 500 points) while preserving the visual shape of the data."
    )
    @APIResponse(
        responseCode = "200",
        description = "Chart data retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.ChartDataPointResponse.class, type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid simulation ID or resolution parameter",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getChartData(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id,
            @Parameter(description = "Target number of data points to return (10-10000, default: 500)")
            @QueryParam("resolution") @jakarta.ws.rs.DefaultValue("500") int resolution) {

        try {
            // Parse ID and build query
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));
            com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery query =
                new com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery(simulationId, resolution, userId);

            // Execute use case
            List<com.quorumtrading.model.valueobject.ChartDataPoint> chartData = getSimulationChartDataUseCase.execute(query);

            // Convert to response DTOs
            List<com.quorumtrading.adapter.rest.dto.response.ChartDataPointResponse> response = chartData.stream()
                .map(point -> new com.quorumtrading.adapter.rest.dto.response.ChartDataPointResponse(
                    point.timestamp(),
                    point.portfolioValue(),
                    point.cashAvailable()
                ))
                .toList();

            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid parameter: " + e.getMessage());
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        }
    }

    @POST
    @Path("/{id}/positions/{symbol}/sell")
    @Operation(
        summary = "Sell a position",
        description = "Sells the entire position of a symbol at market price"
    )
    @APIResponse(responseCode = "200", description = "Sell result",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.SellPositionResponse.class)))
    @APIResponse(responseCode = "404", description = "Simulation not found",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public Response sellPosition(
            @PathParam("id") String id,
            @PathParam("symbol") String symbol) {
        try {
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            Symbol sym = SymbolParser.parse(symbol, InvestmentType.fromTicker(symbol));
            var result = sellPositionUseCase.execute(simulationId, sym, userId);
            var response = new com.quorumtrading.adapter.rest.dto.response.SellPositionResponse(
                result.success(), result.message(), result.filledQuantity(), result.executionPrice());
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid parameter: " + e.getMessage());
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        }
    }

    @POST
    @Path("/{id}/liquidate")
    @Operation(
        summary = "Liquidate all positions",
        description = "Cancels all open orders and sells all positions at market price"
    )
    @APIResponse(responseCode = "200", description = "Liquidation result",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.LiquidateAllResponse.class)))
    @APIResponse(responseCode = "404", description = "Simulation not found",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public Response liquidateAll(@PathParam("id") String id) {
        try {
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            var result = liquidateAllPositionsUseCase.execute(simulationId, userId);
            var response = new com.quorumtrading.adapter.rest.dto.response.LiquidateAllResponse(
                result.positionsLiquidated(), result.positionsFailed(),
                result.errors(), result.cashAfterLiquidation());
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @POST
    @Path("/{id}/end")
    @Operation(
        summary = "Force-end a simulation",
        description = "Permanently ends a simulation, optionally liquidating all positions first"
    )
    @APIResponse(responseCode = "200", description = "End simulation result",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.ForceEndSimulationResponse.class)))
    @APIResponse(responseCode = "404", description = "Simulation not found",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "409", description = "Simulation already in terminal state",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public Response forceEndSimulation(
            @PathParam("id") String id,
            com.quorumtrading.adapter.rest.dto.request.EndSimulationRequest request) {
        try {
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();
            boolean liquidate = request != null && request.liquidatePositions();
            var command = new com.quorumtrading.application.usecase.command.ForceEndSimulationCommand(simulationId, liquidate, userId);
            var result = forceEndSimulationUseCase.execute(command);
            var response = new com.quorumtrading.adapter.rest.dto.response.ForceEndSimulationResponse(
                result.success(), result.liquidated(), result.liquidatedCount(), result.message());
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @POST
    @Path("/{id}/restart-from/preview")
    @Operation(
        summary = "Preview restart from decision",
        description = "Shows what data will be deleted and archived when restarting from a specific consensus decision. BACKTEST only."
    )
    @APIResponse(responseCode = "200", description = "Preview generated successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.RestartFromPreviewResponse.class)))
    @APIResponse(responseCode = "404", description = "Simulation or decision not found",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "409", description = "Simulation cannot be restarted (not a backtest or invalid state)",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public Response restartFromPreview(
            @PathParam("id") String id,
            com.quorumtrading.adapter.rest.dto.request.RestartFromRequest request) {
        try {
            UUID simulationId = UUID.fromString(id);
            UserId userId = currentUser.getCurrentUserId();
            var preview = restartSimulationFromService.preview(simulationId, request.consensusDecisionId(), userId);
            var response = new com.quorumtrading.adapter.rest.dto.response.RestartFromPreviewResponse(
                preview.consensusDecisionsToDelete(),
                preview.llmDecisionsToDelete(),
                preview.portfolioSnapshotsToDelete(),
                preview.archivedCostUsd(),
                preview.archivedTokens(),
                preview.portfolioCashAtRestart(),
                preview.portfolioValueAtRestart(),
                preview.restartTimestamp()
            );
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid parameter: " + e.getMessage());
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @POST
    @Path("/{id}/restart-from")
    @Operation(
        summary = "Restart simulation from decision",
        description = "Archives LLM costs and deletes decisions/snapshots at or after the given consensus decision, " +
                      "then sets the simulation to SIMULATING (paused). BACKTEST only."
    )
    @APIResponse(responseCode = "200", description = "Simulation restarted successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.RestartFromPreviewResponse.class)))
    @APIResponse(responseCode = "404", description = "Simulation or decision not found",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "409", description = "Simulation cannot be restarted",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public Response restartFrom(
            @PathParam("id") String id,
            com.quorumtrading.adapter.rest.dto.request.RestartFromRequest request) {
        try {
            UUID simulationId = UUID.fromString(id);
            UserId userId = currentUser.getCurrentUserId();
            var result = restartSimulationFromService.execute(simulationId, request.consensusDecisionId(), userId);
            var response = new com.quorumtrading.adapter.rest.dto.response.RestartFromPreviewResponse(
                result.consensusDecisionsToDelete(),
                result.llmDecisionsToDelete(),
                result.portfolioSnapshotsToDelete(),
                result.archivedCostUsd(),
                result.archivedTokens(),
                result.portfolioCashAtRestart(),
                result.portfolioValueAtRestart(),
                result.restartTimestamp()
            );
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid parameter: " + e.getMessage());
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        }
    }

    @POST
    @Path("/{id}/trigger-consensus")
    @Operation(
        summary = "Trigger manual consensus",
        description = "Manually triggers a consensus round for a LIVE simulation. " +
                      "The consensus executes asynchronously — progress is streamed via WebSocket. " +
                      "Returns 202 Accepted immediately."
    )
    @APIResponse(
        responseCode = "202",
        description = "Consensus trigger accepted"
    )
    @APIResponse(
        responseCode = "404",
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "409",
        description = "Simulation is not in a triggerable state (must be LIVE or SIMULATING and not paused)",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response triggerConsensus(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id) {

        try {
            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();

            // Validate simulation exists and is in a triggerable state
            Simulation simulation = getSimulationUseCase.execute(new GetSimulationQuery(simulationId, userId));
            if (simulation == null) {
                throw new NotFoundException("Simulation not found with ID: " + id);
            }

            boolean isActive = simulation.getStatus() == com.quorumtrading.model.enums.SimulationStatus.LIVE
                || simulation.getStatus() == com.quorumtrading.model.enums.SimulationStatus.SIMULATING;
            if (!isActive || simulation.isPaused()) {
                throw new WebApplicationException(
                    "Simulation must be LIVE or SIMULATING and not paused to trigger consensus (current: "
                        + simulation.getStatus() + ", paused: " + simulation.isPaused() + ")",
                    Response.Status.CONFLICT);
            }

            // Initialize broker portfolio from latest snapshot
            List<com.quorumtrading.model.entity.PortfolioSnapshot> snapshots =
                portfolioRepository.findBySimulationId(simulationId, null, null, 1);
            if (!snapshots.isEmpty()) {
                brokerRouter.initializePortfolio(simulationId, snapshots.get(0).getCashAvailable());
            }

            // Execute consensus asynchronously
            managedExecutor.runAsync(() -> {
                try {
                    var command = new com.quorumtrading.application.usecase.command.ReachConsensusCommand(
                        simulationId, Instant.now());
                    reachConsensusUseCase.execute(command);
                } catch (Exception e) {
                    // Error is logged inside ReachConsensusService; WebSocket CONSENSUS_FAILED event
                    // is broadcast by the service layer's error handling
                    org.jboss.logging.Logger.getLogger(SimulationResource.class)
                        .errorf(e, "Async consensus failed for simulation %s", id);
                }
            });

            return Response.accepted().build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        }
    }

    @GET
    @Path("/{id}/overlays")
    @Operation(
        summary = "Get buy-and-hold overlays for a simulation",
        description = "Computes buy-and-hold reference curves for the given symbols over the simulation's date range. "
            + "Shows what the portfolio would be worth if you simply bought and held SPY, QQQ, BTC, etc."
    )
    @APIResponse(
        responseCode = "200",
        description = "Overlay results computed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.OverlayResponse.class,
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
        description = "Simulation not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getSimulationOverlays(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id,
            @Parameter(description = "Comma-separated ticker symbols (e.g., SPY,QQQ,BTC)", required = true)
            @QueryParam("symbols") String symbols,
            @Parameter(description = "Data resolution: 5m, 1h, 1d (default), 1w")
            @QueryParam("resolution") @DefaultValue("1d") String resolution) {

        try {
            if (symbols == null || symbols.isBlank()) {
                throw new BadRequestException("symbols query parameter is required");
            }

            SimulationId simulationId = new SimulationId(UUID.fromString(id));
            UserId userId = currentUser.getCurrentUserId();

            List<Symbol> symbolList = java.util.Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> SymbolParser.parse(s, InvestmentType.fromTicker(s)))
                .toList();

            var command = new com.quorumtrading.application.usecase.command.CalculateSimulationOverlaysCommand(
                simulationId, symbolList, userId, resolution);

            List<com.quorumtrading.model.valueobject.OverlayResult> results =
                calculateSimulationOverlaysUseCase.execute(command);

            List<com.quorumtrading.adapter.rest.dto.response.OverlayResponse> response = results.stream()
                .map(result -> new com.quorumtrading.adapter.rest.dto.response.OverlayResponse(
                    result.symbol(),
                    result.assetType(),
                    result.status().name(),
                    result.dataPoints() != null
                        ? result.dataPoints().stream()
                            .map(dp -> new com.quorumtrading.adapter.rest.dto.response.OverlayDataPointResponse(
                                dp.timestamp().toString(),
                                dp.portfolioValue()))
                            .toList()
                        : List.of(),
                    result.returnPercent(),
                    result.errorMessage()
                ))
                .toList();

            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @POST
    @Path("/{id}/contributions")
    @Operation(
        summary = "Record a cash contribution into a LIVE simulation",
        description = "Injects cash into a LIVE simulation (recurring-investment top-up or manual contribution). "
                + "Idempotent on (sourcePlanId, contributionDate) when both are present.")
    @APIResponse(responseCode = "201", description = "Contribution recorded",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.ContributionResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request (non-positive amount, malformed IDs)")
    @APIResponse(responseCode = "404", description = "Simulation not found")
    @APIResponse(responseCode = "409", description = "Simulation is not in LIVE state")
    public Response recordContribution(
            @Parameter(description = "Simulation unique identifier", required = true)
            @PathParam("id") String id,
            @RequestBody(description = "Contribution request", required = true)
            com.quorumtrading.adapter.rest.dto.request.RecordContributionRequest request) {

        if (request == null || request.amount() == null) {
            throw new BadRequestException("amount is required");
        }
        if (request.amount().signum() <= 0) {
            throw new BadRequestException("amount must be positive");
        }

        SimulationId simulationId;
        try {
            simulationId = new SimulationId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        }

        Money.Currency currency = Money.Currency.USD;
        if (request.currency() != null && !request.currency().isBlank()) {
            try {
                currency = Money.Currency.valueOf(request.currency().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown currency: " + request.currency());
            }
        }
        Money amount = new Money(request.amount(), currency);

        UUID planId = parseUuidOrNull(request.sourcePlanId(), "sourcePlanId");
        UUID allocationId = parseUuidOrNull(request.sourceAllocationId(), "sourceAllocationId");
        Instant contributionDate = request.contributionDate() != null ? request.contributionDate() : Instant.now();

        try {
            var outcome = recordSimulationContributionUseCase.execute(
                    new com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand(
                            simulationId, amount, contributionDate, planId, allocationId,
                            currentUser.getCurrentUserId()));
            // 200 rather than 201 when the idempotency key matched an existing row: nothing was
            // created, and answering 201 to a repeat tells the caller it made a second deposit.
            return Response.status(outcome.alreadyRecorded()
                            ? Response.Status.OK : Response.Status.CREATED)
                    .entity(com.quorumtrading.adapter.rest.dto.response.ContributionResponse
                            .fromDomain(outcome.contribution()))
                    .build();
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Simulation not found with ID: " + id);
        } catch (IllegalStateException e) {
            // Simulation not in LIVE state, or no portfolio row to top up.
            return Response.status(Response.Status.CONFLICT)
                    .entity(ProblemDetails.of(
                            "conflict",
                            "Cannot record contribution",
                            Response.Status.CONFLICT.getStatusCode(),
                            e.getMessage(),
                            "/api/v1/simulations/" + id + "/contributions"))
                    .type(ProblemDetails.MEDIA_TYPE)
                    .build();
        }
    }

    @GET
    @Path("/{id}/contributions")
    @Operation(summary = "List contributions for a simulation",
            description = "Returns all contributions injected into this simulation, oldest first.")
    public Response listContributions(@PathParam("id") String id) {
        SimulationId simulationId;
        try {
            simulationId = new SimulationId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulation ID format: " + id);
        }
        var contributions = getSimulationContributionsUseCase.execute(
                simulationId, currentUser.getCurrentUserId());
        var response = contributions.stream()
                .map(com.quorumtrading.adapter.rest.dto.response.ContributionResponse::fromDomain)
                .toList();
        return Response.ok(response).build();
    }

    private static UUID parseUuidOrNull(String value, String paramName) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + paramName + ": " + value);
        }
    }
}
