package com.quorumtrading.model.orchestration;

import com.quorumtrading.model.enums.ConsensusMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * An unrecognised {@code consensus_method} is rejected rather than coerced.
 *
 * <p>It used to yield {@code MAJORITY}, which is not the default for an absent key — that is
 * {@code ORCHESTRATOR_OVERRIDE}. So one transposed letter silently swapped the decision system:
 * no orchestrator LLM call, no override of agent votes, a different price per evaluation. The read
 * APIs made it undetectable by echoing the raw frontmatter value back, so a strategy could report
 * {@code consensusMethod: ORCHESTRATOR_OVERIDE} while running pure majority voting.</p>
 */
class StrategyConfigParserConsensusMethodTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    private static final String TEMPLATE = """
            ---
            stock_tickers:
              - AAPL
            live_schedule:
              evaluation_times: ["10:00"]
            %s
            ---
            Buy when RSI is low.
            """;

    private String strategyWith(String yaml) {
        return TEMPLATE.formatted(yaml);
    }

    @Test
    @DisplayName("an absent consensus_method still defaults to ORCHESTRATOR_OVERRIDE")
    void shouldDefaultToOrchestratorOverrideWhenAbsent() {
        StrategyConfig config = parser.parse(strategyWith(""));

        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.ORCHESTRATOR_OVERRIDE);
    }

    @Test
    @DisplayName("both real values parse")
    void shouldParseBothSupportedValues() {
        assertThat(parser.parse(strategyWith("consensus_method: MAJORITY")).getConsensusMethod())
                .isEqualTo(ConsensusMethod.MAJORITY);
        assertThat(parser.parse(strategyWith("consensus_method: ORCHESTRATOR_OVERRIDE")).getConsensusMethod())
                .isEqualTo(ConsensusMethod.ORCHESTRATOR_OVERRIDE);
    }

    @Test
    @DisplayName("lowercase is still accepted")
    void shouldAcceptLowercase() {
        assertThat(parser.parse(strategyWith("consensus_method: majority")).getConsensusMethod())
                .isEqualTo(ConsensusMethod.MAJORITY);
    }

    @Test
    @DisplayName("documented aliases still map to MAJORITY")
    void shouldStillAcceptDocumentedAliases() {
        for (String alias : new String[] { "MAJORITY_VOTE", "WEIGHTED", "WEIGHTED_AVERAGE", "CONFIDENCE_BASED" }) {
            assertThat(parser.parse(strategyWith("consensus_method: " + alias)).getConsensusMethod())
                    .as("alias %s", alias)
                    .isEqualTo(ConsensusMethod.MAJORITY);
        }
    }

    @Test
    @DisplayName("a typo is rejected instead of silently becoming MAJORITY")
    void shouldRejectTypo() {
        assertThatThrownBy(() -> parser.parse(strategyWith("consensus_method: ORCHESTRATOR_OVERIDE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown consensus_method 'ORCHESTRATOR_OVERIDE'")
                .hasMessageContaining("MAJORITY")
                .hasMessageContaining("ORCHESTRATOR_OVERRIDE");
    }

    @Test
    @DisplayName("outright nonsense is rejected")
    void shouldRejectNonsense() {
        assertThatThrownBy(() -> parser.parse(strategyWith("consensus_method: TELEPATHY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown consensus_method 'TELEPATHY'");
    }

    @Test
    @DisplayName("a blank value names itself rather than reporting an unknown ''")
    void shouldRejectBlankWithItsOwnMessage() {
        assertThatThrownBy(() -> parser.parse(strategyWith("consensus_method: \"\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consensus_method is present but blank");
    }
}
