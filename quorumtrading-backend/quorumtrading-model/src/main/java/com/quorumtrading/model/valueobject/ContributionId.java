package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record ContributionId(UUID value) {
    public ContributionId {
        if (value == null) {
            throw new IllegalArgumentException("ContributionId value cannot be null");
        }
    }

    public static ContributionId generate() {
        return new ContributionId(UUID.randomUUID());
    }

    public static ContributionId fromString(String value) {
        return new ContributionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ContributionId[" + value + "]";
    }
}
