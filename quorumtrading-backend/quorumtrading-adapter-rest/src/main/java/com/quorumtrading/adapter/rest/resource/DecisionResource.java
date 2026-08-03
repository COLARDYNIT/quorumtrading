package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.ConsensusDecisionResponse;
import com.quorumtrading.adapter.rest.dto.response.ConsensusDecisionsResponse;
import com.quorumtrading.adapter.rest.dto.response.ConsensusWithAgentsResponseDTO;
import com.quorumtrading.adapter.rest.dto.response.LLMDecisionResponse;
import com.quorumtrading.adapter.rest.dto.response.LLMDecisionsResponse;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.mapper.DtoMapper;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.application.usecase.GetConsensusWithAgentsUseCase;
import com.quorumtrading.application.usecase.QueryConsensusDecisionsUseCase;
import com.quorumtrading.application.usecase.QueryLLMDecisionsUseCase;
import com.quorumtrading.application.usecase.command.GetConsensusWithAgentsCommand;
import com.quorumtrading.application.usecase.query.QueryConsensusDecisionsQuery;
import com.quorumtrading.application.usecase.query.QueryLLMDecisionsQuery;
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
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import io.quarkus.security.Authenticated;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@Authenticated
@Path("/api/v1/decisions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Decisions", description = "Agent decision history")
public class DecisionResource {

    @Inject
    QueryLLMDecisionsUseCase queryLLMDecisionsUseCase;

    @Inject
    QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase;

    @Inject
    GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase;

    @Inject
    com.quorumtrading.application.usecase.GetMarketDataContextUseCase getMarketDataContextUseCase;

    @Inject
    DtoMapper dtoMapper;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    @Path("/llm")
    @Operation(
        summary = "List LLM decisions",
        description = "Retrieves individual LLM agent decisions for a simulation, optionally filtered by time range and agent ID"
    )
    @APIResponse(
        responseCode = "200",
        description = "LLM decisions retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = LLMDecisionsResponse.class)
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
    public LLMDecisionsResponse queryLLMDecisions(
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
                description = "Filter by specific agent ID",
                required = false,
                example = "agent-001"
            )
            @QueryParam("agentId") String agentId,
            @Parameter(
                description = "Maximum number of decisions to return (1-1000)",
                required = false,
                example = "100"
            )
            @QueryParam("limit") Integer limit,
            @Parameter(
                description = "Get decisions with ID lower than this value (for pagination backwards/previous)",
                required = false,
                example = "12345"
            )
            @QueryParam("beforeLLMDecisionId") Long beforeLLMDecisionId,
            @Parameter(
                description = "Get decisions with ID higher than this value (for pagination forwards/next)",
                required = false,
                example = "12345"
            )
            @QueryParam("afterLLMDecisionId") Long afterLLMDecisionId) {

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

        // Create query and execute use case
        QueryLLMDecisionsQuery query = new QueryLLMDecisionsQuery(
                simulationId, from, to, agentId, limit, beforeLLMDecisionId, afterLLMDecisionId,
                currentUser.getCurrentUserId());
        List<LLMDecision> decisions = queryLLMDecisionsUseCase.execute(query);

        // Map to response DTOs
        List<LLMDecisionResponse> decisionResponses = dtoMapper.toLLMDecisionResponseList(decisions);

        // Return wrapper response
        return new LLMDecisionsResponse(decisionResponses, decisionResponses.size(),
                simulationId, limit);
    }

    @GET
    @Path("/consensus")
    @Operation(
        summary = "List consensus decisions",
        description = "Retrieves consensus decisions (aggregated from LLM agents) for a simulation, optionally filtered by time range and execution status"
    )
    @APIResponse(
        responseCode = "200",
        description = "Consensus decisions retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ConsensusDecisionsResponse.class)
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
    public ConsensusDecisionsResponse queryConsensusDecisions(
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
                description = "Filter by execution status (true = executed, false = not executed). Deprecated: use executionOutcome instead",
                required = false,
                example = "true"
            )
            @QueryParam("executed") Boolean executed,
            @Parameter(
                description = "Filter by execution outcome (PENDING, EXECUTED, NO_TRADE_REQUIRED, "
                    + "BLOCKED_BY_RISK_LIMITS, BELOW_MINIMUM_TRADE_VALUE, REJECTED, SKIPPED)",
                required = false,
                example = "EXECUTED"
            )
            @QueryParam("executionOutcome") String executionOutcomeStr,
            @Parameter(
                description = "Filter by consensus action (BUY, SELL, HOLD). Can specify comma-separated values for multiple actions",
                required = false,
                example = "BUY,SELL"
            )
            @QueryParam("action") String actionStr,
            @Parameter(
                description = "Maximum number of decisions to return (1-1000)",
                required = false,
                example = "100"
            )
            @QueryParam("limit") Integer limit) {

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

        // Parse executionOutcome if provided
        ExecutionOutcome executionOutcome = null;
        if (executionOutcomeStr != null && !executionOutcomeStr.isBlank()) {
            try {
                executionOutcome = ExecutionOutcome.valueOf(executionOutcomeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid executionOutcome value. Must be one of: "
                    + java.util.Arrays.stream(ExecutionOutcome.values())
                        .map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
            }
        }

        // Parse actions if provided (comma-separated list)
        List<String> actions = null;
        if (actionStr != null && !actionStr.isBlank()) {
            actions = List.of(actionStr.split(","))
                .stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(action -> !action.isEmpty())
                .toList();

            // Validate action values
            for (String action : actions) {
                if (!action.equals("BUY") && !action.equals("SELL") && !action.equals("HOLD")) {
                    throw new BadRequestException("Invalid action value: " + action + ". Must be one of: BUY, SELL, HOLD");
                }
            }
        }

        // Create query and execute use case
        UserId userId = currentUser.getCurrentUserId();
        QueryConsensusDecisionsQuery query = new QueryConsensusDecisionsQuery(
                simulationId, from, to, executed, executionOutcome, actions, limit, userId);
        List<ConsensusDecision> decisions = queryConsensusDecisionsUseCase.execute(query);

        // Map to response DTOs
        List<ConsensusDecisionResponse> decisionResponses = dtoMapper.toConsensusDecisionResponseList(decisions);

        // Return wrapper response
        return new ConsensusDecisionsResponse(decisionResponses, decisionResponses.size(),
                simulationId, limit);
    }

    @GET
    @Path("/consensus-with-agents")
    @Operation(
        summary = "Get consensus decisions with agent details",
        description = "Retrieves consensus decisions along with the individual agent LLM decisions that contributed to each consensus. " +
                "Supports filtering by simulation ID, ticker symbol, and consensus action, with ID-based pagination for efficient navigation."
    )
    @APIResponse(
        responseCode = "200",
        description = "Consensus decisions with agent details retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ConsensusWithAgentsResponseDTO.class)
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
    public List<ConsensusWithAgentsResponseDTO> getConsensusWithAgents(
            @Parameter(
                description = "Simulation unique identifier",
                required = true,
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @QueryParam("simulationId") String simulationIdStr,
            @Parameter(
                description = "Filter by ticker symbol (e.g., 'BTC', 'AAPL')",
                required = false,
                example = "BTC"
            )
            @QueryParam("ticker") String ticker,
            @Parameter(
                description = "Filter by consensus action (BUY, SELL, HOLD). Can specify comma-separated values for multiple actions",
                required = false,
                example = "BUY,SELL"
            )
            @QueryParam("action") String actionStr,
            @Parameter(
                description = "Maximum number of consensus decisions to return (1-100)",
                required = false,
                example = "20"
            )
            @QueryParam("limit") Integer limit,
            @Parameter(
                description = "Get consensus decisions with ID lower than this value (for pagination backwards/previous)",
                required = false,
                example = "12345"
            )
            @QueryParam("beforeConsensusId") Long beforeConsensusId,
            @Parameter(
                description = "Get consensus decisions with ID higher than this value (for pagination forwards/next)",
                required = false,
                example = "12345"
            )
            @QueryParam("afterConsensusId") Long afterConsensusId) {

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

        // Validate limit bounds
        if (limit != null) {
            if (limit < 1 || limit > 100) {
                throw new BadRequestException("Query parameter 'limit' must be between 1 and 100");
            }
        }

        // Parse actions if provided (comma-separated list)
        List<String> actions = null;
        if (actionStr != null && !actionStr.isBlank()) {
            actions = List.of(actionStr.split(","))
                .stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(action -> !action.isEmpty())
                .toList();

            // Validate action values
            for (String action : actions) {
                if (!action.equals("BUY") && !action.equals("SELL") && !action.equals("HOLD")) {
                    throw new BadRequestException("Invalid action value: " + action + ". Must be one of: BUY, SELL, HOLD");
                }
            }
        }

        // Create command and execute use case
        UserId userId = currentUser.getCurrentUserId();
        GetConsensusWithAgentsCommand command = new GetConsensusWithAgentsCommand(
                simulationId,
                ticker,
                actions,
                limit,
                beforeConsensusId,
                afterConsensusId,
                userId
        );
        List<ConsensusWithAgentsResponse> responses = getConsensusWithAgentsUseCase.execute(command);

        // Map to response DTOs
        return dtoMapper.toConsensusWithAgentsResponseDTOList(responses);
    }

    @GET
    @Path("/consensus-with-agents/{consensusId}")
    @Operation(
        summary = "Get a single consensus decision with agent details",
        description = "Retrieves a single consensus decision by ID along with the individual agent LLM decisions that contributed to it. " +
                "Use this for on-demand loading of agent details when a user clicks a timeline entry."
    )
    @APIResponse(
        responseCode = "200",
        description = "Consensus decision with agent details retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ConsensusWithAgentsResponseDTO.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Consensus decision not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public ConsensusWithAgentsResponseDTO getConsensusWithAgentsById(
            @Parameter(
                description = "Consensus decision ID",
                required = true,
                example = "12345"
            )
            @PathParam("consensusId") Long consensusId) {

        UserId userId = currentUser.getCurrentUserId();
        return getConsensusWithAgentsUseCase.executeById(consensusId, userId)
                .map(dtoMapper::toConsensusWithAgentsResponseDTO)
                .orElseThrow(() -> new NotFoundException("Consensus decision not found: " + consensusId));
    }

    @GET
    @Path("/market-data-context")
    @Operation(
        summary = "Get market data context for simulation",
        description = "Retrieves the exact market data context that was (or would be) available to AI agents " +
                "during consensus evaluation at a specific timestamp. Returns multi-timeframe market data " +
                "grouped by ticker, matching exactly what the agents see during analysis."
    )
    @APIResponse(
        responseCode = "200",
        description = "Market data context retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO.class)
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
        description = "Simulation or strategy not found",
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
    public com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO getMarketDataContext(
            @Parameter(
                description = "Simulation unique identifier",
                required = true,
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @QueryParam("simulationId") String simulationIdStr,
            @Parameter(
                description = "Analysis timestamp in ISO-8601 format (the point in time to retrieve market data for)",
                required = true,
                example = "2025-11-03T18:14:29Z"
            )
            @QueryParam("timestamp") String timestampStr) {

        // Validate required parameters
        if (simulationIdStr == null || simulationIdStr.isBlank()) {
            throw new BadRequestException("Query parameter 'simulationId' is required");
        }
        if (timestampStr == null || timestampStr.isBlank()) {
            throw new BadRequestException("Query parameter 'timestamp' is required");
        }

        // Parse simulationId
        UUID simulationUuid;
        try {
            simulationUuid = UUID.fromString(simulationIdStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid simulationId format. Must be a valid UUID");
        }

        // Parse timestamp
        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampStr);
        } catch (Exception e) {
            throw new BadRequestException("Invalid timestamp format. Use ISO-8601 format (e.g., 2025-11-03T18:14:29Z)");
        }

        // Create command and execute use case
        com.quorumtrading.model.valueobject.SimulationId simulationId = new com.quorumtrading.model.valueobject.SimulationId(simulationUuid);
        UserId userId = currentUser.getCurrentUserId();
        com.quorumtrading.application.usecase.command.GetMarketDataContextCommand command =
                new com.quorumtrading.application.usecase.command.GetMarketDataContextCommand(simulationId, timestamp, userId);

        Map<String, com.quorumtrading.model.marketdata.MarketDataContext> contextByTicker =
                getMarketDataContextUseCase.execute(command);

        // Map to response DTO
        return dtoMapper.toMarketDataContextResponseDTO(contextByTicker, simulationId, timestamp);
    }
}
