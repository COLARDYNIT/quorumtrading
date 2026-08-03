package com.quorumtrading.model.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class USMarketHolidayCalendarTest {

    private USMarketHolidayCalendar calendar;

    @BeforeEach
    void setUp() {
        calendar = new USMarketHolidayCalendar();
    }

    // Fixed holidays

    @Test
    void shouldRecognizeChristmas2024() {
        // Christmas 2024 is Wednesday Dec 25 — no observation shift
        assertThat(calendar.isHoliday(LocalDate.of(2024, 12, 25))).isTrue();
    }

    @Test
    void shouldRecognizeIndependenceDay2024() {
        // Jul 4 2024 is Thursday — no observation shift
        assertThat(calendar.isHoliday(LocalDate.of(2024, 7, 4))).isTrue();
    }

    // Observed holidays (weekend → shifted)

    @Test
    void shouldObserveIndependenceDayOnFridayWhenSaturday() {
        // Jul 4 2020 is Saturday → observed Friday Jul 3
        assertThat(calendar.isHoliday(LocalDate.of(2020, 7, 3))).isTrue();
        assertThat(calendar.isHoliday(LocalDate.of(2020, 7, 4))).isFalse();
    }

    @Test
    void shouldObserveJuneteenthOnFridayWhenSaturday() {
        // Jun 19 2021 is Saturday → observed Friday Jun 18
        assertThat(calendar.isHoliday(LocalDate.of(2021, 6, 18))).isTrue();
        assertThat(calendar.isHoliday(LocalDate.of(2021, 6, 19))).isFalse();
    }

    // Floating holidays

    @Test
    void shouldRecognizeMLKDay2024() {
        // 3rd Monday in January 2024 = Jan 15
        assertThat(calendar.isHoliday(LocalDate.of(2024, 1, 15))).isTrue();
    }

    @Test
    void shouldRecognizeThanksgiving2024() {
        // 4th Thursday in November 2024 = Nov 28
        assertThat(calendar.isHoliday(LocalDate.of(2024, 11, 28))).isTrue();
    }

    @Test
    void shouldRecognizeMemorialDay2024() {
        // Last Monday in May 2024 = May 27
        assertThat(calendar.isHoliday(LocalDate.of(2024, 5, 27))).isTrue();
    }

    @Test
    void shouldRecognizePresidentsDay2024() {
        // 3rd Monday in February 2024 = Feb 19
        assertThat(calendar.isHoliday(LocalDate.of(2024, 2, 19))).isTrue();
    }

    @Test
    void shouldRecognizeLaborDay2024() {
        // 1st Monday in September 2024 = Sep 2
        assertThat(calendar.isHoliday(LocalDate.of(2024, 9, 2))).isTrue();
    }

    // Good Friday

    @Test
    void shouldRecognizeGoodFriday2023() {
        // Easter 2023 = Apr 9, Good Friday = Apr 7
        assertThat(calendar.isHoliday(LocalDate.of(2023, 4, 7))).isTrue();
    }

    @Test
    void shouldRecognizeGoodFriday2024() {
        // Easter 2024 = Mar 31, Good Friday = Mar 29
        assertThat(calendar.isHoliday(LocalDate.of(2024, 3, 29))).isTrue();
    }

    @Test
    void shouldRecognizeGoodFriday2025() {
        // Easter 2025 = Apr 20, Good Friday = Apr 18
        assertThat(calendar.isHoliday(LocalDate.of(2025, 4, 18))).isTrue();
    }

    // Juneteenth not before 2021

    @Test
    void shouldNotRecognizeJuneteenthBefore2021() {
        // Jun 19 2020 is Friday — would be a holiday if Juneteenth existed, but it didn't yet
        assertThat(calendar.isHoliday(LocalDate.of(2020, 6, 19))).isFalse();
    }

    // New Year cross-year edge case

    @Test
    void shouldRecognizeNewYearObservedInPreviousYear() {
        // Jan 1 2022 is Saturday → observed Friday Dec 31 2021
        assertThat(calendar.isHoliday(LocalDate.of(2021, 12, 31))).isTrue();
    }

    // Holiday count per year

    @Test
    void shouldHave9HolidaysIn2020() {
        // 2020 is pre-Juneteenth → 9 holidays
        Set<LocalDate> holidays = calendar.getHolidaysForYear(2020);
        assertThat(holidays).hasSize(9);
    }

    @Test
    void shouldHave10HolidaysIn2024() {
        // 2024 includes Juneteenth → 10 holidays
        Set<LocalDate> holidays = calendar.getHolidaysForYear(2024);
        assertThat(holidays).hasSize(10);
    }

    // Non-holiday

    @Test
    void shouldNotRecognizeRegularWeekday() {
        // A regular Tuesday in 2024
        assertThat(calendar.isHoliday(LocalDate.of(2024, 6, 4))).isFalse();
    }

    // Cache behavior

    @Test
    void shouldReturnSameSetOnRepeatedCalls() {
        Set<LocalDate> first = calendar.getHolidaysForYear(2024);
        Set<LocalDate> second = calendar.getHolidaysForYear(2024);
        assertThat(first).isSameAs(second);
    }
}
