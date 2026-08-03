package com.quorumtrading.adapter.rest.exception;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.model.exception.StrategyValidationException;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the single canonical RFC 9457 exception mapper.
 * Covers the mappings ported from the removed error.ProblemDetailExceptionMapper,
 * the PersistenceException status split (409/400/500), the WebApplicationException
 * logging/sanitization behavior, and the application/problem+json media type.
 */
class ProblemDetailsExceptionMapperTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private ProblemDetailsExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProblemDetailsExceptionMapper();
    }

    private ProblemDetails problemOf(Response response) {
        assertThat(response.getMediaType()).isNotNull();
        assertThat(response.getMediaType().toString()).isEqualTo(PROBLEM_JSON);
        return (ProblemDetails) response.getEntity();
    }

    @Test
    void shouldMapFeatureDisabledExceptionTo403NamingTheFlag() {
        // A service that guards itself, on a path FeatureFlagGateFilter's prefix list does not
        // cover. The flag name has to survive into the response body — a bare "not available"
        // leaves the caller with nothing to act on, and these gate product areas, not secrets.
        var exception = new com.quorumtrading.application.exception.FeatureDisabledException(
            com.quorumtrading.model.enums.Feature.MARKET_RESEARCH);

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(403);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType())
            .isEqualTo("https://api.quorumtrading.com/problems/feature-disabled");
        assertThat(problem.getTitle()).isEqualTo("Feature not available");
        assertThat(problem.getDetail()).contains("market-research");
    }

    // --- Mappings ported from the removed dead mapper ---

    @Test
    void shouldMapStrategyValidationExceptionTo400WithErrorList() {
        StrategyValidationException exception =
            new StrategyValidationException(List.of("Missing consensus_method", "Missing schedule"));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/validation-error");
        assertThat(problem.getTitle()).isEqualTo("Strategy Validation Failed");
        assertThat(problem.getDetail()).contains("Missing consensus_method");
        assertThat(problem.getErrors()).hasSize(2);
        assertThat(problem.getErrors().get(0).getField()).isEqualTo("strategy");
        assertThat(problem.getErrors().get(0).getMessage()).isEqualTo("Missing consensus_method");
    }

    @Test
    void shouldMapEntityNotFoundExceptionTo404() {
        Response response = mapper.toResponse(new EntityNotFoundException("Strategy not found: some-id"));

        assertThat(response.getStatus()).isEqualTo(404);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/resource-not-found");
        assertThat(problem.getDetail()).contains("Strategy not found");
    }

    @Test
    void shouldMapIllegalArgumentExceptionTo400() {
        Response response = mapper.toResponse(new IllegalArgumentException("Strategy contains non-tradable tickers: XYZFAKE"));

        assertThat(response.getStatus()).isEqualTo(400);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/validation-error");
        assertThat(problem.getDetail()).contains("non-tradable tickers");
    }

    @Test
    void shouldMapIllegalStateExceptionTo409() {
        Response response = mapper.toResponse(new IllegalStateException("Cannot modify tickers for a strategy with 3 simulation(s)"));

        assertThat(response.getStatus()).isEqualTo(409);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/conflict");
        assertThat(problem.getDetail()).contains("Cannot modify tickers");
    }

    @Test
    void shouldMapUnknownExceptionTo500WithSanitizedDetail() {
        Response response = mapper.toResponse(new RuntimeException("secret internal state"));

        assertThat(response.getStatus()).isEqualTo(500);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/internal-error");
        assertThat(problem.getDetail()).doesNotContain("secret internal state");
    }

    // --- PersistenceException mapping ---

    @Test
    void shouldMapUniqueConstraintViolationTo409() {
        PersistenceException exception = new PersistenceException("could not execute statement",
            new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_contribution_plan_date\""));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(409);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/conflict");
        assertThat(problem.getDetail()).isEqualTo("A record with this value already exists.");
    }

    @Test
    void shouldMapNotNullConstraintViolationTo400() {
        PersistenceException exception = new PersistenceException("could not execute statement",
            new RuntimeException("ERROR: null value in column \"name\" violates not-null constraint"));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/database-error");
        assertThat(problem.getDetail()).isEqualTo("A required field is missing.");
    }

    @Test
    void shouldMapForeignKeyConstraintViolationTo400() {
        PersistenceException exception = new PersistenceException("could not execute statement",
            new RuntimeException("ERROR: insert or update violates foreign key constraint \"fk_sim_strategy\""));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(problemOf(response).getDetail()).isEqualTo("Referenced record not found.");
    }

    @Test
    void shouldMapCheckConstraintViolationTo400() {
        PersistenceException exception = new PersistenceException("could not execute statement",
            new RuntimeException("ERROR: new row violates check constraint \"chk_amount_positive\""));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(problemOf(response).getDetail()).isEqualTo("A field value is invalid.");
    }

    @Test
    void shouldMapUnrecognizedPersistenceExceptionTo500WithSanitizedDetail() {
        PersistenceException exception = new PersistenceException("could not flush session",
            new RuntimeException("Connection to db-host:5432 refused"));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(500);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/internal-error");
        assertThat(problem.getDetail()).doesNotContain("db-host");
    }

    // --- WebApplicationException mapping ---

    @Test
    void shouldMapJaxRsNotFoundExceptionTo404ResourceNotFound() {
        Response response = mapper.toResponse(new jakarta.ws.rs.NotFoundException("nope"));

        assertThat(response.getStatus()).isEqualTo(404);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/resource-not-found");
    }

    @Test
    void shouldMapJaxRsBadRequestExceptionToBadRequestType() {
        Response response = mapper.toResponse(new jakarta.ws.rs.BadRequestException("Invalid simulation ID format"));

        assertThat(response.getStatus()).isEqualTo(400);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/bad-request");
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).contains("Invalid simulation ID format");
    }

    @Test
    void shouldMapCustomBadRequestExceptionToSameBadRequestType() {
        Response response = mapper.toResponse(new BadRequestException("Invalid simulation ID format"));

        assertThat(response.getStatus()).isEqualTo(400);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/bad-request");
    }

    @Test
    void shouldEchoDetailForOther4xxWebApplicationExceptions() {
        Response response = mapper.toResponse(new jakarta.ws.rs.ForbiddenException("You shall not pass"));

        assertThat(response.getStatus()).isEqualTo(403);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getType()).isEqualTo("https://api.quorumtrading.com/problems/http-error");
        assertThat(problem.getDetail()).contains("You shall not pass");
    }

    @Test
    void shouldSanitizeDetailFor5xxWebApplicationExceptions() {
        Response response = mapper.toResponse(
            new jakarta.ws.rs.WebApplicationException("stack trace internals here", 503));

        assertThat(response.getStatus()).isEqualTo(503);
        ProblemDetails problem = problemOf(response);
        assertThat(problem.getDetail()).doesNotContain("stack trace internals");
        assertThat(problem.getDetail()).contains("An unexpected error occurred");
    }
}
