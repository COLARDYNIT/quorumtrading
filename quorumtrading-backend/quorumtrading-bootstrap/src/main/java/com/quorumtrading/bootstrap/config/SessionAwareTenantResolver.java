package com.quorumtrading.bootstrap.config;

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Resolves the OIDC tenant from the session cookie for all API paths.
 *
 * <p>By default, Quarkus OIDC only processes sessions for paths matching
 * {@code tenant-paths} (login/callback URLs). Endpoints without {@code @Authenticated}
 * never have their {@code @IdToken} populated, causing 401 errors even for
 * logged-in users whose session cookie is present.</p>
 *
 * <p>This resolver detects Quarkus OIDC session cookies ({@code q_session_<tenant>})
 * and returns the tenant ID, allowing Quarkus to validate the session and populate
 * the {@code @IdToken} for all paths — not just auth endpoints.</p>
 *
 * <p>Evaluation order: {@code tenant-paths} is checked first. This resolver
 * only runs for paths that don't match any {@code tenant-paths}.</p>
 */
@ApplicationScoped
public class SessionAwareTenantResolver implements TenantResolver {

    private static final String SESSION_COOKIE_PREFIX = "q_session_";

    @Override
    public String resolve(RoutingContext context) {
        // Look for Quarkus OIDC session cookies (named q_session_<tenant-id>)
        for (String cookieName : context.request().cookieMap().keySet()) {
            if (cookieName.startsWith(SESSION_COOKIE_PREFIX)
                    && cookieName.length() > SESSION_COOKIE_PREFIX.length()) {
                return cookieName.substring(SESSION_COOKIE_PREFIX.length());
            }
        }

        // No session cookie → no OIDC tenant → proceed without OIDC auth
        return null;
    }
}
