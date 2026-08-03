package com.quorumtrading.adapter.rest.filter;

import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.application.usecase.EvaluateFeatureUseCase;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Refuses REST calls into product areas whose feature flag is off for the caller.
 *
 * <p>Gating by path prefix rather than per-endpoint annotation means an endpoint added to
 * {@code ChallengeResource} tomorrow is covered without anyone remembering to annotate it.</p>
 *
 * <h2>Why this is not {@code @PreMatching}</h2>
 * <p>Flags can be targeted at individual people, so the gate has to know who is calling, and in a
 * pre-matching filter the {@link SecurityIdentity} has not been established yet — every request
 * would evaluate as anonymous and email targeting would silently never work. Running at
 * {@link Priorities#AUTHORIZATION} puts this after authentication and before the resource method.
 * The cost is that a malformed request can be answered 415 before reaching here, which is
 * acceptable: a disabled flag is not a secret, and the refusal below deliberately names it.</p>
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class FeatureFlagGateFilter implements ContainerRequestFilter {

    /** Path prefixes, without the leading slash, mapped to the feature that governs them. */
    private static final Map<String, Feature> GATED_PREFIXES = new LinkedHashMap<>();

    static {
        GATED_PREFIXES.put("api/v1/challenges", Feature.CHALLENGES);
        GATED_PREFIXES.put("api/v1/market-research", Feature.MARKET_RESEARCH);
    }

    @Inject
    EvaluateFeatureUseCase evaluateFeature;

    @Inject
    SecurityIdentity securityIdentity;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String normalised = path.startsWith("/") ? path.substring(1) : path;

        Feature gated = null;
        for (Map.Entry<String, Feature> entry : GATED_PREFIXES.entrySet()) {
            String prefix = entry.getKey();
            if (normalised.equals(prefix) || normalised.startsWith(prefix + "/")) {
                gated = entry.getValue();
                break;
            }
        }
        if (gated == null || evaluateFeature.isEnabled(gated, callerContext())) {
            return;
        }

        ProblemDetails problem = new ProblemDetails(
            ProblemDetails.TYPE_BASE + "feature-disabled",
            "Feature not available",
            Response.Status.FORBIDDEN.getStatusCode(),
            String.format(
                "The '%s' feature is switched off on this instance. An administrator can switch on"
                    + " the '%s' feature flag under Admin -> Feature flags, or with"
                    + " PUT /api/v1/admin/feature-flags/%s.",
                gated.description(), gated.flagName(), gated.flagName()),
            "/" + path);

        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
            .type(ProblemDetails.MEDIA_TYPE)
            .entity(problem)
            .build());
    }

    /**
     * The caller's email off the security identity — no database lookup, because this runs on every
     * request into a gated area. Anonymous callers get an anonymous context, so a flag targeted at
     * named people is off for them.
     */
    private FeatureFlagContext callerContext() {
        if (securityIdentity == null || securityIdentity.isAnonymous()
            || securityIdentity.getPrincipal() == null) {
            return FeatureFlagContext.anonymous();
        }
        return FeatureFlagContext.forEmail(securityIdentity.getPrincipal().getName());
    }
}
