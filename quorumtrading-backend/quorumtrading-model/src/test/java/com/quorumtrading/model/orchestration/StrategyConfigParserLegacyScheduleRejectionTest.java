package com.quorumtrading.model.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The legacy schedule frontmatter (market_hours / evaluation_interval_seconds) was removed:
 * stored strategies were rewritten by Flyway migration V122__Migrate_legacy_schedule_frontmatter
 * and the parser now rejects legacy input with an error pointing at the migration.
 */
class StrategyConfigParserLegacyScheduleRejectionTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    @Test
    @DisplayName("legacy evaluation_interval_seconds is rejected with a migration-pointing error")
    void shouldRejectLegacyEvaluationIntervalSeconds() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            evaluation_interval_seconds: 600
            crypto_tickers:
              - BTC
            ---
            # Legacy Crypto Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evaluation_interval_seconds")
            .hasMessageContaining("no longer supported")
            .hasMessageContaining("live_schedule")
            .hasMessageContaining("V122");
    }

    @Test
    @DisplayName("legacy market_hours is rejected with a migration-pointing error")
    void shouldRejectLegacyMarketHours() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            consensus_threshold: 0.6
            stock_tickers:
              - AAPL
            market_hours:
              timezone: "America/New_York"
              evaluation_times: ["10:00", "14:00"]
            ---
            # Legacy Stock Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("market_hours")
            .hasMessageContaining("no longer supported")
            .hasMessageContaining("live_schedule")
            .hasMessageContaining("V122");
    }

    @Test
    @DisplayName("legacy keys are rejected even when live_schedule is also present")
    void shouldRejectLegacyKeysAlongsideLiveSchedule() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            live_schedule:
              interval: 1h
            evaluation_interval_seconds: 600
            crypto_tickers:
              - BTC
            ---
            # Mixed Format Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evaluation_interval_seconds")
            .hasMessageContaining("no longer supported");
    }

    @Test
    @DisplayName("both legacy keys are listed in the rejection message")
    void shouldListBothLegacyKeys() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            evaluation_interval_seconds: 600
            stock_tickers:
              - AAPL
            market_hours:
              timezone: "America/New_York"
              evaluation_times: ["10:00"]
            ---
            # Doubly Legacy Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("market_hours")
            .hasMessageContaining("evaluation_interval_seconds");
    }

    @Test
    @DisplayName("migrated stock format (live_schedule with market-hours fields) parses fine")
    void shouldParseMigratedStockFormat() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            consensus_threshold: 0.6
            stock_tickers:
              - AAPL
            live_schedule:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00", "14:00"]
            ---
            # Migrated Stock Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLiveSchedule()).isNotNull();
        assertThat(config.getLiveSchedule().evaluationTimes()).hasSize(2);
    }
}
