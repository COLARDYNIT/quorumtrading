package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateApiTokenRequest;
import com.quorumtrading.adapter.rest.dto.response.ApiTokenCreatedResponse;
import com.quorumtrading.adapter.rest.dto.response.ApiTokenResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.exception.NotFoundException;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase;
import com.quorumtrading.application.usecase.apitoken.ListApiTokensUseCase;
import com.quorumtrading.application.usecase.apitoken.RevokeApiTokenUseCase;
import com.quorumtrading.application.usecase.command.CreateApiTokenCommand;
import com.quorumtrading.application.usecase.command.RevokeApiTokenCommand;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;
import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * REST resource for API token management.
 * Allows users to create, list, and revoke API tokens for programmatic access (e.g., MCP clients).
 */
@Path("/api/v1/settings/api-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "API Tokens", description = "Manage API tokens for programmatic access")
public class ApiTokenResource {

    @Inject
    CreateApiTokenUseCase createApiTokenUseCase;

    @Inject
    ListApiTokensUseCase listApiTokensUseCase;

    @Inject
    RevokeApiTokenUseCase revokeApiTokenUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @POST
    @Operation(
        summary = "Create API token",
        description = "Generates a new API token. The raw token is returned only in this response and cannot be retrieved again."
    )
    @APIResponse(
        responseCode = "201",
        description = "API token created successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ApiTokenCreatedResponse.class)
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
    public Response createApiToken(
            @RequestBody(description = "API token creation request", required = true)
            @Valid CreateApiTokenRequest request,
            @Context UriInfo uriInfo) {

        UserId userId = currentUser.getCurrentUserId();

        Instant expiresAt = null;
        if (request.expiresInDays() != null && request.expiresInDays() > 0) {
            expiresAt = Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS);
        }

        CreateApiTokenCommand command = new CreateApiTokenCommand(userId, request.name(), expiresAt);
        ApiTokenCreateResult result = createApiTokenUseCase.execute(command);

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(result.tokenId().value().toString())
                .build();

        return Response.created(location)
                .entity(ApiTokenCreatedResponse.fromResult(result))
                .build();
    }

    @GET
    @Operation(
        summary = "List API tokens",
        description = "Retrieves all API tokens for the current user. Does not include raw token values."
    )
    @APIResponse(
        responseCode = "200",
        description = "List of API tokens",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                implementation = ApiTokenResponse.class,
                type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY
            )
        )
    )
    public Response listApiTokens() {
        UserId userId = currentUser.getCurrentUserId();
        List<ApiToken> tokens = listApiTokensUseCase.execute(userId);
        List<ApiTokenResponse> response = tokens.stream()
                .map(ApiTokenResponse::fromDomain)
                .toList();
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(
        summary = "Revoke API token",
        description = "Revokes an API token, making it permanently unusable."
    )
    @APIResponse(
        responseCode = "204",
        description = "API token revoked successfully"
    )
    @APIResponse(
        responseCode = "404",
        description = "API token not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Response revokeApiToken(
            @Parameter(description = "API token unique identifier", required = true)
            @PathParam("id") String id) {

        UserId userId = currentUser.getCurrentUserId();

        try {
            ApiTokenId tokenId = ApiTokenId.fromString(id);
            RevokeApiTokenCommand command = new RevokeApiTokenCommand(tokenId, userId);
            revokeApiTokenUseCase.execute(command);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("API token not found: " + id);
        } catch (SecurityException e) {
            throw new NotFoundException("API token not found: " + id);
        }
    }
}
