package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Locally stored price-data coverage for one ticker: how many bars exist, over what range,
 * and which providers supplied them. Lets an agent verify what a backfill actually landed
 * without querying the database directly.
 *
 * @param ticker      the ticker symbol
 * @param barCount    total price bars stored
 * @param distinctDays number of distinct calendar days covered (equals barCount for clean daily data)
 * @param firstBar    timestamp of the oldest stored bar
 * @param lastBar     timestamp of the newest stored bar
 * @param sources     distinct providers that supplied bars (e.g. alpaca, kraken)
 * @param window      per-window detail when the caller named a date range, else {@code null}
 */
public record PriceCoverage(
    String ticker,
    long barCount,
    long distinctDays,
    Instant firstBar,
    Instant lastBar,
    List<String> sources,
    Window window
) {

    /**
     * What is actually covered inside a date range the caller cares about — normally a backtest
     * window.
     *
     * <p>Global first/last bar cannot answer "is my window covered?", and reading it as if it
     * could is the mistake the market-data guide sends people to this tool to avoid. BTC reported
     * {@code firstBar 2016-01-01, lastBar 2026-07-29} over 1,522 distinct days: a continuous
     * decade by the look of it, under 40% of the span in fact.</p>
     *
     * @param from        first day of the window (inclusive)
     * @param to          last day of the window (inclusive)
     * @param expectedDays days the {@code calendar} says should carry a bar
     * @param coveredDays days inside the window that do carry at least one bar
     * @param gaps        runs of expected-but-missing days, oldest first
     * @param calendar    which day-count rule was applied, named so the number can be judged
     */
    public record Window(
        LocalDate from,
        LocalDate to,
        long expectedDays,
        long coveredDays,
        List<Gap> gaps,
        String calendar
    ) {
        /** Whole days missing, i.e. {@code expectedDays - coveredDays}. */
        public long missingDays() {
            return Math.max(0, expectedDays - coveredDays);
        }

        public boolean isComplete() {
            return gaps.isEmpty();
        }
    }

    /**
     * One run of consecutive expected days with no stored bar.
     *
     * @param from  first missing day (inclusive)
     * @param to    last missing day (inclusive)
     * @param days  number of expected days in the run
     */
    public record Gap(LocalDate from, LocalDate to, long days) {}

    /** Legacy 6-arg form — no window was requested. */
    public PriceCoverage(String ticker, long barCount, long distinctDays, Instant firstBar,
                         Instant lastBar, List<String> sources) {
        this(ticker, barCount, distinctDays, firstBar, lastBar, sources, null);
    }

    public boolean isEmpty() {
        return barCount == 0;
    }

    public static PriceCoverage empty(String ticker) {
        return new PriceCoverage(ticker, 0, 0, null, null, List.of(), null);
    }

    /** This coverage with a window attached. */
    public PriceCoverage withWindow(Window newWindow) {
        return new PriceCoverage(ticker, barCount, distinctDays, firstBar, lastBar, sources, newWindow);
    }

    /**
     * Build the window view from the days actually stored.
     *
     * <p>Pure, so the gap arithmetic is testable without a database — the part most likely to be
     * wrong is the calendar, not the SQL.</p>
     *
     * <p><b>The calendar is a stated assumption, not a fact.</b> Crypto trades every day; equities
     * trade weekdays, so weekends are excluded from {@code expectedDays} rather than reported as
     * missing. Exchange holidays are <em>not</em> known here and will appear as one-day gaps for
     * stocks. That is deliberate: inventing a holiday calendar would let this tool assert coverage
     * it has not measured, which is the failure mode it exists to fix. The {@code calendar} field
     * names the rule so the caller can discount accordingly.</p>
     *
     * @param from       first day of the window (inclusive)
     * @param to         last day of the window (inclusive)
     * @param type       asset class, deciding which days are expected to carry a bar
     * @param storedDays the days inside the window that carry at least one bar
     * @return the window view
     */
    public static Window buildWindow(LocalDate from, LocalDate to, InvestmentType type,
                                     Set<LocalDate> storedDays) {
        boolean everyDay = type == InvestmentType.CRYPTO;
        String calendar = everyDay
            ? "CRYPTO — every calendar day is expected to have a bar"
            : "STOCK — weekdays are expected to have a bar; exchange holidays are not known here"
              + " and show up as one-day gaps";

        Set<LocalDate> stored = storedDays == null ? Set.of() : new TreeSet<>(storedDays);
        long expected = 0;
        long covered = 0;
        List<Gap> gaps = new ArrayList<>();
        LocalDate gapStart = null;
        LocalDate gapEnd = null;
        long gapDays = 0;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (!everyDay && isWeekend(day)) {
                continue;
            }
            expected++;
            if (stored.contains(day)) {
                covered++;
                if (gapStart != null) {
                    gaps.add(new Gap(gapStart, gapEnd, gapDays));
                    gapStart = null;
                    gapDays = 0;
                }
            } else {
                if (gapStart == null) {
                    gapStart = day;
                    gapDays = 0;
                }
                gapEnd = day;
                gapDays++;
            }
        }
        if (gapStart != null) {
            gaps.add(new Gap(gapStart, gapEnd, gapDays));
        }
        return new Window(from, to, expected, covered, List.copyOf(gaps), calendar);
    }

    private static boolean isWeekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
