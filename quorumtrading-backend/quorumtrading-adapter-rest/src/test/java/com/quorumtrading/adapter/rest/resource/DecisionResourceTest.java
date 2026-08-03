package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.QueryConsensusDecisionsUseCase;
import com.quorumtrading.application.usecase.QueryLLMDecisionsUseCase;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.query.QueryLLMDecisionsQuery;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The decision-history query endpoints, previously untested.
 *
 * <p>These are how anyone audits what the agents decided and why, and they carry the only
 * hand-written parameter validation of any read endpoint in the API — required ids, ISO-8601
 * timestamps, an ordered time range, bounded page sizes. Validation that nothing exercises is
 * validation that can quietly stop applying, and the failure would be silent: a malformed filter
 * accepted and ignored returns a plausible-looking page of the wrong rows.</p>
 */
@QuarkusTest
@DisplayName("DecisionResource")
class DecisionResourceTest {

    @InjectMock QueryLLMDecisionsUseCase queryLLMDecisionsUseCase;
    @InjectMock QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String EMAIL = "decisions@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();
    private static final String SIM_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
        when(queryLLMDecisionsUseCase.execute(any(QueryLLMDecisionsQuery.class))).thenReturn(List.of());
    }

    private io.restassured.specification.RequestSpecification llmRequest() {
        return given().cookie("dev_session", EMAIL).queryParam("simulationId", SIM_ID);
    }

    @Test
    @DisplayName("a valid query returns an empty page rather than an error")
    void shouldReturnEmptyPage() {
        llmRequest()
        .when()
            .get("/api/v1/decisions/llm")
        .then()
            .statusCode(200)
            .body("total", equalTo(0))
            .body("simulationId", equalTo(SIM_ID));
    }

    @Test
    @DisplayName("every filter reaches the query object — none is accepted and dropped")
    void shouldPassEveryFilterThrough() {
        llmRequest()
            .queryParam("from", "2026-01-01T00:00:00Z")
            .queryParam("to", "2026-02-01T00:00:00Z")
            .queryParam("agentId", "TECHNICAL")
            .queryParam("limit", 25)
        .when()
            .get("/api/v1/decisions/llm")
        .then()
            .statusCode(200);

        ArgumentCaptor<QueryLLMDecisionsQuery> query = ArgumentCaptor.forClass(QueryLLMDecisionsQuery.class);
        verify(queryLLMDecisionsUseCase).execute(query.capture());
        assertThat(query.getValue().simulationId()).isEqualTo(UUID.fromString(SIM_ID));
        assertThat(query.getValue().from()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(query.getValue().to()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        assertThat(query.getValue().agentId()).isEqualTo("TECHNICAL");
        assertThat(query.getValue().limit()).isEqualTo(25);
        assertThat(query.getValue().requestingUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("simulationId is required")
    void shouldRequireSimulationId() {
        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/decisions/llm")
        .then()
            .statusCode(400);

        verify(queryLLMDecisionsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a malformed simulationId is refused, not passed downstream")
    void shouldRejectMalformedSimulationId() {
        given()
            .cookie("dev_session", EMAIL)
            .queryParam("simulationId", "not-a-uuid")
        .when()
            .get("/api/v1/decisions/llm")
        .then()
            .statusCode(400);

        verify(queryLLMDecisionsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a timestamp that is not ISO-8601 is refused rather than silently ignored")
    void shouldRejectMalformedTimestamps() {
        llmRequest().queryParam("from", "01/01/2026")
        .when().get("/api/v1/decisions/llm")
        .then().statusCode(400);

        llmRequest().queryParam("to", "yesterday")
        .when().get("/api/v1/decisions/llm")
        .then().statusCode(400);

        verify(queryLLMDecisionsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("an inverted time range is refused — it would silently return nothing")
    void shouldRejectInvertedRange() {
        llmRequest()
            .queryParam("from", "2026-02-01T00:00:00Z")
            .queryParam("to", "2026-01-01T00:00:00Z")
        .when()
            .get("/api/v1/decisions/llm")
        .then()
            .statusCode(400);

        verify(queryLLMDecisionsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("an equal from/to is refused too — the range is half-open, so it can never match")
    void shouldRejectAnEmptyRange() {
        llmRequest()
            .queryParam("from", "2026-01-01T00:00:00Z")
            .queryParam("to", "2026-01-01T00:00:00Z")
        .when()
            .get("/api/v1/decisions/llm")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("limit is bounded at both ends")
    void shouldBoundTheLimit() {
        llmRequest().queryParam("limit", 0)
        .when().get("/api/v1/decisions/llm")
        .then().statusCode(400);

        llmRequest().queryParam("limit", 1001)
        .when().get("/api/v1/decisions/llm")
        .then().statusCode(400);

        verify(queryLLMDecisionsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("the boundary values themselves are allowed")
    void shouldAcceptTheLimitBoundaries() {
        llmRequest().queryParam("limit", 1)
        .when().get("/api/v1/decisions/llm")
        .then().statusCode(200);

        llmRequest().queryParam("limit", 1000)
        .when().get("/api/v1/decisions/llm")
        .then().statusCode(200);
    }

    @Test
    @DisplayName("decision history is not public")
    void shouldRequireAuthentication() {
        given()
            .queryParam("simulationId", SIM_ID)
        .when()
            .get("/api/v1/decisions/llm")
        .then()
            .statusCode(401);
    }
}
