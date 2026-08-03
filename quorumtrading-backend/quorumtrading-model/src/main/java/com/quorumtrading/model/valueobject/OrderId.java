package com.quorumtrading.model.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a unique order identifier.
 * Immutable and type-safe alternative to using raw UUID.
 */
public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "OrderId value cannot be null");
    }

    /**
     * Create a new OrderId from an existing UUID.
     */
    public static OrderId from(UUID uuid) {
        return new OrderId(uuid);
    }

    /**
     * Generate a new random OrderId.
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }

    /**
     * Create an OrderId from a string representation.
     */
    public static OrderId fromString(String value) {
        Objects.requireNonNull(value, "OrderId string cannot be null");
        return new OrderId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
