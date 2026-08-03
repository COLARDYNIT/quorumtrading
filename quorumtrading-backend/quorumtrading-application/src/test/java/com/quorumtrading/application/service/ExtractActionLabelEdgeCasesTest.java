package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge cases of the prompt-facing past-tense action label, companion to
 * {@link ExtractActionLabelTest}.
 *
 * <p>The historical letters-only char-scan ({@code extractActionLabel}, deleted) had
 * quirks that mislabeled non-actions as trades in LLM prompts. The typed parser
 * deliberately corrects them (unifications U3/U5 in the refactor design — prompt-text
 * only; the trade-history query filters to BUY/SELL rows, so these inputs are
 * unreachable in production):</p>
 * <ul>
 *   <li>U3: {@code {"action":"BUY2"}} was BOUGHT (scan read the leading letters), now TRADED</li>
 *   <li>U5: arbitrary tokens ({@code "FOO"}, numeric actions) leaked verbatim/empty, now TRADED</li>
 * </ul>
 */
class ExtractActionLabelEdgeCasesTest {

    private static String label(String finalAction) {
        return ConsensusActionParser.parse(finalAction).type().pastTenseLabel();
    }

    @Test
    void unterminatedJsonWithRecognizableBuyIsSalvaged() {
        // Preserved leniency: malformed-but-salvageable worked before and still works.
        assertThat(label("{\"action\":\"BUY\"")).isEqualTo("BOUGHT");
    }

    @Test
    void unterminatedJsonWithRecognizableSellIsSalvaged() {
        assertThat(label("{\"action\":\"SELL\"")).isEqualTo("SOLD");
    }

    @Test
    void buy2IsNotMislabeledAsBought() {
        // UNIFICATION U3: was BOUGHT via the letters-only scan quirk.
        assertThat(label("{\"action\":\"BUY2\"}")).isEqualTo("TRADED");
    }

    @Test
    void unrecognizedActionValueIsTraded() {
        // UNIFICATION U5: was "FOO" (leaked verbatim into prompts).
        assertThat(label("{\"action\":\"foo\"}")).isEqualTo("TRADED");
    }

    @Test
    void numericActionValueIsTraded() {
        // UNIFICATION U5: was the empty string (letters-only scan on a number).
        assertThat(label("{\"action\": 123}")).isEqualTo("TRADED");
    }
}
