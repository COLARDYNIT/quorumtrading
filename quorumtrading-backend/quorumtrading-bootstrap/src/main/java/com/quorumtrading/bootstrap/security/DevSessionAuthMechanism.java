package com.quorumtrading.bootstrap.security;

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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Set;

/**
 * Custom authentication mechanism that creates a SecurityIdentity from the dev_session cookie.
 *
 * <p>This allows {@code @Authenticated} to work in dev/test/local profiles where OIDC is disabled.
 * In production, dev-login is disabled and this mechanism returns null for every request,
 * letting the OIDC mechanism handle authentication.</p>
 */
@ApplicationScoped
public class DevSessionAuthMechanism implements HttpAuthenticationMechanism {

    private static final Logger LOG = Logger.getLogger(DevSessionAuthMechanism.class);
    private static final String DEV_SESSION_COOKIE = "dev_session";

    @ConfigProperty(name = "quorumtrading.auth.dev-login-enabled", defaultValue = "false")
    boolean devLoginEnabled;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (!devLoginEnabled) {
            return Uni.createFrom().nullItem();
        }

        Cookie cookie = context.request().getCookie(DEV_SESSION_COOKIE);
        if (cookie == null) {
            return Uni.createFrom().nullItem();
        }

        String email = cookie.getValue();
        if (email == null || email.isBlank()) {
            return Uni.createFrom().nullItem();
        }

        // Strip quotes if present (JAX-RS may quote values with special chars like @)
        if (email.startsWith("\"") && email.endsWith("\"")) {
            email = email.substring(1, email.length() - 1);
        }

        LOG.debugf("Authenticated via dev_session cookie: %s", email);

        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(email))
                .addRole("user")
                .build();

        return Uni.createFrom().item(identity);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        // No challenge — unauthenticated requests get a 401 from Quarkus automatically
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }
}
