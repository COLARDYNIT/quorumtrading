package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record BrokerAccountId(UUID value) {

    public BrokerAccountId {
        if (value == null) {
            throw new IllegalArgumentException("BrokerAccountId cannot be null");
        }
    }

    public static BrokerAccountId generate() {
        return new BrokerAccountId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "BrokerAccountId[" + value + "]";
    }
}
