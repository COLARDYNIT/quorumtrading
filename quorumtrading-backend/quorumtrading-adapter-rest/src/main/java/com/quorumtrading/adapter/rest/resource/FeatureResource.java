package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.FeatureAvailabilityResponse;
import com.quorumtrading.application.usecase.EvaluateFeatureUseCase;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the caller is allowed to see, so the frontend can hide navigation it would only 403 on.
 *
 * <p>Anonymous-friendly: a logged-out visitor gets the flags as they apply to nobody in
 * particular, which is what the public pages need. Hiding navigation is a courtesy, not a control
 * — the REST gate and the MCP guards are what actually enforce a disabled feature, and direct
 * navigation to a hidden page is expected to work up to the point where the API refuses.</p>
 */
@Path("/api/v1/features")
@ApplicationScoped
@Tag(name = "Features", description = "Which flagged features are available to the caller")
@PermitAll
public class FeatureResource {

    @Inject
    EvaluateFeatureUseCase evaluateFeature;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get feature availability",
            description = "Flag name to whether that feature is switched on for the current caller.")
    @APIResponse(responseCode = "200", description = "Feature availability",
            content = @Content(schema = @Schema(implementation = FeatureAvailabilityResponse.class)))
    public FeatureAvailabilityResponse get() {
        Map<Feature, Boolean> evaluated = evaluateFeature.evaluateAll(callerContext());

        Map<String, Boolean> byFlagName = new LinkedHashMap<>();
        for (Map.Entry<Feature, Boolean> entry : evaluated.entrySet()) {
            byFlagName.put(entry.getKey().flagName(), entry.getValue());
        }
        return new FeatureAvailabilityResponse(byFlagName);
    }

    private FeatureFlagContext callerContext() {
        if (securityIdentity == null || securityIdentity.isAnonymous()
            || securityIdentity.getPrincipal() == null) {
            return FeatureFlagContext.anonymous();
        }
        return FeatureFlagContext.forEmail(securityIdentity.getPrincipal().getName());
    }
}
