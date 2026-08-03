package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record BenchmarkId(UUID value) {

    public BenchmarkId {
        if (value == null) {
            throw new IllegalArgumentException("BenchmarkId cannot be null");
        }
    }

    public static BenchmarkId generate() {
        return new BenchmarkId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "BenchmarkId[" + value + "]";
    }
}
