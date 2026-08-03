package com.quorumtrading.model.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class StrategyConfigParserCooldownTest {

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

    private String strategyWith(String extraYaml) {
        return MINIMAL_STRATEGY_TEMPLATE.formatted(extraYaml);
    }

    @Test
    void shouldParseCooldown14Days() {
        StrategyConfig config = parser.parse(strategyWith("position_cooldown: 14d"));

        assertThat(config.getPositionCooldown()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void shouldParseCooldown2Weeks() {
        StrategyConfig config = parser.parse(strategyWith("position_cooldown: 2w"));

        assertThat(config.getPositionCooldown()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void shouldParseCooldown12Hours() {
        StrategyConfig config = parser.parse(strategyWith("position_cooldown: 12h"));

        assertThat(config.getPositionCooldown()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void shouldReturnNullWhenCooldownNotSpecified() {
        StrategyConfig config = parser.parse(strategyWith(""));

        assertThat(config.getPositionCooldown()).isNull();
    }

    @Test
    void shouldRejectInvalidCooldownFormat() {
        assertThatThrownBy(() -> parser.parse(strategyWith("position_cooldown: banana")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid duration format");
    }
}
