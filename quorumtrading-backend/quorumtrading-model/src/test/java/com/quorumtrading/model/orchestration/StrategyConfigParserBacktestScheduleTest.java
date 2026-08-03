package com.quorumtrading.model.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * A {@code backtest_schedule} declared without a {@code live_schedule} must still set the backtest
 * cadence.
 *
 * <p>It used not to. The parser decided whether to read either schedule key by testing for
 * {@code live_schedule} alone, so a strategy written only for backtesting fell through to the
 * defaults branch and its {@code interval} was never read — every backtest ran at the 1-week
 * default, silently. A one-week BTC backtest asking for {@code interval: 1d} produced a single
 * consensus decision instead of seven, with nothing in the logs to explain it.</p>
 */
class StrategyConfigParserBacktestScheduleTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    private static final int ONE_DAY = 86400;
    private static final int ONE_WEEK = 604800;
    private static final int DEFAULT_CRYPTO_LIVE = 300;

    @Test
    @DisplayName("crypto: backtest_schedule alone sets the backtest interval")
    void shouldHonourBacktestScheduleWithoutLiveSchedule() {
        String markdown = """
            ---
            consensus_method: MAJORITY
            crypto_tickers:
              - BTC
            backtest_schedule:
              interval: 1d
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(ONE_DAY);
        assertThat(config.getIntervalForMode(true)).isEqualTo(ONE_DAY);
    }

    @Test
    @DisplayName("crypto: backtest_schedule alone leaves the live cadence at its default")
    void shouldDefaultLiveIntervalWhenOnlyBacktestScheduleDeclared() {
        String markdown = """
            ---
            crypto_tickers:
              - BTC
            backtest_schedule:
              interval: 1d
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLiveIntervalSeconds()).isEqualTo(DEFAULT_CRYPTO_LIVE);
        assertThat(config.getLiveSchedule()).isNull();
    }

    @Test
    @DisplayName("crypto: backtest_schedule alone accepts the interval_seconds fallback")
    void shouldHonourIntervalSecondsWithoutLiveSchedule() {
        String markdown = """
            ---
            crypto_tickers:
              - ETH
            backtest_schedule:
              interval_seconds: 3600
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("crypto: no schedule keys at all still defaults to 300s live / 1w backtest")
    void shouldStillDefaultWhenNoScheduleKeysPresent() {
        String markdown = """
            ---
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLiveIntervalSeconds()).isEqualTo(DEFAULT_CRYPTO_LIVE);
        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(ONE_WEEK);
    }

    @Test
    @DisplayName("crypto: a non-map backtest_schedule is rejected, not ignored")
    void shouldRejectNonMapBacktestScheduleWithoutLiveSchedule() {
        String markdown = """
            ---
            crypto_tickers:
              - BTC
            backtest_schedule: 1d
            ---
            # Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("backtest_schedule must be a YAML object/map");
    }

    @Test
    @DisplayName("stock: backtest_schedule alone is not enough — live_schedule carries market hours")
    void shouldStillRequireLiveScheduleForStocks() {
        String markdown = """
            ---
            stock_tickers:
              - AAPL
            backtest_schedule:
              evaluation_times: ["10:00"]
            ---
            # Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("live_schedule");
    }
}
