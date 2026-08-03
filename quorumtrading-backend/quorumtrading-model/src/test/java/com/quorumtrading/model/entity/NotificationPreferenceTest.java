package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPreferenceTest {

    @Test
    void createsWithProvidedChannels() {
        NotificationPreference pref = NotificationPreference.create(
                new UserId(UUID.randomUUID()),
                NotificationType.INVESTMENT_EXECUTED,
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        assertEquals(2, pref.getChannels().size());
        assertTrue(pref.isEnabled(NotificationChannel.IN_APP));
        assertTrue(pref.isEnabled(NotificationChannel.EMAIL));
    }

    @Test
    void nullChannelsBecomeEmptySet() {
        NotificationPreference pref = NotificationPreference.create(
                new UserId(UUID.randomUUID()),
                NotificationType.GENERIC,
                null);
        assertTrue(pref.getChannels().isEmpty());
    }

    @Test
    void setChannelsReplacesWholesale() {
        NotificationPreference pref = NotificationPreference.create(
                new UserId(UUID.randomUUID()),
                NotificationType.PLAN_PAUSED,
                Set.of(NotificationChannel.IN_APP));
        pref.setChannels(Set.of(NotificationChannel.EMAIL));
        assertFalse(pref.isEnabled(NotificationChannel.IN_APP));
        assertTrue(pref.isEnabled(NotificationChannel.EMAIL));
    }

    @Test
    void setChannelsEmptyMeansOptOut() {
        NotificationPreference pref = NotificationPreference.create(
                new UserId(UUID.randomUUID()),
                NotificationType.INVESTMENT_SKIPPED,
                Set.of(NotificationChannel.IN_APP));
        pref.setChannels(Set.of());
        assertTrue(pref.getChannels().isEmpty());
    }

    @Test
    void getChannelsReturnsDefensiveCopy() {
        NotificationPreference pref = NotificationPreference.create(
                new UserId(UUID.randomUUID()),
                NotificationType.GENERIC,
                Set.of(NotificationChannel.IN_APP));
        Set<NotificationChannel> view = pref.getChannels();
        view.add(NotificationChannel.EMAIL);
        // Mutation must not leak back into the aggregate.
        assertFalse(pref.isEnabled(NotificationChannel.EMAIL));
    }

    @Test
    void equalityByUserIdAndType() {
        UserId user = new UserId(UUID.randomUUID());
        NotificationPreference a = NotificationPreference.create(
                user, NotificationType.GENERIC, Set.of(NotificationChannel.IN_APP));
        NotificationPreference b = NotificationPreference.create(
                user, NotificationType.GENERIC, Set.of(NotificationChannel.EMAIL));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
