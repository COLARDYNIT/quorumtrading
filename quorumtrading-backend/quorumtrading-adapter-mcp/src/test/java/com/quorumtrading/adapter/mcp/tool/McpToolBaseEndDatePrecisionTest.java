package com.quorumtrading.adapter.mcp.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-of-day bound must survive being stored in a Postgres {@code timestamptz}.
 *
 * <p>{@code price_bars.timestamp} is a {@code timestamptz}, whose precision is microseconds. A
 * nanosecond-precision bound is therefore rounded on the way in, and {@code .999999999} rounds
 * <b>up</b>:
 *
 * <pre>SELECT '2026-07-10T23:59:59.999999999Z'::timestamptz;  ->  2026-07-11 00:00:00+00</pre>
 *
 * <p>That turns "{@code <=} end of the 10th" into "{@code <=} midnight on the 11th". Measured
 * against the live stack: BTC over 2026-07-01..07-10 reported the 11th's close and a change of
 * +6.36% where the truth for the requested window was +6.95%. US equity bars are stamped at the
 * 04:00Z session open, so nothing sits on the rounded boundary and they were never affected —
 * crypto bars are stamped at 00:00Z, exactly where it lands.
 */
class McpToolBaseEndDatePrecisionTest {

    private final TestableToolBase tool = new TestableToolBase();

    /** What Postgres does to the value: round half-up to the nearest microsecond. */
    private static Instant asStoredByPostgres(Instant bound) {
        return bound.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    @DisplayName("a plain end date carries no sub-microsecond component, so storing it cannot round")
    void endOfDayBoundIsMicrosecondPrecise() {
        Instant bound = tool.parseEndDatePublic("2026-07-10");

        assertThat(bound.getNano() % 1000)
            .as("a bound with sub-microsecond digits is rounded by timestamptz, and .999999999 "
                + "rounds UP across midnight into the next day's bar")
            .isZero();
    }

    @Test
    @DisplayName("the stored bound still falls on the day the caller named")
    void storedBoundDoesNotCrossIntoTheNextDay() {
        Instant stored = asStoredByPostgres(tool.parseEndDatePublic("2026-07-10"));

        assertThat(stored)
            .as("crypto bars are stamped at 00:00Z, so a bound that reaches the 11th's midnight "
                + "silently includes an extra day and reports the wrong end price")
            .isBefore(Instant.parse("2026-07-11T00:00:00Z"));
        assertThat(stored)
            .as("it must still include the whole of the named day")
            .isAfter(Instant.parse("2026-07-10T23:59:59Z"));
    }

    @Test
    @DisplayName("a 00:00Z-stamped bar on the following day is excluded")
    void excludesTheNextMidnightBar() {
        // The exact comparison the price-bar query makes: timestamp <= bound.
        Instant nextMidnightBar = Instant.parse("2026-07-11T00:00:00Z");
        Instant stored = asStoredByPostgres(tool.parseEndDatePublic("2026-07-10"));

        assertThat(nextMidnightBar.isAfter(stored))
            .as("the 11th's bar must not satisfy timestamp <= end-of-the-10th")
            .isTrue();
    }

    @Test
    @DisplayName("an explicit timestamp is still honoured exactly as given")
    void explicitTimestampIsUntouched() {
        assertThat(tool.parseEndDatePublic("2026-07-10T12:34:56Z"))
            .isEqualTo(Instant.parse("2026-07-10T12:34:56Z"));
    }

    /** Minimal concrete subclass exposing the protected end-date parser. */
    private static class TestableToolBase extends McpToolBase {
        Instant parseEndDatePublic(String dateStr) {
            return super.parseEndDate(dateStr);
        }
    }
}
