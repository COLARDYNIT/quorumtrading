package com.quorumtrading.application.service;

import com.quorumtrading.application.service.consensus.ConsensusContextBuilder;
import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.valueobject.MarketHours;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConsensusContextBuilder#isInCooldown} — the position cooldown logic.
 *
 * <p>STOCK mode with day-scale cooldowns uses calendar-day snapping (truncate trade to start-of-day,
 * add N days). CRYPTO mode and sub-day cooldowns use exact duration comparison.</p>
 */
class ReachConsensusServiceCooldownTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    private static final MarketHours NYSE_SCHEDULE = new MarketHours(
        NY,
        List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        List.of(LocalTime.of(10, 0), LocalTime.of(14, 0)),
        LocalTime.of(9, 30),
        LocalTime.of(16, 0)
    );

    @Nested
    @DisplayName("STOCK mode with day-scale cooldown (calendar-day snapping)")
    class StockCalendarDay {

        @Test
        @DisplayName("14d cooldown: eval at 10:00 exactly 14 days after trade at 10:05 → NOT in cooldown")
        void evalOnDay14_notInCooldown() {
            // Trade on March 1 at 10:05 ET
            Instant tradeTime = ZonedDateTime.of(2026, 3, 1, 10, 5, 0, 0, NY).toInstant();
            // Eval on March 15 at 10:00 ET (exactly 14 calendar days later, but 5 min before exact 14d)
            Instant evalTime = ZonedDateTime.of(2026, 3, 15, 10, 0, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofDays(14), NYSE_SCHEDULE, tradeTime, evalTime);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("14d cooldown: eval at 10:00 only 13 days after trade → in cooldown")
        void evalOnDay13_inCooldown() {
            // Trade on March 1 at 10:05 ET
            Instant tradeTime = ZonedDateTime.of(2026, 3, 1, 10, 5, 0, 0, NY).toInstant();
            // Eval on March 14 at 10:00 ET (13 calendar days later)
            Instant evalTime = ZonedDateTime.of(2026, 3, 14, 10, 0, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofDays(14), NYSE_SCHEDULE, tradeTime, evalTime);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("14d cooldown: eval at 23:59 on day 13 → still in cooldown")
        void evalLateOnDay13_inCooldown() {
            Instant tradeTime = ZonedDateTime.of(2026, 3, 1, 10, 5, 0, 0, NY).toInstant();
            Instant evalTime = ZonedDateTime.of(2026, 3, 14, 23, 59, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofDays(14), NYSE_SCHEDULE, tradeTime, evalTime);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("14d cooldown: eval at midnight on day 14 → NOT in cooldown (cooldownEnd = midnight day 15)")
        void evalAtMidnightDay14_notInCooldown() {
            Instant tradeTime = ZonedDateTime.of(2026, 3, 1, 15, 30, 0, 0, NY).toInstant();
            // March 1 + 14 days = March 15 midnight. March 15 00:00 = midnight = cooldownEnd.
            Instant evalTime = ZonedDateTime.of(2026, 3, 15, 0, 0, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofDays(14), NYSE_SCHEDULE, tradeTime, evalTime);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("1d cooldown: trade in afternoon, eval next morning → NOT in cooldown")
        void oneDayCooldown_nextMorning() {
            Instant tradeTime = ZonedDateTime.of(2026, 3, 10, 14, 0, 0, 0, NY).toInstant();
            // Next day at 10:00 ET
            Instant evalTime = ZonedDateTime.of(2026, 3, 11, 10, 0, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofDays(1), NYSE_SCHEDULE, tradeTime, evalTime);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Null schedule falls back to America/New_York")
        void nullSchedule_fallsBackToNY() {
            Instant tradeTime = ZonedDateTime.of(2026, 3, 1, 10, 5, 0, 0, NY).toInstant();
            Instant evalTime = ZonedDateTime.of(2026, 3, 15, 10, 0, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofDays(14), null, tradeTime, evalTime);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("CRYPTO mode (exact duration)")
    class CryptoExactDuration {

        @Test
        @DisplayName("14d cooldown: eval 5 min before exact 14d mark → in cooldown")
        void exactDuration_fiveMinShort_inCooldown() {
            Instant tradeTime = Instant.parse("2026-03-01T10:05:00Z");
            Instant evalTime = Instant.parse("2026-03-15T10:00:00Z");

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.CRYPTO, Duration.ofDays(14), null, tradeTime, evalTime);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("14d cooldown: eval exactly 14 * 24h after trade → NOT in cooldown")
        void exactDuration_exactMatch_notInCooldown() {
            Instant tradeTime = Instant.parse("2026-03-01T10:05:00Z");
            Instant evalTime = Instant.parse("2026-03-15T10:05:00Z");

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.CRYPTO, Duration.ofDays(14), null, tradeTime, evalTime);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Sub-day cooldown (4h): eval 3h59m later → in cooldown")
        void subDayCooldown_inCooldown() {
            Instant tradeTime = Instant.parse("2026-03-10T10:00:00Z");
            Instant evalTime = Instant.parse("2026-03-10T13:59:00Z");

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.CRYPTO, Duration.ofHours(4), null, tradeTime, evalTime);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Sub-day cooldown (4h): eval 4h later → NOT in cooldown")
        void subDayCooldown_notInCooldown() {
            Instant tradeTime = Instant.parse("2026-03-10T10:00:00Z");
            Instant evalTime = Instant.parse("2026-03-10T14:00:00Z");

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.CRYPTO, Duration.ofHours(4), null, tradeTime, evalTime);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("STOCK mode with sub-day cooldown (exact duration, not calendar-day)")
    class StockSubDay {

        @Test
        @DisplayName("12h cooldown: eval 11h59m later → in cooldown (exact duration)")
        void subDayCooldown_inCooldown() {
            Instant tradeTime = ZonedDateTime.of(2026, 3, 10, 10, 0, 0, 0, NY).toInstant();
            Instant evalTime = ZonedDateTime.of(2026, 3, 10, 21, 59, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofHours(12), NYSE_SCHEDULE, tradeTime, evalTime);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("12h cooldown: eval 12h later → NOT in cooldown")
        void subDayCooldown_notInCooldown() {
            Instant tradeTime = ZonedDateTime.of(2026, 3, 10, 10, 0, 0, 0, NY).toInstant();
            Instant evalTime = ZonedDateTime.of(2026, 3, 10, 22, 0, 0, 0, NY).toInstant();

            boolean result = ConsensusContextBuilder.isInCooldown(
                TradingMode.STOCK, Duration.ofHours(12), NYSE_SCHEDULE, tradeTime, evalTime);

            assertThat(result).isFalse();
        }
    }
}
