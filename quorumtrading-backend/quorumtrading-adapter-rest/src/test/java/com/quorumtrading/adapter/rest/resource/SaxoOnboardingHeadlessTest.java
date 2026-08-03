package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The self-host (local) edition runs headless — there is no web UI, so the account-linking result
 * page must not link to {@code /settings/broker-accounts} (it would 404). With
 * {@code quorumtrading.frontend.enabled=false} the success page directs the user back to their agent.
 */
@QuarkusTest
@TestProfile(SaxoOnboardingHeadlessTest.HeadlessProfile.class)
class SaxoOnboardingHeadlessTest {

    public static class HeadlessProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quorumtrading.frontend.enabled", "false",
                "quorumtrading.broker.saxo.enabled", "true");
        }
    }

    @InjectMock
    CompleteSaxoOnboardingUseCase completeSaxoOnboardingUseCase;

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String TEST_EMAIL = "test@quorumtrading.local";

    @Test
    void successPageOmitsWebAppLinkWhenHeadless() {
        BrokerAccount account = org.mockito.Mockito.mock(BrokerAccount.class);
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(UserId.generate());
        when(account.getName()).thenReturn("Saxo (Sim)");
        when(account.getId()).thenReturn(BrokerAccountId.generate());
        when(completeSaxoOnboardingUseCase.complete(any())).thenReturn(account);

        given()
            .when()
            .get("/api/v1/broker-accounts/saxo/onboard/callback?code=auth-code&state=abc123")
        .then()
            .statusCode(200)
            .body(containsString("state-success"))
            // Headless: agent-centric footer copy, no dead link into a non-existent web app.
            .body(containsString("Ask your agent to unlink it"))
            .body(not(containsString("/settings/broker-accounts")));
    }
}
