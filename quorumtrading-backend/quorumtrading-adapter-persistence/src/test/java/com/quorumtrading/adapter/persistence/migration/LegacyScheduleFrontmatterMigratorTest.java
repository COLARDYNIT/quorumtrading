package com.quorumtrading.adapter.persistence.migration;

import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the V122 legacy-schedule rewrite logic (no database required).
 */
class LegacyScheduleFrontmatterMigratorTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    // =====================================================================
    // Stock mode: market_hours → live_schedule rename
    // =====================================================================

    @Test
    @DisplayName("stock legacy market_hours is renamed to live_schedule, everything else untouched")
    void shouldRenameMarketHoursForStockMode() {
        String legacy = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            consensus_threshold: 0.6
            stock_tickers:
              - AAPL
              - MSFT
            market_hours:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00", "14:00"]
              market_open: "09:30"
              market_close: "16:00"
            reserve_cash_percentage: 0.2
            ---
            # Stock Strategy

            ## BUY Signals
            - RSI < 30
            """;

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy);

        assertThat(migrated).isPresent();
        String result = migrated.get();
        assertThat(result).doesNotContain("market_hours");
        assertThat(result).contains("live_schedule:");
        // nested fields and body preserved byte-for-byte
        assertThat(result).contains("  timezone: \"America/New_York\"");
        assertThat(result).contains("  evaluation_times: [\"10:00\", \"14:00\"]");
        assertThat(result).contains("reserve_cash_percentage: 0.2");
        assertThat(result).contains("## BUY Signals\n- RSI < 30");

        // migrated output parses to the exact schedule the legacy path used to produce
        StrategyConfig config = parser.parse(result);
        assertThat(config.getLiveSchedule()).isNotNull();
        assertThat(config.getLiveSchedule().evaluationTimes())
            .containsExactly(LocalTime.of(10, 0), LocalTime.of(14, 0));
        assertThat(config.getLiveSchedule().tradingDays()).hasSize(5);
        assertThat(config.getLiveSchedule().marketOpen()).isEqualTo(LocalTime.of(9, 30));
        // default backtest schedule (Wednesday 10:00) is generated identically for both formats
        assertThat(config.getBacktestSchedule().tradingDays()).containsExactly(DayOfWeek.WEDNESDAY);
        assertThat(config.getBacktestSchedule().evaluationTimes()).containsExactly(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("stock mode drops an ignored legacy evaluation_interval_seconds line")
    void shouldDropIgnoredIntervalInStockMode() {
        String legacy = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            consensus_threshold: 0.6
            evaluation_interval_seconds: 3600
            stock_tickers:
              - AAPL
            market_hours:
              timezone: "America/New_York"
              evaluation_times: ["10:00"]
            ---
            # Stock Strategy
            """;

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy);

        assertThat(migrated).isPresent();
        assertThat(migrated.get()).doesNotContain("evaluation_interval_seconds");
        assertThat(migrated.get()).doesNotContain("market_hours");
        assertThat(migrated.get()).contains("live_schedule:");

        StrategyConfig config = parser.parse(migrated.get());
        assertThat(config.getLiveSchedule()).isNotNull();
    }

    // =====================================================================
    // Crypto mode: evaluation_interval_seconds → live_schedule.interval
    // =====================================================================

    @Test
    @DisplayName("crypto legacy interval becomes live_schedule.interval with the same seconds")
    void shouldConvertCryptoIntervalSeconds() {
        String legacy = """
            ---
            version: v2.0
            consensus_method: MAJORITY
            consensus_threshold: 0.7
            evaluation_interval_seconds: 600
            crypto_tickers:
              - BTC
              - ETH
            position_limits:
              BTC: 0.5
            ---
            # Crypto Strategy
            """;

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy);

        assertThat(migrated).isPresent();
        assertThat(migrated.get()).doesNotContain("evaluation_interval_seconds");
        assertThat(migrated.get()).contains("live_schedule:\n  interval: 600");

        StrategyConfig config = parser.parse(migrated.get());
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(600);
        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(604800); // default 1w, same as legacy
    }

    @Test
    @DisplayName("non-numeric legacy interval falls back to the legacy default of 300 seconds")
    void shouldFallBackTo300ForNonNumericInterval() {
        String legacy = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            evaluation_interval_seconds: hourly
            crypto_tickers:
              - BTC
            ---
            # Crypto Strategy
            """;

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy);

        assertThat(migrated).isPresent();
        assertThat(migrated.get()).contains("interval: 300");

        StrategyConfig config = parser.parse(migrated.get());
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("crypto mode drops an ignored market_hours block (equivalent 300s default)")
    void shouldDropMarketHoursBlockInCryptoMode() {
        String legacy = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            crypto_tickers:
              - BTC
            market_hours:
              timezone: "America/New_York"
              evaluation_times: ["10:00"]
            reserve_cash_percentage: 0.1
            ---
            # Crypto Strategy With Stray Market Hours
            """;

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy);

        assertThat(migrated).isPresent();
        assertThat(migrated.get()).doesNotContain("market_hours");
        assertThat(migrated.get()).doesNotContain("timezone");
        assertThat(migrated.get()).contains("reserve_cash_percentage: 0.1");

        // legacy path resolved crypto to 300s when no interval was given; so does the default path
        StrategyConfig config = parser.parse(migrated.get());
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("crypto with empty stock_tickers list is treated as crypto mode (parser parity)")
    void shouldTreatEmptyStockTickersAsCrypto() {
        String legacy = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            evaluation_interval_seconds: 7200
            stock_tickers: []
            crypto_tickers:
              - BTC
            ---
            # Crypto Strategy
            """;

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy);

        assertThat(migrated).isPresent();
        assertThat(migrated.get()).contains("live_schedule:\n  interval: 7200");

        StrategyConfig config = parser.parse(migrated.get());
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(7200);
    }

    // =====================================================================
    // Dead legacy keys next to an existing live_schedule
    // =====================================================================

    @Test
    @DisplayName("legacy keys are stripped when live_schedule is already present")
    void shouldStripDeadLegacyKeysWhenLiveSchedulePresent() {
        String mixed = """
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

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(mixed);

        assertThat(migrated).isPresent();
        assertThat(migrated.get()).doesNotContain("evaluation_interval_seconds");
        assertThat(migrated.get()).contains("live_schedule:\n  interval: 1h");

        StrategyConfig config = parser.parse(migrated.get());
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(3600);
    }

    // =====================================================================
    // Idempotency + non-legacy content untouched
    // =====================================================================

    @Test
    @DisplayName("content already in the new format is untouched")
    void shouldLeaveNewFormatUntouched() {
        String newFormat = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            live_schedule:
              interval: 1h
            crypto_tickers:
              - BTC
            ---
            # Already Migrated
            """;

        assertThat(LegacyScheduleFrontmatterMigrator.migrate(newFormat)).isEmpty();
    }

    @Test
    @DisplayName("migration is idempotent: rerunning on migrated output changes nothing")
    void shouldBeIdempotent() {
        String legacy = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            evaluation_interval_seconds: 600
            crypto_tickers:
              - BTC
            ---
            # Crypto Strategy
            """;

        String migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy).orElseThrow();
        assertThat(LegacyScheduleFrontmatterMigrator.migrate(migrated)).isEmpty();
    }

    @Test
    @DisplayName("null and blank content are untouched")
    void shouldIgnoreNullAndBlank() {
        assertThat(LegacyScheduleFrontmatterMigrator.migrate(null)).isEmpty();
        assertThat(LegacyScheduleFrontmatterMigrator.migrate("   ")).isEmpty();
    }

    @Test
    @DisplayName("body mentions of legacy field names are not rewritten")
    void shouldNotTouchBodyMentions() {
        String content = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            live_schedule:
              interval: 1h
            crypto_tickers:
              - BTC
            ---
            # Strategy

            This strategy used to use market_hours: and evaluation_interval_seconds: fields.
            """;

        assertThat(LegacyScheduleFrontmatterMigrator.migrate(content)).isEmpty();
    }

    // =====================================================================
    // Unmigratable content is reported, not mangled
    // =====================================================================

    @Test
    @DisplayName("content without standard frontmatter is skipped but flagged as containing legacy keys")
    void shouldSkipButFlagContentWithoutClosingDelimiter() {
        String noClosing = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            evaluation_interval_seconds: 600
            crypto_tickers:
              - BTC
            # Strategy body without closing delimiter
            """;

        assertThat(LegacyScheduleFrontmatterMigrator.migrate(noClosing)).isEmpty();
        assertThat(LegacyScheduleFrontmatterMigrator.frontmatterContainsLegacyKeys(noClosing)).isTrue();
    }

    @Test
    @DisplayName("frontmatterContainsLegacyKeys is false for migrated content")
    void shouldReportNoLegacyKeysAfterMigration() {
        String legacy = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.6
            evaluation_interval_seconds: 600
            crypto_tickers:
              - BTC
            ---
            # Crypto Strategy
            """;

        String migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy).orElseThrow();
        assertThat(LegacyScheduleFrontmatterMigrator.frontmatterContainsLegacyKeys(migrated)).isFalse();
    }

    @Test
    @DisplayName("CRLF line endings are handled")
    void shouldHandleCrlfContent() {
        String legacy = "---\r\n"
            + "version: v1.0\r\n"
            + "consensus_method: MAJORITY\r\n"
            + "consensus_threshold: 0.6\r\n"
            + "evaluation_interval_seconds: 1800\r\n"
            + "crypto_tickers:\r\n"
            + "  - BTC\r\n"
            + "---\r\n"
            + "# CRLF Strategy\r\n";

        Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(legacy);

        assertThat(migrated).isPresent();
        assertThat(migrated.get()).doesNotContain("evaluation_interval_seconds");

        StrategyConfig config = parser.parse(migrated.get());
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(1800);
    }
}
