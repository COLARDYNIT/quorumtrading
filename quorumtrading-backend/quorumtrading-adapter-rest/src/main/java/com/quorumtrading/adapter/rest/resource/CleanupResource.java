package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.PruneUnusedUseCase;
import com.quorumtrading.application.usecase.command.PruneUnusedCommand;
import com.quorumtrading.application.usecase.result.PruneUnusedResult;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * REST resource for cleanup operations.
 * Provides endpoints for pruning unused strategies and research.
 */
@Path("/api/v1/cleanup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Cleanup", description = "Cleanup operations for unused resources")
public class CleanupResource {

    @Inject
    PruneUnusedUseCase pruneUnusedUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @DELETE
    @Path("/unused")
    @Operation(summary = "Prune unused strategies and research",
        description = "Deletes strategies with 0 simulations and research with 0 created strategies.")
    @APIResponse(responseCode = "200", description = "Pruning completed successfully")
    @APIResponse(responseCode = "401", description = "Authentication required")
    public Map<String, Integer> pruneUnused(
            @Parameter(description = "Remove strategies with no simulations")
            @QueryParam("strategies") @DefaultValue("true") boolean strategies,
            @Parameter(description = "Remove research with no created strategies")
            @QueryParam("research") @DefaultValue("true") boolean research) {

        UserId userId = currentUser.getCurrentUserId();

        PruneUnusedResult result = pruneUnusedUseCase.execute(
            new PruneUnusedCommand(userId, strategies, research));

        return Map.of(
            "deletedStrategies", result.deletedStrategies(),
            "deletedResearch", result.deletedResearch()
        );
    }

}
