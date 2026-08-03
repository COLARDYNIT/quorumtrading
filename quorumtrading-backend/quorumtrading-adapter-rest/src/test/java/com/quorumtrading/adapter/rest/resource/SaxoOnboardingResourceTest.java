package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase;
import com.quorumtrading.application.usecase.saxo.OnboardingStart;
import com.quorumtrading.application.usecase.saxo.StartSaxoOnboardingUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(SaxoOnboardingResourceTest.SaxoEnabledProfile.class)
class SaxoOnboardingResourceTest {

    public static class SaxoEnabledProfile implements QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of("quorumtrading.broker.saxo.enabled", "true");
        }
    }

    @InjectMock
    StartSaxoOnboardingUseCase startSaxoOnboardingUseCase;

    @InjectMock
    CompleteSaxoOnboardingUseCase completeSaxoOnboardingUseCase;

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final UserId TEST_USER_ID = UserId.generate();
    private static final String TEST_EMAIL = "test@quorumtrading.local";
    private static final String EXPECTED_REDIRECT_URI =
            "http://localhost:8085/api/v1/broker-accounts/saxo/onboard/callback";

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(TEST_USER_ID);
    }

    @Test
    void shouldReturnConsentUrlOnStart() {
        OnboardingStart start = new OnboardingStart(
                URI.create("https://sim.logonvalidation.net/authorize?response_type=code&state=abc123"),
                "abc123");
        when(startSaxoOnboardingUseCase.start(any())).thenReturn(start);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"environment\": \"SIM\"}")
        .when()
            .post("/api/v1/broker-accounts/saxo/onboard/start")
        .then()
            .statusCode(200)
            .body("authorizationUrl", containsString("logonvalidation.net/authorize"))
            .body("state", equalTo("abc123"));

        ArgumentCaptor<StartSaxoOnboardingUseCase.Command> captor =
                ArgumentCaptor.forClass(StartSaxoOnboardingUseCase.Command.class);
        verify(startSaxoOnboardingUseCase).start(captor.capture());
        StartSaxoOnboardingUseCase.Command command = captor.getValue();
        assertEquals(TEST_USER_ID, command.userId());
        assertEquals(BrokerType.SAXO_SIM, command.brokerType());
        assertEquals(SaxoEnvironment.SIM, command.environment());
        assertEquals(EXPECTED_REDIRECT_URI, command.redirectUri());
    }

    @Test
    void shouldDefaultToSimWhenNoBody() {
        OnboardingStart start = new OnboardingStart(
                URI.create("https://sim.logonvalidation.net/authorize?state=def456"), "def456");
        when(startSaxoOnboardingUseCase.start(any())).thenReturn(start);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
        .when()
            .post("/api/v1/broker-accounts/saxo/onboard/start")
        .then()
            .statusCode(200)
            .body("state", equalTo("def456"));
    }

    @Test
    void shouldRejectStartWhenUnauthenticated() {
        given()
            .contentType("application/json")
            .body("{\"environment\": \"SIM\"}")
        .when()
            .post("/api/v1/broker-accounts/saxo/onboard/start")
        .then()
            .statusCode(401);

        verify(startSaxoOnboardingUseCase, never()).start(any());
    }

    @Test
    void shouldRejectLiveEnvironmentOnStart() {
        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"environment\": \"LIVE\"}")
        .when()
            .post("/api/v1/broker-accounts/saxo/onboard/start")
        .then()
            .statusCode(400);

        verify(startSaxoOnboardingUseCase, never()).start(any());
    }

    @Test
    void shouldLinkAccountOnCallback() {
        BrokerAccount account = org.mockito.Mockito.mock(BrokerAccount.class);
        when(account.getName()).thenReturn("Saxo (Sim)");
        when(account.getId()).thenReturn(BrokerAccountId.generate());
        when(completeSaxoOnboardingUseCase.complete(any())).thenReturn(account);

        given()
            .when()
            .get("/api/v1/broker-accounts/saxo/onboard/callback?code=auth-code&state=abc123")
        .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("Saxo (Sim)"))
            .body(containsString("linked"))
            // design markers: success state, brand wordmark, IBM Plex fonts, corrected link target
            .body(containsString("state-success"))
            .body(containsString("Quorum<span class=\"accent\">Trading</span>"))
            .body(containsString("IBM+Plex+Sans"))
            .body(containsString("/settings/broker-accounts"));

        ArgumentCaptor<CompleteSaxoOnboardingUseCase.Command> captor =
                ArgumentCaptor.forClass(CompleteSaxoOnboardingUseCase.Command.class);
        verify(completeSaxoOnboardingUseCase).complete(captor.capture());
        assertEquals("abc123", captor.getValue().state());
        assertEquals("auth-code", captor.getValue().authorizationCode());
    }

    @Test
    void shouldRenderErrorWhenStateRejected() {
        when(completeSaxoOnboardingUseCase.complete(any()))
                .thenThrow(new IllegalArgumentException("OAuth state is unknown, expired, or already consumed"));

        given()
            .when()
            .get("/api/v1/broker-accounts/saxo/onboard/callback?code=auth-code&state=stale")
        .then()
            .statusCode(400)
            .contentType(containsString("text/html"))
            .body(containsString("expired"))
            .body(containsString("state-error"));
    }

    @Test
    void shouldRenderErrorOnConsentDenied() {
        given()
            .queryParam("error", "access_denied")
            .queryParam("error_description", "User declined")
        .when()
            .get("/api/v1/broker-accounts/saxo/onboard/callback")
        .then()
            .statusCode(400)
            .contentType(containsString("text/html"))
            .body(containsString("User declined"))
            .body(containsString("state-denied"));

        verify(completeSaxoOnboardingUseCase, never()).complete(any());
    }

    @Test
    void shouldRenderErrorWhenParamsMissing() {
        given()
            .when()
            .get("/api/v1/broker-accounts/saxo/onboard/callback")
        .then()
            .statusCode(400)
            .contentType(containsString("text/html"));

        verify(completeSaxoOnboardingUseCase, never()).complete(any());
    }
}
