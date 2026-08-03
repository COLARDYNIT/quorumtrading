package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase;
import com.quorumtrading.application.usecase.dto.ChallengeLeaderboard;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.quorumtrading.adapter.rest.resource.TestConstants.TEST_USER_ID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * How {@code GET /challenges/{id}} renders the shared leaderboard.
 *
 * <p>Ranking and return maths moved to the application layer because this resource and the MCP
 * tool computed them separately and disagreed. What stays here is presentation: how many rows
 * to show, how to round them, and pinning the viewer's own entry — which is now decided by an
 * ownership flag rather than by matching the owner's display name, so two participants sharing
 * a name no longer collide.</p>
 */
@QuarkusTest
class ChallengeLeaderboardResourceTest {

    private static final String TEST_EMAIL = "test@quorumtrading.local";
    private static final ChallengeId CHALLENGE_ID = new ChallengeId(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @InjectMock BuildChallengeLeaderboardUseCase buildChallengeLeaderboardUseCase;
    @InjectMock ChallengeRepository challengeRepository;
    @InjectMock UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User testUser = User.reconstitute(TEST_USER_ID, TEST_EMAIL, "Test User", null,
            com.quorumtrading.model.enums.UserRole.USER,
            Instant.now(), Instant.now(), List.of(), true, false);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge()));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void aShortLeaderboardIsShownWhole() {
        givenLeaderboard(3, ranked(3, -1));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/challenges/" + CHALLENGE_ID.value())
            .then().statusCode(200)
            .body("entries", hasSize(3))
            .body("participantCount", is(3));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void aLongLeaderboardIsCutToItsHead() {
        givenLeaderboard(9, ranked(9, -1));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/challenges/" + CHALLENGE_ID.value())
            .then().statusCode(200)
            .body("entries", hasSize(5))
            .body("entries[0].rank", is(1))
            .body("entries[4].rank", is(5));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void theViewersOwnEntryIsPinnedOnWhenItRankedBelowTheCut() {
        givenLeaderboard(9, ranked(9, 8)); // viewer owns the last-placed entry

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/challenges/" + CHALLENGE_ID.value())
            .then().statusCode(200)
            .body("entries", hasSize(6))
            .body("entries[5].rank", is(9));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void anOwnedEntryAlreadyInTheHeadIsNotShownTwice() {
        givenLeaderboard(9, ranked(9, 2));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/challenges/" + CHALLENGE_ID.value())
            .then().statusCode(200)
            .body("entries", hasSize(5));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void participantCountIncludesEntriesTheViewerCannotSee() {
        givenLeaderboard(12, ranked(2, -1));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/challenges/" + CHALLENGE_ID.value())
            .then().statusCode(200)
            .body("entries", hasSize(2))
            .body("participantCount", is(12));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void percentagesAndCostsAreRoundedForDisplay() {
        ChallengeLeaderboard.Entry entry = new ChallengeLeaderboard.Entry(
            1, SimulationId.generate(), "entry", "strategy", "Alice",
            Money.usd(1234), new BigDecimal("23.4567"), SimulationStatus.COMPLETED,
            new BigDecimal("0.123456"), 42L, false);
        givenLeaderboard(1, List.of(entry));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/challenges/" + CHALLENGE_ID.value())
            .then().statusCode(200)
            .body("entries[0].returnPercent", is("23.46"))
            .body("entries[0].llmCostUsd", is("0.1235"))
            .body("entries[0].executionTimeSeconds", is(42));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void anUncomputableReturnIsRenderedAsAbsentRatherThanZero() {
        ChallengeLeaderboard.Entry entry = new ChallengeLeaderboard.Entry(
            1, SimulationId.generate(), "entry", "strategy", "Alice",
            Money.usd(1000), null, SimulationStatus.LIVE, null, null, false);
        givenLeaderboard(1, List.of(entry));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/challenges/" + CHALLENGE_ID.value())
            .then().statusCode(200)
            .body("entries[0].returnPercent", is(nullValue()))
            .body("entries[0].llmCostUsd", is(nullValue()))
            .body("entries[0].executionTimeSeconds", is(nullValue()));
    }

    // ----- fixtures -----

    private void givenLeaderboard(int totalParticipants, List<ChallengeLeaderboard.Entry> entries) {
        when(buildChallengeLeaderboardUseCase.execute(any()))
            .thenReturn(new ChallengeLeaderboard(totalParticipants, entries));
    }

    /** {@code ownedIndex} marks which 0-based entry belongs to the viewer, or -1 for none. */
    private static List<ChallengeLeaderboard.Entry> ranked(int count, int ownedIndex) {
        List<ChallengeLeaderboard.Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new ChallengeLeaderboard.Entry(
                i + 1, SimulationId.generate(), "entry-" + i, "strategy", "Someone",
                Money.usd(BigDecimal.valueOf(10_000L - i)), BigDecimal.valueOf(10L - i),
                SimulationStatus.COMPLETED, new BigDecimal("0.5"), 60L, i == ownedIndex));
        }
        return entries;
    }

    private static Challenge challenge() {
        return new Challenge(CHALLENGE_ID, "Test Challenge", "desc",
            new Symbol("BTC", InvestmentType.CRYPTO), Money.usd(1000),
            T0, T0.plus(365, ChronoUnit.DAYS),
            T0.minus(7, ChronoUnit.DAYS), T0,
            T0.minus(7, ChronoUnit.DAYS), new UserId(UUID.randomUUID()), true);
    }
}
