package com.quorumtrading.adapter.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * With {@code quorumtrading.broker.saxo.enabled=false} (the default — and every SaaS deployment
 * until the broker approves hosted integration) the Saxo onboarding endpoints must 404, even for
 * authenticated users: no consent URLs, no onboarding-state rows. Same disappear-when-disabled
 * design as the LLM-selection endpoints.
 */
@QuarkusTest
class SaxoOnboardingDisabledTest {

    @Test
    @TestSecurity(user = "prod-user@quorumtrading.com")
    @DisplayName("should 404 the onboarding start when Saxo is disabled, even authenticated")
    void startShouldReturn404WhenSaxoDisabled() {
        given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/v1/broker-accounts/saxo/onboard/start")
            .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("should 404 the callback when Saxo is disabled")
    void callbackShouldReturn404WhenSaxoDisabled() {
        given()
            .when()
            .get("/api/v1/broker-accounts/saxo/onboard/callback?code=x&state=y")
            .then()
            .statusCode(404);
    }
}
