package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.ReorderQueueCommand;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.application.usecase.query.ListSimulationsQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.quorumtrading.adapter.rest.resource.TestConstants.TEST_USER_ID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SimulationResource.
 * Uses mocked use cases to test REST layer in isolation.
 */
@QuarkusTest
class SimulationResourceTest {

    private static final String TEST_EMAIL = "test@quorumtrading.local";

    @InjectMock
    CreateSimulationUseCase createSimulationUseCase;

    @InjectMock
    ListSimulationsUseCase listSimulationsUseCase;

    @InjectMock
    GetSimulationUseCase getSimulationUseCase;

    @InjectMock
    com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase getSimulationHealthUseCase;

    @InjectMock
    DeleteSimulationUseCase deleteSimulationUseCase;

    @InjectMock
    StartSimulationUseCase startSimulationUseCase;

    @InjectMock
    DeletePausedSimulationsUseCase deletePausedSimulationsUseCase;

    @InjectMock
    ReorderQueueUseCase reorderQueueUseCase;

    @InjectMock
    UserRepository userRepository;

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    @BeforeEach
    void setUp() {
        User testUser = User.reconstitute(
            TEST_USER_ID, TEST_EMAIL, "Test User", null,
            com.quorumtrading.model.enums.UserRole.USER,
            Instant.now(), Instant.now(), List.of(), true, false
        );
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        // CurrentUserProvider.getCurrentUserId() resolves through this use case, not the
        // repository directly — the real implementation throws when the email is unknown.
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(TEST_USER_ID);
    }

    @Test
    void shouldReturnEmptyListWhenNoSimulations() {
        // Given - mock returns empty list
        when(listSimulationsUseCase.execute(any())).thenReturn(Collections.emptyList());

        // When/Then - endpoint returns empty list
        given()
            .cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/simulations")
            .then()
            .statusCode(200)
            .body("$.size()", is(0));
    }

    @Test
    void shouldReturnEmptyListWithStatusFilter() {
        // Given - mock returns empty list
        when(listSimulationsUseCase.execute(any())).thenReturn(Collections.emptyList());

        // When/Then - verify query parameter handling works
        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("status", "PENDING")
            .when().get("/api/v1/simulations")
            .then()
            .statusCode(200)
            .body("$.size()", is(0));
    }

    @Test
    void startNextQueuedSimulation_shouldStartFirstPendingSimulation() {
        // Given: Two PENDING simulations exist
        Simulation pendingSim1 = createMockSimulation(UUID.randomUUID(), "Pending 1", SimulationStatus.PENDING);
        Simulation pendingSim2 = createMockSimulation(UUID.randomUUID(), "Pending 2", SimulationStatus.PENDING);
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(pendingSim1)); // First pending sim

        Simulation startedSim = createMockSimulation(pendingSim1.getSimulationId().value(), "Pending 1", SimulationStatus.SIMULATING);
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class)))
            .thenReturn(startedSim);

        // When: POST /queue/start-next
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/simulations/queue/start-next")
        .then()
            .statusCode(200)
            .body("id", equalTo(pendingSim1.getSimulationId().value().toString()))
            .body("status", equalTo("SIMULATING"));

        // Then: startSimulationUseCase was called with correct ID
        // The resource now forwards the authenticated caller rather than a null user.
        verify(startSimulationUseCase).execute(
            new StartSimulationCommand(pendingSim1.getSimulationId(), TEST_USER_ID));
    }

    @Test
    void simulationStatus_reportsTheLiveHealthReadModel() {
        // GET /{id}/status promised stall detection in its OpenAPI text for months while
        // returning the plain simulation DTO. It now renders the health read model — the
        // same one MCP embeds — so a stalled run explains itself over REST too.
        UUID simId = UUID.randomUUID();
        when(getSimulationHealthUseCase.execute(any(GetSimulationQuery.class))).thenReturn(
            new com.quorumtrading.application.usecase.health.SimulationExecutionHealth(
                "SIMULATING",
                com.quorumtrading.application.usecase.health.SimulationExecutionHealth.Verdict.STALLED,
                "The engine is not producing decisions: the last 4 consensus row(s) are all skips.",
                null,
                Instant.parse("2024-03-05T15:00:00Z"),
                4, 4, 4, 9, 9,
                List.of(new com.quorumtrading.application.usecase.health.SimulationExecutionHealth.SkipReason(
                    "Consensus pipeline for AAPL did not complete within 360s (slow or failed LLM engine)", 4)),
                null, null));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get("/api/v1/simulations/" + simId + "/status")
        .then()
            .statusCode(200)
            .body("verdict", equalTo("STALLED"))
            .body("status", equalTo("SIMULATING"))
            .body("consecutiveSkippedRows", equalTo(4))
            .body("detail", containsString("not producing decisions"))
            .body("topSkipReasons[0].count", equalTo(4))
            .body("topSkipReasons[0].reason", containsString("slow or failed LLM engine"));
    }

    @Test
    void startNextQueuedSimulation_shouldReturn404WhenNoQueuedSimulations() {
        // Given: No PENDING simulations
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of());

        // When: POST /queue/start-next
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/simulations/queue/start-next")
        .then()
            .statusCode(404)
            .body("detail", containsString("No pending simulations"));
    }

    @Test
    void clearPausedSimulations_shouldReturnCount() {
        // Given: Two paused simulations
        when(deletePausedSimulationsUseCase.execute(any())).thenReturn(2);

        // When
        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .delete("/api/v1/simulations/paused")
        .then()
            .statusCode(200)
            .body(equalTo("2"));

        // Then
        verify(deletePausedSimulationsUseCase).execute(any());
    }

    @Test
    void clearPausedSimulations_shouldReturnZeroWhenNoPausedSimulations() {
        // Given: No paused simulations
        when(deletePausedSimulationsUseCase.execute(any())).thenReturn(0);

        // When
        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .delete("/api/v1/simulations/paused")
        .then()
            .statusCode(200)
            .body(equalTo("0"));

        // Then
        verify(deletePausedSimulationsUseCase).execute(any());
    }

    @Test
    void clearPausedSimulations_shouldReturn401WhenNotAuthenticated() {
        // When/Then - no auth cookie → 401 with Problem Details body (not an empty body)
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/api/v1/simulations/paused")
        .then()
            .statusCode(401)
            .body("status", is(401));
    }

    @Test
    void reorderQueue_shouldReturnReorderedList() {
        // Given: 3 simulations to reorder
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Simulation sim1 = createMockSimulation(id1, "Sim 1", SimulationStatus.PENDING);
        Simulation sim2 = createMockSimulation(id2, "Sim 2", SimulationStatus.PENDING);
        Simulation sim3 = createMockSimulation(id3, "Sim 3", SimulationStatus.PENDING);

        when(reorderQueueUseCase.execute(any(ReorderQueueCommand.class)))
            .thenReturn(List.of(sim3, sim1, sim2));

        // When
        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"orderedSimulationIds\": [\"" + id3 + "\", \"" + id1 + "\", \"" + id2 + "\"]}")
        .when()
            .patch("/api/v1/simulations/queue/reorder")
        .then()
            .statusCode(200)
            .body("size()", equalTo(3))
            .body("[0].id", equalTo(id3.toString()))
            .body("[1].id", equalTo(id1.toString()))
            .body("[2].id", equalTo(id2.toString()));

        // Then
        verify(reorderQueueUseCase).execute(any(ReorderQueueCommand.class));
    }

    @Test
    void reorderQueue_shouldReturn400WhenEmptyOrder() {
        // Given: Empty order list

        // When
        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"orderedSimulationIds\": []}")
        .when()
            .patch("/api/v1/simulations/queue/reorder")
        .then()
            .statusCode(400);
    }

    @Test
    void reorderQueue_shouldReturn404WhenSimulationNotFound() {
        // Given: Use case throws NoSuchElementException
        when(reorderQueueUseCase.execute(any(ReorderQueueCommand.class)))
            .thenThrow(new java.util.NoSuchElementException("Simulation not found"));

        // When
        given()
            .contentType(ContentType.JSON)
            .cookie("dev_session", TEST_EMAIL)
            .body("{\"orderedSimulationIds\": [\"" + UUID.randomUUID() + "\"]}")
        .when()
            .patch("/api/v1/simulations/queue/reorder")
        .then()
            .statusCode(404);
    }

    @Test
    void reorderQueue_shouldReturn401WhenNotAuthenticated() {
        // When/Then - no auth cookie → 401 with Problem Details body (not an empty body)
        given()
            .contentType(ContentType.JSON)
            .body("{\"orderedSimulationIds\": [\"" + UUID.randomUUID() + "\"]}")
        .when()
            .patch("/api/v1/simulations/queue/reorder")
        .then()
            .statusCode(401)
            .body("status", is(401));
    }

    // Helper method to create mock Simulation objects for testing
    private Simulation createMockSimulation(UUID id, String name, SimulationStatus status) {
        SimulationId simId = new SimulationId(id);
        StrategyId strategyId = StrategyId.generate();
        Instant now = Instant.now();
        Instant startDate = now.minus(90, ChronoUnit.DAYS);
        Instant endDate = now.minus(30, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);

        // Start the simulation if status is not PENDING
        if (status == SimulationStatus.SIMULATING || status == SimulationStatus.LIVE) {
            simulation.start();
        }

        return simulation;
    }
}
