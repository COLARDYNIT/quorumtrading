package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record NotificationId(UUID value) {
    public NotificationId {
        if (value == null) {
            throw new IllegalArgumentException("NotificationId value cannot be null");
        }
    }

    public static NotificationId generate() {
        return new NotificationId(UUID.randomUUID());
    }

    public static NotificationId fromString(String value) {
        return new NotificationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "NotificationId[" + value + "]";
    }
}
