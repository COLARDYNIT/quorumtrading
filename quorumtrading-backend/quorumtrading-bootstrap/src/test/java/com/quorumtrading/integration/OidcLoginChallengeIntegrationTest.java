package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Guards the single request that starts every interactive login.
 *
 * <p>{@code /api/v1/auth/callback/google} is {@code @Authenticated} on purpose. Hitting it with
 * no session is not an error — it is how the OIDC code flow begins: the security layer raises
 * {@link io.quarkus.security.UnauthorizedException}, and Quarkus turns that into the tenant's
 * <em>challenge</em>, a 302 to the provider carrying the client id and the state cookie. The user
 * authenticates there and comes back to the same path with a code, which is when the resource
 * method finally runs.</p>
 *
 * <p>That makes the exception ambiguous, and the ambiguity is what broke production. A JAX-RS
 * {@code ExceptionMapper<UnauthorizedException>} added to give REST clients an RFC 9457 body
 * replaced Quarkus' built-in mapper for the same type — the one that issues the challenge. Every
 * anonymous request to the callback got a tidy {@code 401 application/problem+json} instead of a
 * redirect, so nobody could reach Google at all and no one could log in. Nothing caught it: every
 * other test authenticates with a dev-session cookie, and the OIDC tenants are disabled in every
 * profile but prod, so the challenge path was never exercised outside production.</p>
 *
 * <p>So this test enables a Google tenant with discovery off and fake endpoints — no network, no
 * secrets — and asserts the two halves that must both hold. A redirect on the callback path, and
 * a problem+json 401 everywhere else. Weakening authentication to fix the first (dropping
 * {@code @Authenticated}, enabling the default tenant, permitting the path) breaks the second.</p>
 */
@QuarkusTest
@TestProfile(OidcLoginChallengeIntegrationTest.GoogleTenantProfile.class)
class OidcLoginChallengeIntegrationTest {

    private static final String AUTH_SERVER = "https://accounts.example.test";
    private static final String AUTHORIZATION_PATH = "/o/oauth2/v2/auth";
    private static final String CLIENT_ID = "test-client-id";

    /**
     * Mirrors the production Google tenant closely enough to exercise tenant resolution and the
     * challenge, without contacting anything. Discovery is off so the fake authorization endpoint
     * is used verbatim, and JWKS resolution is deferred so startup makes no HTTP call — the flow
     * never gets far enough to verify a token.
     *
     * <p>Everything else the tenant needs — {@code application-type=web-app}, the redirect path
     * and, critically, {@code tenant-paths} — is left to come from the real
     * {@code application.properties}, because those are the settings under test.</p>
     */
    public static class GoogleTenantProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.oidc.google.tenant-enabled", "true",
                "quarkus.oidc.google.discovery-enabled", "false",
                "quarkus.oidc.google.auth-server-url", AUTH_SERVER,
                "quarkus.oidc.google.authorization-path", AUTHORIZATION_PATH,
                "quarkus.oidc.google.token-path", "/token",
                "quarkus.oidc.google.jwks-path", "/jwks",
                "quarkus.oidc.google.jwks.resolve-early", "false",
                "quarkus.oidc.google.client-id", CLIENT_ID,
                "quarkus.oidc.google.credentials.secret", "test-client-secret-0123456789"
            );
        }
    }

    @Test
    @DisplayName("anonymous callback request starts the code flow rather than refusing it")
    void anonymousCallbackRedirectsToProvider() {
        Response response = given()
            .redirects().follow(false)
            .when()
            .get("/api/v1/auth/callback/google")
            .then()
            .statusCode(302)
            .header("location", startsWith(AUTH_SERVER + AUTHORIZATION_PATH))
            .header("location", containsString("client_id=" + CLIENT_ID))
            .extract().response();

        // A redirect alone would still be a dead end. The state cookie is what the callback is
        // verified against when the provider sends the user back, so its presence is the
        // difference between a flow that can complete and one that only looks like it starts.
        assertThat(response.getCookies().keySet())
            .as("OIDC state cookie must accompany the redirect")
            .anyMatch(name -> name.startsWith("q_auth"));
    }

    @Test
    @DisplayName("a protected endpoint outside the tenant paths still refuses with problem+json")
    void protectedEndpointStillReturnsProblemDetails401() {
        given()
            .when()
            .get("/api/v1/auth/me")
            .then()
            .statusCode(401)
            .header("Content-Type", containsString("application/problem+json"))
            .body("title", equalTo("Unauthorized"));
    }
}
