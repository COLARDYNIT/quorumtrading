package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record InvestmentPlanId(UUID value) {
    public InvestmentPlanId {
        if (value == null) {
            throw new IllegalArgumentException("InvestmentPlanId value cannot be null");
        }
    }

    public static InvestmentPlanId generate() {
        return new InvestmentPlanId(UUID.randomUUID());
    }

    public static InvestmentPlanId fromString(String value) {
        return new InvestmentPlanId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "InvestmentPlanId[" + value + "]";
    }
}
