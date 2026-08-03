package com.quorumtrading.adapter.rest.exception;

import io.quarkus.security.UnauthorizedException;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticator;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Renders the security layer's 401 as RFC 9457 Problem Details — without swallowing the
 * authentication challenge.
 *
 * <p>{@link UnauthorizedException} carries two different meanings. On a plain API path it means
 * "you are not authenticated and nothing here can fix that", and the right answer is a 401 with an
 * {@code application/problem+json} body, because this API promises every error looks like that.
 * On an OIDC {@code web-app} tenant path it means "start the login": Quarkus asks the tenant's
 * mechanism for a <em>challenge</em>, which is a 302 to the provider's authorization endpoint
 * plus the state cookie the eventual callback is verified against.</p>
 *
 * <p>The distinction is not ours to make — {@link HttpAuthenticator} already makes it, and this
 * mapper defers to it. An earlier version did not: it implemented
 * {@code ExceptionMapper<UnauthorizedException>} and returned the 401 body unconditionally, which
 * replaced Quarkus' built-in mapper for that exact type — the one that issues the challenge. The
 * effect in production was total: {@code /api/v1/auth/callback/google} is {@code @Authenticated}
 * precisely so that an anonymous hit redirects to Google, so with the redirect suppressed nobody
 * could start a login at all. Only the body shape was ever meant to change.</p>
 *
 * <p>So: ask for the challenge, return it as-is when it is a redirect, and attach the problem
 * body only when the challenge is itself a bare 401 (or there is no challenge to be had). Headers
 * the mechanism set — {@code WWW-Authenticate} and friends — are preserved either way. This
 * mirrors Quarkus' own {@code SecurityExceptionMapperUtil}, differing only in the body.</p>
 */
public class UnauthorizedExceptionMapper {

    /**
     * Priority 1 keeps the precedence the previous {@code @Priority(1)} provider had, so this
     * still wins over Quarkus' built-in mapper for {@link UnauthorizedException} — it has to, or
     * the problem+json contract is lost.
     */
    @ServerExceptionMapper(value = UnauthorizedException.class, priority = 1)
    public Uni<Response> toResponse(RoutingContext context) {
        HttpAuthenticator authenticator = context.get(HttpAuthenticator.class.getName());
        if (authenticator == null) {
            return Uni.createFrom().item(problemDetails(context));
        }
        return authenticator.getChallenge(context)
            .map(challenge -> challengeResponse(challenge, context))
            .onFailure().recoverWithItem(() -> problemDetails(context));
    }

    private Response challengeResponse(ChallengeData challenge, RoutingContext context) {
        if (challenge == null) {
            return problemDetails(context);
        }
        Response.ResponseBuilder builder = Response.status(challenge.status);
        challenge.getHeaders().forEach((name, value) -> builder.header(name.toString(), value));
        if (challenge.status == Response.Status.UNAUTHORIZED.getStatusCode()) {
            builder.entity(problem(context)).type(ProblemDetails.MEDIA_TYPE);
        }
        return builder.build();
    }

    private Response problemDetails(RoutingContext context) {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(problem(context))
            .type(ProblemDetails.MEDIA_TYPE)
            .build();
    }

    private ProblemDetails problem(RoutingContext context) {
        String path = context.normalizedPath();
        return ProblemDetails.of(
            "unauthorized",
            "Unauthorized",
            Response.Status.UNAUTHORIZED.getStatusCode(),
            "Authentication required.",
            path != null ? path : "/unknown"
        );
    }
}
