package com.quorumtrading.adapter.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.persistence.entity.InvestmentAllocationJpaEntity;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class InvestmentAllocationMapper {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    @Inject
    ObjectMapper objectMapper;

    public InvestmentAllocationJpaEntity toJpaEntity(InvestmentAllocation a) {
        InvestmentAllocationJpaEntity e = new InvestmentAllocationJpaEntity();
        e.setId(a.getId().value());
        e.setPlanId(a.getPlanId().value());
        e.setUserId(a.getUserId().value());
        e.setExecutionDate(a.getExecutionDate());
        e.setStatus(a.getStatus().name());
        e.setIntendedAmount(a.getIntendedAmount().amount());
        e.setCurrency(a.getIntendedAmount().currency().name());
        if (a.getActualAmount() != null) {
            e.setActualAmount(a.getActualAmount().amount());
        }
        if (a.getDeployedAmount() != null) {
            e.setDeployedAmount(a.getDeployedAmount().amount());
        }
        // NULL/FALSE both mean "no whole-share rounding"; store NULL for the unconstrained case
        // so legacy fractional rows stay byte-identical (V118 column default is NULL).
        e.setFractionalConstraint(a.isFractionalConstraint() ? Boolean.TRUE : null);
        if (a.getChosenStrategyId() != null) {
            e.setChosenStrategyId(a.getChosenStrategyId().value());
        }
        e.setPickerReasoning(a.getPickerReasoning());
        e.setPickerLlmCostUsd(a.getPickerLlmCostUsd());
        if (a.getAction() != null) {
            e.setAction(a.getAction().name());
        }
        if (a.getTargetSimulationId() != null) {
            e.setTargetSimulationId(a.getTargetSimulationId().value());
        }
        if (a.getContributionId() != null) {
            e.setContributionId(a.getContributionId().value());
        }
        e.setBrokerOrderRefs(serialize(a.getBrokerOrderRefs()));
        e.setFailureReason(a.getFailureReason());
        e.setCreatedAt(a.getCreatedAt());
        return e;
    }

    public InvestmentAllocation toDomain(InvestmentAllocationJpaEntity e) {
        Money.Currency currency = Money.Currency.valueOf(e.getCurrency());
        Money intended = new Money(e.getIntendedAmount(), currency);
        Money actual = e.getActualAmount() != null ? new Money(e.getActualAmount(), currency) : null;
        Money deployed = e.getDeployedAmount() != null ? new Money(e.getDeployedAmount(), currency) : null;
        boolean fractionalConstraint = Boolean.TRUE.equals(e.getFractionalConstraint());
        return InvestmentAllocation.reconstitute(
                new InvestmentAllocationId(e.getId()),
                new InvestmentPlanId(e.getPlanId()),
                new UserId(e.getUserId()),
                e.getExecutionDate(),
                AllocationStatus.valueOf(e.getStatus()),
                intended,
                actual,
                e.getChosenStrategyId() != null ? new StrategyId(e.getChosenStrategyId()) : null,
                e.getPickerReasoning(),
                e.getPickerLlmCostUsd(),
                e.getAction() != null ? AllocationAction.valueOf(e.getAction()) : null,
                e.getTargetSimulationId() != null ? new SimulationId(e.getTargetSimulationId()) : null,
                e.getContributionId() != null ? new ContributionId(e.getContributionId()) : null,
                deserialize(e.getBrokerOrderRefs()),
                e.getFailureReason(),
                deployed,
                fractionalConstraint,
                e.getCreatedAt()
        );
    }

    private String serialize(List<String> refs) {
        if (refs == null || refs.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize broker order refs", ex);
        }
    }

    private List<String> deserialize(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize broker order refs", ex);
        }
    }
}
