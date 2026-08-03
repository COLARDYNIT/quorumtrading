package com.quorumtrading.model.enums;

/**
 * Delivery channel for a {@code Notification}.
 *
 * <p>Each enabled channel materialises as one {@code Notification} row, so
 * delivery state is tracked per channel.</p>
 */
public enum NotificationChannel {
    /** Persisted in the in-app inbox; surfaced via the bell icon in the web UI. */
    IN_APP,
    /** Delivered as email (adapter wired in a later phase). */
    EMAIL
}
