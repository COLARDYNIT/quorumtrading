package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.PortfolioSnapshotResponse;
import com.quorumtrading.adapter.rest.dto.response.PortfolioSnapshotsResponse;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.mapper.DtoMapper;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.application.usecase.QueryPortfolioSnapshotsUseCase;
import com.quorumtrading.application.usecase.query.QueryPortfolioSnapshotsQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@io.quarkus.security.Authenticated
@Path("/api/v1/portfolios")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Portfolio", description = "Portfolio snapshots")
public class PortfolioResource {

    @Inject
    QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase;

    @Inject
    DtoMapper dtoMapper;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    @Path("/snapshots")
    @Operation(
        summary = "Query portfolio snapshots",
        description = "Retrieves historical portfolio snapshots for a simulation, showing portfolio state over time"
    )
    @APIResponse(
        responseCode = "200",
        description = "Portfolio snapshots retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = PortfolioSnapshotsResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - missing required parameters or invalid format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "500",
        description = "Internal server error",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public PortfolioSnapshotsResponse queryPortfolioSnapshots(
            @Parameter(
                description = "Simulation unique identifier",
                required = true,
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @QueryParam("simulationId") String simulationIdStr,
            @Parameter(
                description = "Start timestamp in ISO-8601 format",
                required = false,
                example = "2023-01-01T00:00:00Z"
            )
            @QueryParam("from") String fromStr,
            @Parameter(
                description = "End timestamp in ISO-8601 format",
                required = false,
                example = "2023-12-31T23:59:59Z"
            )
            @QueryParam("to") String toStr,
            @Parameter(
                description = "Maximum number of snapshots to return (1-1000)",
                required = false,
                example = "100"
            )
            @QueryParam("limit") Integer limit,
            @Parameter(
                description = "Snapshot resolution: 'all' (default) returns every snapshot, 'daily' returns one per day",
                required = false,
                example = "daily"
            )
            @QueryParam("resolution") @DefaultValue("all") String resolution,
            @Parameter(
                description = "When true, omit position details from snapshots for lighter responses (useful for charts that only need totalValue)",
                required = false
            )
            @QueryParam("excludePositions") @DefaultValue("false") boolean excludePositions) {

        // Validate required parameters
        if (simulationIdStr == null || simulationIdStr.isBlank()) {
            throw new BadRequestException("Query parameter 'simulationId' is required");
        }

        // Parse simulationId
        UUID simulationId;
        try {
            simulationId = UUID.fromString(simulationIdStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulationId format. Must be a valid UUID");
        }

        // Parse timestamps if provided
        Instant from = null;
        Instant to = null;
        if (fromStr != null && !fromStr.isBlank()) {
            try {
                from = Instant.parse(fromStr);
            } catch (Exception e) {
                throw new BadRequestException("Invalid 'from' timestamp format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)");
            }
        }
        if (toStr != null && !toStr.isBlank()) {
            try {
                to = Instant.parse(toStr);
            } catch (Exception e) {
                throw new BadRequestException("Invalid 'to' timestamp format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)");
            }
        }

        // Validate time range order if both provided
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("Query parameter 'from' must be before 'to'");
        }

        // Validate limit bounds
        if (limit != null) {
            if (limit < 1 || limit > 1000) {
                throw new BadRequestException("Query parameter 'limit' must be between 1 and 1000");
            }
        }

        // Validate resolution
        if (!"all".equalsIgnoreCase(resolution) && !"daily".equalsIgnoreCase(resolution)) {
            throw new BadRequestException("Query parameter 'resolution' must be 'all' or 'daily'");
        }

        // Authentication check
        UserId userId = currentUser.getCurrentUserIdOptional()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));

        // Create query and execute use case
        QueryPortfolioSnapshotsQuery query = new QueryPortfolioSnapshotsQuery(
                simulationId, from, to, limit, excludePositions, resolution, userId);
        List<PortfolioSnapshot> snapshots = queryPortfolioSnapshotsUseCase.execute(query);

        // Map to response DTOs
        List<PortfolioSnapshotResponse> snapshotResponses = dtoMapper.toPortfolioSnapshotResponseList(snapshots);

        // Return wrapper response
        return new PortfolioSnapshotsResponse(snapshotResponses, snapshotResponses.size(),
                simulationId, limit);
    }

    @GET
    @Path("/snapshots/latest")
    @Operation(
        summary = "Get latest portfolio snapshot",
        description = "Retrieves the most recent portfolio snapshot for a simulation"
    )
    @APIResponse(
        responseCode = "200",
        description = "Latest portfolio snapshot retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = PortfolioSnapshotResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - missing required parameters or invalid format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "No portfolio snapshots found for this simulation",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public PortfolioSnapshotResponse getLatestSnapshot(
            @Parameter(
                description = "Simulation unique identifier",
                required = true,
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @QueryParam("simulationId") String simulationIdStr) {

        // Validate required parameters
        if (simulationIdStr == null || simulationIdStr.isBlank()) {
            throw new BadRequestException("Query parameter 'simulationId' is required");
        }

        // Parse simulationId
        UUID simulationId;
        try {
            simulationId = UUID.fromString(simulationIdStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulationId format. Must be a valid UUID");
        }

        // Authentication check
        UserId userId = currentUser.getCurrentUserIdOptional()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));

        // Query with limit=1 to get only the latest snapshot (ordered by timestamp DESC)
        QueryPortfolioSnapshotsQuery query = new QueryPortfolioSnapshotsQuery(
                simulationId, null, null, 1, false, "all", userId);
        List<PortfolioSnapshot> snapshots = queryPortfolioSnapshotsUseCase.execute(query);

        if (snapshots.isEmpty()) {
            throw new NotFoundException("No portfolio snapshots found for simulation: " + simulationId);
        }

        return dtoMapper.toPortfolioSnapshotResponse(snapshots.get(0));
    }

}
