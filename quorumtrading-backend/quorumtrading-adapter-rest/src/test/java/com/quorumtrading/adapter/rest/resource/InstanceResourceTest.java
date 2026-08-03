package com.quorumtrading.adapter.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * Default (SaaS-shaped) instance descriptor: edition falls back to {@code saas} and only the US
 * broker is offered while the EU broker flag is off. The endpoint must be reachable anonymously —
 * the frontend probes it before any session exists.
 */
@QuarkusTest
class InstanceResourceTest {

    @Test
    @DisplayName("should describe a saas instance with US broker only by default")
    void shouldDescribeSaasInstanceByDefault() {
        given()
            .when()
            .get("/api/v1/instance")
            .then()
            .statusCode(200)
            .body("edition", equalTo("saas"))
            .body("brokers", contains("ALPACA"));
    }

    /**
     * The DCA scheduler ships disabled in every environment, so on a default install an ACTIVE
     * plan with a {@code nextExecutionAt} date will never fire. That is a deliberate safety
     * default and documented in six places — but no user-facing surface carried it, while five
     * of them asserted the opposite ("The plan runs at 13:30 UTC", "on schedule", "the system
     * will pick a strategy and fund it"). For a set-and-forget product, silently not investing
     * is the one failure that must never be quiet. The frontend needs to be able to ask.
     */
    @Test
    @DisplayName("should report that automatic investing is off by default")
    void shouldReportAutomaticInvestmentsDisabledByDefault() {
        given()
            .when()
            .get("/api/v1/instance")
            .then()
            .statusCode(200)
            .body("automaticInvestmentsEnabled", equalTo(false));
    }

    @Test
    @DisplayName("should not require authentication")
    void shouldNotRequireAuthentication() {
        // No cookie, no bearer token — still 200.
        given()
            .when()
            .get("/api/v1/instance")
            .then()
            .statusCode(200);
    }
}
