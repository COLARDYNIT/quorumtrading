package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvestmentAllocationRepository {

    InvestmentAllocation save(InvestmentAllocation allocation);

    Optional<InvestmentAllocation> findById(InvestmentAllocationId id);

    /** Allocations for a single plan, newest first. */
    List<InvestmentAllocation> findByPlanId(InvestmentPlanId planId, int limit);

    /** Cross-plan history for a user, newest first — backs the global DCA timeline. */
    List<InvestmentAllocation> findByUserId(UserId userId, int limit);

    /**
     * The plan's earliest <em>funded</em> allocation inside a half-open execution-date window, if
     * any — the same-day idempotency probe for plan execution.
     *
     * <p>"Funded" means {@code EXECUTED} or {@code EXECUTED_PARTIAL_DEPLOY}, i.e. exactly
     * {@link InvestmentAllocation#wasExecuted()}. Skips and failures must not match: a
     * {@code SKIPPED_INSUFFICIENT_FUNDS} tick at 06:00 has to be retryable at 18:00 once the cash
     * lands, and a {@code FAILED_BROKER_ERROR} row is explicitly documented as reattemptable.</p>
     *
     * <p>The window is supplied by the caller rather than derived here, so the definition of a
     * "funding day" lives in one place in the application layer instead of being reinvented in
     * SQL. Backed by {@code idx_investment_allocations_plan (plan_id, execution_date DESC)}.</p>
     *
     * @param planId        the plan to probe
     * @param fromInclusive start of the window (inclusive)
     * @param toExclusive   end of the window (exclusive)
     * @return the earliest funded allocation in the window, or empty if the plan funded nothing
     */
    Optional<InvestmentAllocation> findFundedOnDay(InvestmentPlanId planId,
                                                  Instant fromInclusive,
                                                  Instant toExclusive);
}
