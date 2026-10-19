package com.quorumtrading.model.service;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A closed market is not missing data.
 *
 * <p>Prod, 2026-09/10: two LIVE simulations holding QQQ skipped every portfolio snapshot from
 * Saturday ~20:30 UTC to Monday ~12:10 UTC, every weekend, because Friday's last price had fallen
 * out of a fixed 24-hour window. Alpaca kept answering with that same Friday price all weekend.</p>
 */
@DisplayName("PriceLookbackWindow — an equity's last session price stays current while its market is closed")
class PriceLookbackWindowTest {

    private final PriceLookbackWindow window = new PriceLookbackWindow();

    private Duration equity(String asOf) {
        return window.lookbackFor(InvestmentType.STOCK, Instant.parse(asOf));
    }

    @Test
    @DisplayName("an ordinary weekday keeps the 24h window, so a feed that stops mid-week is still a gap")
    void weekdayIsTwentyFourHours() {
        assertThat(equity("2026-10-14T18:00:00Z")).isEqualTo(Duration.ofHours(24)); // Wednesday
    }

    @Test
    @DisplayName("Saturday evening reaches back to Friday's session")
    void saturdayReachesFriday() {
        Instant asOf = Instant.parse("2026-10-17T21:00:00Z");
        Instant fridayClose = Instant.parse("2026-10-16T20:00:00Z");

        assertThat(window.earliestAcceptable(InvestmentType.STOCK, asOf)).isBefore(fridayClose);
        assertThat(equity("2026-10-17T21:00:00Z")).isEqualTo(Duration.ofHours(48));
    }

    @Test
    @DisplayName("Sunday reaches back over Saturday to Friday")
    void sundayReachesFriday() {
        assertThat(equity("2026-10-18T10:00:00Z")).isEqualTo(Duration.ofHours(72));
    }

    @Test
    @DisplayName("Monday pre-market (the prod case, 11:27 UTC) still accepts Friday's close")
    void mondayPreMarketAcceptsFriday() {
        Instant asOf = Instant.parse("2026-10-19T11:27:54Z");

        assertThat(window.earliestAcceptable(InvestmentType.STOCK, asOf))
                .isBefore(Instant.parse("2026-10-16T20:37:00Z"));
    }

    @Test
    @DisplayName("exchange days are New York days: Monday 02:00 UTC is still Sunday on the exchange")
    void exchangeDayIsNewYorkLocal() {
        assertThat(equity("2026-10-19T02:00:00Z")).isEqualTo(Duration.ofHours(72));
        // Saturday 03:00 UTC is Friday 23:00 in New York: Friday's session is the last one, 24h holds it.
        assertThat(equity("2026-10-17T03:00:00Z")).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("a Monday market holiday widens the window by one more day (Tuesday after Labor Day)")
    void holidayMondayWidensByOneDay() {
        Instant asOf = Instant.parse("2026-09-08T11:00:00Z");

        assertThat(equity("2026-09-08T11:00:00Z")).isEqualTo(Duration.ofHours(96));
        assertThat(window.earliestAcceptable(InvestmentType.STOCK, asOf))
                .isBefore(Instant.parse("2026-09-04T20:00:00Z"));
    }

    @Test
    @DisplayName("crypto trades every day: its window is 24h even on a Sunday")
    void cryptoIsAlwaysTwentyFourHours() {
        assertThat(window.lookbackFor(InvestmentType.CRYPTO, Instant.parse("2026-10-18T10:00:00Z")))
                .isEqualTo(Duration.ofHours(24));
        assertThat(window.lookbackFor(InvestmentType.CRYPTO, Instant.parse("2026-09-08T11:00:00Z")))
                .isEqualTo(Duration.ofHours(24));
    }
}
