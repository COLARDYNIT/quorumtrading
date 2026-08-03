package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.InvestmentAllocation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Audit row for one investment-plan execution attempt")
public record InvestmentAllocationResponse(
        String id,
        String planId,
        String userId,
        Instant executionDate,
        String status,
        BigDecimal intendedAmount,
        BigDecimal actualAmount,
        String currency,
        String chosenStrategyId,
        String pickerReasoning,
        BigDecimal pickerLlmCostUsd,
        String action,
        String targetSimulationId,
        String contributionId,
        List<String> brokerOrderRefs,
        String failureReason,
        Instant createdAt
) {
    public static InvestmentAllocationResponse fromDomain(InvestmentAllocation a) {
        return new InvestmentAllocationResponse(
                a.getId().value().toString(),
                a.getPlanId().value().toString(),
                a.getUserId().value().toString(),
                a.getExecutionDate(),
                a.getStatus().name(),
                a.getIntendedAmount().amount(),
                a.getActualAmount() != null ? a.getActualAmount().amount() : null,
                a.getIntendedAmount().currency().name(),
                a.getChosenStrategyId() != null ? a.getChosenStrategyId().value().toString() : null,
                a.getPickerReasoning(),
                a.getPickerLlmCostUsd(),
                a.getAction() != null ? a.getAction().name() : null,
                a.getTargetSimulationId() != null ? a.getTargetSimulationId().value().toString() : null,
                a.getContributionId() != null ? a.getContributionId().value().toString() : null,
                a.getBrokerOrderRefs(),
                a.getFailureReason(),
                a.getCreatedAt());
    }
}
