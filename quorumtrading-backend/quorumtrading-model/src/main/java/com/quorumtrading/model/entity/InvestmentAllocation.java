package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Audit row for one investment-plan execution attempt — success, skip, or failure.
 *
 * <p><b>Aggregate Root.</b> Allocations are immutable once written; only
 * {@code FAILED_BROKER_ERROR} rows can be reattempted (a separate row is written
 * for the retry).</p>
 *
 * <p>Every plan tick writes exactly one row regardless of outcome. The row is
 * the load-bearing record for "what did the plan do, when, and why" — frontend
 * timelines and MCP audit tools read this stream directly.</p>
 */
public class InvestmentAllocation implements AggregateRoot {

    private final InvestmentAllocationId id;
    private final InvestmentPlanId planId;
    private final UserId userId;
    private final Instant executionDate;
    private final AllocationStatus status;
    private final Money intendedAmount;
    private final Money actualAmount;        // null on skip/fail
    private final StrategyId chosenStrategyId; // null when no strategy was chosen
    private final String pickerReasoning;
    private final BigDecimal pickerLlmCostUsd;
    private final AllocationAction action;   // null on skip/fail
    private final SimulationId targetSimulationId; // null on skip/fail
    private final ContributionId contributionId;   // populated only on EXECUTED
    private final List<String> brokerOrderRefs;
    private final String failureReason;
    private final Money deployedAmount;        // null unless the broker forced whole-share rounding
    private final boolean fractionalConstraint; // true when the executing broker rounds down to whole shares
    private final Instant createdAt;

    private InvestmentAllocation(InvestmentAllocationId id, InvestmentPlanId planId, UserId userId,
                                 Instant executionDate, AllocationStatus status,
                                 Money intendedAmount, Money actualAmount,
                                 StrategyId chosenStrategyId, String pickerReasoning,
                                 BigDecimal pickerLlmCostUsd, AllocationAction action,
                                 SimulationId targetSimulationId, ContributionId contributionId,
                                 List<String> brokerOrderRefs, String failureReason,
                                 Money deployedAmount, boolean fractionalConstraint,
                                 Instant createdAt) {
        this.id = id;
        this.planId = planId;
        this.userId = userId;
        this.executionDate = executionDate;
        this.status = status;
        this.intendedAmount = intendedAmount;
        this.actualAmount = actualAmount;
        this.chosenStrategyId = chosenStrategyId;
        this.pickerReasoning = pickerReasoning;
        this.pickerLlmCostUsd = pickerLlmCostUsd == null ? BigDecimal.ZERO : pickerLlmCostUsd;
        this.action = action;
        this.targetSimulationId = targetSimulationId;
        this.contributionId = contributionId;
        this.brokerOrderRefs = brokerOrderRefs == null ? List.of() : List.copyOf(brokerOrderRefs);
        this.failureReason = failureReason;
        this.deployedAmount = deployedAmount;
        this.fractionalConstraint = fractionalConstraint;
        this.createdAt = createdAt;
    }

    /**
     * Builder-style factory — pass nulls for fields that don't apply to your
     * outcome. The construction-time validators enforce per-status invariants
     * (EXECUTED needs a contributionId; skips do not).
     */
    public static InvestmentAllocation create(InvestmentAllocationId id, InvestmentPlanId planId,
                                              UserId userId, Instant executionDate,
                                              AllocationStatus status,
                                              Money intendedAmount, Money actualAmount,
                                              StrategyId chosenStrategyId, String pickerReasoning,
                                              BigDecimal pickerLlmCostUsd, AllocationAction action,
                                              SimulationId targetSimulationId,
                                              ContributionId contributionId,
                                              List<String> brokerOrderRefs,
                                              String failureReason, Instant now) {
        return create(id, planId, userId, executionDate, status, intendedAmount, actualAmount,
                chosenStrategyId, pickerReasoning, pickerLlmCostUsd, action, targetSimulationId,
                contributionId, brokerOrderRefs, failureReason, null, false, now);
    }

    /**
     * Fractional-aware factory. {@code deployedAmount} records the cash actually put to work
     * after whole-share rounding (null when the contribution fully deployed);
     * {@code fractionalConstraint} is true when the executing broker forced whole-share
     * rounding (Saxo equities/ETFs, D3). The {@code EXECUTED_PARTIAL_DEPLOY} status carries
     * the same per-status invariants as {@code EXECUTED} (the contribution still funded).
     */
    public static InvestmentAllocation create(InvestmentAllocationId id, InvestmentPlanId planId,
                                              UserId userId, Instant executionDate,
                                              AllocationStatus status,
                                              Money intendedAmount, Money actualAmount,
                                              StrategyId chosenStrategyId, String pickerReasoning,
                                              BigDecimal pickerLlmCostUsd, AllocationAction action,
                                              SimulationId targetSimulationId,
                                              ContributionId contributionId,
                                              List<String> brokerOrderRefs,
                                              String failureReason,
                                              Money deployedAmount, boolean fractionalConstraint,
                                              Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(executionDate, "executionDate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(intendedAmount, "intendedAmount");
        if (status == AllocationStatus.EXECUTED || status == AllocationStatus.EXECUTED_PARTIAL_DEPLOY) {
            if (actualAmount == null) {
                throw new IllegalArgumentException("EXECUTED allocation requires actualAmount");
            }
            if (action == null) {
                throw new IllegalArgumentException("EXECUTED allocation requires action");
            }
            if (targetSimulationId == null) {
                throw new IllegalArgumentException("EXECUTED allocation requires targetSimulationId");
            }
            if (contributionId == null && action == AllocationAction.TOP_UP_EXISTING) {
                throw new IllegalArgumentException(
                        "TOP_UP_EXISTING allocation requires contributionId");
            }
        }
        return new InvestmentAllocation(id, planId, userId, executionDate, status,
                intendedAmount, actualAmount, chosenStrategyId, pickerReasoning,
                pickerLlmCostUsd, action, targetSimulationId, contributionId,
                brokerOrderRefs, failureReason, deployedAmount, fractionalConstraint, now);
    }

    public static InvestmentAllocation reconstitute(InvestmentAllocationId id,
                                                    InvestmentPlanId planId, UserId userId,
                                                    Instant executionDate, AllocationStatus status,
                                                    Money intendedAmount, Money actualAmount,
                                                    StrategyId chosenStrategyId, String pickerReasoning,
                                                    BigDecimal pickerLlmCostUsd,
                                                    AllocationAction action,
                                                    SimulationId targetSimulationId,
                                                    ContributionId contributionId,
                                                    List<String> brokerOrderRefs,
                                                    String failureReason, Instant createdAt) {
        return reconstitute(id, planId, userId, executionDate, status, intendedAmount, actualAmount,
                chosenStrategyId, pickerReasoning, pickerLlmCostUsd, action, targetSimulationId,
                contributionId, brokerOrderRefs, failureReason, null, false, createdAt);
    }

    public static InvestmentAllocation reconstitute(InvestmentAllocationId id,
                                                    InvestmentPlanId planId, UserId userId,
                                                    Instant executionDate, AllocationStatus status,
                                                    Money intendedAmount, Money actualAmount,
                                                    StrategyId chosenStrategyId, String pickerReasoning,
                                                    BigDecimal pickerLlmCostUsd,
                                                    AllocationAction action,
                                                    SimulationId targetSimulationId,
                                                    ContributionId contributionId,
                                                    List<String> brokerOrderRefs,
                                                    String failureReason,
                                                    Money deployedAmount, boolean fractionalConstraint,
                                                    Instant createdAt) {
        return new InvestmentAllocation(id, planId, userId, executionDate, status,
                intendedAmount, actualAmount, chosenStrategyId, pickerReasoning,
                pickerLlmCostUsd, action, targetSimulationId, contributionId,
                brokerOrderRefs, failureReason, deployedAmount, fractionalConstraint, createdAt);
    }

    public InvestmentAllocationId getId() { return id; }
    public InvestmentPlanId getPlanId() { return planId; }
    public UserId getUserId() { return userId; }
    public Instant getExecutionDate() { return executionDate; }
    public AllocationStatus getStatus() { return status; }
    public Money getIntendedAmount() { return intendedAmount; }
    public Money getActualAmount() { return actualAmount; }
    public StrategyId getChosenStrategyId() { return chosenStrategyId; }
    public String getPickerReasoning() { return pickerReasoning; }
    public BigDecimal getPickerLlmCostUsd() { return pickerLlmCostUsd; }
    public AllocationAction getAction() { return action; }
    public SimulationId getTargetSimulationId() { return targetSimulationId; }
    public ContributionId getContributionId() { return contributionId; }
    public List<String> getBrokerOrderRefs() { return brokerOrderRefs; }
    public String getFailureReason() { return failureReason; }
    /** Cash actually deployed this tick after whole-share rounding; null when fully deployed. */
    public Money getDeployedAmount() { return deployedAmount; }
    /** True when the executing broker forced whole-share rounding (Saxo equities/ETFs, D3). */
    public boolean isFractionalConstraint() { return fractionalConstraint; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean wasExecuted() {
        return status == AllocationStatus.EXECUTED
                || status == AllocationStatus.EXECUTED_PARTIAL_DEPLOY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestmentAllocation that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
