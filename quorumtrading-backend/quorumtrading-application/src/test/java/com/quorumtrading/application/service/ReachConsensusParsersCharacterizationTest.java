package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.model.enums.ConsensusActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN TESTS mapping the historical P2/P4 parser inputs (previously
 * {@code ReachConsensusService.extractActionType} / {@code parseQuantityFromJson},
 * both deleted) onto the typed {@link ConsensusActionParser} outputs their consumers
 * now derive.
 *
 * <p>The P2 consumer (cooldown skip-note text) now renders
 * {@code action.type().name()}. Unifications relative to the deleted parser — all
 * note-text only, and unreachable in production because the cooldown query filters
 * rows to BUY/SELL actions:</p>
 * <ul>
 *   <li><b>U1</b> malformed-but-salvageable JSON: was raw-truncated text, now the salvaged
 *       action name (P2 aligns to the money path's leniency).</li>
 *   <li><b>U2</b> garbage / JSON without a resolvable action: was raw text truncated to
 *       20 chars + "...", now {@code UNKNOWN}.</li>
 * </ul>
 *
 * <p>The P4 consumer (majority-vote quantity) now reads {@code action.quantity()} —
 * behavior identical: any failure yields {@code null}.</p>
 */
class ReachConsensusParsersCharacterizationTest {

    private static String actionTypeName(String input) {
        return ConsensusActionParser.parse(input).type().name();
    }

    private static BigDecimal quantity(String input) {
        return ConsensusActionParser.parse(input).quantity();
    }

    @Nested
    @DisplayName("P2 replacement: cooldown note action name")
    class CooldownNoteActionName {

        @Test
        void nullReturnsUnknown() {
            assertThat(actionTypeName(null)).isEqualTo("UNKNOWN");
        }

        @Test
        void blankReturnsUnknown() {
            assertThat(actionTypeName("  ")).isEqualTo("UNKNOWN");
        }

        @Test
        void validJsonReturnsActionValue() {
            assertThat(actionTypeName("{\"action\":\"BUY\",\"symbol\":\"AAPL\"}")).isEqualTo("BUY");
        }

        @Test
        void validJsonWithNonStandardActionIsUnknown() {
            // UNIFICATION U2: was passed through verbatim ("FOO"); now typed UNKNOWN.
            assertThat(actionTypeName("{\"action\":\"FOO\"}")).isEqualTo("UNKNOWN");
        }

        @Test
        void garbageIsUnknown() {
            // UNIFICATION U2: was the raw value (truncated to 20 chars + "..." when long).
            assertThat(actionTypeName("notjson")).isEqualTo("UNKNOWN");
            assertThat(actionTypeName("this is a very long non-json string")).isEqualTo("UNKNOWN");
        }

        @Test
        void unterminatedJsonIsSalvagedLikeTheMoneyPath() {
            // UNIFICATION U1: was the raw malformed text; now the salvaged action.
            assertThat(actionTypeName("{\"action\":\"BUY\"")).isEqualTo("BUY");
        }

        @Test
        void jsonWithoutActionKeyIsUnknown() {
            assertThat(actionTypeName("{\"symbol\":\"AAPL\"}")).isEqualTo("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("P4 replacement: quantity from action JSON (behavior identical)")
    class QuantityFromActionJson {

        @Test
        void quantityExtractedFromValidJson() {
            assertThat(quantity("{\"action\":\"BUY\",\"quantity\":2.5}"))
                .isEqualByComparingTo(new BigDecimal("2.5"));
        }

        @Test
        void stringQuantityExtracted() {
            assertThat(quantity("{\"quantity\":\"3\"}")).isEqualByComparingTo(new BigDecimal("3"));
        }

        @Test
        void missingQuantityReturnsNull() {
            assertThat(quantity("{\"action\":\"BUY\"}")).isNull();
        }

        @Test
        void nullQuantityReturnsNull() {
            assertThat(quantity("{\"quantity\":null}")).isNull();
        }

        @Test
        void nonNumericQuantityReturnsNull() {
            assertThat(quantity("{\"quantity\":\"lots\"}")).isNull();
        }

        @Test
        void malformedJsonReturnsNull() {
            assertThat(quantity("{\"quantity\": 2.5")).isNull();
        }

        @Test
        void nullInputReturnsNull() {
            assertThat(quantity(null)).isNull();
        }
    }

    @Test
    void unknownTypeIsNeverATradeSignal() {
        assertThat(ConsensusActionType.UNKNOWN.isTradeSignal()).isFalse();
        assertThat(ConsensusActionType.UNKNOWN.toRecommendationType()).isEmpty();
    }
}
