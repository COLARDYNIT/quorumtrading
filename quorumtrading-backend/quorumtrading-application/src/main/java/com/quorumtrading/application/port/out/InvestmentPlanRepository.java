package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvestmentPlanRepository {

    InvestmentPlan save(InvestmentPlan plan);

    Optional<InvestmentPlan> findById(InvestmentPlanId id);

    /** All plans owned by a user, newest-first. */
    List<InvestmentPlan> findByUserId(UserId userId, Optional<InvestmentPlanStatus> statusFilter);

    /**
     * Plans that should fire at or before {@code asOf} — ACTIVE status only.
     * Used by the scheduler in Phase 5.
     */
    List<InvestmentPlan> findDueForExecution(Instant asOf, int limit);

    boolean deleteById(InvestmentPlanId id);
}
