package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase;
import com.quorumtrading.application.usecase.apitoken.ListApiTokensUseCase;
import com.quorumtrading.application.usecase.apitoken.RevokeApiTokenUseCase;
import com.quorumtrading.application.usecase.command.CreateApiTokenCommand;
import com.quorumtrading.application.usecase.command.RevokeApiTokenCommand;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;
import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class ApiTokenResourceTest {

    @InjectMock
    CreateApiTokenUseCase createApiTokenUseCase;

    @InjectMock
    ListApiTokensUseCase listApiTokensUseCase;

    @InjectMock
    RevokeApiTokenUseCase revokeApiTokenUseCase;

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final UserId TEST_USER_ID = UserId.generate();
    private static final String TEST_EMAIL = "test@quorumtrading.local";

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(TEST_USER_ID);
    }

    @Test
    void shouldCreateApiToken() {
        ApiTokenId tokenId = ApiTokenId.generate();
        Instant now = Instant.now();
        ApiTokenCreateResult result = new ApiTokenCreateResult(
                tokenId, "qt_abcdef12345", "Claude Code", "qt_abcde", now, null);

        when(createApiTokenUseCase.execute(any(CreateApiTokenCommand.class))).thenReturn(result);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"name\": \"Claude Code\"}")
        .when()
            .post("/api/v1/settings/api-tokens")
        .then()
            .statusCode(201)
            .body("rawToken", equalTo("qt_abcdef12345"))
            .body("name", equalTo("Claude Code"))
            .body("tokenPrefix", equalTo("qt_abcde"))
            .body("id", equalTo(tokenId.value().toString()));
    }

    @Test
    void shouldCreateApiTokenWithExpiration() {
        ApiTokenId tokenId = ApiTokenId.generate();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(30 * 86400);
        ApiTokenCreateResult result = new ApiTokenCreateResult(
                tokenId, "qt_xyz789", "Short-lived", "qt_xyz78", now, expiresAt);

        when(createApiTokenUseCase.execute(any(CreateApiTokenCommand.class))).thenReturn(result);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"name\": \"Short-lived\", \"expiresInDays\": 30}")
        .when()
            .post("/api/v1/settings/api-tokens")
        .then()
            .statusCode(201)
            .body("expiresAt", notNullValue());
    }

    @Test
    void shouldRejectCreateWithBlankName() {
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"name\": \"\"}")
        .when()
            .post("/api/v1/settings/api-tokens")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldListApiTokens() {
        ApiToken token1 = ApiToken.create(ApiTokenId.generate(), TEST_USER_ID, "Token 1", "hash1", "qt_tok1x", null);
        ApiToken token2 = ApiToken.create(ApiTokenId.generate(), TEST_USER_ID, "Token 2", "hash2", "qt_tok2x", null);

        when(listApiTokensUseCase.execute(TEST_USER_ID)).thenReturn(List.of(token1, token2));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get("/api/v1/settings/api-tokens")
        .then()
            .statusCode(200)
            .body("$.size()", is(2))
            .body("[0].name", equalTo("Token 1"))
            .body("[1].name", equalTo("Token 2"))
            .body("[0].rawToken", nullValue())  // raw token not in list response
            .body("[1].rawToken", nullValue());
    }

    @Test
    void shouldReturnEmptyListWhenNoTokens() {
        when(listApiTokensUseCase.execute(TEST_USER_ID)).thenReturn(Collections.emptyList());

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get("/api/v1/settings/api-tokens")
        .then()
            .statusCode(200)
            .body("$.size()", is(0));
    }

    @Test
    void shouldRevokeApiToken() {
        ApiTokenId tokenId = ApiTokenId.generate();

        doNothing().when(revokeApiTokenUseCase).execute(any(RevokeApiTokenCommand.class));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .delete("/api/v1/settings/api-tokens/" + tokenId.value().toString())
        .then()
            .statusCode(204);

        verify(revokeApiTokenUseCase).execute(any(RevokeApiTokenCommand.class));
    }

    @Test
    void shouldReturn404ForNonExistentTokenRevoke() {
        doThrow(new IllegalArgumentException("Token not found"))
            .when(revokeApiTokenUseCase).execute(any(RevokeApiTokenCommand.class));

        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .delete("/api/v1/settings/api-tokens/" + ApiTokenId.generate().value().toString())
        .then()
            .statusCode(404);
    }

}
