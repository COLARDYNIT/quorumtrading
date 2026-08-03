package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.TopUpBudgetRequest;
import com.quorumtrading.adapter.rest.dto.request.UpdateDailySpendLimitRequest;
import com.quorumtrading.adapter.rest.dto.response.UserBudgetResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.usecase.GetUserBudgetUseCase;
import com.quorumtrading.application.usecase.TopUpBudgetUseCase;
import com.quorumtrading.application.usecase.UpdateDailySpendLimitUseCase;
import com.quorumtrading.application.usecase.dto.UserBudgetDto;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * REST resource for user AI budget management.
 * Provides endpoints for viewing budget status and adding funds.
 */
@Path("/api/v1/budget")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Budget", description = "AI budget management")
public class BudgetResource {

    private static final Logger LOG = Logger.getLogger(BudgetResource.class);

    @Inject
    GetUserBudgetUseCase getUserBudgetUseCase;

    @Inject
    TopUpBudgetUseCase topUpBudgetUseCase;

    @Inject
    UpdateDailySpendLimitUseCase updateDailySpendLimitUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    PaymentPort paymentPort;

    /**
     * Get the current user's budget information.
     *
     * @return user budget details
     */
    @GET
    @Operation(
        summary = "Get budget information",
        description = "Returns the current user's AI budget including limit, spend, and remaining balance."
    )
    @APIResponse(
        responseCode = "200",
        description = "Budget information retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = UserBudgetResponse.class)
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
    public Response getBudget() {
        UserId userId = currentUser.getCurrentUserId();

        LOG.debugf("Getting budget for user %s", userId.value());

        UserBudgetDto budget = getUserBudgetUseCase.execute(userId);
        UserBudgetResponse response = UserBudgetResponse.fromDto(budget);

        return Response.ok(response).build();
    }

    /**
     * Top up the user's budget (mock/dev only).
     * Disabled when a real payment provider (Stripe) is configured.
     * In production, use POST /api/v1/payment/checkout instead.
     *
     * @param request the top-up request with amount
     * @return updated budget information
     */
    @POST
    @Path("/top-up")
    @Operation(
        summary = "Top up budget (dev only)",
        description = "Add funds to the user's AI budget. Only available when no payment provider is configured. " +
                      "In production, use POST /api/v1/payment/checkout for real payments via Stripe."
    )
    @APIResponse(
        responseCode = "200",
        description = "Budget topped up successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = UserBudgetResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Invalid request - amount must be positive",
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
    @APIResponse(
        responseCode = "403",
        description = "Forbidden - mock top-up disabled when payment provider is configured",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response topUp(@Valid TopUpBudgetRequest request) {
        if (paymentPort.isConfigured()) {
            LOG.warnf("Mock top-up attempt blocked — payment provider is configured");
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ProblemDetails.of(
                            "mock-topup-disabled",
                            "Mock Top-Up Disabled",
                            Response.Status.FORBIDDEN.getStatusCode(),
                            "Mock top-up is disabled when payment provider is configured. Use POST /api/v1/payment/checkout instead.",
                            "/api/v1/budget/top-up"
                    ))
                    .type(ProblemDetails.MEDIA_TYPE)
                    .build();
        }

        UserId userId = currentUser.getCurrentUserId();

        LOG.infof("Processing mock top-up of $%s for user %s", request.amount(), userId.value());

        UserBudgetDto budget = topUpBudgetUseCase.execute(userId, request.amount());
        UserBudgetResponse response = UserBudgetResponse.fromDto(budget);

        return Response.ok(response).build();
    }

    /**
     * Update the user's daily spend limit.
     * Send null to clear the limit.
     */
    @PUT
    @Path("/daily-limit")
    @Operation(
        summary = "Update daily spend limit",
        description = "Set or clear the user's daily AI spend limit. Send null to remove the limit."
    )
    @APIResponse(
        responseCode = "200",
        description = "Daily spend limit updated successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = UserBudgetResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Invalid request - limit must be positive if set",
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
    public Response updateDailyLimit(UpdateDailySpendLimitRequest request) {
        UserId userId = currentUser.getCurrentUserId();

        LOG.infof("Updating daily spend limit for user %s to %s", userId.value(),
                request.dailySpendLimit() != null ? "$" + request.dailySpendLimit() : "no limit");

        try {
            updateDailySpendLimitUseCase.execute(userId, request.dailySpendLimit());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetails.of(
                            "invalid-daily-limit",
                            "Invalid Daily Limit",
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            e.getMessage(),
                            "/api/v1/budget/daily-limit"
                    ))
                    .type(ProblemDetails.MEDIA_TYPE)
                    .build();
        }

        // Return updated budget
        UserBudgetDto budget = getUserBudgetUseCase.execute(userId);
        return Response.ok(UserBudgetResponse.fromDto(budget)).build();
    }

}
