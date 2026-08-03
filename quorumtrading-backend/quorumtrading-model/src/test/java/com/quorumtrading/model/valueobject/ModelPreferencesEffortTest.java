package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.enums.AgentRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Effort as a second dimension of a model preference, kept out of the model id.
 *
 * <p>A compound id like {@code sonnet:high} would make {@link ModelId} stop being a model id and
 * would collide with the {@code :online} web-search decoration, which already uses that syntax for
 * something else. The two dimensions therefore live in two maps keyed the same way, and effort
 * follows the model's own precedence because it is part of the same choice.</p>
 */
@DisplayName("ModelPreferences — model and effort, two dimensions of one choice")
class ModelPreferencesEffortTest {

    private static final TradingContext CONTEXT = TradingContext.BACKTESTING;

    @ParameterizedTest
    @EnumSource(ModelEffort.class)
    @DisplayName("every effort level round-trips through its flag value")
    void effortRoundTrips(ModelEffort effort) {
        assertThat(ModelEffort.fromValue(effort.flagValue())).contains(effort);
        assertThat(ModelEffort.fromValue(effort.name())).contains(effort);
        assertThat(effort.flagValue()).isEqualTo(effort.name().toLowerCase(java.util.Locale.ROOT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "extreme", "HIGHER"})
    @DisplayName("an unknown effort level is empty rather than a guess")
    void unknownEffortIsEmpty(String value) {
        assertThat(ModelEffort.fromValue(value)).isEmpty();
    }

    @Test
    @DisplayName("null effort is empty")
    void nullEffortIsEmpty() {
        assertThat(ModelEffort.fromValue(null)).isEmpty();
    }

    @Test
    @DisplayName("an effort attaches to a key without touching the model id")
    void effortAttachesWithoutTouchingTheModel() {
        ModelPreferenceKey key = ModelPreferenceKey.global(CONTEXT);
        ModelPreferences prefs = ModelPreferences.of(Map.of(key, ModelId.of("sonnet")))
                .withEffort(key, ModelEffort.HIGH);

        assertThat(prefs.getFor(CONTEXT)).map(ModelId::value)
                .as("the model id stays pure")
                .contains("sonnet");
        assertThat(prefs.effortFor(key)).contains(ModelEffort.HIGH);
    }

    @Test
    @DisplayName("a null effort clears it")
    void nullEffortClears() {
        ModelPreferenceKey key = ModelPreferenceKey.global(CONTEXT);
        ModelPreferences prefs = ModelPreferences.EMPTY
                .withEffort(key, ModelEffort.MAX)
                .withEffort(key, null);

        assertThat(prefs.effortFor(key)).isEmpty();
    }

    @Test
    @DisplayName("effort follows the model's precedence: per-agent beats context-global")
    void effortFollowsModelPrecedence() {
        ModelPreferences prefs = ModelPreferences.EMPTY
                .withEffort(ModelPreferenceKey.global(CONTEXT), ModelEffort.LOW)
                .withEffort(ModelPreferenceKey.forAgent(CONTEXT, AgentKind.TECHNICAL), ModelEffort.MAX);

        assertThat(prefs.resolveEffortForAgent(CONTEXT, AgentKind.TECHNICAL)).contains(ModelEffort.MAX);
        assertThat(prefs.resolveEffortForAgent(CONTEXT, AgentKind.SENTIMENT)).contains(ModelEffort.LOW);
    }

    @Test
    @DisplayName("no effort anywhere is empty, so an engine omits the flag rather than inventing a level")
    void noEffortIsEmpty() {
        assertThat(ModelPreferences.EMPTY.resolveEffortForAgent(CONTEXT, AgentKind.TECHNICAL)).isEmpty();
    }

    @Test
    @DisplayName("preferences with only an effort are not 'empty'")
    void effortAloneIsNotEmpty() {
        assertThat(ModelPreferences.EMPTY.isEmpty()).isTrue();
        assertThat(ModelPreferences.EMPTY.withEffort(ModelPreferenceKey.global(CONTEXT), ModelEffort.LOW).isEmpty())
                .isFalse();
    }

    @Test
    @DisplayName("setting a model preserves any effort already attached")
    void settingAModelPreservesEffort() {
        ModelPreferenceKey key = ModelPreferenceKey.global(CONTEXT);
        ModelPreferences prefs = ModelPreferences.EMPTY
                .withEffort(key, ModelEffort.HIGH)
                .withPreference(CONTEXT, ModelId.of("sonnet"));

        assertThat(prefs.effortFor(key)).contains(ModelEffort.HIGH);
        assertThat(prefs.getFor(CONTEXT)).map(ModelId::value).contains("sonnet");
    }

    @Test
    @DisplayName("agent overrides are readable through both vocabularies")
    void agentOverridesReadableBothWays() {
        UserBudget budget = UserBudget.createUnlimited(new UserId(UUID.randomUUID()));
        budget.setAgentModelPreference(CONTEXT, AgentKind.TECHNICAL, ModelId.of("sonnet"));
        budget.setAgentModelPreference(TradingContext.RESEARCH, AgentKind.STRATEGY_DESIGNER, ModelId.of("opus"));

        ModelPreferences prefs = budget.getModelPreferences();

        assertThat(prefs.getAgentOverrides(CONTEXT))
                .containsExactly(Map.entry(AgentRole.TECHNICAL, ModelId.of("sonnet")));
        assertThat(prefs.getAllAgentOverrides(TradingContext.RESEARCH))
                .containsExactly(Map.entry(AgentKind.STRATEGY_DESIGNER, ModelId.of("opus")));
        assertThat(prefs.hasAnyAgentOverrides()).isTrue();
    }

    @Test
    @DisplayName("clearing an agent preference removes it, for either vocabulary")
    void clearingAnAgentPreference() {
        UserBudget budget = UserBudget.createUnlimited(new UserId(UUID.randomUUID()));
        budget.setAgentModelPreference(CONTEXT, AgentKind.TECHNICAL, ModelId.of("sonnet"));
        budget.clearAgentModelPreference(CONTEXT, AgentKind.TECHNICAL);
        assertThat(budget.getModelPreferences().getForAgent(CONTEXT, AgentKind.TECHNICAL)).isEmpty();

        budget.setAgentModelPreference(CONTEXT, AgentRole.SENTIMENT, ModelId.of("opus"));
        budget.clearAgentModelPreference(CONTEXT, AgentRole.SENTIMENT);
        assertThat(budget.getModelPreferences().getForAgent(CONTEXT, AgentRole.SENTIMENT)).isEmpty();
    }

    @Test
    @DisplayName("grouped overrides key by agent name, spanning both vocabularies")
    void groupedOverridesUseAgentNames() {
        ModelPreferences prefs = ModelPreferences.EMPTY
                .withAgentPreference(CONTEXT, AgentKind.TECHNICAL, ModelId.of("sonnet"))
                .withAgentPreference(TradingContext.RESEARCH, AgentKind.VIABILITY_ASSESSOR, ModelId.of("opus"));

        assertThat(prefs.getAllAgentOverridesGrouped())
                .containsKeys("BACKTESTING", "RESEARCH");
        assertThat(prefs.getAllAgentOverridesGrouped().get("RESEARCH"))
                .containsKey("VIABILITY_ASSESSOR");
    }

    @Test
    @DisplayName("resolveForAgent falls back from the agent to the context")
    void resolveForAgentFallsBackToContext() {
        ModelPreferences prefs = ModelPreferences.ofContextMap(Map.of(CONTEXT, ModelId.of("sonnet")));

        assertThat(prefs.resolveForAgent(CONTEXT, AgentKind.TECHNICAL)).map(ModelId::value).contains("sonnet");
        assertThat(prefs.hasPreferenceFor(CONTEXT)).isTrue();
        assertThat(prefs.withoutPreference(CONTEXT).hasPreferenceFor(CONTEXT)).isFalse();
    }
}
