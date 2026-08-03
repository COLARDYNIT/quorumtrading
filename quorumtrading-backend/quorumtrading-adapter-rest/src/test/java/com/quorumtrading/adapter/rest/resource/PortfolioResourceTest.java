package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.QueryPortfolioSnapshotsUseCase;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.query.QueryPortfolioSnapshotsQuery;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
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
 * The portfolio-snapshot query behind every equity curve, previously untested.
 *
 * <p>{@code resolution} is the parameter worth pinning: {@code all} and {@code daily} return
 * materially different series from the same simulation, so a value that were accepted and ignored
 * would draw a chart nobody asked for and nothing would look broken. The dog-fooding sweep found
 * duplicate same-instant snapshot rows through this endpoint, which is exactly the kind of thing
 * that only shows up when someone reads the series carefully.</p>
 */
@QuarkusTest
@DisplayName("PortfolioResource")
class PortfolioResourceTest {

    @InjectMock QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;
    // This resource resolves the caller through getCurrentUserIdOptional(), which reads the
    // UserRepository directly rather than going through ResolveCurrentUserUseCase like its
    // siblings. Stubbing only the use case leaves the request anonymous and every call 401s.
    @InjectMock UserRepository userRepository;

    private static final String EMAIL = "portfolio@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();
    private static final String SIM_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.create(USER_ID, EMAIL, EMAIL, null, OAuthProvider.GOOGLE)));
        when(queryPortfolioSnapshotsUseCase.execute(any(QueryPortfolioSnapshotsQuery.class)))
                .thenReturn(List.of());
    }

    private io.restassured.specification.RequestSpecification request() {
        return given().cookie("dev_session", EMAIL).queryParam("simulationId", SIM_ID);
    }

    private QueryPortfolioSnapshotsQuery capturedQuery() {
        ArgumentCaptor<QueryPortfolioSnapshotsQuery> captor =
                ArgumentCaptor.forClass(QueryPortfolioSnapshotsQuery.class);
        verify(queryPortfolioSnapshotsUseCase).execute(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a valid query returns an empty series rather than an error")
    void shouldReturnEmptySeries() {
        request()
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(200)
            .body("simulationId", equalTo(SIM_ID));
    }

    @Test
    @DisplayName("resolution reaches the query — the two values draw different charts")
    void shouldPassResolutionThrough() {
        request().queryParam("resolution", "daily")
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(200);

        assertThat(capturedQuery().resolution()).isEqualToIgnoringCase("daily");
    }

    @Test
    @DisplayName("omitting resolution uses the documented default of 'all'")
    void shouldDefaultResolutionToAll() {
        request()
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(200);

        assertThat(capturedQuery().resolution()).isEqualToIgnoringCase("all");
    }

    @Test
    @DisplayName("an unrecognised resolution is refused, not quietly treated as the default")
    void shouldRejectUnknownResolution() {
        request().queryParam("resolution", "hourly")
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(400);

        verify(queryPortfolioSnapshotsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("excludePositions reaches the query")
    void shouldPassExcludePositionsThrough() {
        request().queryParam("excludePositions", true)
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(200);

        assertThat(capturedQuery().excludePositions()).isTrue();
    }

    @Test
    @DisplayName("simulationId is required")
    void shouldRequireSimulationId() {
        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(400);

        verify(queryPortfolioSnapshotsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a malformed simulationId is refused")
    void shouldRejectMalformedSimulationId() {
        given()
            .cookie("dev_session", EMAIL)
            .queryParam("simulationId", "not-a-uuid")
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("malformed and inverted time ranges are refused")
    void shouldValidateTheTimeRange() {
        request().queryParam("from", "01/01/2026")
        .when().get("/api/v1/portfolios/snapshots")
        .then().statusCode(400);

        request()
            .queryParam("from", "2026-02-01T00:00:00Z")
            .queryParam("to", "2026-01-01T00:00:00Z")
        .when().get("/api/v1/portfolios/snapshots")
        .then().statusCode(400);

        verify(queryPortfolioSnapshotsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("limit is bounded")
    void shouldBoundTheLimit() {
        request().queryParam("limit", 0)
        .when().get("/api/v1/portfolios/snapshots")
        .then().statusCode(400);

        request().queryParam("limit", 1001)
        .when().get("/api/v1/portfolios/snapshots")
        .then().statusCode(400);
    }

    @Test
    @DisplayName("portfolio history is not public")
    void shouldRequireAuthentication() {
        given()
            .queryParam("simulationId", SIM_ID)
        .when()
            .get("/api/v1/portfolios/snapshots")
        .then()
            .statusCode(401);
    }
}
