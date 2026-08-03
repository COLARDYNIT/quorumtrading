package com.quorumtrading.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every instant here is fixed. That is the point of the class under test: the rule used to read
 * {@code Instant.now()} internally, so the daylight-saving case below could only be exercised by
 * running the suite on one particular day of the year, which nobody ever did.
 */
class BacktestWindowTest {

    /** Observes DST: clocks go back on the last Sunday of October, making that day 25 hours. */
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    /** The last instant a user can mean by "end of today" — what the UI and the API send. */
    private static Instant endOfDay(LocalDate day, ZoneId zone) {
        return day.atTime(23, 59, 59).atZone(zone).toInstant();
    }

    private static Instant middayOn(LocalDate day, ZoneId zone) {
        return day.atTime(12, 0).atZone(zone).toInstant();
    }

    // ---- The defect this class was extracted to fix ----

    @Test
    @DisplayName("a backtest ending at the end of the DST fall-back day is not 'in the future'")
    void fallBackDayEndIsStillToday() {
        LocalDate fallBack = LocalDate.of(2026, 10, 25);   // Brussels: 03:00 CEST → 02:00 CET, a 25-hour day
        Instant now = middayOn(fallBack, BRUSSELS);
        Instant start = now.minusSeconds(7 * 24 * 3600);

        // The old rule derived tomorrow's midnight as todayMidnight + 24h, which on this day is
        // 23:00 local — so 23:59:59 local read as tomorrow and the window was rejected outright.
        assertFalse(
            BacktestWindow.extendsIntoTheFuture(start, endOfDay(fallBack, BRUSSELS), now, BRUSSELS),
            "the last hour of a 25-hour day is still today");
    }

    @Test
    @DisplayName("the spring-forward day, 23 hours long, is handled the same way")
    void springForwardDayEndIsStillToday() {
        LocalDate springForward = LocalDate.of(2026, 3, 29);   // Brussels: 02:00 CET → 03:00 CEST
        Instant now = middayOn(springForward, BRUSSELS);
        Instant start = now.minusSeconds(7 * 24 * 3600);

        assertFalse(
            BacktestWindow.extendsIntoTheFuture(start, endOfDay(springForward, BRUSSELS), now, BRUSSELS),
            "a short day ends at its own end too");
    }

    @Test
    @DisplayName("a DST day still rejects a window that genuinely runs past today")
    void fallBackDayDoesNotBecomePermissive() {
        LocalDate fallBack = LocalDate.of(2026, 10, 25);
        Instant now = middayOn(fallBack, BRUSSELS);
        Instant start = now.minusSeconds(7 * 24 * 3600);
        Instant tomorrowNoon = middayOn(fallBack.plusDays(1), BRUSSELS);

        assertTrue(BacktestWindow.extendsIntoTheFuture(start, tomorrowNoon, now, BRUSSELS),
            "widening the boundary must not swallow a window that really does reach into tomorrow");
    }

    // ---- The ordinary rule, on a day with no transition ----

    @Test
    @DisplayName("a window ending at the end of an ordinary today is accepted")
    void ordinaryDayEndIsToday() {
        LocalDate day = LocalDate.of(2026, 8, 1);
        Instant now = middayOn(day, BRUSSELS);

        assertFalse(BacktestWindow.extendsIntoTheFuture(
            now.minusSeconds(3 * 24 * 3600), endOfDay(day, BRUSSELS), now, BRUSSELS));
    }

    @Test
    @DisplayName("tomorrow's midnight is the first instant that counts as the future")
    void tomorrowMidnightIsExclusive() {
        LocalDate day = LocalDate.of(2026, 8, 1);
        Instant now = middayOn(day, BRUSSELS);
        Instant start = now.minusSeconds(3 * 24 * 3600);
        Instant tomorrowMidnight = day.plusDays(1).atStartOfDay(BRUSSELS).toInstant();

        assertTrue(BacktestWindow.extendsIntoTheFuture(start, tomorrowMidnight, now, BRUSSELS));
        assertFalse(BacktestWindow.extendsIntoTheFuture(
            start, tomorrowMidnight.minusMillis(1), now, BRUSSELS));
    }

    @Test
    @DisplayName("a window starting today or later is not a backtest, however it ends")
    void futureStartIsNotABacktest() {
        LocalDate day = LocalDate.of(2026, 8, 1);
        Instant now = middayOn(day, BRUSSELS);
        Instant nextMonth = middayOn(day.plusMonths(1), BRUSSELS);

        assertFalse(BacktestWindow.extendsIntoTheFuture(now, nextMonth, now, BRUSSELS),
            "a live window is allowed to run into the future — that is what makes it live");
    }

    @Test
    @DisplayName("an ongoing window has no end to judge")
    void nullEndIsNeverInTheFuture() {
        Instant now = middayOn(LocalDate.of(2026, 8, 1), BRUSSELS);

        assertFalse(BacktestWindow.extendsIntoTheFuture(now.minusSeconds(86400), null, now, BRUSSELS));
        assertFalse(BacktestWindow.extendsIntoTheFuture(null, now, now, BRUSSELS));
    }

    @Test
    @DisplayName("UTC, which has no transitions, behaves identically")
    void utcMatchesTheZonedResult() {
        ZoneId utc = ZoneId.of("UTC");
        LocalDate day = LocalDate.of(2026, 10, 25);
        Instant now = middayOn(day, utc);

        assertFalse(BacktestWindow.extendsIntoTheFuture(
            now.minusSeconds(7 * 24 * 3600), endOfDay(day, utc), now, utc));
    }
}
