package com.quorumtrading.application.service;

import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.MarketHours;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A schedule used to be checked only against itself. A strategy holding a NYSE-listed ADR with a
 * Tokyo session was accepted in silence and evaluated every round with the exchange shut.
 */
class ScheduleVenueValidatorTest {

    private ScheduleVenueValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ScheduleVenueValidator();
    }

    private static StrategyConfig config(Set<Symbol> stocks, Set<Symbol> crypto, MarketHours live) {
        StrategyConfig c = new StrategyConfig();
        if (stocks != null) c.setStockTickers(stocks);
        if (crypto != null) c.setCryptoTickers(crypto);
        c.setLiveSchedule(live);
        return c;
    }

    private static Set<Symbol> stocks(String... tickers) {
        Set<Symbol> set = new LinkedHashSet<>();
        for (String t : tickers) set.add(new Symbol(t, InvestmentType.STOCK));
        return set;
    }

    private static Set<Symbol> crypto(String... tickers) {
        Set<Symbol> set = new LinkedHashSet<>();
        for (String t : tickers) set.add(new Symbol(t, InvestmentType.CRYPTO));
        return set;
    }

    private static MarketHours schedule(String zone, String open, String close, String... evalTimes) {
        List<LocalTime> times = java.util.Arrays.stream(evalTimes).map(LocalTime::parse).toList();
        return new MarketHours(
            ZoneId.of(zone),
            MarketHours.DEFAULT_TRADING_DAYS,
            times,
            LocalTime.parse(open),
            LocalTime.parse(close)
        );
    }

    @Nested
    @DisplayName("the regression this validator exists for")
    class TokyoSessionOnNyseTicker {

        @Test
        void rejectsAJapaneseSessionOnAUsListedTicker() {
            // The exact probe from the audit: TM is NYSE, the session declared is TSE's.
            StrategyConfig c = config(stocks("TM"), null,
                schedule("Asia/Tokyo", "09:00", "15:30", "10:00"));

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Asia/Tokyo")
                .hasMessageContaining("10:00")
                .hasMessageContaining("21:00")            // what it actually converts to in ET
                .hasMessageContaining("already closed")
                .hasMessageContaining("09:30-16:00 America/New_York");
        }

        @Test
        void tellsTheUserThatMarketOpenAndCloseMustBeWidenedToo() {
            // The remedy used to read "keep your own timezone and choose times that convert
            // into it" — advice the product then refused, because MarketHours rejects any
            // evaluation time outside the DECLARED open/close before this validator runs.
            // A user who followed it correctly was punished, and concluded the validator was
            // broken rather than their schedule.
            StrategyConfig c = config(stocks("TM"), null,
                schedule("Asia/Tokyo", "09:00", "15:30", "10:00"));

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("market_open and market_close describe YOUR window")
                .hasMessageContaining("must be widened");
        }
    }

    @Nested
    @DisplayName("schedules that are genuinely fine")
    class Accepted {

        @Test
        void acceptsAUsSessionInUsHours() {
            StrategyConfig c = config(stocks("MSFT"), null,
                schedule("America/New_York", "09:30", "16:00", "10:00", "15:00"));

            assertThatCode(() -> validator.validate(c)).doesNotThrowAnyException();
        }

        @Test
        void acceptsAEuropeanTimezoneWhoseTimesLandInsideTheUsSession() {
            // Paris 16:00 is 10:00 ET on both sides of the DST boundary — a correct EU schedule.
            StrategyConfig c = config(stocks("LVMUY"), null,
                schedule("Europe/Paris", "09:00", "17:30", "16:00"));

            assertThatCode(() -> validator.validate(c)).doesNotThrowAnyException();
        }

        @Test
        void acceptsTheSessionBoundariesThemselves() {
            StrategyConfig c = config(stocks("AAPL"), null,
                schedule("America/New_York", "09:30", "16:00", "09:30", "16:00"));

            assertThatCode(() -> validator.validate(c)).doesNotThrowAnyException();
        }

        @Test
        void ignoresCryptoOnlyStrategiesBecauseCryptoIs24x7() {
            StrategyConfig c = config(null, crypto("BTC"),
                schedule("Asia/Tokyo", "00:00", "23:59", "03:00"));

            assertThatCode(() -> validator.validate(c)).doesNotThrowAnyException();
        }

        @Test
        void ignoresAConfigWithNoScheduleAtAll() {
            StrategyConfig c = config(stocks("AAPL"), null, null);

            assertThatCode(() -> validator.validate(c)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("pre-market and after-hours inside the right timezone")
    class WrongHoursRightZone {

        @Test
        void rejectsAPreMarketEvaluationTime() {
            StrategyConfig c = config(stocks("AAPL"), null,
                schedule("America/New_York", "09:00", "16:00", "09:00"));

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("09:00")
                .hasMessageContaining("has not opened yet");
        }

        @Test
        void rejectsAnAfterHoursEvaluationTime() {
            StrategyConfig c = config(stocks("AAPL"), null,
                schedule("America/New_York", "09:30", "19:00", "18:00"));

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18:00")
                .hasMessageContaining("already closed");
        }
    }

    @Nested
    @DisplayName("weekends and the backtest schedule")
    class OtherCarriers {

        @Test
        void rejectsWeekendTradingDaysForAStockStrategy() {
            MarketHours weekend = new MarketHours(
                MarketHours.US_EASTERN,
                List.of(DayOfWeek.SATURDAY, DayOfWeek.MONDAY),
                List.of(LocalTime.of(10, 0)),
                LocalTime.of(9, 30),
                LocalTime.of(16, 0)
            );
            StrategyConfig c = config(stocks("AAPL"), null, weekend);

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SATURDAY")
                .hasMessageContaining("do not open at the weekend");
        }

        @Test
        void acceptsATokyoSaturdayThatIsFridayOnTheUsVenue() {
            // The flaw the previous version had: days were compared by NAME in the declared
            // zone, so SATURDAY was banned outright. Tokyo is 13-14h AHEAD of New York, so
            // Saturday 02:00 JST is Friday 12:00/13:00 ET — a live session — which meant a
            // Tokyo-clock user could not express Friday's US trading day at all.
            MarketHours tokyoSaturday = new MarketHours(
                ZoneId.of("Asia/Tokyo"),
                List.of(DayOfWeek.SATURDAY),
                List.of(LocalTime.of(2, 0)),
                LocalTime.of(1, 0),
                LocalTime.of(3, 0)
            );
            StrategyConfig c = config(stocks("AAPL"), null, tokyoSaturday);

            assertThatCode(() -> validator.validate(c)).doesNotThrowAnyException();
        }

        @Test
        void rejectsALocalWeekdayThatLandsOnAUsWeekend() {
            // Tokyo MONDAY 10:00 is Sunday 21:00 in New York. Telling this user to "remove
            // SATURDAY" would be nonsense — they never wrote a weekend day.
            MarketHours tokyoMonday = new MarketHours(
                ZoneId.of("Asia/Tokyo"),
                List.of(DayOfWeek.MONDAY),
                List.of(LocalTime.of(10, 0)),
                LocalTime.of(9, 0),
                LocalTime.of(15, 30)
            );
            StrategyConfig c = config(stocks("AAPL"), null, tokyoMonday);

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MONDAY")
                .hasMessageContaining("SUNDAY in New York");
        }

        @Test
        void checksTheBacktestScheduleToo() {
            StrategyConfig c = config(stocks("AAPL"), null,
                schedule("America/New_York", "09:30", "16:00", "10:00"));
            c.setBacktestSchedule(schedule("Asia/Tokyo", "09:00", "15:30", "10:00"));

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backtest_schedule");
        }

        @Test
        void reportsEveryOffendingTimeNotJustTheFirst() {
            StrategyConfig c = config(stocks("AAPL"), null,
                schedule("America/New_York", "08:00", "19:00", "08:30", "18:30"));

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("08:30")
                .hasMessageContaining("18:30");
        }
    }

    @Nested
    @DisplayName("the neighbouring MarketHours check, which fires first")
    class MarketHoursInteraction {

        @Test
        void marketHoursExplainsThatTheWindowIsTheUsersOwn() {
            // MarketHours' constructor runs before this validator ever sees the schedule, and
            // its old message — "Evaluation time 23:00 is outside market hours (09:30 - 16:00)"
            // — reads as though the EXCHANGE rejected the time. It did not: those are the
            // user's own declared bounds, in their own timezone, and widening them is the fix.
            // Three audit agents hit the resulting quality cliff between the two validators.
            assertThatThrownBy(() -> new MarketHours(
                ZoneId.of("Asia/Tokyo"),
                MarketHours.DEFAULT_TRADING_DAYS,
                List.of(LocalTime.of(23, 30)),
                LocalTime.of(9, 0),
                LocalTime.of(15, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declared session")
                .hasMessageContaining("Asia/Tokyo")
                .hasMessageContaining("your own trading window")
                .hasMessageContaining("widen them to contain 23:30");
        }

        @Test
        void aTokyoScheduleThatWidensItsWindowPassesBothChecks() {
            // The end-to-end path the venue validator's remedy now describes: keep your own
            // timezone, pick a time that converts into the US session, and widen the window
            // to contain it. 23:30 JST is 09:30/10:30 ET.
            StrategyConfig c = config(stocks("AAPL"), null,
                schedule("Asia/Tokyo", "22:00", "23:59", "23:30"));

            assertThatCode(() -> validator.validate(c)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("DST straddle")
    class DstStraddle {

        @Test
        void rejectsATimeThatIsOnlyInsideTheSessionForHalfTheYear() {
            // Asia/Kolkata has no DST. 19:45 IST = 14:15 UTC = 09:15 ET in winter (before the
            // 09:30 open) and 10:15 ET in summer (inside). Valid half the year, dead the other.
            StrategyConfig c = config(stocks("AAPL"), null,
                schedule("Asia/Kolkata", "09:00", "23:00", "19:45"));

            assertThatThrownBy(() -> validator.validate(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only part of the year");
        }
    }
}
