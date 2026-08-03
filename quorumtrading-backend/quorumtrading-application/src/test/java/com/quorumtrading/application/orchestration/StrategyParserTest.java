package com.quorumtrading.application.orchestration;

import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.MarketHours;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyParserTest {

    private final StrategyParser parser = new StrategyParser();

    // =====================================================================
    // New format: live_schedule + backtest_schedule
    // =====================================================================

    @Test
    void shouldParseNewCryptoFormatWithDualSchedule() {
        String markdown = """
            ---
            version: v2.0
            consensus_method: WEIGHTED
            consensus_threshold: 0.7
            position_limits:
              BTC: 0.5
              ETH: 0.3
            reserve_cash_percentage: 0.25
            crypto_tickers:
              - BTC
              - ETH
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1w
            ---

            # My Strategy

            This is the strategy prompt that guides the orchestrator.
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getVersion()).isEqualTo("v2.0");
        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.MAJORITY);
        assertThat(config.getConsensusThreshold()).isEqualTo(0.7);
        assertThat(config.getTradingMode()).isEqualTo(TradingMode.CRYPTO);
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(3600);
        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(604800);
        assertThat(config.getLiveSchedule()).isNull();
        assertThat(config.getBacktestSchedule()).isNull();
        assertThat(config.getPositionLimits()).containsEntry("BTC", 0.5);
        assertThat(config.getPositionLimits()).containsEntry("ETH", 0.3);
        assertThat(config.getStrategyPrompt()).contains("# My Strategy");
    }

    @Test
    void shouldParseNewStockFormatWithDualSchedule() {
        String markdown = """
            ---
            consensus_method: MAJORITY
            stock_tickers:
              - AAPL
              - MSFT
            live_schedule:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00", "14:00"]
              market_open: "09:30"
              market_close: "16:00"
            backtest_schedule:
              timezone: "America/New_York"
              trading_days: [WEDNESDAY]
              evaluation_times: ["10:00"]
              market_open: "09:30"
              market_close: "16:00"
            ---
            # Stock Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getTradingMode()).isEqualTo(TradingMode.STOCK);

        // Live schedule
        MarketHours live = config.getLiveSchedule();
        assertThat(live).isNotNull();
        assertThat(live.tradingDays()).hasSize(5);
        assertThat(live.evaluationTimes()).containsExactly(LocalTime.of(10, 0), LocalTime.of(14, 0));

        // Backtest schedule
        MarketHours bt = config.getBacktestSchedule();
        assertThat(bt).isNotNull();
        assertThat(bt.tradingDays()).containsExactly(DayOfWeek.WEDNESDAY);
        assertThat(bt.evaluationTimes()).containsExactly(LocalTime.of(10, 0));
    }

    @Test
    void shouldDefaultBacktestScheduleForCryptoWhenOmitted() {
        String markdown = """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 6h
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLiveIntervalSeconds()).isEqualTo(21600); // 6h
        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(604800); // default 1w
    }

    @Test
    void shouldDefaultBacktestScheduleForStockWhenOmitted() {
        String markdown = """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            stock_tickers:
              - AAPL
            live_schedule:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00", "14:00"]
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        // Live schedule should be as configured
        assertThat(config.getLiveSchedule().tradingDays()).hasSize(5);
        assertThat(config.getLiveSchedule().evaluationTimes()).hasSize(2);

        // Backtest schedule defaults to a single weekly evaluation, inherited from live
        MarketHours bt = config.getBacktestSchedule();
        assertThat(bt).isNotNull();
        assertThat(bt.tradingDays()).containsExactly(DayOfWeek.WEDNESDAY);
        assertThat(bt.evaluationTimes()).containsExactly(LocalTime.of(10, 0));
        assertThat(bt.timezone()).isEqualTo(config.getLiveSchedule().timezone());
        assertThat(bt.marketOpen()).isEqualTo(config.getLiveSchedule().marketOpen());
        assertThat(bt.marketClose()).isEqualTo(config.getLiveSchedule().marketClose());
    }

    @Test
    void defaultBacktestScheduleInheritsTheLiveTimeInsteadOfPinningAnEasternLiteral() {
        // The default used to copy the live timezone but hard-code 10:00 — an Eastern-shaped
        // literal. For Europe/Amsterdam that is 04:00 ET, so a field documented as OPTIONAL
        // was rejected by the venue check for every non-Eastern user, including one following
        // the guide's own worked Paris example. Inheriting the live time cannot self-invalidate.
        String markdown = """
            ---
            consensus_method: MAJORITY
            stock_tickers:
              - ASML
            live_schedule:
              timezone: "Europe/Amsterdam"
              trading_days: [MONDAY, WEDNESDAY]
              evaluation_times: ["16:00"]
              market_open: "09:00"
              market_close: "17:30"
            ---
            # Strategy
            """;

        MarketHours bt = parser.parse(markdown).getBacktestSchedule();

        assertThat(bt.evaluationTimes()).containsExactly(LocalTime.of(16, 0));
        assertThat(bt.tradingDays()).containsExactly(DayOfWeek.WEDNESDAY);
        assertThat(bt.timezone()).isEqualTo(ZoneId.of("Europe/Amsterdam"));
    }

    @Test
    void defaultBacktestScheduleFallsBackToAnActualTradingDayWhenWednesdayIsNotOne() {
        // Pinning WEDNESDAY when the user never trades it invents a day they did not declare.
        String markdown = """
            ---
            consensus_method: MAJORITY
            stock_tickers:
              - AAPL
            live_schedule:
              timezone: "America/New_York"
              trading_days: [TUESDAY, THURSDAY]
              evaluation_times: ["11:00"]
            ---
            # Strategy
            """;

        MarketHours bt = parser.parse(markdown).getBacktestSchedule();

        assertThat(bt.tradingDays()).containsExactly(DayOfWeek.TUESDAY);
        assertThat(bt.evaluationTimes()).containsExactly(LocalTime.of(11, 0));
    }

    @Test
    void shouldParseCryptoIntervalWithMinuteUnit() {
        String markdown = """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 30m
            backtest_schedule:
              interval: 1d
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLiveIntervalSeconds()).isEqualTo(1800); // 30 minutes
        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(86400); // 1 day
    }

    // =====================================================================
    // Removed legacy format (evaluation_interval_seconds, market_hours) is rejected
    // (stored strategies were rewritten by migration V122)
    // =====================================================================

    @Test
    void shouldRejectOldCryptoFormat() {
        String markdown = """
            ---
            version: v2.0
            consensus_method: WEIGHTED
            consensus_threshold: 0.7
            evaluation_interval_seconds: 600
            reserve_cash_percentage: 0.25
            crypto_tickers:
              - BTC
              - ETH
            ---

            # My Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evaluation_interval_seconds")
            .hasMessageContaining("no longer supported")
            .hasMessageContaining("V122");
    }

    @Test
    void shouldRejectOldStockFormat() {
        String markdown = """
            ---
            consensus_method: MAJORITY
            stock_tickers:
              - AAPL
              - MSFT
            market_hours:
              timezone: "America/New_York"
              trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
              evaluation_times: ["10:00", "14:00"]
            ---
            # Stock Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("market_hours")
            .hasMessageContaining("no longer supported")
            .hasMessageContaining("V122");
    }

    @Test
    void shouldUseDefaultsForMissingYamlFields() {
        String markdown = """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            crypto_tickers:
              - BTC
            ---

            Minimal strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getVersion()).isEqualTo("v1.0");
        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.ORCHESTRATOR_OVERRIDE);
        assertThat(config.getConsensusThreshold()).isEqualTo(0.6);
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(300);
        assertThat(config.getBacktestIntervalSeconds()).isEqualTo(604800);
        assertThat(config.getReserveCashPercentage()).isEqualTo(0.2);
        assertThat(config.getStrategyPrompt()).contains("Minimal strategy");
    }

    // =====================================================================
    // Deprecated accessor compatibility
    // =====================================================================

    @Test
    void shouldReturnLiveValuesFromDeprecatedAccessors() {
        String markdown = """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        // Deprecated getters delegate to live fields
        assertThat(config.getEvaluationIntervalSeconds()).isEqualTo(config.getLiveIntervalSeconds());
        assertThat(config.getMarketHours()).isEqualTo(config.getLiveSchedule());
    }

    @Test
    void shouldReturnCorrectScheduleForMode() {
        String markdown = """
            ---
            consensus_method: ORCHESTRATOR_OVERRIDE
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1w
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getIntervalForMode(false)).isEqualTo(3600);
        assertThat(config.getIntervalForMode(true)).isEqualTo(604800);
    }

    // =====================================================================
    // Error cases
    // =====================================================================

    @Test
    void shouldRejectLegacyFormatWithoutFrontmatter() {
        String legacyMarkdown = """
            # Conservative BTC Strategy v1.0

            ## Consensus Method
            Use: MAJORITY
            Require 75% agreement threshold
            """;

        assertThatThrownBy(() -> parser.parse(legacyMarkdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid YAML frontmatter format");
    }

    @Test
    void shouldRejectMarkdownWithoutFrontmatter() {
        String markdown = """
            # Simple Strategy

            Buy low, sell high.
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid YAML frontmatter format");
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThatThrownBy(() -> parser.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void shouldThrowExceptionForEmptyInput() {
        assertThatThrownBy(() -> parser.parse(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void shouldThrowExceptionForInvalidYamlFrontmatter() {
        String markdown = """
            ---
            invalid frontmatter without closing

            # Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid YAML frontmatter format");
    }

    /**
     * An unrecognised consensus_method is rejected, not quietly coerced.
     *
     * <p>This test used to assert the coercion — that INVALID_METHOD yields MAJORITY. That was the
     * bug: MAJORITY is not the default for an absent key (ORCHESTRATOR_OVERRIDE is), so a typo
     * swapped one decision system for another at a different cost per evaluation, and the read
     * APIs kept echoing the typo back as though it were in effect.</p>
     */
    @Test
    void shouldRejectInvalidConsensusMethod() {
        String yamlMarkdown = """
            ---
            consensus_method: INVALID_METHOD
            crypto_tickers:
              - BTC
            ---
            Strategy prompt
            """;

        assertThatThrownBy(() -> parser.parse(yamlMarkdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown consensus_method 'INVALID_METHOD'")
            .hasMessageContaining("ORCHESTRATOR_OVERRIDE");
    }

    @Test
    void shouldParsePositionLimitsFromYaml() {
        String markdown = """
            ---
            position_limits:
              BTC: 0.4
              ETH: 0.3
              SOL: 0.2
            crypto_tickers:
              - BTC
              - ETH
              - SOL
            ---
            Multi-asset strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getPositionLimits()).hasSize(3);
        assertThat(config.getPositionLimits()).containsEntry("BTC", 0.4);
        assertThat(config.getPositionLimits()).containsEntry("ETH", 0.3);
        assertThat(config.getPositionLimits()).containsEntry("SOL", 0.2);
    }

    @Test
    void shouldExtractCleanStrategyPromptFromYaml() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            crypto_tickers:
              - BTC
            ---

            # Strategic Guidance

            ## Core Principles
            1. Risk management first
            2. Respect position limits

            ## Decision Framework
            - BUY when 3+ agents agree
            - SELL on risk warnings
            """;

        StrategyConfig config = parser.parse(markdown);

        String prompt = config.getStrategyPrompt();
        assertThat(prompt).doesNotContain("---");
        assertThat(prompt).doesNotContain("version:");
        assertThat(prompt).contains("# Strategic Guidance");
        assertThat(prompt).contains("## Core Principles");
        assertThat(prompt).contains("Risk management first");
    }

    @Test
    void shouldHandleWindowsLineEndings() {
        String markdown = "---\r\n" +
                "version: v1.0\r\n" +
                "consensus_method: CONFIDENCE_BASED\r\n" +
                "consensus_threshold: 0.70\r\n" +
                "live_schedule:\r\n" + "  interval: 3600\r\n" +
                "reserve_cash_percentage: 0.10\r\n" +
                "position_limits:\r\n" +
                "  BTC: 0.25\r\n" +
                "  ETH: 0.20\r\n" +
                "crypto_tickers:\r\n" +
                "  - BTC\r\n" +
                "  - ETH\r\n" +
                "---\r\n" +
                "# My Strategy\r\n" +
                "This is the strategy prompt.";

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getVersion()).isEqualTo("v1.0");
        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.MAJORITY);
        assertThat(config.getConsensusThreshold()).isEqualTo(0.70);
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(3600);
        assertThat(config.getReserveCashPercentage()).isEqualTo(0.10);
        assertThat(config.getPositionLimits()).containsEntry("BTC", 0.25);
        assertThat(config.getPositionLimits()).containsEntry("ETH", 0.20);
        assertThat(config.getStrategyPrompt()).contains("# My Strategy");
    }

    @Test
    void shouldHandleUnixLineEndings() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            consensus_threshold: 0.60
            live_schedule:
              interval: 1800
            reserve_cash_percentage: 0.05
            crypto_tickers:
              - BTC
            ---

            # Test Strategy

            This strategy should work with Unix line endings.
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getVersion()).isEqualTo("v1.0");
        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.MAJORITY);
        assertThat(config.getConsensusThreshold()).isEqualTo(0.60);
        assertThat(config.getLiveIntervalSeconds()).isEqualTo(1800);
        assertThat(config.getReserveCashPercentage()).isEqualTo(0.05);
        assertThat(config.getStrategyPrompt()).contains("# Test Strategy");
    }

    @Test
    void shouldHandleUTF8BOMCorruptionFromDatabase() {
        String markdownWithBOM = "\uFEFF---\n" +
                "version: v1.0\n" +
                "consensus_method: CONFIDENCE_BASED\n" +
                "consensus_threshold: 0.75\n" +
                "live_schedule:\n" + "  interval: 7200\n" +
                "reserve_cash_percentage: 0.05\n" +
                "position_limits:\n" +
                "  BTC: 0.5\n" +
                "  ETH: 0.3\n" +
                "  SOL: 0.2\n" +
                "stock_tickers: []\n" +
                "crypto_tickers:\n" +
                "  - BTC\n" +
                "  - ETH\n" +
                "  - SOL\n" +
                "---\n" +
                "# Strategy with BOM\n" +
                "This strategy was retrieved from database with UTF-8 BOM corruption.";

        StrategyConfig config = parser.parse(markdownWithBOM);

        assertThat(config.getVersion()).isEqualTo("v1.0");
        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.MAJORITY);
        assertThat(config.getConsensusThreshold()).isEqualTo(0.75);
        assertThat(config.getPositionLimits()).containsEntry("BTC", 0.5);
        assertThat(config.getCryptoTickers()).extracting("ticker").contains("BTC", "ETH", "SOL");
    }

    @Test
    void shouldHandleZeroWidthCharactersFromEncodingCorruption() {
        String markdownWithZeroWidth = "---\n" +
                "version: v1.0\n" +
                "consensus_method: CONFIDENCE_BASED\n" +
                "consensus_threshold: 0.8\n" +
                "live_schedule:\n" + "  interval: 3600\n" +
                "reserve_cash_percentage: 0.0\n" +
                "position_limits:\n" +
                "  BTC: 0.40\n" +
                "  ETH: 0.30\n" +
                "  SOL: 0.15\n" +
                "  DOGE: 0.15\n" +
                "stock_tickers: []\n" +
                "crypto_tickers:\n" +
                "  - BTC\u200B\n" +
                "  - ETH\n" +
                "  - SOL\n" +
                "  - DOGE\n" +
                "---\n" +
                "# Strategy with Zero-Width Characters\n" +
                "This strategy has invisible zero-width characters from DB encoding issues.";

        StrategyConfig config = parser.parse(markdownWithZeroWidth);

        assertThat(config.getVersion()).isEqualTo("v1.0");
        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.MAJORITY);
        assertThat(config.getConsensusThreshold()).isEqualTo(0.8);
        assertThat(config.getPositionLimits()).hasSize(4);
        assertThat(config.getCryptoTickers()).extracting("ticker").contains("BTC", "ETH", "SOL", "DOGE");
    }

    @Test
    void shouldParseLlmModelFromFrontmatter() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            llm_model: anthropic/claude-3.5-sonnet
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLlmModel()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(config.getLlmTemperature()).isNull();
    }

    @Test
    void shouldParseLlmTemperatureFromFrontmatter() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            llm_model: openai/gpt-4o
            llm_temperature: 0.5
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLlmModel()).isEqualTo("openai/gpt-4o");
        assertThat(config.getLlmTemperature()).isEqualTo(0.5);
    }

    @Test
    void shouldDefaultLlmFieldsToNull() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getLlmModel()).isNull();
        assertThat(config.getLlmTemperature()).isNull();
    }

    @Test
    void shouldRejectInvalidLlmTemperature() {
        String markdown = """
            ---
            version: v1.0
            llm_temperature: 3.0
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        assertThatThrownBy(() -> parser.parse(markdown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("llm_temperature must be between 0.0 and 2.0");
    }

    @Test
    void shouldAcceptLlmTemperatureBoundary() {
        String markdown = """
            ---
            version: v1.0
            llm_temperature: 2.0
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfig config = parser.parse(markdown);
        assertThat(config.getLlmTemperature()).isEqualTo(2.0);
    }

    @Test
    void shouldHandleCombinedEncodingCorruption() {
        String markdown = "\uFEFF---\r\n" +
                "version: v1.0\r\n" +
                "consensus_method: CONFIDENCE_BASED\r\n" +
                "consensus_threshold: 0.75\r\n" +
                "live_schedule:\r\n" + "  interval: 7200\r\n" +
                "reserve_cash_percentage: 0.05\r\n" +
                "position_limits:\r\n" +
                "  BTC: 0.5\r\n" +
                "  ETH: 0.3\r\n" +
                "  SOL: 0.2\r\n" +
                "stock_tickers: []\r\n" +
                "crypto_tickers:\r\n" +
                "  - BTC\r\n" +
                "  - ETH\u200D\r\n" +
                "  - SOL\r\n" +
                "---\r\n" +
                "# Strategy with Combined Corruption\r\n" +
                "This has BOM, mixed line endings, and zero-width chars.";

        StrategyConfig config = parser.parse(markdown);

        assertThat(config.getVersion()).isEqualTo("v1.0");
        assertThat(config.getConsensusMethod()).isEqualTo(ConsensusMethod.MAJORITY);
        assertThat(config.getConsensusThreshold()).isEqualTo(0.75);
        assertThat(config.getPositionLimits()).containsEntry("BTC", 0.5);
        assertThat(config.getCryptoTickers()).extracting("ticker").contains("BTC", "ETH", "SOL");
    }
}
