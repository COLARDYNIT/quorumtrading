package com.quorumtrading.model.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class StrategyConfigParserSnapshotIntervalTest {

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
    void shouldParseSnapshotInterval6Hours() {
        StrategyConfig config = parser.parse(strategyWith("portfolio_snapshot_interval: 6h"));

        assertThat(config.getPortfolioSnapshotInterval()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void shouldParseSnapshotInterval1Day() {
        StrategyConfig config = parser.parse(strategyWith("portfolio_snapshot_interval: 1d"));

        assertThat(config.getPortfolioSnapshotInterval()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void shouldParseSnapshotInterval30Minutes() {
        StrategyConfig config = parser.parse(strategyWith("portfolio_snapshot_interval: 30m"));

        assertThat(config.getPortfolioSnapshotInterval()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void shouldReturnNullWhenNotSpecified() {
        StrategyConfig config = parser.parse(strategyWith(""));

        assertThat(config.getPortfolioSnapshotInterval()).isNull();
    }

    @Test
    void shouldDefaultTo6HoursViaEffectiveGetter() {
        StrategyConfig config = parser.parse(strategyWith(""));

        assertThat(config.getEffectivePortfolioSnapshotInterval()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> parser.parse(strategyWith("portfolio_snapshot_interval: banana")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid duration format");
    }
}
