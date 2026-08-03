package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record ApiTokenId(UUID value) {
    public ApiTokenId {
        if (value == null) {
            throw new IllegalArgumentException("ApiTokenId value cannot be null");
        }
    }

    public static ApiTokenId generate() {
        return new ApiTokenId(UUID.randomUUID());
    }

    public static ApiTokenId fromString(String value) {
        return new ApiTokenId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ApiTokenId[" + value + "]";
    }
}
