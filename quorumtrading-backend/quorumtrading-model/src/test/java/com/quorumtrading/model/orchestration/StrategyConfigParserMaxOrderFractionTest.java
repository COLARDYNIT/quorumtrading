package com.quorumtrading.model.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@code max_order_fraction} frontmatter field: the per-order ceiling on a
 * single BUY, as a fraction of portfolio value. Absent means "not configured" (null) — the
 * execution engine then applies its own default only to platform-sized orders.
 */
@DisplayName("StrategyConfigParser: max_order_fraction")
class StrategyConfigParserMaxOrderFractionTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    private String withFrontmatterLine(String line) {
        return """
            ---
            crypto_tickers:
              - BTC
            %s
            ---
            Body.
            """.formatted(line);
    }

    @Test
    @DisplayName("a valid fraction is parsed onto the config")
    void parsesValidFraction() {
        StrategyConfig config = parser.parse(withFrontmatterLine("max_order_fraction: 0.15"));

        assertThat(config.getMaxOrderFraction()).isEqualTo(0.15);
    }

    @Test
    @DisplayName("absent key stays null — 'not configured' is distinct from any value")
    void absentKeyIsNull() {
        StrategyConfig config = parser.parse(withFrontmatterLine("consensus_threshold: 0.6"));

        assertThat(config.getMaxOrderFraction()).isNull();
    }

    @Test
    @DisplayName("1.0 (the whole portfolio per order) is the permitted maximum")
    void oneIsAccepted() {
        StrategyConfig config = parser.parse(withFrontmatterLine("max_order_fraction: 1.0"));

        assertThat(config.getMaxOrderFraction()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("zero is rejected — it would block every BUY")
    void zeroIsRejected() {
        assertThatThrownBy(() -> parser.parse(withFrontmatterLine("max_order_fraction: 0.0")))
                .hasMessageContaining("max_order_fraction")
                .hasMessageContaining("greater than 0.0");
    }

    @Test
    @DisplayName("a value above 1.0 is rejected")
    void aboveOneIsRejected() {
        assertThatThrownBy(() -> parser.parse(withFrontmatterLine("max_order_fraction: 1.5")))
                .hasMessageContaining("max_order_fraction")
                .hasMessageContaining("got: 1.5");
    }

    @Test
    @DisplayName("a negative value is rejected")
    void negativeIsRejected() {
        assertThatThrownBy(() -> parser.parse(withFrontmatterLine("max_order_fraction: -0.1")))
                .hasMessageContaining("max_order_fraction");
    }
}
