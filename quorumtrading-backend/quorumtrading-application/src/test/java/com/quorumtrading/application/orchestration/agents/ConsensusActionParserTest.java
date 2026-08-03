package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.model.enums.ConsensusActionType;
import com.quorumtrading.model.valueobject.ConsensusAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConsensusActionParser} — the single boundary where LLM-originated
 * consensus action text becomes typed. The input matrix mirrors the characterization
 * tests of the three historical parsers it replaces (see
 * {@code ExecuteConsensusDecisionActionParsingTest}, {@code ExtractActionLabelTest},
 * {@code ReachConsensusParsersCharacterizationTest}).
 */
class ConsensusActionParserTest {

    // ---------- null / blank ----------

    @Test
    void nullInputIsUnknownWithNullRawAndToken() {
        ConsensusAction action = ConsensusActionParser.parse(null);

        assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
        assertThat(action.actionToken()).isNull();
        assertThat(action.raw()).isNull();
    }

    @Test
    void blankInputIsUnknownPreservingRaw() {
        ConsensusAction action = ConsensusActionParser.parse("   ");

        assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
        assertThat(action.actionToken()).isNull();
        assertThat(action.raw()).isEqualTo("   ");
    }

    // ---------- plain tokens ----------

    @Nested
    @DisplayName("plain tokens (incl. legacy V3-era scalar rows)")
    class PlainTokens {

        @ParameterizedTest
        @ValueSource(strings = {"BUY", "buy", " Buy "})
        void buyTokensResolveCaseInsensitively(String input) {
            ConsensusAction action = ConsensusActionParser.parse(input);

            assertThat(action.type()).isEqualTo(ConsensusActionType.BUY);
            assertThat(action.actionToken()).isEqualTo(input.trim());
            assertThat(action.raw()).isEqualTo(input); // raw preserved unmodified
        }

        @Test
        void allFourActionsResolve() {
            assertThat(ConsensusActionParser.parse("SELL").type()).isEqualTo(ConsensusActionType.SELL);
            assertThat(ConsensusActionParser.parse("HOLD").type()).isEqualTo(ConsensusActionType.HOLD);
            assertThat(ConsensusActionParser.parse("SKIP").type()).isEqualTo(ConsensusActionType.SKIP);
        }

        @Test
        void unresolvablePlainTokenIsUnknownWithNullToken() {
            // Token null → the money path falls back to the whole raw string in its
            // failure message, exactly like the historical parser.
            ConsensusAction action = ConsensusActionParser.parse("FOO");

            assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
            assertThat(action.actionToken()).isNull();
            assertThat(action.raw()).isEqualTo("FOO");
        }
    }

    // ---------- valid JSON ----------

    @Nested
    @DisplayName("valid JSON payloads")
    class ValidJson {

        @Test
        void fullW1ShapeExtractsAllFields() {
            String json = "{\"action\":\"HOLD\",\"symbol\":\"AAPL\",\"quantity\":0.0,\"confidence\":0.5,\"reasoning\":\"wait\"}";
            ConsensusAction action = ConsensusActionParser.parse(json);

            assertThat(action.type()).isEqualTo(ConsensusActionType.HOLD);
            assertThat(action.actionToken()).isEqualTo("HOLD");
            assertThat(action.symbol()).isEqualTo("AAPL");
            assertThat(action.quantity()).isEqualByComparingTo("0.0");
            assertThat(action.confidence()).isEqualByComparingTo("0.5");
            assertThat(action.reasoning()).isEqualTo("wait");
            assertThat(action.raw()).isEqualTo(json);
        }

        @Test
        void lowercaseActionResolves() {
            assertThat(ConsensusActionParser.parse("{\"action\":\"buy\"}").type())
                .isEqualTo(ConsensusActionType.BUY);
        }

        @Test
        void legacyReasonKeyPopulatesReasoning() {
            // W3 shape: the no-market-data SKIP literal uses "reason", not "reasoning".
            ConsensusAction action = ConsensusActionParser.parse(
                "{\"action\": \"SKIP\", \"reason\": \"No market data available\"}");

            assertThat(action.type()).isEqualTo(ConsensusActionType.SKIP);
            assertThat(action.symbol()).isNull();
            assertThat(action.reasoning()).isEqualTo("No market data available");
        }

        @Test
        void missingActionKeyIsUnknownWithNullTokenAndPayloadKept() {
            ConsensusAction action = ConsensusActionParser.parse("{\"symbol\":\"BTC\",\"quantity\":2}");

            assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
            assertThat(action.actionToken()).isNull();
            assertThat(action.symbol()).isEqualTo("BTC");
            assertThat(action.quantity()).isEqualByComparingTo("2");
        }

        @Test
        void unresolvableActionKeepsTokenForFailureMessages() {
            ConsensusAction action = ConsensusActionParser.parse("{\"action\":\"FOO\",\"symbol\":\"BTC\"}");

            assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
            assertThat(action.actionToken()).isEqualTo("FOO");
        }

        @Test
        void numericActionYieldsCleanToken() {
            ConsensusAction action = ConsensusActionParser.parse("{\"action\": 5}");

            assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
            assertThat(action.actionToken()).isEqualTo("5");
        }

        @Test
        void unparseableQuantityIsNullNotFailure() {
            ConsensusAction action = ConsensusActionParser.parse("{\"action\":\"BUY\",\"quantity\":\"lots\"}");

            assertThat(action.type()).isEqualTo(ConsensusActionType.BUY);
            assertThat(action.quantity()).isNull();
        }

        @Test
        void nullJsonFieldsAreNull() {
            ConsensusAction action = ConsensusActionParser.parse(
                "{\"action\":\"BUY\",\"symbol\":null,\"quantity\":null,\"confidence\":null,\"reasoning\":null}");

            assertThat(action.type()).isEqualTo(ConsensusActionType.BUY);
            assertThat(action.symbol()).isNull();
            assertThat(action.quantity()).isNull();
            assertThat(action.confidence()).isNull();
            assertThat(action.reasoning()).isNull();
        }
    }

    // ---------- malformed JSON: the money path's lenient salvage ----------

    @Nested
    @DisplayName("malformed JSON salvage (P1 leniency preserved)")
    class MalformedJsonSalvage {

        @Test
        void unterminatedJsonWithRecognizableActionResolvesWithNoPayload() {
            ConsensusAction action = ConsensusActionParser.parse("{\"action\":\"BUY\"");

            assertThat(action.type()).isEqualTo(ConsensusActionType.BUY);
            assertThat(action.actionToken()).isEqualTo("BUY");
            assertThat(action.symbol()).isNull();
            assertThat(action.quantity()).isNull(); // salvage never yields a payload
        }

        @Test
        void salvageIsCaseInsensitive() {
            assertThat(ConsensusActionParser.parse("{\"action\": \"sell\", broken").type())
                .isEqualTo(ConsensusActionType.SELL);
        }

        @Test
        void salvagedUnresolvableTokenIsPreserved() {
            ConsensusAction action = ConsensusActionParser.parse("{\"action\":\"FOO\"");

            assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
            assertThat(action.actionToken()).isEqualTo("FOO");
        }

        @Test
        void malformedJsonWithoutActionKeyIsUnknownWithNullToken() {
            ConsensusAction action = ConsensusActionParser.parse("{broken json");

            assertThat(action.type()).isEqualTo(ConsensusActionType.UNKNOWN);
            assertThat(action.actionToken()).isNull();
            assertThat(action.raw()).isEqualTo("{broken json");
        }
    }

    // ---------- factories: raw byte-identical to the historical writers ----------

    @Nested
    @DisplayName("typed factories render byte-identical raw JSON")
    class Factories {

        @Test
        void holdFactoryMatchesBuildActionJson() {
            ConsensusAction action = ConsensusActionParser.hold("AAPL", 0.0, 0.5, "Tied vote, defaulting to HOLD");

            assertThat(action.raw()).isEqualTo(AgentResponseParser.buildActionJson(
                "HOLD", "AAPL", 0.0, 0.5, "Tied vote, defaulting to HOLD"));
            assertThat(action.type()).isEqualTo(ConsensusActionType.HOLD);
            assertThat(action.symbol()).isEqualTo("AAPL");
            assertThat(action.confidence()).isEqualByComparingTo("0.5");
        }

        @Test
        void skipFactoryMatchesBuildSkipActionJson() {
            ConsensusAction action = ConsensusActionParser.skip("BTC", "All specialized agents failed to provide analysis");

            assertThat(action.raw()).isEqualTo(AgentResponseParser.buildSkipActionJson(
                "BTC", "All specialized agents failed to provide analysis"));
            assertThat(action.type()).isEqualTo(ConsensusActionType.SKIP);
        }

        @Test
        void skipNoMarketDataIsTheVerbatimLegacyLiteral() {
            ConsensusAction action = ConsensusActionParser.skipNoMarketData();

            assertThat(action.raw()).isEqualTo("{\"action\": \"SKIP\", \"reason\": \"No market data available\"}");
            assertThat(action.type()).isEqualTo(ConsensusActionType.SKIP);
            assertThat(action.reasoning()).isEqualTo("No market data available");
        }

        @Test
        void parseOfBuildActionJsonRoundTripsAllFields() {
            String raw = AgentResponseParser.buildActionJson("BUY", "NVDA", 1.5, 0.9, "momentum");
            ConsensusAction action = ConsensusActionParser.parse(raw);

            assertThat(action.type()).isEqualTo(ConsensusActionType.BUY);
            assertThat(action.actionToken()).isEqualTo("BUY");
            assertThat(action.symbol()).isEqualTo("NVDA");
            assertThat(action.quantity()).isEqualByComparingTo(new BigDecimal("1.5"));
            assertThat(action.confidence()).isEqualByComparingTo(new BigDecimal("0.9"));
            assertThat(action.reasoning()).isEqualTo("momentum");
            assertThat(action.raw()).isEqualTo(raw);
        }
    }
}
