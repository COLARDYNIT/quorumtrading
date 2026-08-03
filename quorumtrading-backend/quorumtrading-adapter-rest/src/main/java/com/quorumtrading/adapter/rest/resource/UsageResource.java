package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.LLMUsageStatisticsResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.GetLLMUsageStatisticsUseCase;
import com.quorumtrading.application.usecase.dto.LLMUsageStatistics;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * REST resource for usage statistics and billing information.
 * Provides endpoints for viewing LLM usage costs.
 */
@Path("/api/v1/usage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Usage", description = "Usage statistics and billing information")
public class UsageResource {

    private static final Logger LOG = Logger.getLogger(UsageResource.class);

    @Inject
    GetLLMUsageStatisticsUseCase getLLMUsageStatisticsUseCase;

    @Inject
    CurrentUserProvider currentUser;

    /**
     * Get LLM usage statistics for the current user.
     * Returns daily breakdown for the last 30 days plus weekly, monthly, and all-time totals.
     *
     * @return LLM usage statistics
     */
    @GET
    @Path("/llm")
    @Operation(
        summary = "Get LLM usage statistics",
        description = "Returns LLM usage statistics for the current user including daily breakdown " +
                      "for the last 30 days, and weekly, monthly, and all-time totals. " +
                      "Costs shown are billed costs (raw cost + markup)."
    )
    @APIResponse(
        responseCode = "200",
        description = "Usage statistics retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = LLMUsageStatisticsResponse.class)
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
    public Response getLLMUsageStatistics() {
        UserId userId = currentUser.getCurrentUserId();

        LOG.debugf("Getting LLM usage statistics for user %s", userId.value());

        LLMUsageStatistics statistics = getLLMUsageStatisticsUseCase.execute(userId);
        LLMUsageStatisticsResponse response = LLMUsageStatisticsResponse.fromDomain(statistics);

        return Response.ok(response).build();
    }

}
