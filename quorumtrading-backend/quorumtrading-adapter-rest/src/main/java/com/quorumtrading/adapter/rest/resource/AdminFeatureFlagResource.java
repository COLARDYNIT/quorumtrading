package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.UpdateFeatureFlagRequest;
import com.quorumtrading.adapter.rest.dto.response.FeatureFlagResponse;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.ListFeatureFlagsUseCase;
import com.quorumtrading.application.usecase.UpdateFeatureFlagUseCase;
import com.quorumtrading.application.usecase.command.UpdateFeatureFlagCommand;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Administration of feature flags.
 *
 * <p>{@link Authenticated} is only the outer door — being an administrator is checked inside
 * {@link ListFeatureFlagsUseCase} and {@link UpdateFeatureFlagUseCase}, so any future driving
 * adapter inherits the same rule instead of re-implementing it. On a standalone self-hosted
 * instance the check treats the authenticated operator as the administrator, which is what lets a
 * self-hoster switch a feature on without hand-editing their own row in the database.</p>
 */
@Path("/api/v1/admin/feature-flags")
@ApplicationScoped
@Tag(name = "Admin", description = "Administrative operations")
@Authenticated
public class AdminFeatureFlagResource {

    @Inject
    ListFeatureFlagsUseCase listFeatureFlags;

    @Inject
    UpdateFeatureFlagUseCase updateFeatureFlag;

    @Inject
    CurrentUserProvider currentUserProvider;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List feature flags",
            description = "Every registered feature with its current state. Administrators only.")
    @APIResponse(responseCode = "200", description = "Feature flags",
            content = @Content(schema = @Schema(implementation = FeatureFlagResponse.class)))
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "403", description = "Not an administrator")
    public List<FeatureFlagResponse> list() {
        UserId requestingUserId = currentUserProvider.getCurrentUserId();
        return listFeatureFlags.execute(requestingUserId).stream()
            .map(AdminFeatureFlagResource::toResponse)
            .toList();
    }

    @PUT
    @Path("/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change a feature flag",
            description = "Switches a feature on or off, optionally targeting named users. Administrators only.")
    @APIResponse(responseCode = "200", description = "Updated flag",
            content = @Content(schema = @Schema(implementation = FeatureFlagResponse.class)))
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "403", description = "Not an administrator")
    @APIResponse(responseCode = "404", description = "No such feature flag")
    public FeatureFlagResponse update(@PathParam("name") String name, UpdateFeatureFlagRequest request) {
        Feature feature = Feature.fromFlagName(name)
            .orElseThrow(() -> new NotFoundException("No such feature flag: " + name));

        UserId requestingUserId = currentUserProvider.getCurrentUserId();
        FeatureFlag updated = updateFeatureFlag.execute(new UpdateFeatureFlagCommand(
            feature,
            request != null && request.isEnabled(),
            toStrategies(request),
            requestingUserId));
        return toResponse(updated);
    }

    /**
     * {@code enabledForEmails} is a convenience over {@code strategies}: targeting a handful of
     * people is the common case and spelling out Unleash's {@code userWithId} shape for it in the
     * UI would be noise. An explicit {@code strategies} list wins when both are sent.
     */
    private static List<FeatureFlagStrategy> toStrategies(UpdateFeatureFlagRequest request) {
        if (request == null) {
            return List.of();
        }
        if (request.strategies() != null && !request.strategies().isEmpty()) {
            List<FeatureFlagStrategy> strategies = new ArrayList<>();
            for (UpdateFeatureFlagRequest.StrategyPayload payload : request.strategies()) {
                strategies.add(new FeatureFlagStrategy(payload.name(), payload.parameters()));
            }
            return strategies;
        }
        if (request.enabledForEmails() != null && !request.enabledForEmails().isEmpty()) {
            return List.of(FeatureFlagStrategy.forEmails(request.enabledForEmails()));
        }
        return List.of();
    }

    private static FeatureFlagResponse toResponse(FeatureFlag flag) {
        List<FeatureFlagResponse.FeatureFlagStrategyDto> strategies = flag.getStrategies().stream()
            .map(strategy -> new FeatureFlagResponse.FeatureFlagStrategyDto(
                strategy.name(), strategy.parameters()))
            .toList();
        return new FeatureFlagResponse(
            flag.getFeature().flagName(),
            flag.getFeature().description(),
            flag.isEnabled(),
            strategies,
            flag.getUpdatedAt(),
            flag.getUpdatedBy() == null ? null : flag.getUpdatedBy().value().toString());
    }
}
