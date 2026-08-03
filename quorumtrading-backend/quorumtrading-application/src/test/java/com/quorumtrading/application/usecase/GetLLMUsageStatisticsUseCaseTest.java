package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.LLMUsageRepository.DailyUsageRecord;
import com.quorumtrading.application.usecase.dto.LLMUsageStatistics;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.model.service.PlatformMarkup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetLLMUsageStatisticsUseCaseTest {

    @Mock private LLMUsageRepository llmUsageRepository;

    private GetLLMUsageStatisticsUseCase useCase;
    private final UserId userId = new UserId(UUID.randomUUID());

    @AfterEach
    void resetMarkup() {
        PlatformMarkup.reset();
    }

    @BeforeEach
    void setUp() {
        // Tests in this class assert billed figures at the historical 1.50 hosted rate
        PlatformMarkup.configure(new java.math.BigDecimal("1.50"));
        useCase = new GetLLMUsageStatisticsUseCase(llmUsageRepository);
    }

    /**
     * Every window here is anchored on the UTC day (ADR-0003). Bracketing the call keeps the
     * assertion honest across a UTC-midnight straddle while still failing if this ever reverts to
     * the host's day.
     */
    @Test
    @DisplayName("all windows are anchored on the UTC day")
    void windowsAreAnchoredOnTheUtcDay() {
        when(llmUsageRepository.getDailyUsage(any(), any(), any())).thenReturn(List.of());
        when(llmUsageRepository.getTotalRawCost(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        LocalDate utcBefore = LocalDate.now(ZoneOffset.UTC);
        useCase.execute(userId);
        LocalDate utcAfter = LocalDate.now(ZoneOffset.UTC);

        ArgumentCaptor<LocalDate> dailyFrom = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> dailyTo = ArgumentCaptor.forClass(LocalDate.class);
        verify(llmUsageRepository).getDailyUsage(eq(userId), dailyFrom.capture(), dailyTo.capture());

        LocalDate today = dailyTo.getValue();
        assertThat(today).isIn(utcBefore, utcAfter);
        assertThat(dailyFrom.getValue()).isEqualTo(today.minusDays(30));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(llmUsageRepository, org.mockito.Mockito.times(3))
            .getTotalRawCost(eq(userId), from.capture(), eq(today));

        // minusDays(6), not (7): the window is inclusive at both ends, so seven days is
        // today-6 … today. minusDays(7) would span eight.
        assertThat(from.getAllValues().get(0)).as("weekly").isEqualTo(today.minusDays(6));
        assertThat(from.getAllValues().get(1)).as("monthly").isEqualTo(today.withDayOfMonth(1));
        assertThat(from.getAllValues().get(2)).as("all-time").isEqualTo(LocalDate.of(2020, 1, 1));
    }

    @Test
    @DisplayName("a null userId returns empty statistics rather than throwing")
    void nullUserIdReturnsEmpty() {
        LLMUsageStatistics stats = useCase.execute(null);

        assertThat(stats.dailyUsage()).isEmpty();
        assertThat(stats.weeklyTotalUsd()).isEqualByComparingTo("0");
        verifyNoInteractions(llmUsageRepository);
    }

    @Test
    @DisplayName("daily rows keep their raw cost and carry the marked-up billed cost alongside")
    void dailyRowsCarryRawAndBilledCost() {
        LocalDate day = LocalDate.of(2026, 7, 30);
        when(llmUsageRepository.getDailyUsage(any(), any(), any()))
            .thenReturn(List.of(new DailyUsageRecord(day, new BigDecimal("2.00"), 1500)));
        when(llmUsageRepository.getTotalRawCost(any(), any(), any())).thenReturn(new BigDecimal("4.00"));

        LLMUsageStatistics stats = useCase.execute(userId);

        assertThat(stats.dailyUsage()).hasSize(1);
        assertThat(stats.dailyUsage().get(0).date()).isEqualTo(day);
        assertThat(stats.dailyUsage().get(0).rawCostUsd())
            .as("the raw provider cost is preserved, not overwritten by the billed figure")
            .isEqualByComparingTo("2.00");
        assertThat(stats.dailyUsage().get(0).billedCostUsd()).isEqualByComparingTo("3.00");
        assertThat(stats.dailyUsage().get(0).totalTokens()).isEqualTo(1500);
        assertThat(stats.weeklyTotalUsd()).isEqualByComparingTo("6.00");
    }

    /**
     * Models the repository's real window semantics — {@code BETWEEN}, inclusive at both ends, as
     * pinned against Postgres by {@code LLMUsageLedgerIntegrationTest} — so the totals this use
     * case reports become a function of the window it asks for. Without this, a change to the
     * window would leave every total assertion silently unchanged.
     */
    private void stubLedgerWith(Map<LocalDate, String> rawCostByDay) {
        when(llmUsageRepository.getDailyUsage(any(), any(), any())).thenReturn(List.of());
        when(llmUsageRepository.getTotalRawCost(eq(userId), any(), any())).thenAnswer(call -> {
            LocalDate from = call.getArgument(1);
            LocalDate to = call.getArgument(2);
            return rawCostByDay.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(from) && !e.getKey().isAfter(to))
                .map(e -> new BigDecimal(e.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        });
    }

    /**
     * "Last 7 days" must mean seven days. The window is inclusive at both ends, so it runs
     * {@code today-6 … today}; {@code minusDays(7)} spanned eight calendar days, and the budget
     * page divides this total by 7 to advertise a daily burn rate — overstating it by up to ⅛.
     *
     * <p>The all-time figure is the audited standalone install's real one: {@code 0.12864991} raw
     * in the ledger, {@code 0.19297487} once the 1.50 markup is applied at scale 8.</p>
     *
     * <p>Anchored relative to today rather than on July's calendar dates, so it keeps testing the
     * window rather than quietly passing once those dates age out of it.</p>
     */
    @Test
    @DisplayName("the weekly window spans exactly seven calendar days")
    void weeklyWindowSpansExactlySevenCalendarDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        stubLedgerWith(Map.of(
            today.minusDays(8), "0.03122190",
            today.minusDays(7), "0.09742801"
        ));

        LLMUsageStatistics stats = useCase.execute(userId);

        assertThat(stats.weeklyTotalUsd())
            .as("spend 7 days back is outside a seven-day window ending today")
            .isEqualByComparingTo("0");
        assertThat(stats.allTimeTotalUsd())
            .as("the audited install's all-time figure: 0.12864991 raw at the 1.50 markup")
            .isEqualByComparingTo("0.19297487");
    }

    /**
     * The inside edge of the same window: spend six days back is the oldest day still counted.
     * Together with the test above this brackets the boundary from both sides, so neither an
     * off-by-one back to eight days nor a slip to six can pass.
     */
    @Test
    @DisplayName("the oldest day the weekly window counts is six days back")
    void weeklyWindowCountsTheSixthDayBack() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        stubLedgerWith(Map.of(today.minusDays(6), "0.09742801"));

        assertThat(useCase.execute(userId).weeklyTotalUsd())
            .isEqualByComparingTo("0.14614202");
    }

    /**
     * The monthly window is calendar-based, not rolling, which is why the audited install reported
     * {@code monthlyTotalUsd 0} on 2 August for spend incurred on 25–26 July.
     */
    @Test
    @DisplayName("the monthly window starts at the 1st, so last month's spend is excluded")
    void monthlyWindowExcludesThePreviousMonth() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate lastMonth = today.withDayOfMonth(1).minusDays(5);
        stubLedgerWith(Map.of(lastMonth, "0.12864991"));

        LLMUsageStatistics stats = useCase.execute(userId);

        assertThat(stats.monthlyTotalUsd())
            .as("spend before the 1st is not this month's spend")
            .isEqualByComparingTo("0");
        assertThat(stats.allTimeTotalUsd())
            .as("all-time still sees it — the money was spent")
            .isEqualByComparingTo("0.19297487");
    }

    @Test
    @DisplayName("a null total reads as zero rather than propagating")
    void nullTotalsBecomeZero() {
        when(llmUsageRepository.getDailyUsage(any(), any(), any())).thenReturn(List.of());
        when(llmUsageRepository.getTotalRawCost(any(), any(), any())).thenReturn(null);

        LLMUsageStatistics stats = useCase.execute(userId);

        assertThat(stats.weeklyTotalUsd()).isEqualByComparingTo("0");
        assertThat(stats.monthlyTotalUsd()).isEqualByComparingTo("0");
        assertThat(stats.allTimeTotalUsd()).isEqualByComparingTo("0");
    }
}
