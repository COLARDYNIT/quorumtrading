package com.quorumtrading.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * The single definition of "the day an investment plan funded".
 *
 * <p>An investment plan contributes at most once per calendar day, so every same-day guard in the
 * DCA subsystem is keyed on a day rather than an instant. There are two such guards — the
 * contribution-level one behind the {@code UNIQUE (source_plan_id, contribution_date)} index in
 * {@link SimulationContributionService}, and the allocation-level one in
 * {@link ExecuteInvestmentPlanService} that covers the {@code NEW_SIMULATION} path where no
 * contribution row exists at all. If those two ever disagreed about what "today" means, one of
 * them would let a second funding through, which is precisely the class of bug they exist to
 * prevent. They therefore both truncate through here, and nowhere else.</p>
 *
 * <p>The basis is UTC: {@link ChronoUnit#DAYS} truncation of an {@link Instant} snaps to UTC
 * midnight, matching the {@code timestamptz} column the unique index is built on. It is
 * deliberately not the user's local day — no timezone is carried on a plan, and inventing one
 * here would make the two guards disagree the moment either forgot to apply it.</p>
 */
final class PlanFundingDay {

    private PlanFundingDay() {
    }

    /**
     * Snap an execution instant to the start of its funding day.
     *
     * @param executionInstant the instant a tick fired (or a contribution was requested for)
     * @return UTC midnight of that instant's day
     */
    static Instant startOf(Instant executionInstant) {
        return executionInstant.truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * The exclusive upper bound of the funding day containing {@code executionInstant} — the
     * half-open range {@code [startOf(t), endOf(t))} is what a range query on a raw
     * {@code execution_date} column needs in order to mean "the same day".
     *
     * @param executionInstant the instant a tick fired
     * @return UTC midnight of the following day
     */
    static Instant endOf(Instant executionInstant) {
        return startOf(executionInstant).plus(1, ChronoUnit.DAYS);
    }

    /**
     * The funding day as user-facing copy — {@code 2026-07-30}.
     *
     * <p>Exists because the skip messages interpolated the raw {@link Instant}, which rendered a
     * calendar-day guard as {@code 2026-07-30T18:42:11.123456Z} and invited the reader to think
     * the guard compared instants.</p>
     *
     * @param executionInstant the instant a tick fired
     * @return the ISO date of that instant's UTC day
     */
    static String label(Instant executionInstant) {
        return LocalDate.ofInstant(executionInstant, ZoneOffset.UTC).toString();
    }
}
