package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.InstanceInfoResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Instance descriptor — tells a frontend (or agent) what kind of deployment it is talking to.
 *
 * <p>One frontend build serves every edition: the index page shows the marketing hero on the
 * hosted SaaS and the local home on a self-hosted standalone instance, and broker-region choices
 * (EU/US) only render where the instance actually offers them. The endpoint is anonymous and
 * always on — it reveals deployment shape (edition name, enabled broker integrations), never
 * configuration values or user data.</p>
 */
@Path("/api/v1/instance")
@ApplicationScoped
@Tag(name = "Instance", description = "Deployment descriptor for runtime frontend adaptation")
@PermitAll
public class InstanceResource {

    @ConfigProperty(name = "quorumtrading.edition", defaultValue = "saas")
    String edition;

    @ConfigProperty(name = "quorumtrading.broker.saxo.enabled", defaultValue = "false")
    boolean saxoEnabled;

    /**
     * Whether the DCA scheduler will actually run plans on this instance. Off by default in every
     * environment, so the frontend must be able to ask rather than assume — it used to assume,
     * and told users their plan ran monthly on installs where nothing would ever run it.
     */
    @ConfigProperty(name = "quorumtrading.scheduler.investments.enabled", defaultValue = "false")
    boolean automaticInvestmentsEnabled;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get instance descriptor",
            description = "Edition (saas/standalone) and the broker integrations this instance offers.")
    @APIResponse(responseCode = "200", description = "Instance descriptor",
            content = @Content(schema = @Schema(implementation = InstanceInfoResponse.class)))
    public InstanceInfoResponse get() {
        List<String> brokers = saxoEnabled ? List.of("ALPACA", "SAXO") : List.of("ALPACA");
        return new InstanceInfoResponse(edition, brokers, automaticInvestmentsEnabled);
    }
}
