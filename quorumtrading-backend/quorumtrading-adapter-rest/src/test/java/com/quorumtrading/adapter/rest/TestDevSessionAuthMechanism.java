package com.quorumtrading.adapter.rest;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Test-scoped copy of DevSessionAuthMechanism for adapter-rest isolated tests.
 *
 * <p>The real DevSessionAuthMechanism lives in the bootstrap module, which is not
 * available during adapter-rest unit tests. This test bean provides the same
 * functionality so that {@code @Authenticated} works with dev_session cookies in tests.</p>
 */
@ApplicationScoped
public class TestDevSessionAuthMechanism implements HttpAuthenticationMechanism {

    private static final String DEV_SESSION_COOKIE = "dev_session";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        Cookie cookie = context.request().getCookie(DEV_SESSION_COOKIE);
        if (cookie == null) {
            return Uni.createFrom().nullItem();
        }

        String email = cookie.getValue();
        if (email == null || email.isBlank()) {
            return Uni.createFrom().nullItem();
        }

        if (email.startsWith("\"") && email.endsWith("\"")) {
            email = email.substring(1, email.length() - 1);
        }

        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(email))
                .addRole("user")
                .build();

        return Uni.createFrom().item(identity);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }
}
