package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record SimulationId(UUID value) {

    public SimulationId {
        if (value == null) {
            throw new IllegalArgumentException("SimulationId cannot be null");
        }
    }

    public static SimulationId generate() {
        return new SimulationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "SimulationId[" + value + "]";
    }
}
