package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase;
import com.quorumtrading.application.usecase.dto.BenchmarkLeaderboard;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
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
 * How {@code GET /benchmarks/{id}/entries} renders the shared leaderboard.
 *
 * <p>The endpoint used to assemble entries itself from four repositories, which is how it came
 * to rank differently from the MCP view of the same benchmark. It now renders whatever the use
 * case ranked; what is still its own decision is which rows to show and what colour to give
 * them, and that is what these tests pin.</p>
 */
@QuarkusTest
class BenchmarkEntriesResourceTest {

    private static final String TEST_EMAIL = "test@quorumtrading.local";
    private static final String BENCHMARK_ID = UUID.randomUUID().toString();

    @InjectMock BuildBenchmarkLeaderboardUseCase buildBenchmarkLeaderboardUseCase;
    @InjectMock BenchmarkRepository benchmarkRepository;
    @InjectMock UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User testUser = User.reconstitute(TEST_USER_ID, TEST_EMAIL, "Test User", null,
            com.quorumtrading.model.enums.UserRole.USER,
            Instant.now(), Instant.now(), List.of(), true, false);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void rankedOrderFromTheUseCaseIsPreserved() {
        givenLeaderboard(
            entry(1, 0, "winner", new BigDecimal("40.0000"), true),
            entry(2, 1, "loser", new BigDecimal("-5.0000"), true));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/benchmarks/" + BENCHMARK_ID + "/entries")
            .then().statusCode(200)
            .body("$", hasSize(2))
            .body("[0].name", is("winner"))
            .body("[1].name", is("loser"));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void entriesWithoutAMeasurementAreNotShownAsAFlatZero() {
        // A benchmark member that has produced no snapshot yet carries a seed-cash placeholder.
        givenLeaderboard(
            entry(1, 0, "measured", new BigDecimal("12.0000"), true),
            entry(2, 1, "not started", BigDecimal.ZERO, false));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/benchmarks/" + BENCHMARK_ID + "/entries")
            .then().statusCode(200)
            .body("$", hasSize(1))
            .body("[0].name", is("measured"));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void colourFollowsBenchmarkMembershipSoASimulationKeepsItAcrossReRankings() {
        // Ordinal 1 ranked first: it must still get the second palette colour, not the first.
        givenLeaderboard(
            entry(1, 1, "second member", new BigDecimal("40.0000"), true),
            entry(2, 0, "first member", new BigDecimal("10.0000"), true));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/benchmarks/" + BENCHMARK_ID + "/entries")
            .then().statusCode(200)
            .body("[0].color", is("#9D4EDD"))
            .body("[1].color", is("#4AA3FF"));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void limitTakesTheHeadOfTheRanking() {
        givenLeaderboard(
            entry(1, 0, "first", new BigDecimal("40.0000"), true),
            entry(2, 1, "second", new BigDecimal("20.0000"), true),
            entry(3, 2, "third", new BigDecimal("10.0000"), true));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/benchmarks/" + BENCHMARK_ID + "/entries?limit=2")
            .then().statusCode(200)
            .body("$", hasSize(2))
            .body("[0].name", is("first"))
            .body("[1].name", is("second"));
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void anUnknownBenchmarkIsNotFound() {
        when(buildBenchmarkLeaderboardUseCase.execute(any()))
            .thenThrow(new NoSuchElementException("Benchmark not found: " + BENCHMARK_ID));

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/benchmarks/" + BENCHMARK_ID + "/entries")
            .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void aMalformedIdIsABadRequestAndNeverReachesTheUseCase() {
        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/benchmarks/not-a-uuid/entries")
            .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = TEST_EMAIL)
    void anEmptyBenchmarkRendersAnEmptyList() {
        givenLeaderboard();

        given().cookie("dev_session", TEST_EMAIL)
            .when().get("/api/v1/benchmarks/" + BENCHMARK_ID + "/entries")
            .then().statusCode(200)
            .body("$", hasSize(0));
    }

    private void givenLeaderboard(BenchmarkLeaderboard.Entry... entries) {
        when(buildBenchmarkLeaderboardUseCase.execute(any()))
            .thenReturn(new BenchmarkLeaderboard(List.of(entries)));
    }

    private static BenchmarkLeaderboard.Entry entry(int rank, int ordinal, String name,
                                                    BigDecimal returnPercent, boolean measured) {
        return new BenchmarkLeaderboard.Entry(
            rank, ordinal, SimulationId.generate(), name, "strategy", "BACKTEST",
            Money.usd(1000), Money.usd(1200), measured,
            measured ? returnPercent : null,
            SimulationStatus.COMPLETED, measured ? 5L : 0L);
    }
}
