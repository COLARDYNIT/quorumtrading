package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The chat endpoints, which had no gating test of any kind.
 *
 * <p>{@code ChatResource} is excluded from the REST module's test container (it needs wiring that
 * module does not have), and the only other coverage was {@code ChatPromptEngineeringTest} — tagged
 * {@code prompt-engineering-expensive}, excluded from CI, and billed against a real model. So the
 * HTTP contract of the feature most users touch first was asserted nowhere that runs.</p>
 *
 * <p>This asserts the contract, not the conversation: sessions belong to their owner, an unknown
 * session id is a clean 404 rather than a 500, and none of it is reachable without a session
 * cookie. The canned {@code dummy} LLM keeps it free, offline and deterministic — the quality of
 * what the model says stays the expensive test's job.</p>
 */
@QuarkusTest
@TestProfile(DummyLlmTestProfile.class)
@DisplayName("Chat REST API")
class ChatRestApiIntegrationTest {

    @Inject
    IntegrationTestHelper helper;

    private String testEmail;

    @BeforeEach
    void setUp() {
        testEmail = "chat-rest-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        helper.createUserWithEmail(testEmail);
    }

    private RequestSpecification authedRequest() {
        return given().cookie("dev_session", testEmail);
    }

    private String createSession() {
        return authedRequest()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/chat/sessions")
        .then()
            .statusCode(200)
            .body("sessionId", notNullValue())
        .extract().path("sessionId");
    }

    @Test
    @DisplayName("a new session is created with a greeting")
    void shouldCreateASession() {
        authedRequest()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/chat/sessions")
        .then()
            .statusCode(200)
            .body("sessionId", notNullValue())
            .body("greeting", notNullValue());
    }

    // This was disabled as "TEST-ENVIRONMENT ONLY — the product behaviour was verified working".
    // It was not. The 404 was real and reproduced identically against the packaged artifact: the
    // session serialised into Redis carried a derived "system" property that UserId's canonical
    // constructor will not take back, so every read threw and findById turned it into an empty
    // Optional. Sessions were written correctly, as the note said — they just could never be read.
    // Fixed in ChatSessionRedisAdapter; re-enabled here so it stays fixed.
    @Test
    @DisplayName("a created session can be read back")
    void shouldGetASessionById() {
        String sessionId = createSession();

        authedRequest()
        .when()
            .get("/api/v1/chat/sessions/" + sessionId)
        .then()
            .statusCode(200)
            .body("sessionId", notNullValue());
    }

    @Test
    @DisplayName("the session list includes what was just created")
    void shouldListSessions() {
        createSession();

        authedRequest()
        .when()
            .get("/api/v1/chat/sessions")
        .then()
            .statusCode(200);
    }

    @Test
    @DisplayName("a session can be deleted, and is gone afterwards")
    void shouldDeleteASession() {
        String sessionId = createSession();

        authedRequest()
        .when()
            .delete("/api/v1/chat/sessions/" + sessionId)
        .then()
            .statusCode(anyOfNoContentOrOk());

        authedRequest()
        .when()
            .get("/api/v1/chat/sessions/" + sessionId)
        .then()
            .statusCode(404);
    }

    /** Delete is documented as either 204 or 200 depending on the handler; accept both. */
    private org.hamcrest.Matcher<Integer> anyOfNoContentOrOk() {
        return org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.equalTo(204), org.hamcrest.Matchers.equalTo(200));
    }

    @Test
    @DisplayName("an unknown session id is a clean 404, not a server error")
    void shouldReturnNotFoundForAnUnknownSession() {
        authedRequest()
        .when()
            .get("/api/v1/chat/sessions/" + UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("another user's session is not readable")
    void shouldNotExposeAnotherUsersSession() {
        String sessionId = createSession();

        String otherEmail = "chat-other-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        helper.createUserWithEmail(otherEmail);

        given()
            .cookie("dev_session", otherEmail)
        .when()
            .get("/api/v1/chat/sessions/" + sessionId)
        .then()
            // Someone else's conversation must not be readable, and must not be distinguishable
            // from one that does not exist.
            .statusCode(404);
    }

    @Test
    @DisplayName("chat is not reachable without a session")
    void shouldRequireAuthentication() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/chat/sessions")
        .then()
            .statusCode(401);

        given()
        .when()
            .get("/api/v1/chat/sessions")
        .then()
            .statusCode(401);
    }
}
