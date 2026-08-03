package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CostEstimateRequest;
import com.quorumtrading.adapter.rest.dto.request.UpdateModelPreferencesRequest;
import com.quorumtrading.adapter.rest.dto.response.CostEstimateResponse;
import com.quorumtrading.adapter.rest.dto.response.ContextRequirementsResponse;
import com.quorumtrading.adapter.rest.dto.response.FallbackConfigResponse;
import com.quorumtrading.adapter.rest.dto.response.LLMModelResponse;
import com.quorumtrading.adapter.rest.dto.response.ModelPreferencesResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.EstimateBacktestCostUseCase;
import com.quorumtrading.application.usecase.GetAvailableModelsUseCase;
import com.quorumtrading.application.usecase.GetModelReliabilityUseCase;
import com.quorumtrading.application.usecase.UpdateModelPreferencesUseCase;
import com.quorumtrading.application.usecase.dto.ModelReliabilityStats;
import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.valueobject.CategorizedModel;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.NewsDataAvailability;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.cache.CacheResult;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST resource for AI configuration settings.
 * Provides endpoints for viewing available models and managing model preferences.
 */
@Path("/api/v1/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Config", description = "AI configuration settings")
@PermitAll
public class ConfigResource {

    private static final Logger LOG = Logger.getLogger(ConfigResource.class);

    @Inject
    GetAvailableModelsUseCase getAvailableModelsUseCase;

    @Inject
    UpdateModelPreferencesUseCase updateModelPreferencesUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    EstimateBacktestCostUseCase estimateBacktestCostUseCase;

    @Inject
    GetModelReliabilityUseCase getModelReliabilityUseCase;

    @Inject
    com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase describeFallbackBehaviourUseCase;

    @Inject
    com.quorumtrading.application.usecase.GetEffectiveModelsUseCase getEffectiveModelsUseCase;

    @Inject
    NewsDataAvailability newsDataAvailability;

    /**
     * Get available LLM models, optionally filtered by trading context.
     *
     * @param context Optional trading context to filter models
     * @return List of available models
     */
    @GET
    @Path("/models")
    @Operation(
        summary = "Get available LLM models",
        description = "Returns a list of available LLM models from OpenRouter. " +
                      "Optionally filter by trading context to get only models that meet the requirements."
    )
    @APIResponse(
        responseCode = "200",
        description = "Models retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = LLMModelResponse[].class)
        )
    )
    public Response getModels(
        @Parameter(description = "Trading context to filter models (RESEARCH, BACKTESTING, PAPER_TRADING, LIVE_TRADING)")
        @QueryParam("context") String context,
        @Parameter(description = "Comma-separated model tiers to include (e.g. RECOMMENDED,PROVEN)")
        @QueryParam("tiers") String tiers
    ) {
        TradingContext tradingContext = parseContext(context);
        Set<ModelTier> tierFilter = parseTiers(tiers);

        List<LLMModelResponse> response;
        if (tradingContext != null) {
            LOG.debugf("Getting models with tiers for context %s", tradingContext);
            List<CategorizedModel> categorized = getAvailableModelsUseCase.executeWithTiers(tradingContext, tierFilter);
            response = categorized.stream()
                .map(LLMModelResponse::fromCategorized)
                .toList();
        } else {
            LOG.debug("Getting all available models");
            List<LLMModelInfo> models = getAvailableModelsUseCase.execute();
            response = models.stream()
                .map(LLMModelResponse::fromDomain)
                .toList();
        }

        return Response.ok(response).build();
    }

    /**
     * Get the current user's model preferences.
     *
     * @return User's model preferences
     */
    @GET
    @Path("/preferences")
    @Operation(
        summary = "Get model preferences",
        description = "Returns the current user's preferred LLM models for each trading context."
    )
    @APIResponse(
        responseCode = "200",
        description = "Preferences retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ModelPreferencesResponse.class)
        )
    )
    @APIResponse(
        responseCode = "401",
        description = "Unauthorized - user not authenticated",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @Authenticated
    public Response getPreferences() {
        UserId userId = currentUser.getCurrentUserId();

        LOG.debugf("Getting model preferences for user %s", userId.value());

        ModelPreferences preferences = updateModelPreferencesUseCase.getPreferences(userId);
        ModelPreferencesResponse response = ModelPreferencesResponse.fromDomain(preferences);

        return Response.ok(response).build();
    }

    /**
     * Update the current user's model preferences.
     *
     * @param request The model preferences to set
     * @return Updated preferences
     */
    @PUT
    @Path("/preferences")
    @Operation(
        summary = "Update model preferences",
        description = "Update the current user's preferred LLM models for each trading context. " +
                      "Models are validated to ensure they exist and meet context requirements."
    )
    @APIResponse(
        responseCode = "200",
        description = "Preferences updated successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ModelPreferencesResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Invalid request - model not found or not valid for context",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "401",
        description = "Unauthorized - user not authenticated",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @Authenticated
    public Response updatePreferences(@Valid UpdateModelPreferencesRequest request) {
        UserId userId = currentUser.getCurrentUserId();

        LOG.infof("Updating model preferences for user %s", userId.value());

        try {
            ModelPreferences preferences = updateModelPreferencesUseCase.execute(
                userId,
                request.toContextMap(),
                request.toAgentOverridesMap()
            );
            ModelPreferencesResponse response = ModelPreferencesResponse.fromDomain(preferences);

            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid model preference: %s", e.getMessage());
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Get trading context requirements.
     *
     * @param context The trading context
     * @return Requirements description
     */
    @GET
    @Path("/requirements/{context}")
    @Operation(
        summary = "Get context requirements",
        description = "Get the model requirements for a specific trading context."
    )
    @APIResponse(
        responseCode = "200",
        description = "Requirements retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = String.class)
        )
    )
    public Response getContextRequirements(
        @Parameter(description = "Trading context (RESEARCH, BACKTESTING, PAPER_TRADING, LIVE_TRADING)")
        @PathParam("context") String context
    ) {
        TradingContext tradingContext = parseContext(context);
        if (tradingContext == null) {
            throw new BadRequestException("Invalid context: " + context);
        }

        String requirements = getAvailableModelsUseCase.getContextRequirements(tradingContext);
        return Response.ok(new ContextRequirementsResponse(tradingContext.name(), requirements)).build();
    }

    /**
     * Estimate the AI budget cost for a backtest or benchmark.
     */
    @POST
    @Path("/cost-estimate")
    @Operation(
        summary = "Estimate backtest cost",
        description = "Estimate the AI budget cost for a backtest or benchmark based on strategy configuration, " +
                      "date range, and the user's selected model. Returns dollar estimate with confidence range."
    )
    @APIResponse(
        responseCode = "200",
        description = "Cost estimate computed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = CostEstimateResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @Authenticated
    public Response estimateCost(@Valid CostEstimateRequest request) {
        UserId userId = currentUser.getCurrentUserId();

        long calendarDays = Duration.between(request.startDate(), request.endDate()).toDays();
        if (calendarDays <= 0) {
            throw new BadRequestException("End date must be after start date");
        }

        List<UUID> strategyIds = request.strategyIds().stream()
            .map(UUID::fromString)
            .toList();

        com.quorumtrading.model.valueobject.CostEstimate estimate;
        if (strategyIds.size() == 1) {
            // Dates, not day count: how many trading days a span holds depends on which weekday
            // it starts on, and the request already carries them. The day-count form remains the
            // benchmark path, which prices several strategies over one nominal span.
            estimate = estimateBacktestCostUseCase.estimateForSimulation(
                new com.quorumtrading.model.valueobject.StrategyId(strategyIds.get(0)),
                request.startDate(),
                request.endDate(),
                userId
            );
        } else {
            estimate = estimateBacktestCostUseCase.estimateForBenchmark(strategyIds, calendarDays, userId);
        }

        return Response.ok(CostEstimateResponse.fromDomain(estimate)).build();
    }

    /**
     * Get aggregated model reliability statistics.
     * This endpoint is public — it returns platform-wide aggregate data with no user-specific info.
     *
     * @param days Number of days to look back (default 30)
     * @return Model reliability entries with success/failure counts and failure rates
     */
    @GET
    @Path("/model-reliability")
    @Operation(
        summary = "Get model reliability statistics",
        description = "Returns aggregated success/failure counts per LLM model over the specified time window. " +
                      "Useful for identifying models with high parse failure rates."
    )
    @APIResponse(
        responseCode = "200",
        description = "Reliability stats retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ModelReliabilityStats.class)
        )
    )
    @CacheResult(cacheName = "model-reliability")
    public ModelReliabilityStats getModelReliability(
        @Parameter(description = "Number of days to look back (default 30)")
        @QueryParam("days") @DefaultValue("30") int days
    ) {
        return getModelReliabilityUseCase.execute(days);
    }

    /**
     * Get the LLM model fallback chain configuration.
     * This endpoint is public and does not require authentication.
     *
     * @return Fallback chain configuration
     */
    @GET
    @Path("/fallback")
    @Operation(
        summary = "Get model fallback configuration",
        description = "Returns the LLM model fallback chain configuration. " +
                      "When a model fails (rate limit, spend limit, or service unavailable), " +
                      "the system automatically tries the next model in this chain."
    )
    @APIResponse(
        responseCode = "200",
        description = "Fallback configuration retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = FallbackConfigResponse.class)
        )
    )
    public Response getFallbackConfig() {
        LOG.debug("Getting fallback behaviour of the active LLM engine");
        FallbackConfigResponse response =
                FallbackConfigResponse.from(describeFallbackBehaviourUseCase.execute());
        return Response.ok(response).build();
    }

    /**
     * The model each trading context will actually run.
     *
     * <p>The one endpoint the settings page needs and never had. Without it the frontend duplicated
     * the backend's defaults in a hardcoded constant, which drifted exactly as an unowned duplicate
     * always will — it went on advertising a model the backend's own comments had marked deprecated
     * and sunset.</p>
     */
    @GET
    @Path("/effective-models")
    @Operation(
        summary = "Effective model per trading context",
        description = "What each trading context will actually run on the currently selected LLM engine, "
                + "including which tier decided it and any substitution. This is the value every surface "
                + "should render; do not recompute it."
    )
    @APIResponse(responseCode = "200", description = "Effective models resolved successfully")
    @Authenticated
    public Response getEffectiveModels() {
        UserId userId = currentUser.getCurrentUserId();
        LOG.debugf("Getting effective models for user %s", userId.value());
        return Response.ok(
                com.quorumtrading.adapter.rest.dto.response.EffectiveModelsResponse.fromDomain(
                        getEffectiveModelsUseCase.execute(userId))).build();
    }

    /**
     * Get the earliest date from which news data is available for backtesting.
     * Used by the frontend to warn users when backtests start before news coverage begins.
     */
    @GET
    @Path("/news-availability")
    @Operation(
        summary = "Get news data availability",
        description = "Returns the earliest date from which historical news data is available. " +
                      "Backtests starting before this date will have no news context for Fundamental/Sentiment agents."
    )
    @APIResponse(
        responseCode = "200",
        description = "News availability retrieved successfully"
    )
    public Response getNewsAvailability() {
        return Response.ok(Map.of(
            "earliestNewsDate", newsDataAvailability.earliestNewsDate().toString()
        )).build();
    }

    /**
     * Parse context string to TradingContext enum.
     */
    private TradingContext parseContext(String context) {
        if (context == null || context.isBlank()) {
            return null;
        }
        try {
            return TradingContext.valueOf(context.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parse comma-separated tiers string to a Set of ModelTier.
     * Invalid values are silently ignored.
     */
    private Set<ModelTier> parseTiers(String tiers) {
        if (tiers == null || tiers.isBlank()) {
            return null;
        }
        Set<ModelTier> result = EnumSet.noneOf(ModelTier.class);
        Arrays.stream(tiers.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(s -> {
                try {
                    result.add(ModelTier.valueOf(s.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // silently ignore invalid tier values
                }
            });
        return result.isEmpty() ? null : result;
    }

}
