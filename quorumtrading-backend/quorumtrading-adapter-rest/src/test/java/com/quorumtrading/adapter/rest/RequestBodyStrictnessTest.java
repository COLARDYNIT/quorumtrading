package com.quorumtrading.adapter.rest;

import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase;
import com.quorumtrading.application.usecase.command.CreateApiTokenCommand;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A JSON body carrying a field the DTO does not declare is rejected, not quietly trimmed.
 *
 * <p>Jackson ignores unknown properties by default, which makes a typo indistinguishable from a
 * setting: a caller sending {@code "expiresInDay": 30} — singular — had the field dropped and the
 * request succeeded with no expiry, reporting success for something it did not do. That is the
 * same defect the strategy parser had with frontmatter keys, one layer up, and it accounted for
 * most of what the MCP dog-fooding sweep turned up.</p>
 *
 * <p>Uses the API-token endpoint as the probe because its DTO is two fields and its use case is
 * mockable; the behaviour under test is the global
 * {@code quarkus.jackson.fail-on-unknown-properties} setting, so any endpoint would do.</p>
 */
@QuarkusTest
@DisplayName("Request body strictness")
class RequestBodyStrictnessTest {

    @InjectMock
    CreateApiTokenUseCase createApiTokenUseCase;

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String TEST_EMAIL = "strictness@quorumtrading.local";

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(UserId.generate());
    }

    private void useCaseSucceeds() {
        when(createApiTokenUseCase.execute(any(CreateApiTokenCommand.class))).thenReturn(
                new ApiTokenCreateResult(ApiTokenId.generate(), "qt_raw", "probe", "qt_raw",
                        Instant.now(), null));
    }

    @Test
    @DisplayName("a misspelled field is refused rather than dropped")
    void shouldRejectAnUnknownField() {
        // Stubbed so that, if the unknown field were silently dropped, the request would succeed
        // with 201 — making "accepted and trimmed" distinguishable from any other failure.
        useCaseSucceeds();

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"name\": \"probe\", \"expiresInDay\": 30}")
        .when()
            .post("/api/v1/settings/api-tokens")
        .then()
            .statusCode(400)
            // Names the offending field and lists what is accepted, so the caller can fix it
            // without reading the DTO. A bare 400 would leave them guessing which field is wrong.
            .body("detail", containsString("expiresInDay"))
            .body("detail", containsString("expiresInDays"))
            .body("title", equalTo("Unknown Request Field"));

        // The request must not have been carried out with the typo'd field silently discarded.
        verify(createApiTokenUseCase, never()).execute(any(CreateApiTokenCommand.class));
    }

    @Test
    @DisplayName("an entirely invented field is refused too")
    void shouldRejectAnInventedField() {
        useCaseSucceeds();

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"name\": \"probe\", \"grantAdmin\": true}")
        .when()
            .post("/api/v1/settings/api-tokens")
        .then()
            .statusCode(400)
            .body("detail", containsString("grantAdmin"));

        verify(createApiTokenUseCase, never()).execute(any(CreateApiTokenCommand.class));
    }

    @Test
    @DisplayName("a body using only declared fields still works")
    void shouldAcceptADeclaredBody() {
        useCaseSucceeds();

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"name\": \"probe\", \"expiresInDays\": 30}")
        .when()
            .post("/api/v1/settings/api-tokens")
        .then()
            .statusCode(201)
            .body("name", equalTo("probe"));
    }

    @Test
    @DisplayName("omitting an optional field is not the same as misspelling one")
    void shouldAcceptAMinimalBody() {
        useCaseSucceeds();

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"name\": \"probe\"}")
        .when()
            .post("/api/v1/settings/api-tokens")
        .then()
            .statusCode(201);
    }
}
