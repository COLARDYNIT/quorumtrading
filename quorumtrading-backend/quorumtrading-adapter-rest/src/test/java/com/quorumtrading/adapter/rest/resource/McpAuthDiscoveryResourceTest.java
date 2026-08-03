package com.quorumtrading.adapter.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Regression guard: these endpoints exist solely so that MCP-client OAuth
 * probes get OAuth-shaped JSON instead of the global RFC 7807 ProblemDetails
 * (which crashes the SDK's Zod parser at path {@code ["error"]}).
 *
 * <p>If any of these tests fail, the SDK will revert to the cryptic
 * {@code "SDK auth failed: HTTP 404: Invalid OAuth error response: ZodError"}
 * users hit before.
 */
@QuarkusTest
class McpAuthDiscoveryResourceTest {

    @Test
    @DisplayName("POST /register returns OAuth-shaped 404 with error+error_description")
    void register_returns_oauth_shaped_error() {
        given()
            .contentType("application/json")
            .body("{\"redirect_uris\":[\"http://localhost/cb\"]}")
        .when()
            .post("/register")
        .then()
            .statusCode(404)
            .contentType("application/json")
            .body("error", equalTo("registration_not_supported"))
            .body("error_description", notNullValue())
            .body("error_description", containsString("/settings/api-tokens"));
    }

    @Test
    @DisplayName("GET /.well-known/oauth-protected-resource returns OAuth-shaped 404")
    void oauth_protected_resource_returns_oauth_shaped_error() {
        given()
        .when()
            .get("/.well-known/oauth-protected-resource")
        .then()
            .statusCode(404)
            .contentType("application/json")
            .body("error", equalTo("no_authorization_server"))
            .body("error_description", containsString("API tokens"));
    }

    @Test
    @DisplayName("GET /.well-known/oauth-authorization-server returns OAuth-shaped 404")
    void oauth_authorization_server_returns_oauth_shaped_error() {
        given()
        .when()
            .get("/.well-known/oauth-authorization-server")
        .then()
            .statusCode(404)
            .contentType("application/json")
            .body("error", equalTo("no_authorization_server"))
            .body("error_description", containsString("authorization server"));
    }

    @Test
    @DisplayName("Default 404 still returns RFC 9457 ProblemDetails (we only override the three OAuth paths)")
    void unrelated_path_still_returns_rfc9457() {
        given()
        .when()
            .get("/some-random-path-that-does-not-exist")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", containsString("/problems/resource-not-found"));
    }
}
