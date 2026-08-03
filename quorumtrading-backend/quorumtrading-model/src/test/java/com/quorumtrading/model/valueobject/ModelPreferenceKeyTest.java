package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.enums.AgentRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The storage-key grammar shared by trading and research preferences.
 *
 * <p>Two properties matter here and both are load-bearing. Keys already written to
 * {@code user_budgets.model_preferences} must keep parsing after the agent half became
 * {@link AgentKind} — no data migration was performed, so the enum's constant names <em>are</em> the
 * compatibility contract. And an unrecognised key must be refused rather than dropped: returning
 * null meant a user's preference silently ceased to exist, on both the read and the write path.</p>
 */
@DisplayName("ModelPreferenceKey — one grammar for trading and research")
class ModelPreferenceKeyTest {

    @Nested
    @DisplayName("stored keys written before AgentKind existed")
    class BackwardCompatibility {

        @ParameterizedTest
        @EnumSource(AgentRole.class)
        @DisplayName("every per-agent key an AgentRole could have produced still round-trips")
        void legacyAgentRoleKeysStillParse(AgentRole role) {
            for (TradingContext context : TradingContext.values()) {
                String legacyKey = context.name() + "_" + role.name();

                ModelPreferenceKey parsed = ModelPreferenceKey.fromStorageKey(legacyKey);

                assertThat(parsed.context()).isEqualTo(context);
                assertThat(parsed.agentKind()).isEqualTo(AgentKind.of(role));
                assertThat(parsed.agentRole()).contains(role);
                assertThat(parsed.toStorageKey())
                        .as("re-serialising must reproduce the byte-identical stored key")
                        .isEqualTo(legacyKey);
            }
        }

        @ParameterizedTest
        @EnumSource(TradingContext.class)
        @DisplayName("global keys are unchanged")
        void legacyGlobalKeysStillParse(TradingContext context) {
            ModelPreferenceKey parsed = ModelPreferenceKey.fromStorageKey(context.name());

            assertThat(parsed.isGlobal()).isTrue();
            assertThat(parsed.toStorageKey()).isEqualTo(context.name());
        }

        @Test
        @DisplayName("a preference written the old way is still readable through the new API")
        void aPreferenceWrittenBeforeTheRefactorIsStillFound() {
            // Exactly what a pre-refactor row holds: a key built from AgentRole.name().
            ModelPreferences prefs = ModelPreferences.of(java.util.Map.of(
                    ModelPreferenceKey.fromStorageKey("BACKTESTING_TECHNICAL"), ModelId.of("some/model")));

            assertThat(prefs.getForAgent(TradingContext.BACKTESTING, AgentRole.TECHNICAL))
                    .contains(ModelId.of("some/model"));
            assertThat(prefs.getForAgent(TradingContext.BACKTESTING, AgentKind.TECHNICAL))
                    .contains(ModelId.of("some/model"));
        }
    }

    @Nested
    @DisplayName("research agents, newly expressible")
    class ResearchKeys {

        @ParameterizedTest
        @ValueSource(strings = {
                "RESEARCH_NEWS_SENTIMENT_ANALYST",
                "RESEARCH_STRATEGY_DESIGNER",
                "RESEARCH_VIABILITY_ASSESSOR"})
        @DisplayName("a research agent parses through the same path as a consensus agent")
        void researchKeysParse(String key) {
            ModelPreferenceKey parsed = ModelPreferenceKey.fromStorageKey(key);

            assertThat(parsed.context()).isEqualTo(TradingContext.RESEARCH);
            assertThat(parsed.agentKind().isResearchAgent()).isTrue();
            assertThat(parsed.agentRole()).isEmpty();
            assertThat(parsed.toStorageKey()).isEqualTo(key);
        }
    }

    @Nested
    @DisplayName("unrecognised keys")
    class Rejection {

        @ParameterizedTest
        @ValueSource(strings = {
                "BACKTESTING_NOT_AN_AGENT",
                "NOT_A_CONTEXT",
                "NOT_A_CONTEXT_TECHNICAL",
                "backtesting_technical"})
        @DisplayName("are refused, not silently dropped — a vanished preference is worse than a rejected one")
        void unrecognisedKeysThrow(String key) {
            assertThatThrownBy(() -> ModelPreferenceKey.fromStorageKey(key))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(key);
        }

        @Test
        @DisplayName("blank and null are refused too")
        void blankIsRefused() {
            assertThatThrownBy(() -> ModelPreferenceKey.fromStorageKey(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ModelPreferenceKey.fromStorageKey("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("AgentKind as a superset")
    class Superset {

        @Test
        @DisplayName("covers every AgentRole and every ResearchAgentType, with matching names")
        void coversBothVocabularies() {
            for (AgentRole role : AgentRole.values()) {
                assertThat(AgentKind.of(role).name()).isEqualTo(role.name());
            }
            for (ResearchAgentType type : ResearchAgentType.values()) {
                assertThat(AgentKind.of(type).name()).isEqualTo(type.name());
            }
            assertThat(AgentKind.values())
                    .hasSize(AgentRole.values().length + ResearchAgentType.values().length);
        }

        @Test
        @DisplayName("a kind belongs to exactly one vocabulary")
        void kindsAreUnambiguous() {
            for (AgentKind kind : AgentKind.values()) {
                assertThat(kind.isConsensusAgent() ^ kind.isResearchAgent())
                        .as("%s must be a consensus agent or a research agent, not both or neither", kind)
                        .isTrue();
            }
        }
    }
}
