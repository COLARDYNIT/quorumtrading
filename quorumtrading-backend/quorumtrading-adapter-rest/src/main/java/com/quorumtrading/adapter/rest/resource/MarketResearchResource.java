package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.adapter.rest.dto.request.CreateMarketResearchRequest;
import com.quorumtrading.adapter.rest.dto.request.UpdateMarketResearchRequest;
import com.quorumtrading.adapter.rest.dto.response.MarketResearchDetailsResponse;
import com.quorumtrading.adapter.rest.dto.response.MarketResearchResponse;
import com.quorumtrading.adapter.rest.dto.response.ResearchExecutionAcceptedResponse;
import com.quorumtrading.adapter.rest.dto.response.ResearchExecutionResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.application.usecase.command.UpdateMarketResearchCommand;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.BacktestConfiguration;
import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for market research management.
 * Provides endpoints for creating, listing, and managing automated market research configurations.
 *
 * This adapter delegates to application layer use cases following hexagonal architecture.
 */
@Path("/api/v1/market-research")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Market Research", description = "Automated market research and strategy discovery")
public class MarketResearchResource {

    @Inject
    CreateMarketResearchUseCase createMarketResearchUseCase;

    @Inject
    UpdateMarketResearchUseCase updateMarketResearchUseCase;

    @Inject
    GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase;

    @Inject
    PauseMarketResearchUseCase pauseMarketResearchUseCase;

    @Inject
    ResumeMarketResearchUseCase resumeMarketResearchUseCase;

    @Inject
    ExecuteMarketResearchUseCase executeMarketResearchUseCase;

    @Inject
    ListMarketResearchUseCase listMarketResearchUseCase;

    @Inject
    DeleteMarketResearchUseCase deleteMarketResearchUseCase;

    @Inject
    DeleteResearchExecutionUseCase deleteResearchExecutionUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @POST
    @Operation(
        summary = "Create market research",
        description = "Creates a new market research configuration for automated opportunity discovery"
    )
    @APIResponse(
        responseCode = "201",
        description = "Market research created successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = MarketResearchResponse.class)
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
    public Response createMarketResearch(
            @RequestBody(description = "Market research creation request", required = true)
            @Valid CreateMarketResearchRequest request) {

        // Get current authenticated user
        UserId ownerId = currentUser.getCurrentUserId();

        // Convert DTO to command with owner
        CreateMarketResearchCommand command = toCommand(request, ownerId);

        // Execute use case
        MarketResearch research = createMarketResearchUseCase.execute(command);

        // Convert to response DTO
        MarketResearchResponse response = MarketResearchResponse.fromDomain(research);

        return Response.status(Response.Status.CREATED)
            .entity(response)
            .build();
    }

    @PUT
    @Path("/{id}")
    @Operation(
        summary = "Update market research",
        description = "Updates an existing market research configuration. Only provided fields will be updated."
    )
    @APIResponse(
        responseCode = "200",
        description = "Market research updated successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = MarketResearchResponse.class)
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
        description = "Market research not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response updateMarketResearch(
            @Parameter(description = "Market research unique identifier", required = true)
            @PathParam("id") String id,
            @RequestBody(description = "Market research update request", required = true)
            @Valid UpdateMarketResearchRequest request) {

        // Convert DTO to command
        UpdateMarketResearchCommand command = toUpdateCommand(UUID.fromString(id), request);

        // Execute use case
        MarketResearch research = updateMarketResearchUseCase.execute(command);

        // Convert to response DTO
        MarketResearchResponse response = MarketResearchResponse.fromDomain(research);

        return Response.ok(response).build();
    }

    @GET
    @Operation(
        summary = "List all market research",
        description = "Retrieves all market research configurations"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of market research configurations retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = MarketResearchResponse.class, type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY)
        )
    )
    public Response listAll() {
        UserId userId = currentUser.getCurrentUserId();
        List<MarketResearchSummary> summaries = listMarketResearchUseCase.execute(userId);
        List<MarketResearchResponse> responses = summaries.stream()
            .map(summary -> MarketResearchResponse.fromDomain(summary.research(), summary.strategyCount()))
            .toList();
        return Response.ok(responses).build();
    }

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Get market research details",
        description = "Retrieves comprehensive details for a market research configuration including execution history"
    )
    @APIResponse(
        responseCode = "200",
        description = "Market research details retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = MarketResearchDetailsResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Market research not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response getDetails(
            @Parameter(description = "Market research unique identifier", required = true)
            @PathParam("id") String id) {

        UUID researchId = UUID.fromString(id);
        UserId userId = currentUser.getCurrentUserId();
        MarketResearchDetails details = getMarketResearchDetailsUseCase.execute(researchId, userId);
        MarketResearchDetailsResponse response = MarketResearchDetailsResponse.fromDomain(details);
        return Response.ok(response).build();
    }

    @POST
    @Path("/{id}/pause")
    @Operation(
        summary = "Pause market research",
        description = "Pauses scheduled executions of a market research configuration"
    )
    @APIResponse(
        responseCode = "204",
        description = "Market research paused successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "Market research not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response pause(
            @Parameter(description = "Market research unique identifier", required = true)
            @PathParam("id") String id) {

        UUID researchId = UUID.fromString(id);
        UserId userId = currentUser.getCurrentUserId();
        pauseMarketResearchUseCase.execute(researchId, userId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/resume")
    @Operation(
        summary = "Resume market research",
        description = "Resumes scheduled executions of a paused market research configuration"
    )
    @APIResponse(
        responseCode = "204",
        description = "Market research resumed successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "Market research not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response resume(
            @Parameter(description = "Market research unique identifier", required = true)
            @PathParam("id") String id) {

        UUID researchId = UUID.fromString(id);
        UserId userId = currentUser.getCurrentUserId();
        resumeMarketResearchUseCase.execute(researchId, userId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/execute")
    @Operation(
        summary = "Manually execute market research",
        description = "Triggers asynchronous execution of market research. " +
            "Returns immediately with an execution ID. Subscribe to WebSocket for progress updates."
    )
    @APIResponse(
        responseCode = "202",
        description = "Market research execution started",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ResearchExecutionAcceptedResponse.class)
        )
    )
    @APIResponse(
        responseCode = "404",
        description = "Market research not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response execute(
            @Parameter(description = "Market research unique identifier", required = true)
            @PathParam("id") String id) {

        UUID researchId = UUID.fromString(id);
        UserId userId = currentUser.getCurrentUserId();
        UUID executionId = executeMarketResearchUseCase.execute(researchId, userId);
        ResearchExecutionAcceptedResponse response = ResearchExecutionAcceptedResponse.of(executionId, researchId);
        return Response.accepted(response).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(
        summary = "Delete market research",
        description = "Deletes a market research configuration and all its execution history"
    )
    @APIResponse(
        responseCode = "204",
        description = "Market research deleted successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "Market research not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response delete(
            @Parameter(description = "Market research unique identifier", required = true)
            @PathParam("id") String id) {

        UUID researchId = UUID.fromString(id);
        UserId userId = currentUser.getCurrentUserId();
        try {
            deleteMarketResearchUseCase.execute(researchId, userId);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Market research not found: " + id);
        }
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{researchId}/executions/{executionId}")
    @Operation(
        summary = "Delete an execution",
        description = "Deletes a specific execution from market research history."
    )
    @APIResponse(
        responseCode = "204",
        description = "Execution deleted successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "Execution not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response deleteExecution(
            @Parameter(description = "Market research unique identifier", required = true)
            @PathParam("researchId") String researchId,
            @Parameter(description = "Execution unique identifier", required = true)
            @PathParam("executionId") String executionId) {

        UUID execId = UUID.fromString(executionId);
        UUID parentResearchId = UUID.fromString(researchId);
        UserId userId = currentUser.getCurrentUserId();

        try {
            deleteResearchExecutionUseCase.execute(execId, parentResearchId, userId);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("Execution not found: " + executionId);
        }
        return Response.noContent().build();
    }

    /**
     * Convert request DTO to domain command.
     */
    private CreateMarketResearchCommand toCommand(CreateMarketResearchRequest request, UserId ownerId) {
        // Convert ticker strings to Symbol value objects (handle null/empty).
        // Shared "TICKER[:TYPE]" convention with the MCP tool: bare → STOCK,
        // explicit type via suffix (e.g. "BTC:CRYPTO").
        Set<Symbol> symbols = request.targetAssets() != null && !request.targetAssets().isEmpty()
            ? request.targetAssets().stream()
                .map(SymbolParser::parseWithTypeSuffix)
                .collect(Collectors.toSet())
            : Set.of();

        // Convert interval DTO to value object
        ResearchInterval interval = new ResearchInterval(
            request.interval().amount(),
            ResearchInterval.Unit.valueOf(request.interval().unit())
        );

        // Convert backtest config DTO to value object (if provided)
        BacktestConfiguration backtestConfig = null;
        if (request.backtestConfig() != null) {
            var config = request.backtestConfig();
            backtestConfig = new BacktestConfiguration(
                Duration.parse(config.backtestLength()),
                config.periodStart() != null ? Instant.parse(config.periodStart()) : null,
                config.periodEnd() != null ? Instant.parse(config.periodEnd()) : null
            );
        }

        // Build command with markdown instructions
        String markdownInstructions = request.description() != null
            ? request.description()
            : "# Default Research Instructions\n\nAnalyze target assets for trading opportunities.";

        return new CreateMarketResearchCommand(
            request.name(),
            markdownInstructions,
            symbols,
            interval,
            request.autoBacktest(),
            backtestConfig,
            ownerId
        );
    }

    /**
     * Convert update request DTO to domain command.
     */
    private UpdateMarketResearchCommand toUpdateCommand(UUID id, UpdateMarketResearchRequest request) {
        // Convert ticker strings to Symbol value objects (if provided).
        // Same shared "TICKER[:TYPE]" convention as create (bare → STOCK).
        Set<Symbol> symbols = null;
        if (request.targetAssets() != null && !request.targetAssets().isEmpty()) {
            symbols = request.targetAssets().stream()
                .map(SymbolParser::parseWithTypeSuffix)
                .collect(Collectors.toSet());
        }

        // Convert interval DTO to value object (if provided)
        ResearchInterval interval = null;
        if (request.interval() != null) {
            interval = new ResearchInterval(
                request.interval().amount(),
                ResearchInterval.Unit.valueOf(request.interval().unit())
            );
        }

        return new UpdateMarketResearchCommand(
            id,
            request.name(),
            request.description(),
            symbols,
            interval,
            currentUser.getCurrentUserId()
        );
    }
}
