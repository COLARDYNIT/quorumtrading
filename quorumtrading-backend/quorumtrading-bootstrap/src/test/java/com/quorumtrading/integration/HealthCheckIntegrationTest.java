package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class HealthCheckIntegrationTest {

    @Test
    void shouldHaveHealthEndpoint() {
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldHaveReadinessProbe() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldHaveLivenessProbe() {
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldIncludeThreadPoolHealthCheck() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("checks.name", hasItem("ThreadPool health check"));
    }
}
