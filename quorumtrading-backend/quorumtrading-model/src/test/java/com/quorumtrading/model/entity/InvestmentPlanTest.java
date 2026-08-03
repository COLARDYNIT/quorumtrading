package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvestmentPlanTest {

    private InvestmentPlan create(BudgetMode mode, Money amount, int dayOfMonth, Instant now) {
        return InvestmentPlan.create(
                InvestmentPlanId.generate(),
                new UserId(UUID.randomUUID()),
                new BrokerAccountId(UUID.randomUUID()),
                mode, amount, dayOfMonth, PickerPolicy.AI_PICK,
                InvestmentPlanPreferences.EMPTY, now);
    }

    @Test
    void createsActivePlanWithFutureNextExecution() {
        Instant now = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
        InvestmentPlan plan = create(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200")), 20, now);
        assertEquals(InvestmentPlanStatus.ACTIVE, plan.getStatus());
        assertNotNull(plan.getNextExecutionAt());
        assertTrue(plan.getNextExecutionAt().isAfter(now));
    }

    @Test
    void nextExecutionRollsToNextMonthWhenDayHasPassed() {
        Instant now = OffsetDateTime.of(2026, 1, 25, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
        InvestmentPlan plan = create(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200")), 10, now);
        LocalDate next = plan.getNextExecutionAt().atOffset(ZoneOffset.UTC).toLocalDate();
        assertEquals(2026, next.getYear());
        assertEquals(2, next.getMonthValue());
        assertEquals(10, next.getDayOfMonth());
    }

    @Test
    void fixedAmountRequiresPositiveAmount() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> create(BudgetMode.FIXED_AMOUNT, null, 1, now));
        assertThrows(IllegalArgumentException.class,
                () -> create(BudgetMode.FIXED_AMOUNT, Money.usd(BigDecimal.ZERO), 1, now));
    }

    @Test
    void allAvailableAcceptsNullAmount() {
        Instant now = Instant.now();
        InvestmentPlan plan = create(BudgetMode.ALL_AVAILABLE, null, 1, now);
        assertNull(plan.getAmount());
    }

    @Test
    void dayOfMonthMustBe1To28() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> create(BudgetMode.FIXED_AMOUNT, Money.usd(BigDecimal.TEN), 0, now));
        assertThrows(IllegalArgumentException.class,
                () -> create(BudgetMode.FIXED_AMOUNT, Money.usd(BigDecimal.TEN), 29, now));
        // 1 and 28 are both legal:
        create(BudgetMode.FIXED_AMOUNT, Money.usd(BigDecimal.TEN), 1, now);
        create(BudgetMode.FIXED_AMOUNT, Money.usd(BigDecimal.TEN), 28, now);
    }

    // ---- State machine ----

    @Test
    void pauseAndResumeRoundTrip() {
        Instant now = Instant.now();
        InvestmentPlan plan = create(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("100")), 15, now);
        plan.pause(now);
        assertEquals(InvestmentPlanStatus.PAUSED, plan.getStatus());
        plan.resume(now.plus(1, ChronoUnit.HOURS));
        assertEquals(InvestmentPlanStatus.ACTIVE, plan.getStatus());
    }

    @Test
    void terminateIsTerminal() {
        Instant now = Instant.now();
        InvestmentPlan plan = create(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("100")), 15, now);
        plan.terminate(now);
        assertEquals(InvestmentPlanStatus.TERMINATED, plan.getStatus());
        assertNull(plan.getNextExecutionAt());
        assertThrows(IllegalStateException.class, () -> plan.pause(now));
        assertThrows(IllegalStateException.class, () -> plan.resume(now));
        assertThrows(IllegalStateException.class, () -> plan.updateSchedule(5, now));
    }

    @Test
    void isDueAtRespectsStatusAndTime() {
        // A FIXED instant, not Instant.now(). computeNextExecution deliberately fires *today* when
        // the target day-of-month is today, so this assertion held on 30 days a month and failed on
        // the 1st — a time bomb that made main red for every PR opened on the 1st, in a test that
        // has nothing to do with dates. The 10th of a month, with the plan due on the 15th, is
        // unambiguously "not due yet" whatever day the suite happens to run.
        Instant now = Instant.parse("2026-03-10T09:00:00Z");
        InvestmentPlan plan = create(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("100")), 15, now);
        assertFalse(plan.isDueAt(now), "Newly created plan with future next-exec should not be due yet");

        // Force-advance the schedule so isDueAt fires.
        Instant past = plan.getNextExecutionAt().plus(1, ChronoUnit.HOURS);
        assertTrue(plan.isDueAt(past));

        plan.pause(now);
        assertFalse(plan.isDueAt(past), "Paused plans are never due");
    }

    @Test
    void recordExecutionTickAdvancesNextExecution() {
        Instant now = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
        InvestmentPlan plan = create(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("100")), 1, now);
        Instant before = plan.getNextExecutionAt();
        plan.recordExecutionTick(before.plus(1, ChronoUnit.HOURS));
        LocalDate nextDate = plan.getNextExecutionAt().atOffset(ZoneOffset.UTC).toLocalDate();
        assertEquals(2026, nextDate.getYear());
        assertEquals(2, nextDate.getMonthValue());
        assertEquals(1, nextDate.getDayOfMonth());
        assertNotNull(plan.getLastExecutionAt());
    }

    // ---- Preferences ----

    @Test
    void blockedTickerOverridesAllowedTicker() {
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, java.util.List.of("AAPL", "MSFT"), java.util.List.of("AAPL"), null, true);
        assertFalse(prefs.isTickerAllowed("AAPL"));
        assertTrue(prefs.isTickerAllowed("MSFT"));
        assertFalse(prefs.isTickerAllowed("TSLA")); // not in allowed
    }

    @Test
    void emptyAllowedTickersMeansAnythingNonBlocked() {
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, java.util.List.of(), java.util.List.of("XYZ"), null, true);
        assertTrue(prefs.isTickerAllowed("AAPL"));
        assertFalse(prefs.isTickerAllowed("XYZ"));
    }

    // ---- Contradictory ticker policy ----
    //
    // A ticker in both lists is unreachable, because isTickerAllowed lets the block win. A plan
    // whose whole allowlist is blocked permits nothing and can never fund anything, yet it is
    // created ACTIVE with a nextExecutionAt and — with automatic execution off by default — looks
    // healthy indefinitely. The only feedback used to arrive per-strategy at execution time and
    // blamed the strategy for a fault in the plan's own configuration.

    private InvestmentPlan planWithPolicy(java.util.List<String> allowed, java.util.List<String> blocked) {
        return InvestmentPlan.create(
                InvestmentPlanId.generate(),
                new UserId(UUID.randomUUID()),
                new BrokerAccountId(UUID.randomUUID()),
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("200")), 15,
                PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, allowed, blocked, null, true),
                Instant.parse("2026-05-15T00:00:00Z"));
    }

    @Test
    void createRejectsFullyUnsatisfiableTickerPolicy() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> planWithPolicy(java.util.List.of("AAPL"), java.util.List.of("AAPL")));
        assertTrue(e.getMessage().contains("AAPL"), "must name the offending ticker: " + e.getMessage());
        assertTrue(e.getMessage().contains("every allowed ticker is blocked"),
                "must say the policy permits nothing: " + e.getMessage());
        assertTrue(e.getMessage().contains("not in any strategy"),
                "must attribute the fault to the plan's policy, not a strategy: " + e.getMessage());
    }

    @Test
    void createRejectsPartiallyOverlappingTickerPolicyWithADifferentRemedy() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> planWithPolicy(java.util.List.of("AAPL", "MSFT"), java.util.List.of("AAPL")));
        assertTrue(e.getMessage().contains("AAPL"), "must name the offending ticker: " + e.getMessage());
        assertTrue(e.getMessage().contains("the rest of allowedTickers still can"),
                "partial overlap must not claim the plan is dead: " + e.getMessage());
        assertFalse(e.getMessage().contains("every allowed ticker is blocked"),
                "partial overlap is a different fault from the total case: " + e.getMessage());
    }

    @Test
    void tickerPolicyOverlapIsDetectedCaseInsensitively() {
        // isTickerAllowed compares with equalsIgnoreCase, so the guard must too — otherwise
        // 'aapl' + 'AAPL' slips through and produces exactly the dead plan we are refusing.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> planWithPolicy(java.util.List.of("aapl"), java.util.List.of("AAPL")));
        assertTrue(e.getMessage().contains("AAPL"), "must name the offending ticker: " + e.getMessage());
    }

    @Test
    void disjointTickerPolicyIsAccepted() {
        InvestmentPlan plan = planWithPolicy(java.util.List.of("AAPL", "MSFT"), java.util.List.of("GME"));
        assertEquals(InvestmentPlanStatus.ACTIVE, plan.getStatus());
    }

    @Test
    void absentPreferencesMeanNoTickerPolicyAtAll() {
        // Null preferences are legal on both paths and mean "no policy", so the guard must not
        // trip over them on its way to the EMPTY default.
        InvestmentPlan plan = InvestmentPlan.create(
                InvestmentPlanId.generate(),
                new UserId(UUID.randomUUID()),
                new BrokerAccountId(UUID.randomUUID()),
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("200")), 15,
                PickerPolicy.AI_PICK, null, Instant.parse("2026-05-15T00:00:00Z"));
        plan.updatePreferences(null, Instant.parse("2026-05-16T00:00:00Z"));
        assertEquals(InvestmentPlanPreferences.EMPTY, plan.getPreferences());
    }

    @Test
    void updatePreferencesRejectsWhatCreateRejects() {
        InvestmentPlan plan = planWithPolicy(java.util.List.of("AAPL"), java.util.List.of("GME"));
        Instant later = Instant.parse("2026-05-16T00:00:00Z");
        InvestmentPlanPreferences contradiction = new InvestmentPlanPreferences(
                null, java.util.List.of("AAPL"), java.util.List.of("aapl"), null, true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> plan.updatePreferences(contradiction, later));
        assertTrue(e.getMessage().contains("AAPL"), "must name the offending ticker: " + e.getMessage());
        assertEquals(java.util.List.of("GME"), plan.getPreferences().blockedTickers(),
                "a rejected update must leave the stored policy untouched");
    }

    @Test
    void reconstituteToleratesAlreadyStoredContradiction() {
        // Rows written before the guard existed must stay readable, or their owner cannot even
        // delete the broken plan.
        InvestmentPlan plan = InvestmentPlan.reconstitute(
                InvestmentPlanId.generate(),
                new UserId(UUID.randomUUID()),
                new BrokerAccountId(UUID.randomUUID()),
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("200")), 15,
                PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, java.util.List.of("AAPL"),
                        java.util.List.of("AAPL"), null, true),
                InvestmentPlanStatus.ACTIVE,
                Instant.parse("2026-06-15T00:00:00Z"), null,
                Instant.parse("2026-05-15T00:00:00Z"), Instant.parse("2026-05-15T00:00:00Z"));
        assertEquals(InvestmentPlanStatus.ACTIVE, plan.getStatus());
    }
}
