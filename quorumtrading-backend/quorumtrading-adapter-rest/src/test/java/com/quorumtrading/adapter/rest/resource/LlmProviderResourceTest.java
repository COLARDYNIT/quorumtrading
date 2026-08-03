package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus;
import com.quorumtrading.application.usecase.llm.LlmProviderStatus.ProviderAvailability;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.model.enums.LlmProvider;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REST edge tests for {@link LlmProviderResource} with the selection feature ENABLED
 * (self-host edition). The disabled/404 path is covered by {@link LlmProviderResourceDisabledTest}.
 */
@QuarkusTest
@TestProfile(LlmProviderResourceTest.SelectionEnabledProfile.class)
// Selecting the engine and submitting an API key are deployment-wide writes and now require a
// caller. The read-only status/setup endpoints stay anonymous, and the feature flag is still
// checked first so a disabled deployment 404s rather than revealing the endpoint.
@io.quarkus.test.security.TestSecurity(user = "llm-admin@quorumtrading.local")
class LlmProviderResourceTest {

    public static class SelectionEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quorumtrading.llm.selection.enabled", "true");
        }
    }

    @InjectMock
    com.quorumtrading.application.usecase.ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    @org.junit.jupiter.api.BeforeEach
    void stubCaller() {
        org.mockito.Mockito.lenient()
            .when(resolveCurrentUserUseCase.execute("llm-admin@quorumtrading.local"))
            .thenReturn(new com.quorumtrading.model.valueobject.UserId(java.util.UUID.randomUUID()));
    }

    @InjectMock
    GetLlmProviderStatusUseCase getStatusUseCase;

    @InjectMock
    SelectLlmProviderUseCase selectUseCase;

    private static LlmProviderStatus unselected(boolean localAvailable, boolean openRouterAvailable) {
        return new LlmProviderStatus(null, null, List.of(
                new ProviderAvailability(LlmProvider.LOCAL, localAvailable),
                new ProviderAvailability(LlmProvider.OPENROUTER, openRouterAvailable)));
    }

    private static LlmProviderStatus selected(LlmProvider provider) {
        return new LlmProviderStatus(provider, Instant.parse("2026-06-10T08:00:00Z"), List.of(
                new ProviderAvailability(LlmProvider.LOCAL, true),
                new ProviderAvailability(LlmProvider.OPENROUTER, true)));
    }

    // --- JSON ---------------------------------------------------------------------------------

    @Test
    void statusReportsProvidersWithAvailability() {
        when(getStatusUseCase.get()).thenReturn(unselected(true, false));

        given()
            .when().get("/api/v1/llm/providers")
            .then()
            .statusCode(200)
            .body("selected", nullValue())
            .body("providers[0].id", equalTo("LOCAL"))
            .body("providers[0].available", equalTo(true))
            .body("providers[1].id", equalTo("OPENROUTER"))
            .body("providers[1].available", equalTo(false));
    }

    @Test
    void selectActivatesProvider() {
        when(selectUseCase.select(any())).thenReturn(selected(LlmProvider.LOCAL));

        given()
            .contentType("application/json")
            .body("{\"provider\":\"LOCAL\"}")
            .when().post("/api/v1/llm/providers/select")
            .then()
            .statusCode(200)
            .body("selected", equalTo("LOCAL"))
            .body("providers[0].selected", equalTo(true));

        ArgumentCaptor<SelectLlmProviderUseCase.Command> captor =
                ArgumentCaptor.forClass(SelectLlmProviderUseCase.Command.class);
        verify(selectUseCase).select(captor.capture());
        assertEquals(LlmProvider.LOCAL, captor.getValue().provider());
    }

    @Test
    void selectPassesApiKeyThrough() {
        when(selectUseCase.select(any())).thenReturn(selected(LlmProvider.OPENROUTER));

        given()
            .contentType("application/json")
            .body("{\"provider\":\"OPENROUTER\",\"apiKey\":\"sk-or-v1-abc\"}")
            .when().post("/api/v1/llm/providers/select")
            .then()
            .statusCode(200);

        ArgumentCaptor<SelectLlmProviderUseCase.Command> captor =
                ArgumentCaptor.forClass(SelectLlmProviderUseCase.Command.class);
        verify(selectUseCase).select(captor.capture());
        assertEquals("sk-or-v1-abc", captor.getValue().openRouterApiKey());
    }

    @Test
    void selectRejectsUnknownProvider() {
        given()
            .contentType("application/json")
            .body("{\"provider\":\"SKYNET\"}")
            .when().post("/api/v1/llm/providers/select")
            .then()
            .statusCode(400);
    }

    @Test
    void selectSurfacesUnavailabilityAs400() {
        when(selectUseCase.select(any()))
                .thenThrow(new IllegalArgumentException("The local provider is not available"));

        given()
            .contentType("application/json")
            .body("{\"provider\":\"LOCAL\"}")
            .when().post("/api/v1/llm/providers/select")
            .then()
            .statusCode(400);
    }

    // --- HTML setup page (claude.ai/design "LLM Provider Setup.html") ----------------------------

    @Test
    void setupPageRendersChooseStateWithBadges() {
        when(getStatusUseCase.get()).thenReturn(unselected(true, false));

        given()
            .when().get("/api/v1/llm/setup")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            // design markers
            .body(containsString("setup-card state-choose"))
            .body(containsString("Quorum<span class=\"accent\">Trading</span>"))
            .body(containsString("IBM+Plex+Sans"))
            .body(containsString("Choose your LLM provider"))
            // tile availability classes + badges + reason lines
            .body(containsString("opt opt-available"))
            .body(containsString("claude CLI detected"))
            .body(containsString("opt opt-needskey"))
            .body(containsString("Needs key"))
            .body(containsString("name=\"apiKey\""))
            // forms post to the design's action with lowercase provider values
            .body(containsString("action=\"/api/v1/llm/providers/select\""))
            .body(containsString("value=\"local\""))
            .body(containsString("value=\"openrouter\""));
    }

    @Test
    void setupPageRendersNoneStateWhenNothingAvailable() {
        when(getStatusUseCase.get()).thenReturn(unselected(false, false));

        given()
            .when().get("/api/v1/llm/setup")
            .then()
            .statusCode(200)
            .body(containsString("setup-card state-none"))
            .body(containsString("No LLM provider is available yet"))
            .body(containsString("claude not found on PATH"))
            .body(containsString("opt opt-unavailable"))
            // the OpenRouter key form stays actionable even in the none state
            .body(containsString("name=\"apiKey\""));
    }

    @Test
    void setupPageMarksActiveProvider() {
        when(getStatusUseCase.get()).thenReturn(selected(LlmProvider.LOCAL));

        given()
            .when().get("/api/v1/llm/setup")
            .then()
            .statusCode(200)
            .body(containsString("opt-active"))
            .body(containsString("Currently active"));
    }

    @Test
    void formPostWithLowercaseProviderConfirmsSelection() {
        when(selectUseCase.select(any())).thenReturn(selected(LlmProvider.LOCAL));

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("provider", "local")
            .when().post("/api/v1/llm/providers/select")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("setup-card state-confirmed"))
            .body(containsString("is now active"))
            .body(containsString("Change provider"));

        ArgumentCaptor<SelectLlmProviderUseCase.Command> captor =
                ArgumentCaptor.forClass(SelectLlmProviderUseCase.Command.class);
        verify(selectUseCase).select(captor.capture());
        assertEquals(LlmProvider.LOCAL, captor.getValue().provider());
    }

    @Test
    void formPostRendersErrorBackOnChoosePage() {
        when(selectUseCase.select(any()))
                .thenThrow(new IllegalArgumentException("OpenRouter is not available: no usable API key"));
        when(getStatusUseCase.get()).thenReturn(unselected(true, false));

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("provider", "openrouter")
            .when().post("/api/v1/llm/providers/select")
            .then()
            .statusCode(400)
            .body(containsString("setup-card state-choose"))
            .body(containsString("no usable API key"))
            .body(not(containsString("setup-card state-confirmed")));
    }
}
