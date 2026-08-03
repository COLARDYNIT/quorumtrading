package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for authentication REST endpoints.
 *
 * Verifies public and protected auth endpoints behave correctly
 * without requiring actual OAuth provider integration.
 */
@QuarkusTest
class AuthSessionIntegrationTest {

    @Test
    @DisplayName("should list enabled auth providers")
    void shouldListEnabledAuthProviders() {
        given()
            .when()
            .get("/api/v1/auth/providers")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("providers", notNullValue())
            .body("devLoginEnabled", is(true)); // test profile has dev-login enabled
    }

    @Test
    @DisplayName("should return 401 for unauthenticated /me")
    void shouldReturn401ForUnauthenticatedMe() {
        given()
            .when()
            .get("/api/v1/auth/me")
            .then()
            .statusCode(401);
    }

    @Test
    @DisplayName("should return 401 for unauthenticated /logout")
    void shouldReturn401ForUnauthenticatedLogout() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/auth/logout")
            .then()
            .statusCode(401);
    }

    @Test
    @DisplayName("should return current user via /me with dev session cookie")
    void shouldReturnCurrentUserWithDevSession() {
        // First do dev-login to create the test user
        given()
            .redirects().follow(false)
            .when()
            .get("/api/v1/auth/dev-login")
            .then()
            .statusCode(302);

        // Then call /me with the dev session cookie (unified endpoint)
        given()
            .cookie("dev_session", "local@quorumtrading.local")
            .when()
            .get("/api/v1/auth/me")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("email", equalTo("local@quorumtrading.local"));
    }

    @Test
    @DisplayName("should reject protected endpoints without authentication")
    void shouldRejectProtectedEndpointsWithoutAuth() {
        // Simulations endpoint requires authentication
        given()
            .when()
            .get("/api/v1/auth/me")
            .then()
            .statusCode(401);

        // Logout requires authentication
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/auth/logout")
            .then()
            .statusCode(401);
    }
}
