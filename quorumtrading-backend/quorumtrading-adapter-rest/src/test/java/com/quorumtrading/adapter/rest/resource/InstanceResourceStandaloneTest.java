package com.quorumtrading.adapter.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * Standalone (self-host) shape: edition reads {@code standalone} and the EU broker joins the
 * offer once its flag is on — this is what lets one frontend build show the EU/US broker choice
 * only where the deployment actually supports it.
 */
@QuarkusTest
@TestProfile(InstanceResourceStandaloneTest.StandaloneProfile.class)
class InstanceResourceStandaloneTest {

    public static class StandaloneProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quorumtrading.edition", "standalone",
                "quorumtrading.broker.saxo.enabled", "true"
            );
        }
    }

    @Test
    @DisplayName("should describe a standalone instance offering both broker regions")
    void shouldDescribeStandaloneInstanceWithBothBrokers() {
        given()
            .when()
            .get("/api/v1/instance")
            .then()
            .statusCode(200)
            .body("edition", equalTo("standalone"))
            .body("brokers", contains("ALPACA", "SAXO"));
    }
}
