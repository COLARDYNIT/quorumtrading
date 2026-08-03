package com.quorumtrading.model.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object for ScheduledSimulation identity.
 */
public final class ScheduledSimulationId {
    private final UUID value;

    private ScheduledSimulationId(UUID value) {
        this.value = Objects.requireNonNull(value, "ScheduledSimulationId value cannot be null");
    }

    public static ScheduledSimulationId generate() {
        return new ScheduledSimulationId(UUID.randomUUID());
    }

    public static ScheduledSimulationId of(UUID value) {
        return new ScheduledSimulationId(value);
    }

    public static ScheduledSimulationId of(String value) {
        return new ScheduledSimulationId(UUID.fromString(value));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScheduledSimulationId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
