package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record ChallengeId(UUID value) {

    public ChallengeId {
        if (value == null) {
            throw new IllegalArgumentException("ChallengeId cannot be null");
        }
    }

    public static ChallengeId generate() {
        return new ChallengeId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "ChallengeId[" + value + "]";
    }
}
