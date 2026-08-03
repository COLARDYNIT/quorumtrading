package com.quorumtrading.application.service;

import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.MarketHours;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rejects a stock strategy whose declared trading session cannot exist on the venue its
 * tickers actually trade on.
 *
 * <p>{@link MarketHours} only ever checked a schedule against <b>itself</b> — that the
 * evaluation times sit inside the user's own declared open/close — and the parser only
 * checked that the timezone string is a real {@link ZoneId}. Nothing cross-referenced the
 * ticker. A strategy holding only {@code TM} (NYSE) with {@code timezone: Asia/Tokyo},
 * {@code market_open: 09:00} and {@code evaluation_times: ["10:00"]} was therefore accepted
 * in silence, and evaluated at 01:00Z — 21:00 the previous evening in New York, with the
 * exchange shut for every single decision. On a backtest the daily bar hides it; the
 * identical {@code live_schedule} sends real orders into a closed exchange.</p>
 *
 * <p>Every tradable stock ticker on this platform is US-listed — non-US venues are refused
 * outright at {@link TickerValidationHelper} — so "the venue" is unambiguous: the US equity
 * regular session, 09:30–16:00 America/New_York, Monday to Friday. Crypto is exempt, being
 * genuinely 24/7.</p>
 *
 * <p><b>Days are converted, not compared by name.</b> An earlier version rejected a
 * {@code SATURDAY} trading day outright, which is wrong for any timezone far enough east:
 * Saturday 01:00 in Tokyo is Friday midday in New York, a live session. Rejecting it meant a
 * Tokyo-clock user could not express Friday's US trading day at all. Each declared
 * (day, time) pair is now resolved to the instant it actually fires and judged on the
 * weekday it lands on in New York.</p>
 *
 * <p><b>What this does NOT do:</b> exchange holidays are not modelled anywhere in the
 * platform, so a schedule that lands on Thanksgiving or Marine Day still passes here. This
 * validator constrains the <i>session</i>, not the <i>calendar</i>.</p>
 */
public class ScheduleVenueValidator {

    /**
     * Two fixed probe weeks, one either side of the DST boundary, each starting on a Monday so
     * a declared {@link DayOfWeek} can be placed on a real date. A schedule is required to be
     * valid in both: a pairing that works in July and breaks in January is a latent failure,
     * not a pass. Fixed rather than "today" so the verdict is deterministic and does not
     * silently change twice a year.
     */
    private static final LocalDate WINTER_PROBE_MONDAY = LocalDate.of(2025, 1, 13);
    private static final LocalDate SUMMER_PROBE_MONDAY = LocalDate.of(2025, 7, 14);

    /**
     * Validates both schedules on the config against the US equity session.
     *
     * @throws IllegalArgumentException if any evaluation fires outside the session or on a day
     *                                  that is a weekend in New York
     */
    public void validate(StrategyConfig config) {
        if (config == null || config.getStockTickers() == null || config.getStockTickers().isEmpty()) {
            return; // crypto-only or ticker-less: nothing trades on a stock exchange
        }

        List<String> problems = new ArrayList<>();
        collectProblems(config.getLiveSchedule(), "live_schedule", problems);
        collectProblems(config.getBacktestSchedule(), "backtest_schedule", problems);

        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                "Strategy schedule does not match the market its tickers trade on: "
                    + String.join("; ", problems)
                    + ". Every stock tradable here is US-listed, so evaluations must fall inside the"
                    + " US equity session (09:30-16:00 America/New_York, Mon-Fri). Either set"
                    + " timezone: \"America/New_York\" with evaluation_times in that range, or keep"
                    + " your own timezone and pick times that convert into it — in which case"
                    + " market_open and market_close describe YOUR window and must be widened to"
                    + " contain those times, or the schedule is rejected before it reaches this check."
            );
        }
    }

    private void collectProblems(MarketHours schedule, String field, List<String> problems) {
        if (schedule == null) {
            return;
        }

        for (LocalTime declared : schedule.evaluationTimes()) {
            collectTimeProblem(schedule, field, declared, problems);
            collectDayProblems(schedule, field, declared, problems);
        }
    }

    /** The time-of-day check, which is independent of which weekday it fires on. */
    private void collectTimeProblem(MarketHours schedule, String field, LocalTime declared,
                                    List<String> problems) {
        LocalTime winter = easternTime(declared, DayOfWeek.WEDNESDAY, schedule.timezone(), WINTER_PROBE_MONDAY);
        LocalTime summer = easternTime(declared, DayOfWeek.WEDNESDAY, schedule.timezone(), SUMMER_PROBE_MONDAY);

        boolean winterOk = withinUsSession(winter);
        boolean summerOk = withinUsSession(summer);
        if (winterOk && summerOk) {
            return;
        }

        if (MarketHours.US_EASTERN.equals(schedule.timezone())) {
            problems.add(String.format(
                "%s.evaluation_times %s is outside the US session (09:30-16:00 ET)%s",
                field, declared, preOrPostMarket(winterOk ? summer : winter)));
        } else if (winterOk != summerOk) {
            // Straddles the boundary — valid half the year, dead the other half.
            problems.add(String.format(
                "%s.evaluation_times %s %s converts to %s ET in winter and %s ET in summer, so it"
                    + " is inside the US session for only part of the year",
                field, declared, schedule.timezone().getId(), winter, summer));
        } else {
            problems.add(String.format(
                "%s.evaluation_times %s %s converts to %s ET, which is outside the US session"
                    + " (09:30-16:00 ET)%s",
                field, declared, schedule.timezone().getId(), summer, preOrPostMarket(summer)));
        }
    }

    /**
     * The weekday check, done on the instant the evaluation actually fires. A declared day is
     * only a problem if it lands on a Saturday or Sunday <i>in New York</i>.
     */
    private void collectDayProblems(MarketHours schedule, String field, LocalTime declared,
                                    List<String> problems) {
        Set<String> reported = new LinkedHashSet<>();

        for (DayOfWeek day : schedule.tradingDays()) {
            DayOfWeek winter = easternDay(declared, day, schedule.timezone(), WINTER_PROBE_MONDAY);
            DayOfWeek summer = easternDay(declared, day, schedule.timezone(), SUMMER_PROBE_MONDAY);

            if (isWeekend(winter)) {
                reported.add(describeWeekendDay(field, declared, schedule.timezone(), day, winter));
            } else if (isWeekend(summer)) {
                reported.add(describeWeekendDay(field, declared, schedule.timezone(), day, summer));
            }
        }

        problems.addAll(reported);
    }

    private String describeWeekendDay(String field, LocalTime declared, ZoneId zone,
                                      DayOfWeek declaredDay, DayOfWeek easternDay) {
        if (declaredDay == easternDay) {
            return String.format(
                "%s.trading_days includes %s, and US equity markets do not open at the weekend",
                field, declaredDay);
        }
        // The declared day is a weekday locally but lands on a weekend in New York — say so,
        // because "remove SATURDAY" would be nonsense advice when the user never wrote it.
        return String.format(
            "%s.trading_days %s at %s %s falls on %s in New York, when US equity markets are shut",
            field, declaredDay, declared, zone.getId(), easternDay);
    }

    /** Names the failure mode so the fix direction is obvious without doing the arithmetic. */
    private String preOrPostMarket(LocalTime eastern) {
        if (eastern.isBefore(MarketHours.DEFAULT_MARKET_OPEN)) {
            return " — the market has not opened yet";
        }
        return " — the market has already closed";
    }

    private boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean withinUsSession(LocalTime eastern) {
        return !eastern.isBefore(MarketHours.DEFAULT_MARKET_OPEN)
            && !eastern.isAfter(MarketHours.DEFAULT_MARKET_CLOSE);
    }

    private LocalTime easternTime(LocalTime declared, DayOfWeek day, ZoneId zone, LocalDate probeMonday) {
        return inEastern(declared, day, zone, probeMonday).toLocalTime();
    }

    private DayOfWeek easternDay(LocalTime declared, DayOfWeek day, ZoneId zone, LocalDate probeMonday) {
        return inEastern(declared, day, zone, probeMonday).getDayOfWeek();
    }

    private ZonedDateTime inEastern(LocalTime declared, DayOfWeek day, ZoneId zone, LocalDate probeMonday) {
        LocalDate probeDate = probeMonday.with(TemporalAdjusters.nextOrSame(day));
        return ZonedDateTime.of(probeDate, declared, zone)
            .withZoneSameInstant(MarketHours.US_EASTERN);
    }
}
