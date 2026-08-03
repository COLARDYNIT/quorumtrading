package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.NotificationDispatcherPort;
import com.quorumtrading.application.port.out.NotificationPreferenceRepository;
import com.quorumtrading.application.port.out.NotificationRepository;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.command.MarkNotificationReadCommand;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private NotificationDispatcherPort inAppDispatcher;
    private NotificationDispatcherPort emailDispatcher;
    private NotificationService service;

    private UserId userId;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        inAppDispatcher = mock(NotificationDispatcherPort.class);
        emailDispatcher = mock(NotificationDispatcherPort.class);

        when(inAppDispatcher.supportedChannel()).thenReturn(NotificationChannel.IN_APP);
        when(emailDispatcher.supportedChannel()).thenReturn(NotificationChannel.EMAIL);
        when(inAppDispatcher.dispatch(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.markDelivered();
            return n;
        });
        when(emailDispatcher.dispatch(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.markDelivered();
            return n;
        });
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new NotificationService(
                notificationRepository, preferenceRepository,
                List.of(inAppDispatcher, emailDispatcher));
        userId = new UserId(UUID.randomUUID());
    }

    @Test
    void duplicateDispatcherChannelIsRejected() {
        NotificationDispatcherPort other = mock(NotificationDispatcherPort.class);
        when(other.supportedChannel()).thenReturn(NotificationChannel.IN_APP);
        assertThatThrownBy(() -> new NotificationService(
                notificationRepository, preferenceRepository,
                List.of(inAppDispatcher, other)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_APP");
    }

    // --- Dispatch ---

    @Test
    void dispatchAppliesSystemDefaultWhenNoPreference() {
        when(preferenceRepository.find(userId, NotificationType.INVESTMENT_EXECUTED))
                .thenReturn(Optional.empty());

        List<Notification> result = service.execute(new DispatchNotificationCommand(
                userId, NotificationType.INVESTMENT_EXECUTED,
                "Investment funded", "body", Map.of("amount", 200), null));

        // Default for INVESTMENT_EXECUTED is IN_APP + EMAIL.
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getStatus() == NotificationStatus.DELIVERED);
        verify(inAppDispatcher).dispatch(any(Notification.class));
        verify(emailDispatcher).dispatch(any(Notification.class));
    }

    @Test
    void brokerReauthRequiredFansOutToInAppAndEmailByDefault() {
        when(preferenceRepository.find(userId, NotificationType.BROKER_REAUTH_REQUIRED))
                .thenReturn(Optional.empty());

        List<Notification> result = service.execute(new DispatchNotificationCommand(
                userId, NotificationType.BROKER_REAUTH_REQUIRED,
                "Re-authorize your broker", "Your connection expired", Map.of(), null));

        // Default for BROKER_REAUTH_REQUIRED is IN_APP + EMAIL.
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getStatus() == NotificationStatus.DELIVERED);
        assertThat(result).extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        verify(inAppDispatcher).dispatch(any(Notification.class));
        verify(emailDispatcher).dispatch(any(Notification.class));
    }

    @Test
    void brokerReauthRequiredDefaultChannelsAreInAppAndEmail() {
        assertThat(NotificationService.defaultChannelsFor(NotificationType.BROKER_REAUTH_REQUIRED))
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
    }

    @Test
    void dispatchHonoursUserPreference() {
        NotificationPreference pref = NotificationPreference.create(
                userId, NotificationType.INVESTMENT_EXECUTED, Set.of(NotificationChannel.IN_APP));
        when(preferenceRepository.find(userId, NotificationType.INVESTMENT_EXECUTED))
                .thenReturn(Optional.of(pref));

        List<Notification> result = service.execute(new DispatchNotificationCommand(
                userId, NotificationType.INVESTMENT_EXECUTED,
                "title", "body", Map.of(), null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannel()).isEqualTo(NotificationChannel.IN_APP);
        verify(inAppDispatcher).dispatch(any(Notification.class));
        verify(emailDispatcher, never()).dispatch(any(Notification.class));
    }

    @Test
    void emptyPreferenceMeansOptOut() {
        NotificationPreference pref = NotificationPreference.create(
                userId, NotificationType.INVESTMENT_EXECUTED, Set.of());
        when(preferenceRepository.find(userId, NotificationType.INVESTMENT_EXECUTED))
                .thenReturn(Optional.of(pref));

        List<Notification> result = service.execute(new DispatchNotificationCommand(
                userId, NotificationType.INVESTMENT_EXECUTED,
                "title", "body", Map.of(), null));

        assertThat(result).isEmpty();
        verify(inAppDispatcher, never()).dispatch(any(Notification.class));
        verify(emailDispatcher, never()).dispatch(any(Notification.class));
    }

    @Test
    void dispatcherExceptionRecordedAsFailed() {
        when(preferenceRepository.find(userId, NotificationType.GENERIC))
                .thenReturn(Optional.of(NotificationPreference.create(
                        userId, NotificationType.GENERIC, Set.of(NotificationChannel.IN_APP))));
        when(inAppDispatcher.dispatch(any(Notification.class)))
                .thenThrow(new RuntimeException("boom"));

        List<Notification> result = service.execute(new DispatchNotificationCommand(
                userId, NotificationType.GENERIC, "title", null, Map.of(), null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.get(0).getFailureReason()).contains("boom");
    }

    // --- Mark read ---

    @Test
    void markReadEnforcesOwnership() {
        UserId other = new UserId(UUID.randomUUID());
        Notification owned = Notification.create(
                NotificationId.generate(), other, NotificationType.GENERIC,
                NotificationChannel.IN_APP, "t", "b", Map.of(), null);
        owned.markDelivered();
        when(notificationRepository.findById(owned.getId())).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.execute(new MarkNotificationReadCommand(
                owned.getId(), userId)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void markReadOnMissingNotificationThrows() {
        NotificationId id = NotificationId.generate();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(
                new MarkNotificationReadCommand(id, userId)))
                .isInstanceOf(NoSuchElementException.class);
    }

    // --- Preferences ---

    @Test
    void listForUserFillsInDefaults() {
        when(preferenceRepository.findByUser(userId)).thenReturn(List.of());
        List<NotificationPreference> prefs = service.listForUser(userId);
        assertThat(prefs).hasSize(NotificationType.values().length);
        // Spot-check the default for INVESTMENT_EXECUTED.
        NotificationPreference investmentPref = prefs.stream()
                .filter(p -> p.getType() == NotificationType.INVESTMENT_EXECUTED)
                .findFirst().orElseThrow();
        assertThat(investmentPref.getChannels())
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
    }

    @Test
    void updatePreferenceCreatesRowWhenAbsent() {
        when(preferenceRepository.find(userId, NotificationType.PLAN_PAUSED))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationPreference updated = service.execute(new UpdateNotificationPreferenceCommand(
                userId, NotificationType.PLAN_PAUSED, Set.of(NotificationChannel.EMAIL)));

        assertThat(updated.getChannels()).containsExactly(NotificationChannel.EMAIL);
        verify(preferenceRepository).save(any(NotificationPreference.class));
    }
}
