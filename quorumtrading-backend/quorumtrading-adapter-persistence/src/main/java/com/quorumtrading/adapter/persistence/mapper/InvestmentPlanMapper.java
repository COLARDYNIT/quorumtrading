package com.quorumtrading.adapter.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.persistence.entity.InvestmentPlanJpaEntity;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InvestmentPlanMapper {

    @Inject
    ObjectMapper objectMapper;

    public InvestmentPlanJpaEntity toJpaEntity(InvestmentPlan plan) {
        InvestmentPlanJpaEntity e = new InvestmentPlanJpaEntity();
        e.setId(plan.getId().value());
        e.setUserId(plan.getUserId().value());
        e.setBrokerAccountId(plan.getBrokerAccountId().value());
        e.setBudgetMode(plan.getBudgetMode().name());
        if (plan.getAmount() != null) {
            e.setAmount(plan.getAmount().amount());
            e.setCurrency(plan.getAmount().currency().name());
        } else {
            e.setCurrency("USD");
        }
        e.setDayOfMonth((short) plan.getDayOfMonth());
        e.setPickerPolicy(plan.getPickerPolicy().name());
        e.setPreferences(serializePreferences(plan.getPreferences()));
        e.setStatus(plan.getStatus().name());
        e.setNextExecutionAt(plan.getNextExecutionAt());
        e.setLastExecutionAt(plan.getLastExecutionAt());
        e.setCreatedAt(plan.getCreatedAt());
        e.setUpdatedAt(plan.getUpdatedAt());
        return e;
    }

    public InvestmentPlan toDomain(InvestmentPlanJpaEntity e) {
        Money amount = e.getAmount() != null
                ? new Money(e.getAmount(), Money.Currency.valueOf(e.getCurrency()))
                : null;
        return InvestmentPlan.reconstitute(
                new InvestmentPlanId(e.getId()),
                new UserId(e.getUserId()),
                new BrokerAccountId(e.getBrokerAccountId()),
                BudgetMode.valueOf(e.getBudgetMode()),
                amount,
                e.getDayOfMonth(),
                PickerPolicy.valueOf(e.getPickerPolicy()),
                deserializePreferences(e.getPreferences()),
                InvestmentPlanStatus.valueOf(e.getStatus()),
                e.getNextExecutionAt(),
                e.getLastExecutionAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private String serializePreferences(InvestmentPlanPreferences prefs) {
        if (prefs == null) return "{}";
        try {
            return objectMapper.writeValueAsString(prefs);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize plan preferences", ex);
        }
    }

    private InvestmentPlanPreferences deserializePreferences(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return InvestmentPlanPreferences.EMPTY;
        }
        try {
            return objectMapper.readValue(json, InvestmentPlanPreferences.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize plan preferences", ex);
        }
    }
}
