package com.quorumtrading.model.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes NYSE/NASDAQ market holidays using rule-based calculation.
 *
 * <p>Covers the 10 US market holidays:</p>
 * <ol>
 *   <li>New Year's Day — observed(Jan 1)</li>
 *   <li>MLK Day — 3rd Monday in January</li>
 *   <li>Presidents' Day — 3rd Monday in February</li>
 *   <li>Good Friday — Friday before Easter (Meeus algorithm)</li>
 *   <li>Memorial Day — Last Monday in May</li>
 *   <li>Juneteenth — observed(Jun 19), only year >= 2021</li>
 *   <li>Independence Day — observed(Jul 4)</li>
 *   <li>Labor Day — 1st Monday in September</li>
 *   <li>Thanksgiving — 4th Thursday in November</li>
 *   <li>Christmas — observed(Dec 25)</li>
 * </ol>
 *
 * <p>Weekend observation rule: Saturday → Friday, Sunday → Monday.</p>
 *
 * <p><b>DDD Classification:</b> Domain service — pure business logic, no CDI annotations.</p>
 */
public class USMarketHolidayCalendar {

    private final Map<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();

    /**
     * Check if the given date is a US market holiday.
     *
     * @param date the date to check
     * @return true if the date is a market holiday
     */
    public boolean isHoliday(LocalDate date) {
        return getHolidaysForYear(date.getYear()).contains(date);
    }

    /**
     * Get all market holidays for a given year.
     * Results are cached per year.
     *
     * @param year the year to compute holidays for
     * @return set of holiday dates
     */
    public Set<LocalDate> getHolidaysForYear(int year) {
        return cache.computeIfAbsent(year, this::computeHolidays);
    }

    private Set<LocalDate> computeHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        // 1. New Year's Day — observed(Jan 1)
        holidays.add(observed(LocalDate.of(year, Month.JANUARY, 1)));

        // 2. MLK Day — 3rd Monday in January
        holidays.add(nthDayOfWeekInMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3));

        // 3. Presidents' Day — 3rd Monday in February
        holidays.add(nthDayOfWeekInMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3));

        // 4. Good Friday — Friday before Easter
        holidays.add(goodFriday(year));

        // 5. Memorial Day — Last Monday in May
        holidays.add(LocalDate.of(year, Month.MAY, 1)
                .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)));

        // 6. Juneteenth — observed(Jun 19), only year >= 2021
        if (year >= 2021) {
            holidays.add(observed(LocalDate.of(year, Month.JUNE, 19)));
        }

        // 7. Independence Day — observed(Jul 4)
        holidays.add(observed(LocalDate.of(year, Month.JULY, 4)));

        // 8. Labor Day — 1st Monday in September
        holidays.add(nthDayOfWeekInMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1));

        // 9. Thanksgiving — 4th Thursday in November
        holidays.add(nthDayOfWeekInMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4));

        // 10. Christmas — observed(Dec 25)
        holidays.add(observed(LocalDate.of(year, Month.DECEMBER, 25)));

        // Edge case: When Jan 1 of year+1 falls on Saturday,
        // the observed holiday (Dec 31) belongs to this year.
        LocalDate nextNewYear = LocalDate.of(year + 1, Month.JANUARY, 1);
        LocalDate observedNextNewYear = observed(nextNewYear);
        if (observedNextNewYear.getYear() == year) {
            holidays.add(observedNextNewYear);
        }

        return Set.copyOf(holidays);
    }

    /**
     * Apply the NYSE weekend observation rule:
     * Saturday holidays are observed on Friday, Sunday holidays on Monday.
     */
    private LocalDate observed(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.minusDays(1);
            case SUNDAY -> date.plusDays(1);
            default -> date;
        };
    }

    /**
     * Find the nth occurrence of a day-of-week in a month.
     * E.g., 3rd Monday in January.
     */
    private LocalDate nthDayOfWeekInMonth(int year, Month month, DayOfWeek dayOfWeek, int n) {
        LocalDate first = LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.firstInMonth(dayOfWeek));
        return first.plusWeeks(n - 1);
    }

    /**
     * Compute Good Friday for a given year using the Anonymous Gregorian (Meeus) algorithm.
     * Good Friday is the Friday before Easter Sunday.
     */
    private LocalDate goodFriday(int year) {
        LocalDate easter = easterSunday(year);
        return easter.minusDays(2);
    }

    /**
     * Compute Easter Sunday using the Anonymous Gregorian algorithm (Meeus/Jones/Butcher).
     */
    private LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
