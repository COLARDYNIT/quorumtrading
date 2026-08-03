package com.quorumtrading.model.orchestration;

import com.quorumtrading.model.enums.AgentRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StrategyConfigParserAgentsTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    private static final String MINIMAL_STRATEGY_TEMPLATE = """
            ---
            stock_tickers:
              - AAPL
            live_schedule:
              evaluation_times: ["10:00"]
            %s
            ---
            Buy when RSI is low.
            """;

    private String strategyWith(String agentsYaml) {
        return MINIMAL_STRATEGY_TEMPLATE.formatted(agentsYaml);
    }

    @Test
    void shouldParseAgentsAsPlainStrings() {
        String markdown = strategyWith("""
                agents:
                  - TECHNICAL
                  - FUNDAMENTAL""");

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getEnabledAgents())
                .containsExactlyInAnyOrder(AgentRole.TECHNICAL, AgentRole.FUNDAMENTAL);
    }

    @Test
    void shouldParseAgentsAsMapWithTypeKey() {
        String markdown = strategyWith("""
                agents:
                  - TYPE: TECHNICAL
                    WEIGHT: 0.3
                  - TYPE: FUNDAMENTAL
                    WEIGHT: 0.35""");

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getEnabledAgents())
                .containsExactlyInAnyOrder(AgentRole.TECHNICAL, AgentRole.FUNDAMENTAL);
    }

    @Test
    void shouldParseAgentsAsMapWithLowercaseTypeKey() {
        String markdown = strategyWith("""
                agents:
                  - type: SENTIMENT
                  - type: FUNDAMENTAL""");

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getEnabledAgents())
                .containsExactlyInAnyOrder(AgentRole.SENTIMENT, AgentRole.FUNDAMENTAL);
    }

    @Test
    void shouldRejectMapEntryWithoutTypeKey() {
        String markdown = strategyWith("""
                agents:
                  - WEIGHT: 0.3
                    NAME: technical""");

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have a 'TYPE' key");
    }

    @Test
    void shouldSkipOrchestratorInMapFormat() {
        String markdown = strategyWith("""
                agents:
                  - TYPE: ORCHESTRATOR""");

        // ORCHESTRATOR-only list should be silently skipped, falling back to defaults (null)
        StrategyConfig config = parser.parse(markdown);
        assertThat(config.getEnabledAgents()).isNull();
    }

    // =====================================================================
    // Unknown roles are rejected, not skipped.
    //
    // Skipping them meant a typo vanished, and a typo in the ONLY entry emptied the list, which
    // getEffectiveEnabledAgents() reads as "use all three" — so a strategy asking for one cheap
    // agent silently ran three and billed three times per evaluation.
    // =====================================================================

    @Test
    void shouldRejectUnknownAgentRole() {
        String markdown = strategyWith("""
                agents:
                  - TECHNICAL
                  - NONSENSE_AGENT""");

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent role 'NONSENSE_AGENT'")
                .hasMessageContaining("TECHNICAL");
    }

    @Test
    void shouldRejectTypoInTheOnlyAgentRatherThanRunningAllThree() {
        String markdown = strategyWith("""
                agents:
                  - TECHNCIAL""");

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent role 'TECHNCIAL'");
    }

    @Test
    void shouldRejectUnknownAgentRoleInMapFormat() {
        String markdown = strategyWith("""
                agents:
                  - TYPE: PSYCHIC
                    WEIGHT: 0.5""");

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown agent role 'PSYCHIC'");
    }

    @Test
    void shouldNotSuggestOrchestratorAsAValidRole() {
        String markdown = strategyWith("""
                agents:
                  - BOGUS""");

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORCHESTRATOR is not listed");
    }
}
