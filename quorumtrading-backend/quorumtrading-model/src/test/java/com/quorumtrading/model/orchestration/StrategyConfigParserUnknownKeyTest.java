package com.quorumtrading.model.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * A frontmatter key the parser does not act on is rejected, not ignored.
 *
 * <p>Silently ignoring it is indistinguishable from honouring it. The bug that motivated this:
 * {@code backtest_schedule} was read only when {@code live_schedule} was also present, so a
 * strategy asking for a daily backtest was accepted and then run at the 1-week default —
 * one consensus decision over a week where the author expected seven, with nothing in the logs
 * pointing at the cause. Fixing that one lookup left the identical failure a transposed letter
 * away: {@code backtest_schedual:} is a key nobody reads.</p>
 *
 * <p>This is the general form of that class of bug, which accounted for ten of the eleven defects
 * found in the MCP dog-fooding sweep: input accepted, then quietly not honoured.</p>
 */
class StrategyConfigParserUnknownKeyTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    private static final String TEMPLATE = """
            ---
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1d
            %s
            ---
            Buy the dip.
            """;

    private String strategyWith(String extraYaml) {
        return TEMPLATE.formatted(extraYaml);
    }

    @Test
    @DisplayName("the motivating typo is caught and named")
    void shouldRejectMisspelledScheduleKey() {
        String markdown = """
            ---
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedual:
              interval: 1d
            ---
            Buy the dip.
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backtest_schedual")
                .hasMessageContaining("Did you mean 'backtest_schedule'");
    }

    @Test
    @DisplayName("a near-miss on any key suggests the intended one")
    void shouldSuggestTheNearestKnownKey() {
        assertThatThrownBy(() -> parser.parse(strategyWith("reserve_cash_percentag: 0.2")))
                .hasMessageContaining("Did you mean 'reserve_cash_percentage'");
        assertThatThrownBy(() -> parser.parse(strategyWith("concensus_method: MAJORITY")))
                .hasMessageContaining("Did you mean 'consensus_method'");
        assertThatThrownBy(() -> parser.parse(strategyWith("position_limit:\n  BTC: 0.5")))
                .hasMessageContaining("Did you mean 'position_limits'");
    }

    @Test
    @DisplayName("a key that resembles nothing is still rejected, without a bogus suggestion")
    void shouldRejectAnInventedKeyWithoutSuggesting() {
        assertThatThrownBy(() -> parser.parse(strategyWith("max_drawdown_guard: 0.3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_drawdown_guard")
                .hasMessageContaining("Valid keys:")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Did you mean"));
    }

    @Test
    @DisplayName("every unrecognised key is reported at once, not one per attempt")
    void shouldReportAllUnknownKeysTogether() {
        assertThatThrownBy(() -> parser.parse(strategyWith("foo: 1\nbar: 2")))
                .hasMessageContaining("'bar', 'foo'")
                .hasMessageContaining("Unrecognised frontmatter keys");
    }

    @Test
    @DisplayName("a removed legacy key keeps its own migration message")
    void shouldPreferTheLegacyMigrationMessage() {
        String markdown = """
            ---
            crypto_tickers:
              - BTC
            evaluation_interval_seconds: 600
            ---
            Buy the dip.
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .hasMessageContaining("no longer supported")
                .hasMessageContaining("V122");
    }

    @Test
    @DisplayName("every documented key is accepted — the contract list is not narrower than the format")
    void shouldAcceptEveryKnownKey() {
        String everything = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            reserve_cash_percentage: 0.2
            max_order_fraction: 0.15
            crypto_tickers:
              - BTC
            stock_tickers:
              - AAPL
            live_schedule:
              timezone: "America/New_York"
              trading_days: [MONDAY]
              evaluation_times: ["10:00"]
            backtest_schedule:
              evaluation_times: ["10:00"]
            agents:
              - TECHNICAL
            llm_model: "anthropic/claude-sonnet-4.6"
            llm_models:
              TECHNICAL: "anthropic/claude-sonnet-4.6"
            llm_temperature: 0.3
            portfolio_snapshot_interval: 6h
            position_cooldown: 2d
            position_limits:
              BTC: 0.4
            ---
            Everything at once.
            """;

        assertThatCode(() -> parser.parse(everything)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the known-key set covers exactly what the parser reads")
    void knownKeySetIsNotEmptyAndHasNoLegacyOverlap() {
        assertThat(StrategyConfigParser.KNOWN_FRONTMATTER_KEYS)
                .isNotEmpty()
                .doesNotContain("market_hours", "evaluation_interval_seconds");
    }
}
