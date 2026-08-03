package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    private static Notification create(NotificationChannel channel) {
        return Notification.create(
                NotificationId.generate(),
                new UserId(UUID.randomUUID()),
                NotificationType.INVESTMENT_EXECUTED,
                channel,
                "Investment funded",
                "Body text",
                Map.of("amount", 200),
                UUID.randomUUID());
    }

    @Test
    void newNotificationStartsPending() {
        Notification n = create(NotificationChannel.IN_APP);
        assertEquals(NotificationStatus.PENDING, n.getStatus());
        assertNotNull(n.getCreatedAt());
        assertNull(n.getDeliveredAt());
    }

    @Test
    void rejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> Notification.create(
                NotificationId.generate(),
                new UserId(UUID.randomUUID()),
                NotificationType.GENERIC,
                NotificationChannel.IN_APP,
                "  ",
                "body",
                Map.of(),
                null));
    }

    @Test
    void markDeliveredTransitionsFromPending() {
        Notification n = create(NotificationChannel.IN_APP);
        n.markDelivered();
        assertEquals(NotificationStatus.DELIVERED, n.getStatus());
        assertNotNull(n.getDeliveredAt());
    }

    @Test
    void markDeliveredOnlyAllowedFromPending() {
        Notification n = create(NotificationChannel.IN_APP);
        n.markDelivered();
        assertThrows(IllegalStateException.class, n::markDelivered);
    }

    @Test
    void inAppNotificationCanBeMarkedRead() {
        Notification n = create(NotificationChannel.IN_APP);
        n.markDelivered();
        n.markRead();
        assertEquals(NotificationStatus.READ, n.getStatus());
        assertNotNull(n.getReadAt());
    }

    @Test
    void emailNotificationCannotBeMarkedRead() {
        Notification n = create(NotificationChannel.EMAIL);
        n.markDelivered();
        assertThrows(IllegalStateException.class, n::markRead);
    }

    @Test
    void markReadIsIdempotent() {
        Notification n = create(NotificationChannel.IN_APP);
        n.markDelivered();
        n.markRead();
        n.markRead(); // should not throw
        assertEquals(NotificationStatus.READ, n.getStatus());
    }

    @Test
    void cannotMarkReadFromPending() {
        Notification n = create(NotificationChannel.IN_APP);
        assertThrows(IllegalStateException.class, n::markRead);
    }

    @Test
    void markFailedRecordsReason() {
        Notification n = create(NotificationChannel.EMAIL);
        n.markFailed("SES rate limit");
        assertEquals(NotificationStatus.FAILED, n.getStatus());
        assertEquals("SES rate limit", n.getFailureReason());
    }

    @Test
    void isUnreadOnlyForDeliveredInApp() {
        Notification pending = create(NotificationChannel.IN_APP);
        assertFalse(pending.isUnread());

        Notification delivered = create(NotificationChannel.IN_APP);
        delivered.markDelivered();
        assertTrue(delivered.isUnread());

        Notification emailDelivered = create(NotificationChannel.EMAIL);
        emailDelivered.markDelivered();
        assertFalse(emailDelivered.isUnread());

        delivered.markRead();
        assertFalse(delivered.isUnread());
    }

    @Test
    void payloadIsDefensivelyCopied() {
        java.util.Map<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("planId", "abc");
        Notification n = Notification.create(
                NotificationId.generate(),
                new UserId(UUID.randomUUID()),
                NotificationType.INVESTMENT_EXECUTED,
                NotificationChannel.IN_APP,
                "title", "body", mutable, null);
        mutable.put("planId", "tampered");
        assertEquals("abc", n.getPayload().get("planId"));
    }
}
