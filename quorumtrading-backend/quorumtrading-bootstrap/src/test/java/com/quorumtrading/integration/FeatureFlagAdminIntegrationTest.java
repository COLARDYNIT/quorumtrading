package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.Feature;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * End-to-end behaviour of feature flags: who may change them, and what a disabled flag does.
 *
 * <p>Deliberately an integration test in the bootstrap module rather than a REST-adapter test. The
 * administrator check lives inside the use case, and the REST adapter's test harness mocks use
 * cases away — an authorization test written there would be asserting against a mock of the exact
 * thing it claims to verify. Here the real service, the real {@code users.role} column and the
 * real authentication mechanism are all in play.</p>
 */
@QuarkusTest
class FeatureFlagAdminIntegrationTest {

    private static final String FLAGS_PATH = "/api/v1/admin/feature-flags";

    @Inject
    IntegrationTestHelper helper;

    @Inject
    UserRepository userRepository;

    @Inject
    FeatureFlagRepository featureFlagRepository;

    private String plainUserEmail;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        plainUserEmail = "plain-" + System.nanoTime() + "@test.com";
        helper.createUserWithEmail(plainUserEmail);

        adminEmail = "admin-" + System.nanoTime() + "@test.com";
        User admin = helper.createUserWithEmail(adminEmail);
        admin.promoteToAdmin();
        userRepository.save(admin);

        // Every test starts from "challenges on for everyone" so ordering cannot matter.
        featureFlagRepository.save(FeatureFlag.seed(Feature.CHALLENGES, true));
    }

    private static Map<String, Object> enabledPayload(boolean enabled) {
        return Map.of("enabled", enabled);
    }

    @Nested
    @DisplayName("only administrators may change a flag")
    class Authorization {

        @Test
        @DisplayName("an unauthenticated caller cannot read flags")
        void unauthenticatedCannotRead() {
            given()
                .when()
                .get(FLAGS_PATH)
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("an unauthenticated caller cannot change a flag")
        void unauthenticatedCannotWrite() {
            given()
                .contentType(ContentType.JSON)
                .body(enabledPayload(false))
                .when()
                .put(FLAGS_PATH + "/challenges")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("a plain authenticated user cannot read flags")
        void plainUserCannotRead() {
            given()
                .cookie("dev_session", plainUserEmail)
                .when()
                .get(FLAGS_PATH)
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("a plain authenticated user cannot change a flag")
        void plainUserCannotWrite() {
            given()
                .cookie("dev_session", plainUserEmail)
                .contentType(ContentType.JSON)
                .body(enabledPayload(false))
                .when()
                .put(FLAGS_PATH + "/challenges")
                .then()
                .statusCode(403);

            // and the attempt changed nothing
            assertFlagEnabled(Feature.CHALLENGES, true);
        }

        @Test
        @DisplayName("an administrator can read flags")
        void adminCanRead() {
            given()
                .cookie("dev_session", adminEmail)
                .when()
                .get(FLAGS_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", hasItem("challenges"))
                .body("name", hasItem("market-research"));
        }

        @Test
        @DisplayName("an administrator can change a flag and the change sticks")
        void adminCanWrite() {
            given()
                .cookie("dev_session", adminEmail)
                .contentType(ContentType.JSON)
                .body(enabledPayload(false))
                .when()
                .put(FLAGS_PATH + "/challenges")
                .then()
                .statusCode(200)
                .body("name", is("challenges"))
                .body("enabled", is(false));

            assertFlagEnabled(Feature.CHALLENGES, false);
        }

        @Test
        @DisplayName("an unknown flag name is a 404, not a silently created flag")
        void unknownFlagIs404() {
            given()
                .cookie("dev_session", adminEmail)
                .contentType(ContentType.JSON)
                .body(enabledPayload(true))
                .when()
                .put(FLAGS_PATH + "/no-such-feature")
                .then()
                .statusCode(404);
        }
    }

    @Nested
    @DisplayName("a disabled flag closes the feature")
    class Enforcement {

        @Test
        @DisplayName("REST refuses and names the flag that has to be switched on")
        void restRefusesNamingTheFlag() {
            disableChallenges();

            given()
                .cookie("dev_session", plainUserEmail)
                .when()
                .get("/api/v1/challenges")
                .then()
                .statusCode(403)
                .body("detail", containsString("challenges"))
                .body("type", containsString("feature-disabled"));
        }

        @Test
        @DisplayName("the refusal covers sub-resources, not just the collection")
        void restRefusesSubResources() {
            disableChallenges();

            given()
                .cookie("dev_session", plainUserEmail)
                .when()
                .get("/api/v1/challenges/00000000-0000-0000-0000-000000000001")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("the feature endpoint reports the flag as off so the UI can hide the nav")
        void featureEndpointReflectsTheFlag() {
            disableChallenges();

            given()
                .cookie("dev_session", plainUserEmail)
                .when()
                .get("/api/v1/features")
                .then()
                .statusCode(200)
                .body("features.challenges", is(false));
        }

        @Test
        @DisplayName("administering flags is never itself gated by a flag")
        void adminEndpointIsNotGated() {
            disableChallenges();

            given()
                .cookie("dev_session", adminEmail)
                .when()
                .get(FLAGS_PATH)
                .then()
                .statusCode(200);
        }
    }

    @Nested
    @DisplayName("a flag can be switched on for one person only")
    class ContextTargeting {

        @Test
        @DisplayName("the targeted email sees the feature, everyone else does not")
        void targetsASingleEmail() {
            given()
                .cookie("dev_session", adminEmail)
                .contentType(ContentType.JSON)
                .body(Map.of("enabled", true, "enabledForEmails", List.of(plainUserEmail)))
                .when()
                .put(FLAGS_PATH + "/challenges")
                .then()
                .statusCode(200)
                .body("enabled", is(true))
                .body("strategies[0].name", is("userWithId"));

            given()
                .cookie("dev_session", plainUserEmail)
                .when()
                .get("/api/v1/features")
                .then()
                .statusCode(200)
                .body("features.challenges", is(true));

            given()
                .cookie("dev_session", adminEmail)
                .when()
                .get("/api/v1/features")
                .then()
                .statusCode(200)
                .body("features.challenges", is(false));

            // and the REST gate agrees with the availability endpoint
            given()
                .cookie("dev_session", adminEmail)
                .when()
                .get("/api/v1/challenges")
                .then()
                .statusCode(403);

            given()
                .cookie("dev_session", plainUserEmail)
                .when()
                .get("/api/v1/challenges")
                .then()
                .statusCode(200);
        }

        @Test
        @DisplayName("an anonymous caller does not match an email-targeted flag")
        void anonymousDoesNotMatch() {
            given()
                .cookie("dev_session", adminEmail)
                .contentType(ContentType.JSON)
                .body(Map.of("enabled", true, "enabledForEmails", List.of(plainUserEmail)))
                .when()
                .put(FLAGS_PATH + "/challenges")
                .then()
                .statusCode(200);

            given()
                .when()
                .get("/api/v1/features")
                .then()
                .statusCode(200)
                .body("features.challenges", is(false));
        }
    }

    private void disableChallenges() {
        given()
            .cookie("dev_session", adminEmail)
            .contentType(ContentType.JSON)
            .body(enabledPayload(false))
            .when()
            .put(FLAGS_PATH + "/challenges")
            .then()
            .statusCode(200);
    }

    private void assertFlagEnabled(Feature feature, boolean expected) {
        FeatureFlag flag = featureFlagRepository.find(feature).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(flag.isEnabled()).isEqualTo(expected);
    }

    @Test
    @DisplayName("the availability endpoint is anonymous-friendly")
    void availabilityIsAnonymous() {
        given()
            .when()
            .get("/api/v1/features")
            .then()
            .statusCode(200)
            .body("features.market-research", equalTo(true));
    }
}
