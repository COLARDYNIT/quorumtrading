package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The question this window answers — "is my backtest window covered?" — is the one the market-data
 * guide sends users to {@code get_price_coverage} with, and the one a global first/last bar
 * cannot answer.
 */
class PriceCoverageWindowTest {

    @Test
    @DisplayName("crypto expects every calendar day")
    void cryptoExpectsEveryDay() {
        PriceCoverage.Window window = PriceCoverage.buildWindow(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), InvestmentType.CRYPTO,
            Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3),
                   LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6),
                   LocalDate.of(2026, 1, 7)));

        assertThat(window.expectedDays()).isEqualTo(7);
        assertThat(window.coveredDays()).isEqualTo(7);
        assertThat(window.isComplete()).isTrue();
    }

    @Test
    @DisplayName("stocks do not count weekends as missing")
    void stocksSkipWeekends() {
        // 2026-01-05 is a Monday; 10th/11th are the weekend.
        PriceCoverage.Window window = PriceCoverage.buildWindow(
            LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11), InvestmentType.STOCK,
            Set.of(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7),
                   LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 9)));

        assertThat(window.expectedDays()).isEqualTo(5);
        assertThat(window.gaps()).isEmpty();
    }

    @Test
    @DisplayName("reports missing days as runs, not as a bare count")
    void reportsGapRuns() {
        PriceCoverage.Window window = PriceCoverage.buildWindow(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), InvestmentType.CRYPTO,
            Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
                   LocalDate.of(2026, 1, 6),
                   LocalDate.of(2026, 1, 10)));

        assertThat(window.coveredDays()).isEqualTo(4);
        assertThat(window.missingDays()).isEqualTo(6);
        assertThat(window.gaps()).containsExactly(
            new PriceCoverage.Gap(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 5), 3),
            new PriceCoverage.Gap(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9), 3));
    }

    @Test
    @DisplayName("a window with nothing stored is one gap, not silence")
    void emptyWindowIsOneGap() {
        PriceCoverage.Window window = PriceCoverage.buildWindow(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), InvestmentType.CRYPTO, Set.of());

        assertThat(window.coveredDays()).isZero();
        assertThat(window.gaps()).hasSize(1);
        assertThat(window.gaps().get(0).days()).isEqualTo(3);
    }

    @Test
    @DisplayName("names the calendar it applied, so the number can be judged")
    void namesTheCalendar() {
        assertThat(PriceCoverage.buildWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1),
            InvestmentType.STOCK, Set.of()).calendar()).contains("holidays");
        assertThat(PriceCoverage.buildWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1),
            InvestmentType.CRYPTO, Set.of()).calendar()).contains("every calendar day");
    }

    @Test
    @DisplayName("the legacy no-window form still reports nothing rather than a fabricated window")
    void legacyFormHasNoWindow() {
        assertThat(PriceCoverage.empty("AAPL").window()).isNull();
        assertThat(new PriceCoverage("AAPL", 1, 1, null, null, java.util.List.of()).window()).isNull();
    }
}
