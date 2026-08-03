package com.quorumtrading.model.valueobject;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Value object representing market hours configuration for stock trading strategies.
 * Defines when consensus evaluations should occur within market hours.
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Timezone must not be null</li>
 *   <li>Trading days must not be empty</li>
 *   <li>Evaluation times must not be empty</li>
 *   <li>All evaluation times must be within market hours (marketOpen to marketClose)</li>
 *   <li>Market open time must be before market close time</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MarketHours nyse = MarketHours.forNYSE(List.of(LocalTime.of(10, 0), LocalTime.of(14, 0)));
 * MarketHours custom = new MarketHours(
 *     ZoneId.of("America/New_York"),
 *     List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
 *     List.of(LocalTime.of(9, 30)),
 *     LocalTime.of(9, 30),
 *     LocalTime.of(16, 0)
 * );
 * </pre>
 *
 * <p><b>Design Rationale:</b></p>
 * Stock trading requires scheduled execution at specific times within market hours.
 * This value object encapsulates all timing constraints and timezone information,
 * preventing trades from occurring outside allowed times.
 */
public record MarketHours(
    ZoneId timezone,
    List<DayOfWeek> tradingDays,
    List<LocalTime> evaluationTimes,
    LocalTime marketOpen,
    LocalTime marketClose
) {

    /**
     * Default US stock market hours (NYSE/NASDAQ).
     * Monday-Friday, 9:30 AM - 4:00 PM ET.
     */
    public static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    public static final LocalTime DEFAULT_MARKET_OPEN = LocalTime.of(9, 30);
    public static final LocalTime DEFAULT_MARKET_CLOSE = LocalTime.of(16, 0);
    public static final List<DayOfWeek> DEFAULT_TRADING_DAYS = List.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    );

    /**
     * Canonical constructor with validation.
     */
    public MarketHours {
        if (timezone == null) {
            throw new IllegalArgumentException("Timezone cannot be null");
        }
        if (tradingDays == null || tradingDays.isEmpty()) {
            throw new IllegalArgumentException("Trading days cannot be null or empty");
        }
        if (evaluationTimes == null || evaluationTimes.isEmpty()) {
            throw new IllegalArgumentException("Evaluation times cannot be null or empty");
        }
        if (marketOpen == null) {
            throw new IllegalArgumentException("Market open time cannot be null");
        }
        if (marketClose == null) {
            throw new IllegalArgumentException("Market close time cannot be null");
        }
        if (marketOpen.isAfter(marketClose) || marketOpen.equals(marketClose)) {
            throw new IllegalArgumentException(
                "Market open (" + marketOpen + ") must be before market close (" + marketClose + ")"
            );
        }

        // Validate all evaluation times are within market hours.
        //
        // The message names the timezone and says what market_open/market_close MEAN, because
        // without that it is actively misleading. All five fields are in the strategy's own
        // declared timezone, so a user told "23:00 is outside market hours (09:30 - 16:00)"
        // reasonably reads those numbers as the exchange's hours and concludes the platform
        // has rejected a time the exchange is in fact open for. They are the user's own
        // window, and widening them is the fix — a fix the old wording never hinted at.
        for (LocalTime time : evaluationTimes) {
            if (time.isBefore(marketOpen) || time.isAfter(marketClose)) {
                throw new IllegalArgumentException(
                    "Evaluation time " + time + " is outside the declared session "
                    + marketOpen + " - " + marketClose + " (all times " + timezone.getId()
                    + "). market_open/market_close describe your own trading window, not the"
                    + " exchange's — widen them to contain " + time
                    + ", or move the evaluation inside the window."
                );
            }
        }

        // Make lists immutable
        tradingDays = List.copyOf(tradingDays);
        evaluationTimes = List.copyOf(evaluationTimes);
    }

    /**
     * Factory method for standard NYSE/NASDAQ market hours.
     * Monday-Friday, 9:30 AM - 4:00 PM ET with custom evaluation times.
     *
     * @param evaluationTimes List of times when consensus should be evaluated
     * @return MarketHours configured for US stock market
     */
    public static MarketHours forNYSE(List<LocalTime> evaluationTimes) {
        return new MarketHours(
            US_EASTERN,
            DEFAULT_TRADING_DAYS,
            evaluationTimes,
            DEFAULT_MARKET_OPEN,
            DEFAULT_MARKET_CLOSE
        );
    }

    /**
     * Factory method for NYSE with single evaluation time.
     *
     * @param evaluationTime Single time when consensus should be evaluated
     * @return MarketHours configured for US stock market
     */
    public static MarketHours forNYSE(LocalTime evaluationTime) {
        return forNYSE(List.of(evaluationTime));
    }

    /**
     * Checks if the given instant falls within a trading day and market hours.
     *
     * @param instant The instant to check
     * @return true if instant is during a trading day and within market hours
     */
    public boolean isWithinMarketHours(ZonedDateTime instant) {
        ZonedDateTime zonedTime = instant.withZoneSameInstant(timezone);
        DayOfWeek dayOfWeek = zonedTime.getDayOfWeek();
        LocalTime time = zonedTime.toLocalTime();

        return tradingDays.contains(dayOfWeek)
            && !time.isBefore(marketOpen)
            && !time.isAfter(marketClose);
    }

    /**
     * Checks if the given instant is close to any scheduled evaluation time.
     * "Close" is defined as within 5 minutes of the scheduled time.
     *
     * @param instant The instant to check
     * @return true if instant is within 5 minutes of any evaluation time
     */
    public boolean isNearEvaluationTime(ZonedDateTime instant) {
        if (!isWithinMarketHours(instant)) {
            return false;
        }

        ZonedDateTime zonedTime = instant.withZoneSameInstant(timezone);
        LocalTime currentTime = zonedTime.toLocalTime();

        for (LocalTime evalTime : evaluationTimes) {
            long minutesDiff = Math.abs(
                java.time.Duration.between(currentTime, evalTime).toMinutes()
            );
            if (minutesDiff <= 5) {
                return true;
            }
        }

        return false;
    }

    /**
     * Calculates the next scheduled evaluation time after the given instant.
     *
     * @param fromInstant The instant to calculate from
     * @return ZonedDateTime of the next evaluation, or null if no future evaluations
     */
    public ZonedDateTime calculateNextEvaluationTime(ZonedDateTime fromInstant) {
        ZonedDateTime current = fromInstant.withZoneSameInstant(timezone);

        // Check remaining evaluation times today
        if (tradingDays.contains(current.getDayOfWeek())) {
            for (LocalTime evalTime : evaluationTimes) {
                if (current.toLocalTime().isBefore(evalTime)) {
                    return current.with(evalTime);
                }
            }
        }

        // No more evaluations today - find next trading day
        ZonedDateTime nextDay = current.plusDays(1).with(evaluationTimes.get(0));
        int maxDaysToCheck = 14; // Prevent infinite loop (covers 2 weeks)
        int daysChecked = 0;

        while (daysChecked < maxDaysToCheck) {
            if (tradingDays.contains(nextDay.getDayOfWeek())) {
                return nextDay;
            }
            nextDay = nextDay.plusDays(1);
            daysChecked++;
        }

        return null; // No valid trading days found in next 2 weeks
    }

    /**
     * Returns a display-friendly representation of trading schedule.
     *
     * @return formatted string like "Mon-Fri 09:30-16:00 ET [10:00, 14:00]"
     */
    public String getDisplaySchedule() {
        String daysDisplay = formatTradingDays();
        String timesDisplay = evaluationTimes.stream()
            .map(LocalTime::toString)
            .collect(java.util.stream.Collectors.joining(", "));

        return String.format("%s %s-%s %s [%s]",
            daysDisplay,
            marketOpen,
            marketClose,
            timezone.getId().replaceAll(".*/", ""), // Show only timezone abbreviation
            timesDisplay
        );
    }

    /**
     * Formats trading days for display.
     * Converts [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY] to "Mon-Fri".
     */
    private String formatTradingDays() {
        if (tradingDays.equals(DEFAULT_TRADING_DAYS)) {
            return "Mon-Fri";
        }

        return tradingDays.stream()
            .map(day -> day.toString().substring(0, 3))
            .collect(java.util.stream.Collectors.joining(", "));
    }
}
