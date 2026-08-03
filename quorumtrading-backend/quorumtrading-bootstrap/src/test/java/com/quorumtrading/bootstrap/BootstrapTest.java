package com.quorumtrading.bootstrap;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * Basic integration tests for the bootstrap application.
 * Tests that the application starts and core endpoints are available.
 */
@QuarkusTest
class BootstrapTest {

    @Test
    void shouldStartApplication() {
        // Verify health endpoint is available
        RestAssured.given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldHaveOpenAPISpec() {
        // Verify OpenAPI spec is generated (returns YAML by default)
        RestAssured.given()
            .when().get("/q/openapi")
            .then()
            .statusCode(200)
            .contentType("application/yaml");
    }

    @Test
    void shouldHaveLivenessProbe() {
        // Verify liveness endpoint
        RestAssured.given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldHaveHealthEndpoint() {
        // Verify main health endpoint
        RestAssured.given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
}
