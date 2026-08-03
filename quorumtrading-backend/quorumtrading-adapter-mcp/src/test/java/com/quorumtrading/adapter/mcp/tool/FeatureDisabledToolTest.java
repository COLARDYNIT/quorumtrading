package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.EvaluateFeatureUseCase;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * What a gated MCP tool does when its feature is switched off.
 *
 * <p>Hiding the tool from {@code tools/list} is the first line, but it is only a courtesy: a client
 * holding a cached tool list, or one that was connected before the flag changed, will still call
 * it. These are the tests for what happens then — and specifically that the refusal <em>names the
 * flag</em>. An agent told only "not available" retries, rephrases, or invents an explanation for
 * the user; told which toggle to flip, it can relay something actionable and stop.</p>
 */
@QuarkusTest
@DisplayName("a tool whose feature flag is off")
class FeatureDisabledToolTest {

    @Inject ChallengeTools challengeTools;
    @Inject MarketResearchTools marketResearchTools;

    @InjectMock EvaluateFeatureUseCase evaluateFeature;
    @InjectMock McpUserContext userContext;

    @BeforeEach
    void everyFeatureIsOff() {
        when(evaluateFeature.isEnabledForUser(any(), any())).thenReturn(false);
        when(userContext.getUserId()).thenReturn(UserId.generate());
    }

    private static String textOf(ToolResponse response) {
        assertThat(response.isError()).as("a refusal must be reported as an error").isTrue();
        return response.firstContent().asText().text();
    }

    private static void assertRefusesNaming(ToolResponse response, Feature feature) {
        assertThat(textOf(response))
            .as("the refusal must name the flag an administrator has to switch on")
            .contains(feature.flagName())
            .contains("Admin");
    }

    @Nested
    @DisplayName("challenges")
    class Challenges {

        @Test
        @DisplayName("list_challenges refuses")
        void listRefuses() {
            assertRefusesNaming(
                challengeTools.listChallenges(Optional.empty(), Optional.empty()),
                Feature.CHALLENGES);
        }

        @Test
        @DisplayName("get_challenge refuses before it even parses its argument")
        void getRefuses() {
            // Deliberately a malformed id: the guard must run first, so the caller gets the flag
            // name rather than a UUID parse error that sends them debugging the wrong thing.
            assertRefusesNaming(
                challengeTools.getChallenge("not-a-uuid"),
                Feature.CHALLENGES);
        }

        @Test
        @DisplayName("join_challenge refuses without creating anything")
        void joinRefuses() {
            assertRefusesNaming(
                challengeTools.joinChallenge(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                Feature.CHALLENGES);
        }
    }

    @Nested
    @DisplayName("market research")
    class MarketResearch {

        @Test
        @DisplayName("list_market_research refuses")
        void listRefuses() {
            assertRefusesNaming(
                marketResearchTools.listMarketResearch(),
                Feature.MARKET_RESEARCH);
        }

        @Test
        @DisplayName("get_market_research_details refuses")
        void detailsRefuses() {
            assertRefusesNaming(
                marketResearchTools.getMarketResearchDetails(
                    "not-a-uuid", Optional.empty(), Optional.empty(), Optional.empty()),
                Feature.MARKET_RESEARCH);
        }

        @Test
        @DisplayName("pause_market_research refuses")
        void pauseRefuses() {
            assertRefusesNaming(
                marketResearchTools.pauseMarketResearch("not-a-uuid"),
                Feature.MARKET_RESEARCH);
        }

        @Test
        @DisplayName("resume_market_research refuses")
        void resumeRefuses() {
            assertRefusesNaming(
                marketResearchTools.resumeMarketResearch("not-a-uuid"),
                Feature.MARKET_RESEARCH);
        }

        @Test
        @DisplayName("delete_market_research refuses")
        void deleteRefuses() {
            assertRefusesNaming(
                marketResearchTools.deleteMarketResearch("not-a-uuid"),
                Feature.MARKET_RESEARCH);
        }

        @Test
        @DisplayName("execute_market_research refuses")
        void executeRefuses() {
            assertRefusesNaming(
                marketResearchTools.executeMarketResearch("not-a-uuid"),
                Feature.MARKET_RESEARCH);
        }
    }

    @Test
    @DisplayName("a caller with no usable token is refused for the flag, not for authentication")
    void unauthenticatedCallerGetsTheFlagReason() {
        // An unresolvable token must not turn a disabled-feature refusal into an auth error —
        // that would send the user hunting for a credential problem they do not have.
        when(userContext.getUserId()).thenThrow(new SecurityException("no token"));

        assertRefusesNaming(
            challengeTools.listChallenges(Optional.empty(), Optional.empty()),
            Feature.CHALLENGES);
    }
}
