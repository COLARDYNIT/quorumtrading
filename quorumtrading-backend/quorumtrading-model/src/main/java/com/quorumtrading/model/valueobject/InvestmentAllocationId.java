package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record InvestmentAllocationId(UUID value) {
    public InvestmentAllocationId {
        if (value == null) {
            throw new IllegalArgumentException("InvestmentAllocationId value cannot be null");
        }
    }

    public static InvestmentAllocationId generate() {
        return new InvestmentAllocationId(UUID.randomUUID());
    }

    public static InvestmentAllocationId fromString(String value) {
        return new InvestmentAllocationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "InvestmentAllocationId[" + value + "]";
    }
}
