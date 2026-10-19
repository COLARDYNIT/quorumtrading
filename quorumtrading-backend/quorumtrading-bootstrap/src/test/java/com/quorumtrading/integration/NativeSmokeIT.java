package com.quorumtrading.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Smoke tests for the packaged artifact — the one the production deploy pushes.
 *
 * <p>Every other test in this repo is a {@code @QuarkusTest}: Quarkus boots inside the test JVM,
 * on the JDK the build happens to be running. Production does not run that. Production runs a
 * GraalVM <em>native image</em>, where anything reached by reflection, resource loading or
 * dynamic proxying has to have been registered at build time or it simply is not there. That class
 * of defect is invisible to every test we have, and the first place it shows up is a 500 from a
 * container that ECS has already started routing traffic to.</p>
 *
 * <p>{@code @QuarkusIntegrationTest} launches the packaged artifact as a separate process and
 * talks to it over HTTP. Run under {@code -Pnative} that artifact is the native binary itself, so
 * these assertions are made against the exact bytes that get pushed to ECR — after the image is
 * built and before it is deployed. Run without {@code -Pnative} (locally, {@code ./mvnw verify})
 * the same assertions run against the packaged fast-jar, which is a weaker but still useful check
 * that the app boots outside dev/test config.</p>
 *
 * <p>Deliberately small. This is a smoke test, not a functional suite — the functional assertions
 * live in the {@code @QuarkusTest} classes next door, gate every PR, and would be pure duplication
 * here at roughly a minute per case. What is worth paying for is one traversal of each
 * reflection-heavy layer: security, Hibernate, Jackson, Redis.</p>
 *
 * <p><b>Chat is here on purpose.</b> Chat session creation is the single most reflection-dependent
 * path in the product — the session is serialised to JSON by hand into Redis
 * ({@code ChatSessionRedisAdapter} carries a {@code @RegisterForReflection} listing five value
 * types) and the response DTO is another six. It is also the endpoint that failed the production
 * deploy of {@code 5940e60f}. If it is going to break in the native image, it breaks here, on a
 * runner, instead of on ECS.</p>
 *
 * <p>Config: launched with {@code -Dquarkus.profile=smoke}; see the {@code %smoke} block in
 * {@code application.properties} for what that changes and why the prod profile cannot be booted
 * in CI.</p>
 */
@QuarkusIntegrationTest
@DisplayName("Native smoke")
class NativeSmokeIT {

    /**
     * Logs in as a brand-new throwaway user and returns the dev_session cookie value.
     *
     * <p>A fresh user rather than the fixed local one, for two reasons: the assertions below want
     * an empty account (an empty strategy list is only an assertion if the user has no
     * strategies), and a user with no data takes the static-greeting branch of chat session
     * creation, so the smoke test never calls an LLM and never costs anything.</p>
     */
    private String freshUserSession() {
        return given()
            .redirects().follow(false)
        .when()
            .get("/api/v1/auth/fut-login")
        .then()
            .statusCode(302)
            .extract().detailedCookie("dev_session").getValue();
    }

    private RequestSpecification authed(String session) {
        return given().cookie("dev_session", session);
    }

    @Test
    @DisplayName("the artifact boots and reports itself ready")
    void readinessIsUp() {
        given()
        .when()
            .get("/q/health/ready")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("the anonymous instance descriptor serialises")
    void instanceDescriptorIsServed() {
        // Smallest possible proof that Jackson can write a DTO in the packaged artifact: no auth,
        // no database, just a record on the wire. If reflection registration is broken wholesale,
        // this fails before anything more interesting gets a chance to.
        given()
        .when()
            .get("/api/v1/instance")
        .then()
            .statusCode(200)
            .body("edition", notNullValue())
            .body("brokers", notNullValue());
    }

    @Test
    @DisplayName("feature flags evaluate in the packaged artifact")
    void featureFlagsAreEvaluated() {
        // Regression cover for the outage of 2026-08-03. The Unleash SDK is deferred to runtime
        // initialization (see quarkus.native.additional-build-args), which moves its static
        // initializers into the running binary — where a root-level classpath resource the SDK
        // reads, app.properties, only exists if it was explicitly embedded. It was not, so this
        // threw ExceptionInInitializerError and every flag-gated endpoint answered 500 while the
        // rest of the API stayed healthy.
        //
        // Evaluation is lazy: the bean is @ApplicationScoped and nothing constructs it until a
        // flag is first read. No other endpoint in this class touches one, which is precisely why
        // a smoke test that already ran against the native binary sailed past the defect.
        given()
        .when()
            .get("/api/v1/features")
        .then()
            .statusCode(200)
            .body("features", notNullValue())
            .body("features", hasKey("challenges"))
            .body("features", hasKey("market-research"));
    }

    @Test
    @DisplayName("a flag-gated endpoint answers, on or off, but never 500")
    void gatedEndpointDoesNotFailClosedWithAnError() {
        // 200 when the challenges flag is on, 403 when it is off — both are the gate working.
        // Only a 5xx means evaluation itself broke, which is the failure this guards.
        given()
        .when()
            .get("/api/v1/challenges")
        .then()
            .statusCode(not(500));
    }

    @Test
    @DisplayName("a chat session can be created and read back")
    void chatSessionSurvivesTheRoundTrip() {
        String session = freshUserSession();

        String sessionId = authed(session)
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/chat/sessions")
        .then()
            .statusCode(200)
            .body("sessionId", notNullValue())
            .body("greeting", notNullValue())
        .extract().path("sessionId");

        // The read-back matters as much as the write. Creation only proves the session serialised
        // INTO Redis; this proves it deserialises back out, which is the direction that needs
        // ChatSession and its value types reachable by reflection.
        authed(session)
        .when()
            .get("/api/v1/chat/sessions/" + sessionId)
        .then()
            .statusCode(200)
            .body("sessionId", equalTo(sessionId))
            .body("messages", notNullValue());
    }

    @Test
    @DisplayName("an authenticated query reaches the database and comes back as JSON")
    void authenticatedDatabaseReadIsServed() {
        String session = freshUserSession();

        // Security identity -> Hibernate -> Jackson, end to end. Empty because the user was
        // created seconds ago, which is the point: it is a real query, not a cached constant.
        authed(session)
        .when()
            .get("/api/v1/strategies")
        .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    @Test
    @DisplayName("the strategy schema, the deepest static DTO tree we serve, serialises")
    void strategySchemaIsServed() {
        // A nested schema DTO rather than another flat record: deep object graphs are where
        // missing reflection registration usually surfaces, and this one needs no fixtures.
        authed(freshUserSession())
        .when()
            .get("/api/v1/strategies/schema")
        .then()
            .statusCode(200)
            .body("$", not(hasKey("error")))
            .body("$", notNullValue());
    }
}
