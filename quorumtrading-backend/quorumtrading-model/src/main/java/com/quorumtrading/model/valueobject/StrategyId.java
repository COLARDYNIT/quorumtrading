package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record StrategyId(UUID value) {

    public StrategyId {
        if (value == null) {
            throw new IllegalArgumentException("StrategyId cannot be null");
        }
    }

    public static StrategyId generate() {
        return new StrategyId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "StrategyId[" + value + "]";
    }
}
