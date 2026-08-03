package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.ForceEndSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationUseCase;
import com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase;
import com.quorumtrading.application.usecase.PauseSimulationUseCase;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.ResumeSimulationUseCase;
import com.quorumtrading.application.usecase.SellPositionUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.ForceEndSimulationCommand;
import com.quorumtrading.application.usecase.result.ForceEndSimulationResult;
import com.quorumtrading.application.usecase.result.LiquidateAllResult;
import com.quorumtrading.application.usecase.result.SellPositionResult;
import com.quorumtrading.application.usecase.contribution.GetSimulationContributionsUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The simulation lifecycle and position endpoints — the busiest resource in the product, and the
 * least covered until now.
 *
 * <p>The sibling {@link SimulationResourceTest} covers listing and the queue. This covers what the
 * queue leads to: starting, pausing, resuming, force-ending, and the two endpoints that sell
 * things. Those last three move positions, so the distinctions that matter are the ones an
 * operator would be misled by — a conflict must not read as success, a missing simulation must not
 * read as a malformed request, and {@code liquidate} must reach the use case as the caller set it,
 * since it decides whether holdings are sold or left in place.</p>
 */
@QuarkusTest
@DisplayName("SimulationResource — lifecycle and positions")
class SimulationLifecycleResourceTest {

    @InjectMock GetSimulationUseCase getSimulationUseCase;
    @InjectMock StartSimulationUseCase startSimulationUseCase;
    @InjectMock PauseSimulationUseCase pauseSimulationUseCase;
    @InjectMock ResumeSimulationUseCase resumeSimulationUseCase;
    @InjectMock ForceEndSimulationUseCase forceEndSimulationUseCase;
    @InjectMock SellPositionUseCase sellPositionUseCase;
    @InjectMock LiquidateAllPositionsUseCase liquidateAllPositionsUseCase;
    @InjectMock GetSimulationContributionsUseCase getSimulationContributionsUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;
    @InjectMock UserRepository userRepository;

    private static final String EMAIL = "lifecycle@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();
    private static final String SIM_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.create(USER_ID, EMAIL, EMAIL, null, OAuthProvider.GOOGLE)));
        when(getSimulationContributionsUseCase.execute(any(), any())).thenReturn(List.of());
    }

    private Simulation simulation() {
        return Simulation.reconstitute(
                new SimulationId(UUID.fromString(SIM_ID)), "Test sim", StrategyId.generate(),
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-08T00:00:00Z"),
                Money.usd(new BigDecimal("10000.00")), null, null, null);
    }

    /**
     * These POSTs declare @Consumes(APPLICATION_JSON), so the content type has to be set even
     * where there is no body — without it the container answers 415 before the handler runs.
     */
    private io.restassured.specification.RequestSpecification authed() {
        return given().cookie("dev_session", EMAIL).contentType("application/json");
    }

    // ---- read ----

    @Test
    @DisplayName("a simulation is fetched by id")
    void shouldGetSimulationById() {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(simulation());

        authed()
        .when().get("/api/v1/simulations/" + SIM_ID)
        .then().statusCode(200).body("id", equalTo(SIM_ID));
    }

    @Test
    @DisplayName("an unknown simulation is a 404, a malformed id a 400 — the caller can tell them apart")
    void shouldDistinguishMissingFromMalformed() {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class)))
                .thenThrow(new NoSuchElementException("gone"));

        authed().when().get("/api/v1/simulations/" + UUID.randomUUID()).then().statusCode(404);
        authed().when().get("/api/v1/simulations/not-a-uuid").then().statusCode(400);
    }

    @Test
    @DisplayName("the query carries the caller, so another user's simulation is not readable")
    void shouldScopeTheReadToTheCaller() {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(simulation());

        authed().when().get("/api/v1/simulations/" + SIM_ID).then().statusCode(200);

        ArgumentCaptor<GetSimulationQuery> query = ArgumentCaptor.forClass(GetSimulationQuery.class);
        verify(getSimulationUseCase).execute(query.capture());
        assertThat(query.getValue().requestingUserId()).isEqualTo(USER_ID);
    }

    // ---- lifecycle ----

    @Test
    @DisplayName("start runs the simulation and returns its refreshed state")
    void shouldStartASimulation() {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(simulation());

        authed().when().post("/api/v1/simulations/" + SIM_ID + "/start").then().statusCode(200);

        verify(startSimulationUseCase).execute(any());
    }

    @Test
    @DisplayName("starting an unknown simulation is a 404, and nothing runs")
    void shouldNotStartAnUnknownSimulation() {
        org.mockito.Mockito.doThrow(new NoSuchElementException("gone"))
                .when(startSimulationUseCase).execute(any());

        authed().when().post("/api/v1/simulations/" + UUID.randomUUID() + "/start")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("pause and resume both return the refreshed simulation")
    void shouldPauseAndResume() {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(simulation());

        authed().when().post("/api/v1/simulations/" + SIM_ID + "/pause").then().statusCode(200);
        authed().when().post("/api/v1/simulations/" + SIM_ID + "/resume").then().statusCode(200);

        verify(pauseSimulationUseCase).execute(any());
        verify(resumeSimulationUseCase).execute(any());
    }

    @Test
    @DisplayName("resuming something that cannot be resumed is a conflict, not a success")
    void shouldReportAConflictOnAnInvalidResume() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Simulation is COMPLETED"))
                .when(resumeSimulationUseCase).execute(any());

        authed()
        .when().post("/api/v1/simulations/" + SIM_ID + "/resume")
        .then().statusCode(409);
    }

    // ---- force end ----

    @Test
    @DisplayName("force-end reports what it did, including whether it liquidated")
    void shouldForceEnd() {
        when(forceEndSimulationUseCase.execute(any(ForceEndSimulationCommand.class)))
                .thenReturn(new ForceEndSimulationResult(true, true, 3, "Ended and liquidated 3 positions"));

        authed()
            .queryParam("liquidate", true)
        .when().post("/api/v1/simulations/" + SIM_ID + "/end")
        .then()
            .statusCode(200)
            .body("liquidated", equalTo(true))
            .body("liquidatedCount", equalTo(3));
    }

    @Test
    @DisplayName("the liquidate flag reaches the use case as set — it decides whether holdings are sold")
    void shouldPassTheLiquidateFlagThrough() {
        when(forceEndSimulationUseCase.execute(any(ForceEndSimulationCommand.class)))
                .thenReturn(new ForceEndSimulationResult(true, false, 0, "Ended"));

        authed()
            .queryParam("liquidate", false)
        .when().post("/api/v1/simulations/" + SIM_ID + "/end")
        .then().statusCode(200);

        ArgumentCaptor<ForceEndSimulationCommand> command =
                ArgumentCaptor.forClass(ForceEndSimulationCommand.class);
        verify(forceEndSimulationUseCase).execute(command.capture());
        assertThat(command.getValue().liquidatePositions())
                .as("a false flag must not become a sell-everything")
                .isFalse();
        assertThat(command.getValue().requestingUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("ending an already-terminal simulation is a conflict")
    void shouldReportConflictOnEndingATerminalSimulation() {
        when(forceEndSimulationUseCase.execute(any(ForceEndSimulationCommand.class)))
                .thenThrow(new IllegalStateException("Simulation is already COMPLETED"));

        authed()
        .when().post("/api/v1/simulations/" + SIM_ID + "/end")
        .then().statusCode(409);
    }

    // ---- positions ----

    @Test
    @DisplayName("selling one position names the symbol it sold")
    void shouldSellOnePosition() {
        when(sellPositionUseCase.execute(any(SimulationId.class), any(), any(UserId.class)))
                .thenReturn(new SellPositionResult(true, "Sold 0.5 BTC", new BigDecimal("0.5"), new BigDecimal("30000.00")));

        authed()
        .when().post("/api/v1/simulations/" + SIM_ID + "/positions/BTC/sell")
        .then().statusCode(200);

        verify(sellPositionUseCase).execute(any(), any(), any());
    }

    @Test
    @DisplayName("selling a position that is not held is a conflict, not a silent no-op")
    void shouldReportConflictWhenSellingSomethingNotHeld() {
        when(sellPositionUseCase.execute(any(SimulationId.class), any(), any(UserId.class)))
                .thenThrow(new IllegalStateException("No open position for BTC"));

        authed()
        .when().post("/api/v1/simulations/" + SIM_ID + "/positions/BTC/sell")
        .then().statusCode(409);
    }

    @Test
    @DisplayName("liquidate-all reports how many positions it closed")
    void shouldLiquidateEverything() {
        when(liquidateAllPositionsUseCase.execute(any(SimulationId.class), any(UserId.class)))
                .thenReturn(new LiquidateAllResult(2, 0, List.of(), new BigDecimal("15000.00")));

        authed()
        .when().post("/api/v1/simulations/" + SIM_ID + "/liquidate")
        .then().statusCode(200);

        verify(liquidateAllPositionsUseCase).execute(any(), any());
    }

    @Test
    @DisplayName("a malformed id never reaches a selling use case")
    void shouldNotSellOnAMalformedId() {
        authed().when().post("/api/v1/simulations/not-a-uuid/liquidate").then().statusCode(400);

        verify(liquidateAllPositionsUseCase, never()).execute(any(), any());
    }

    // ---- auth ----

    @Test
    @DisplayName("none of the lifecycle or position endpoints is public")
    void shouldRequireAuthentication() {
        given().when().get("/api/v1/simulations/" + SIM_ID).then().statusCode(401);
        given().contentType("application/json").when().post("/api/v1/simulations/" + SIM_ID + "/start").then().statusCode(401);
        given().contentType("application/json").when().post("/api/v1/simulations/" + SIM_ID + "/end").then().statusCode(401);
        given().contentType("application/json").when().post("/api/v1/simulations/" + SIM_ID + "/liquidate").then().statusCode(401);
    }
}
