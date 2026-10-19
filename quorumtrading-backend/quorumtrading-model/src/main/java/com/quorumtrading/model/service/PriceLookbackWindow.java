package com.quorumtrading.model.service;

import com.quorumtrading.model.enums.InvestmentType;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * How old a price may be and still count as the instrument's current price.
 *
 * <p>A crypto pair trades around the clock, so a price older than a day is a genuine data gap.
 * An exchange-listed instrument does not: from Friday's close to Monday's open the newest price
 * there can be is Friday's, and it stays the correct valuation until the market reopens. A fixed
 * 24-hour window called that "missing" every weekend, so LIVE simulations holding equities skipped
 * every portfolio snapshot from Saturday evening to Monday's pre-market (prod: QQQ, 2026-09/10).</p>
 *
 * <p>The window is therefore 24 hours plus one day for every closed exchange day (weekend or US
 * market holiday) in the run that ends today. On an ordinary weekday it is still exactly 24 hours,
 * so a feed that stops during the week is still reported as missing the same day.</p>
 *
 * <p>Exchange days are counted in the exchange's own zone ({@code America/New_York}), per ADR 0003's
 * exception for market sessions; the instants themselves stay UTC. Non-US listings use the same
 * rule: weekends are universal, a local holiday that is not a US one still reads as a gap.</p>
 */
public final class PriceLookbackWindow {

    /** The window on an ordinary trading day, and always for crypto. */
    public static final Duration BASE = Duration.ofHours(24);

    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");

    /** Bound on the closed-day run, so a calendar bug can never widen the window without limit. */
    private static final int MAX_CLOSED_DAYS = 6;

    private final USMarketHolidayCalendar calendar;

    public PriceLookbackWindow() {
        this(new USMarketHolidayCalendar());
    }

    public PriceLookbackWindow(USMarketHolidayCalendar calendar) {
        this.calendar = calendar;
    }

    /**
     * @param type  the instrument's asset universe
     * @param asOf  the valuation instant
     * @return how far before {@code asOf} a price may lie and still be current
     */
    public Duration lookbackFor(InvestmentType type, Instant asOf) {
        if (type == InvestmentType.CRYPTO) {
            return BASE;
        }
        LocalDate today = asOf.atZone(EXCHANGE_ZONE).toLocalDate();
        int closedDays = isTradingDay(today) ? 0 : 1;
        LocalDate day = today.minusDays(1);
        while (closedDays < MAX_CLOSED_DAYS && !isTradingDay(day)) {
            closedDays++;
            day = day.minusDays(1);
        }
        return BASE.plusDays(closedDays);
    }

    /** The earliest price timestamp that still counts as current at {@code asOf}. */
    public Instant earliestAcceptable(InvestmentType type, Instant asOf) {
        return asOf.minus(lookbackFor(type, asOf));
    }

    private boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !calendar.isHoliday(date);
    }
}
