package com.quorumtrading.model.enums;

/**
 * Delivery lifecycle of a single {@code Notification} on its channel.
 *
 * <p>Lifecycle: {@code PENDING} → {@code DELIVERED} → optionally {@code READ}
 * (in-app only), or {@code PENDING} → {@code FAILED}.</p>
 */
public enum NotificationStatus {
    PENDING,
    DELIVERED,
    READ,
    FAILED
}
