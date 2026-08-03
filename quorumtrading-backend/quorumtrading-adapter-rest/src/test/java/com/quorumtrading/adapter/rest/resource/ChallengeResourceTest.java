package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.JoinChallengeUseCase;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.ChallengeId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.quorumtrading.adapter.rest.resource.TestConstants.TEST_USER_ID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for ChallengeResource error semantics.
 *
 * <p>Regression coverage for the removed catch-all in
 * {@code GET /challenges/{id}/eligible-strategies} which used to convert
 * 404s (unknown challenge) and server faults into 400s with raw exception text.</p>
 */
@QuarkusTest
class ChallengeResourceTest {

    private static final String TEST_EMAIL = "test@quorumtrading.local";

    @InjectMock
    ChallengeRepository challengeRepository;

    @InjectMock
    StrategyRepository strategyRepository;

    @InjectMock
    UserRepository userRepository;

    @InjectMock
    JoinChallengeUseCase joinChallengeUseCase;

    @BeforeEach
    void setUp() {
        User testUser = User.reconstitute(
            TEST_USER_ID, TEST_EMAIL, "Test User", null,
            com.quorumtrading.model.enums.UserRole.USER,
            Instant.now(), Instant.now(), List.of(), true, false
        );
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
    }

    @Test
    void eligibleStrategies_shouldReturn404_whenChallengeUnknown() {
        // Given - no challenge for any id or slug
        when(challengeRepository.findById(any())).thenReturn(Optional.empty());
        when(challengeRepository.findBySlug(any())).thenReturn(Optional.empty());

        // When/Then - previously masked as 400 by a catch-all; must be 404 now
        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get("/api/v1/challenges/" + UUID.randomUUID() + "/eligible-strategies")
        .then()
            .statusCode(404)
            .body("status", is(404));
    }

    @Test
    void eligibleStrategies_shouldReturn401ProblemDetails_whenNotAuthenticated() {
        // Given - challenge exists but no auth cookie
        Challenge challenge = mock(Challenge.class);
        when(challengeRepository.findById(any())).thenReturn(Optional.of(challenge));

        given()
        .when()
            .get("/api/v1/challenges/" + UUID.randomUUID() + "/eligible-strategies")
        .then()
            .statusCode(401)
            .body("status", is(401));
    }

    @Test
    void eligibleStrategies_shouldReturn200_whenAuthenticatedAndNoStrategies() {
        Challenge challenge = mock(Challenge.class);
        when(challengeRepository.findById(any())).thenReturn(Optional.of(challenge));
        when(strategyRepository.findByOwnerId(TEST_USER_ID)).thenReturn(List.of());

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get("/api/v1/challenges/" + UUID.randomUUID() + "/eligible-strategies")
        .then()
            .statusCode(200)
            .body("$.size()", is(0));
    }

    @Test
    void eligibleStrategies_shouldReturn500WithoutLeakingDetails_whenRepositoryFails() {
        // Given - a server-side fault (previously surfaced as 400 with raw exception text)
        Challenge challenge = mock(Challenge.class);
        when(challengeRepository.findById(any())).thenReturn(Optional.of(challenge));
        when(strategyRepository.findByOwnerId(any())).thenThrow(new RuntimeException("db connection lost: secret-host"));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get("/api/v1/challenges/" + UUID.randomUUID() + "/eligible-strategies")
        .then()
            .statusCode(500)
            .body("status", is(500))
            .body("detail", not(containsString("secret-host")));
    }

    @Test
    void joinChallenge_shouldReturn409_whenChallengeNotAcceptingEntries() {
        // Given - domain invariant violation signalled with IllegalStateException
        Challenge challenge = mock(Challenge.class);
        when(challenge.getChallengeId()).thenReturn(new ChallengeId(UUID.randomUUID()));
        when(challengeRepository.findById(any())).thenReturn(Optional.of(challenge));
        when(joinChallengeUseCase.execute(any()))
            .thenThrow(new IllegalStateException("Challenge is not accepting entries"));

        // When/Then - global mapper convention: IllegalStateException -> 409 Conflict
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType(ContentType.JSON)
            .body("{\"strategyId\": \"" + UUID.randomUUID() + "\"}")
        .when()
            .post("/api/v1/challenges/" + UUID.randomUUID() + "/join")
        .then()
            .statusCode(409)
            .body("status", is(409))
            .body("detail", containsString("not accepting entries"));
    }
}
