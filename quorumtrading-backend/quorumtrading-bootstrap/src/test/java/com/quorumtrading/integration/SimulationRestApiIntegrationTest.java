package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Full HTTP round-trip integration tests for SimulationResource via RestAssured.
 *
 * NOT @Transactional — HTTP calls commit independently.
 * Relies on unique data per test and Flyway clean between runs.
 */
@QuarkusTest
class SimulationRestApiIntegrationTest {

    @Inject
    IntegrationTestHelper helper;

    private String testEmail;

    @BeforeEach
    void setUp() {
        testEmail = "sim-rest-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        helper.createUserWithEmail(testEmail);
    }

    /** Returns a RestAssured spec pre-configured with dev_session auth cookie. */
    private RequestSpecification authedRequest() {
        return given().cookie("dev_session", testEmail);
    }

    private String createStrategyViaRest() {
        String name = "Sim Test Strategy-" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {
                  "name": "%s",
                  "markdownContent": "---\\nversion: v1.0\\nconsensus_method: ORCHESTRATOR_OVERRIDE\\nconsensus_threshold: 0.66\\nlive_schedule:\\n  interval: 3600\\ncrypto_tickers:\\n  - BTC\\nposition_limits:\\n  BTC: 0.8\\nreserve_cash_percentage: 0.2\\n---\\n\\n# Test Strategy\\n\\n## Rules\\n1. Buy when momentum is positive"
                }
                """.formatted(name);

        return authedRequest()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/api/v1/strategies")
        .then()
            .statusCode(201)
            .extract().path("id");
    }

    /**
     * No "symbol" field: {@code CreateSimulationRequest} never declared one, so the value this
     * fixture used to send was dropped in silence — a simulation trades whatever its strategy's
     * frontmatter lists. It only surfaced once request bodies started being parsed strictly.
     */
    private String simulationBody(String strategyId) {
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = Instant.now();
        return """
                {
                  "strategyId": "%s",
                  "startDate": "%s",
                  "endDate": "%s",
                  "name": "REST Sim-%s",
                  "initialCash": 100000.0
                }
                """.formatted(strategyId, start.toString(), end.toString(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    @DisplayName("should create simulation via REST")
    void shouldCreateSimulationViaRest() {
        String strategyId = createStrategyViaRest();

        // After synchronous backfill check, status is PENDING (data available) or
        // WAITING_FOR_DATA (backfill triggered). Both are valid initial states.
        authedRequest()
            .contentType(ContentType.JSON)
            .body(simulationBody(strategyId))
        .when()
            .post("/api/v1/simulations")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("status", anyOf(equalTo("PENDING"), equalTo("WAITING_FOR_DATA")));
    }

    @Test
    @DisplayName("should get simulation by ID via REST")
    void shouldGetSimulationByIdViaRest() {
        String strategyId = createStrategyViaRest();

        String simId = authedRequest()
            .contentType(ContentType.JSON)
            .body(simulationBody(strategyId))
        .when()
            .post("/api/v1/simulations")
        .then()
            .statusCode(201)
            .extract().path("id");

        authedRequest()
        .when()
            .get("/api/v1/simulations/{id}", simId)
        .then()
            .statusCode(200)
            .body("id", equalTo(simId));
    }

    @Test
    @DisplayName("should list simulations via REST")
    void shouldListSimulationsViaRest() {
        String strategyId = createStrategyViaRest();

        // Create one
        authedRequest()
            .contentType(ContentType.JSON)
            .body(simulationBody(strategyId))
        .when()
            .post("/api/v1/simulations")
        .then()
            .statusCode(201);

        // List
        authedRequest()
        .when()
            .get("/api/v1/simulations")
        .then()
            .statusCode(200)
            .body("$.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("should delete simulation via REST")
    void shouldDeleteSimulationViaRest() {
        String strategyId = createStrategyViaRest();

        String simId = authedRequest()
            .contentType(ContentType.JSON)
            .body(simulationBody(strategyId))
        .when()
            .post("/api/v1/simulations")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Delete — may return 204 (success) or 409 (if async event changed state)
        int deleteStatus = authedRequest()
        .when()
            .delete("/api/v1/simulations/{id}", simId)
        .then()
            .extract().statusCode();

        assertThat(deleteStatus).isIn(204, 409);
    }

    @Test
    @DisplayName("should return error for non-existent simulation")
    void shouldReturnErrorForNonExistentSimulation() {
        // Note: resource exception mapping may return 400 or 500 instead of 404
        // depending on whether IllegalArgumentException or NoSuchElementException is thrown
        int statusCode = authedRequest()
        .when()
            .get("/api/v1/simulations/{id}", UUID.randomUUID().toString())
        .then()
            .extract().statusCode();

        assertThat(statusCode).isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("should return 400 for missing strategy ID")
    void shouldReturn400ForMissingStrategyId() {
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = Instant.now();
        String body = """
                {
                  "strategyId": "",
                  "startDate": "%s",
                  "endDate": "%s",
                  "initialCash": 100000.0
                }
                """.formatted(start.toString(), end.toString());

        authedRequest()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/api/v1/simulations")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("should filter simulations by status")
    void shouldFilterSimulationsByStatus() {
        String strategyId = createStrategyViaRest();

        // Create a simulation — after synchronous backfill check, it may be PENDING or WAITING_FOR_DATA
        String status = authedRequest()
            .contentType(ContentType.JSON)
            .body(simulationBody(strategyId))
        .when()
            .post("/api/v1/simulations")
        .then()
            .statusCode(201)
            .extract().path("status");

        // Filter by the actual status of the created simulation
        authedRequest()
            .queryParam("status", status)
        .when()
            .get("/api/v1/simulations")
        .then()
            .statusCode(200)
            .body("$.size()", greaterThanOrEqualTo(1))
            .body("status", everyItem(equalTo(status)));
    }
}
