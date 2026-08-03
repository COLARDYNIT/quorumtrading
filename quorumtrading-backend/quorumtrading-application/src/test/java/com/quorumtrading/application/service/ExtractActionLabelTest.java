package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the prompt-facing past-tense action label (BOUGHT/SOLD/...) used in the
 * recent-trade-history block fed into LLM prompts.
 *
 * <p>Historically produced by {@code ReachConsensusService.extractActionLabel} (deleted);
 * now derived as {@code ConsensusActionParser.parse(x).type().pastTenseLabel()}. All
 * expectations below are unchanged from the historical parser.</p>
 */
class ExtractActionLabelTest {

    private static String label(String finalAction) {
        return ConsensusActionParser.parse(finalAction).type().pastTenseLabel();
    }

    @Test
    void plainBuyString() {
        assertThat(label("BUY")).isEqualTo("BOUGHT");
    }

    @Test
    void plainSellString() {
        assertThat(label("SELL")).isEqualTo("SOLD");
    }

    @Test
    void plainStringCaseInsensitive() {
        assertThat(label("buy")).isEqualTo("BOUGHT");
        assertThat(label("Sell")).isEqualTo("SOLD");
    }

    @Test
    void jsonBuyAction() {
        String json = "{\"action\": \"BUY\", \"symbol\": \"QQQ\", \"quantity\": 189.3, \"confidence\": 0.95, \"reasoning\": \"SMA-50 above SMA-200\"}";
        assertThat(label(json)).isEqualTo("BOUGHT");
    }

    @Test
    void jsonSellAction() {
        String json = "{\"action\": \"SELL\", \"symbol\": \"QQQ\", \"quantity\": 179.87, \"confidence\": 1.0, \"reasoning\": \"Downtrend detected\"}";
        assertThat(label(json)).isEqualTo("SOLD");
    }

    @Test
    void jsonHoldAction() {
        String json = "{\"action\": \"HOLD\", \"symbol\": \"QQQ\", \"quantity\": 0, \"confidence\": 0.9}";
        assertThat(label(json)).isEqualTo("HOLD");
    }

    @Test
    void jsonSkipAction() {
        String json = "{\"action\": \"SKIP\", \"symbol\": \"QQQ\", \"quantity\": 0.0, \"confidence\": 0.0, \"reasoning\": \"Agent failed\"}";
        assertThat(label(json)).isEqualTo("SKIP");
    }

    @Test
    void jsonNoSpacesAroundColon() {
        String json = "{\"action\":\"BUY\",\"symbol\":\"QQQ\"}";
        assertThat(label(json)).isEqualTo("BOUGHT");
    }

    @Test
    void jsonWithLeadingWhitespace() {
        String json = "  { \"action\" : \"SELL\" , \"symbol\": \"BTC\" }";
        assertThat(label(json)).isEqualTo("SOLD");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void nullOrBlankReturnsTraded(String input) {
        assertThat(label(input)).isEqualTo("TRADED");
    }

    @Test
    void unrecognizedPlainStringReturnsTraded() {
        assertThat(label("UNKNOWN_ACTION")).isEqualTo("TRADED");
    }

    @Test
    void jsonWithoutActionFieldReturnsTraded() {
        String json = "{\"symbol\": \"QQQ\", \"quantity\": 100}";
        assertThat(label(json)).isEqualTo("TRADED");
    }

    @Test
    void malformedJsonReturnsTraded() {
        assertThat(label("{broken json")).isEqualTo("TRADED");
    }

    @Test
    void nonJsonNonActionStringReturnsTraded() {
        assertThat(label("some random text")).isEqualTo("TRADED");
    }
}
