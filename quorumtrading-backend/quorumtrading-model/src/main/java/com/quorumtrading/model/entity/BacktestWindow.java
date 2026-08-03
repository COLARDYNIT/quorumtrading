package com.quorumtrading.model.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The one definition of "a backtest that runs past today", shared by {@link Simulation} and
 * {@link Benchmark}.
 *
 * <p>Both entities carried their own copy of this rule, character for character, and both copies
 * had the same defect: they derived tomorrow's midnight as {@code todayMidnight.plus(1, DAYS)} —
 * a fixed 24 hours. A local day is not always 24 hours. On the daylight-saving fall-back day it is
 * 25, so "tomorrow midnight" landed at <strong>23:00 today</strong> and a window ending in that
 * final hour was rejected as reaching into the future. In {@code Europe/Brussels} on 2026-10-25,
 * {@code todayMidnight + 24h} is {@code 22:00Z} while {@code 23:59:59} local is {@code 22:59:59Z}
 * — a backtest ending at the end of that day could not be created at all.</p>
 *
 * <p>Duplicating the rule is what let one bug exist in two places, so it lives here once. The
 * boundary is now computed as tomorrow's <em>local</em> midnight, which is 25 hours after today's
 * on a fall-back day and 23 on a spring-forward day, exactly as a calendar day should be.</p>
 *
 * <p>{@code now} and {@code zone} are parameters rather than {@code Instant.now()} /
 * {@code ZoneId.systemDefault()} reads so the rule can be tested at an arbitrary point in time.
 * A rule about the calendar that can only be exercised on the day the suite happens to run is a
 * rule nobody can test — that is precisely how this survived.</p>
 *
 * <p><strong>Every caller passes {@link java.time.ZoneOffset#UTC}</strong>: a day starts at 00:00
 * UTC across the product, the same boundary used for budget days, free-model rate limits and DCA
 * funding days. The zone stays a parameter because that is what makes the transition arithmetic
 * testable at all, and because a rule that is only correct for one zone is a rule that breaks
 * quietly the day someone passes another one.</p>
 */
final class BacktestWindow {

    private BacktestWindow() {}

    /**
     * Whether {@code [startDate, endDate]} is a historical window that runs past the end of today.
     *
     * <p>A window qualifies as a backtest when it starts before today's local midnight. Such a
     * window may end at any point up to the last instant of today; reaching tomorrow's local
     * midnight or beyond means it covers time that has not happened yet.</p>
     *
     * @param startDate window start, or {@code null} when unset
     * @param endDate   window end, or {@code null} for an ongoing (live) window
     * @param now       the instant to judge "today" against
     * @param zone      the zone whose calendar day defines "today"
     * @return {@code true} when the window starts in the past and ends after today
     */
    static boolean extendsIntoTheFuture(Instant startDate, Instant endDate, Instant now, ZoneId zone) {
        if (startDate == null || endDate == null) return false;

        LocalDate today = now.atZone(zone).toLocalDate();
        Instant todayMidnight = today.atStartOfDay(zone).toInstant();
        Instant tomorrowMidnight = today.plusDays(1).atStartOfDay(zone).toInstant();

        boolean startIsInThePast = startDate.isBefore(todayMidnight);
        boolean endIsInTheFuture = !endDate.isBefore(tomorrowMidnight);

        return startIsInThePast && endIsInTheFuture;
    }

    /** The shared rejection message, so both entities explain the failure identically. */
    static String rejectionMessage(Instant startDate, Instant endDate) {
        return String.format("Backtest cannot extend into the future: startDate %s is before today "
                + "but endDate %s is after today. Set endDate to today or earlier for backtests.",
            startDate, endDate);
    }
}
