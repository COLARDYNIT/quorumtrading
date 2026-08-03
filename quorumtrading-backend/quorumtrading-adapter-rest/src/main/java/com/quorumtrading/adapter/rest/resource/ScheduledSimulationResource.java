package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.ScheduledSimulationDto.ScheduleResponse;
import com.quorumtrading.adapter.rest.dto.ScheduledSimulationDto.TriggerResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.GetScheduledSimulationByStrategyUseCase;
import com.quorumtrading.application.usecase.TriggerSimulationUseCase;
import com.quorumtrading.model.entity.ScheduledSimulation;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST resource for scheduled simulation management.
 *
 * <p><b>Hexagonal Architecture:</b> REST Adapter</p>
 * Provides HTTP endpoints for:
 * <ul>
 *   <li>Manual simulation triggering</li>
 *   <li>Viewing schedule status</li>
 * </ul>
 */
@Path("/api/v1/scheduled-simulations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Scheduled Simulations", description = "Scheduled simulation management and manual triggering")
@Authenticated
public class ScheduledSimulationResource {

    @Inject
    TriggerSimulationUseCase triggerSimulationUseCase;

    @Inject
    GetScheduledSimulationByStrategyUseCase getScheduledSimulationByStrategyUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @POST
    @Path("/strategies/{strategyId}/trigger")
    @Operation(
        summary = "Trigger simulation manually",
        description = "Manually triggers a backtest simulation for the specified strategy with default parameters"
    )
    @APIResponse(
        responseCode = "202",
        description = "Simulation triggered successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = TriggerResponse.class)
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
    @APIResponse(
        responseCode = "400",
        description = "Invalid strategy ID format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response triggerManually(
            @Parameter(description = "Strategy unique identifier", required = true)
            @PathParam("strategyId") String strategyId) {
        try {
            UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));
            UUID uuid = UUID.fromString(strategyId);
            SimulationId simId = triggerSimulationUseCase.triggerManually(new StrategyId(uuid), userId);
            return Response.accepted()
                .entity(Map.of(
                    "message", "Simulation triggered",
                    "simulationId", simId.value().toString()
                ))
                .build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                throw new NotFoundException(e.getMessage());
            }
            throw new BadRequestException("Invalid strategy ID format: " + strategyId);
        }
    }

    @GET
    @Path("/strategies/{strategyId}")
    @Operation(
        summary = "Get schedule for strategy",
        description = "Retrieves the scheduled simulation configuration for a strategy"
    )
    @APIResponse(
        responseCode = "200",
        description = "Schedule retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ScheduleResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "No schedule found for strategy",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Invalid strategy ID format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getSchedule(
            @Parameter(description = "Strategy unique identifier", required = true)
            @PathParam("strategyId") String strategyId) {
        try {
            UserId userId = currentUser.getCurrentUserIdOptional()
                .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));
            UUID uuid = UUID.fromString(strategyId);
            return getScheduledSimulationByStrategyUseCase.execute(new StrategyId(uuid), userId)
                .map(schedule -> Response.ok(toDto(schedule)).build())
                .orElseThrow(() -> new NotFoundException("No schedule found for strategy: " + strategyId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid strategy ID format: " + strategyId);
        }
    }

    private Map<String, Object> toDto(ScheduledSimulation schedule) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", schedule.getId().value().toString());
        dto.put("strategyId", schedule.getStrategyId().value().toString());
        dto.put("frequency", schedule.getFrequency().name());
        dto.put("enabled", schedule.isEnabled());
        dto.put("nextRunAt", schedule.getNextRunAt() != null ? schedule.getNextRunAt().toString() : null);
        dto.put("lastRunAt", schedule.getLastRunAt() != null ? schedule.getLastRunAt().toString() : null);
        return dto;
    }

}
