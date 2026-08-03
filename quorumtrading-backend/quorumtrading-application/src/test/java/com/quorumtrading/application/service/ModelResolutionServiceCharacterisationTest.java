package com.quorumtrading.application.service;

import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterisation of the 6-tier model-resolution chain, written before the unification work so
 * later phases show up as intentional diffs rather than accidents.
 *
 * <p>Each test pins exactly one tier by supplying that tier and every lower-precedence one, then
 * asserts the higher tier wins. The last test pins the documented "nothing configured" outcome —
 * {@code null}, not an exception — because callers downstream depend on that shape.</p>
 */
@DisplayName("ModelResolutionService — the 6-tier chain as it behaves today")
class ModelResolutionServiceCharacterisationTest {

    private final ModelResolutionService service = new ModelResolutionService();

    private static final String TIER_1 = "tier1/strategy-agent";
    private static final String TIER_2 = "tier2/strategy-global";
    private static final String TIER_3 = "tier3/user-agent";
    private static final String TIER_4 = "tier4/user-global";
    private static final String TIER_5 = "tier5/system-agent";
    private static final String TIER_6 = "tier6/system-global";

    /** A context carrying every tier from {@code fromTier} downwards, and nothing above it. */
    private static AgentContext contextFrom(int fromTier) {
        StrategyConfig strategy = null;
        if (fromTier <= 2) {
            strategy = new StrategyConfig();
            if (fromTier <= 1) {
                strategy.setLlmAgentModels(Map.of(AgentRole.TECHNICAL, TIER_1));
            }
            strategy.setLlmModel(TIER_2);
        }
        return context(strategy,
                fromTier <= 3 ? Map.of(AgentRole.TECHNICAL, TIER_3) : Map.of(),
                fromTier <= 4 ? TIER_4 : null,
                fromTier <= 5 ? Map.of(AgentRole.TECHNICAL, TIER_5) : Map.of(),
                fromTier <= 6 ? TIER_6 : null);
    }

    private static AgentContext context(StrategyConfig strategy,
                                        Map<AgentRole, String> userAgentModels,
                                        String userGlobal,
                                        Map<AgentRole, String> systemAgentDefaults,
                                        String systemGlobal) {
        return new AgentContext(
                null, "AAPL", null, null, null, null, null, strategy, null,
                userGlobal, userAgentModels, null,
                systemGlobal, systemAgentDefaults,
                null, null, null, false, null);
    }

    @Nested
    @DisplayName("precedence")
    class Precedence {

        @Test
        @DisplayName("tier 1 — strategy per-agent beats everything below it")
        void strategyPerAgentWins() {
            assertThat(service.resolveModel(contextFrom(1), AgentRole.TECHNICAL)).isEqualTo(TIER_1);
        }

        @Test
        @DisplayName("tier 2 — strategy global wins when no per-agent strategy pin exists")
        void strategyGlobalWins() {
            assertThat(service.resolveModel(contextFrom(2), AgentRole.TECHNICAL)).isEqualTo(TIER_2);
        }

        @Test
        @DisplayName("tier 3 — user per-agent wins when the strategy pins nothing")
        void userPerAgentWins() {
            assertThat(service.resolveModel(contextFrom(3), AgentRole.TECHNICAL)).isEqualTo(TIER_3);
        }

        @Test
        @DisplayName("tier 4 — user global wins when the user set no per-agent override")
        void userGlobalWins() {
            assertThat(service.resolveModel(contextFrom(4), AgentRole.TECHNICAL)).isEqualTo(TIER_4);
        }

        @Test
        @DisplayName("tier 5 — system per-agent wins when the user expressed no preference")
        void systemPerAgentWins() {
            assertThat(service.resolveModel(contextFrom(5), AgentRole.TECHNICAL)).isEqualTo(TIER_5);
        }

        @Test
        @DisplayName("tier 6 — system global is the last resort")
        void systemGlobalWins() {
            assertThat(service.resolveModel(contextFrom(6), AgentRole.TECHNICAL)).isEqualTo(TIER_6);
        }

        @Test
        @DisplayName("nothing configured at any tier resolves to null rather than throwing")
        void nothingResolvesToNull() {
            assertThat(service.resolveModel(contextFrom(7), AgentRole.TECHNICAL)).isNull();
        }
    }

    @Nested
    @DisplayName("blank handling")
    class BlankHandling {

        @Test
        @DisplayName("a blank strategy pin is skipped, not treated as a choice")
        void blankStrategyPinFallsThrough() {
            StrategyConfig strategy = new StrategyConfig();
            strategy.setLlmAgentModels(Map.of(AgentRole.TECHNICAL, "   "));
            strategy.setLlmModel("");
            AgentContext context = context(strategy, Map.of(), null, Map.of(), TIER_6);

            assertThat(service.resolveModel(context, AgentRole.TECHNICAL)).isEqualTo(TIER_6);
        }

        @Test
        @DisplayName("a per-agent pin for another role does not leak across roles")
        void perAgentPinIsRoleScoped() {
            StrategyConfig strategy = new StrategyConfig();
            strategy.setLlmAgentModels(Map.of(AgentRole.TECHNICAL, TIER_1));
            AgentContext context = context(strategy, Map.of(), null, Map.of(), TIER_6);

            assertThat(service.resolveModel(context, AgentRole.SENTIMENT)).isEqualTo(TIER_6);
        }
    }

    @Nested
    @DisplayName("fallback suppression")
    class FallbackSuppression {

        @Test
        @DisplayName("a strategy pin — global or per-agent — disables the fallback chain")
        void strategyPinDisablesFallback() {
            StrategyConfig global = new StrategyConfig();
            global.setLlmModel(TIER_2);
            assertThat(service.shouldDisableFallback(context(global, Map.of(), null, Map.of(), null),
                    AgentRole.TECHNICAL)).isTrue();

            StrategyConfig perAgent = new StrategyConfig();
            perAgent.setLlmAgentModels(Map.of(AgentRole.TECHNICAL, TIER_1));
            assertThat(service.shouldDisableFallback(context(perAgent, Map.of(), null, Map.of(), null),
                    AgentRole.TECHNICAL)).isTrue();
        }

        @Test
        @DisplayName("a user preference does NOT disable the fallback chain today")
        void userPreferenceDoesNotDisableFallback() {
            AgentContext context = context(null, Map.of(AgentRole.TECHNICAL, TIER_3), TIER_4, Map.of(), TIER_6);

            assertThat(service.shouldDisableFallback(context, AgentRole.TECHNICAL)).isFalse();
        }

        @Test
        @DisplayName("no strategy at all leaves the fallback chain enabled")
        void noStrategyLeavesFallbackEnabled() {
            assertThat(service.shouldDisableFallback(contextFrom(6), AgentRole.TECHNICAL)).isFalse();
        }
    }
}
