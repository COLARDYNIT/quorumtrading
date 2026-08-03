package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.NotificationDispatcherPort;
import com.quorumtrading.application.port.out.NotificationPreferenceRepository;
import com.quorumtrading.application.port.out.NotificationRepository;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.command.MarkNotificationReadCommand;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase;
import com.quorumtrading.application.usecase.notification.GetNotificationUseCase;
import com.quorumtrading.application.usecase.notification.ListNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.MarkAllNotificationsReadUseCase;
import com.quorumtrading.application.usecase.notification.MarkNotificationReadUseCase;
import com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase;
import com.quorumtrading.application.usecase.query.ListNotificationsQuery;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Application service implementing notification CRUD, preference management,
 * and channel fan-out. Pure Java — wired in {@code ApplicationConfig}.
 *
 * <p>Hard-coded defaults are applied when a user has no explicit preference
 * row for a given type. The defaults can evolve without per-user backfills
 * because we never materialise a "default row" in the database.</p>
 */
public class NotificationService implements
        ListNotificationsUseCase,
        GetNotificationUseCase,
        MarkNotificationReadUseCase,
        MarkAllNotificationsReadUseCase,
        CountUnreadNotificationsUseCase,
        GetNotificationPreferencesUseCase,
        UpdateNotificationPreferenceUseCase,
        DispatchNotificationUseCase {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    /** System defaults applied when the user has no explicit preference for a type. */
    private static final Map<NotificationType, Set<NotificationChannel>> DEFAULT_CHANNELS;
    static {
        Map<NotificationType, Set<NotificationChannel>> m = new EnumMap<>(NotificationType.class);
        m.put(NotificationType.INVESTMENT_EXECUTED,              Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        m.put(NotificationType.INVESTMENT_SKIPPED,               Set.of(NotificationChannel.IN_APP));
        m.put(NotificationType.INVESTMENT_FAILED,                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        m.put(NotificationType.PLAN_PAUSED,                      Set.of(NotificationChannel.IN_APP));
        m.put(NotificationType.SIMULATION_DECISION,              Set.of(NotificationChannel.IN_APP));
        m.put(NotificationType.SIMULATION_PAUSED_BUDGET,         Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        m.put(NotificationType.SIMULATION_LOW_BUDGET,            Set.of(NotificationChannel.IN_APP));
        m.put(NotificationType.MARKET_RESEARCH_STRATEGY_CREATED, Set.of(NotificationChannel.IN_APP));
        m.put(NotificationType.BROKER_REAUTH_REQUIRED,           Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        m.put(NotificationType.GENERIC,                          Set.of(NotificationChannel.IN_APP));
        DEFAULT_CHANNELS = Map.copyOf(m);
    }

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final Map<NotificationChannel, NotificationDispatcherPort> dispatchers;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               List<NotificationDispatcherPort> dispatchers) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        Map<NotificationChannel, NotificationDispatcherPort> map = new HashMap<>();
        for (NotificationDispatcherPort d : dispatchers) {
            NotificationDispatcherPort prev = map.put(d.supportedChannel(), d);
            if (prev != null) {
                throw new IllegalStateException(
                        "Multiple dispatchers registered for channel " + d.supportedChannel());
            }
        }
        this.dispatchers = Map.copyOf(map);
    }

    // ---- Queries ----

    @Override
    public List<Notification> execute(ListNotificationsQuery query) {
        return notificationRepository.findByUser(query.userId(), query.onlyUnread(), query.limit());
    }

    @Override
    public Notification execute(NotificationId id, UserId userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + id));
        if (!notification.getUserId().equals(userId)) {
            throw new SecurityException("Notification does not belong to user: " + userId);
        }
        return notification;
    }

    @Override
    public long countUnread(UserId userId) {
        return notificationRepository.countUnread(userId);
    }

    // ---- Read state ----

    @Override
    public void execute(MarkNotificationReadCommand command) {
        Notification notification = notificationRepository.findById(command.notificationId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Notification not found: " + command.notificationId()));
        if (!notification.getUserId().equals(command.userId())) {
            throw new SecurityException("Notification does not belong to user: " + command.userId());
        }
        if (notification.getStatus().name().equals("READ")) {
            return; // idempotent
        }
        notification.markRead();
        notificationRepository.save(notification);
    }

    @Override
    public int execute(UserId userId) {
        int updated = notificationRepository.markAllRead(userId);
        logger.info("Marked {} notifications read for user {}", updated, userId);
        return updated;
    }

    // ---- Preferences ----

    @Override
    public List<NotificationPreference> listForUser(UserId userId) {
        List<NotificationPreference> stored = preferenceRepository.findByUser(userId);
        Map<NotificationType, NotificationPreference> byType = new EnumMap<>(NotificationType.class);
        for (NotificationPreference p : stored) {
            byType.put(p.getType(), p);
        }
        List<NotificationPreference> effective = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            NotificationPreference pref = byType.get(type);
            if (pref == null) {
                pref = NotificationPreference.create(userId, type, DEFAULT_CHANNELS.get(type));
            }
            effective.add(pref);
        }
        return effective;
    }

    @Override
    public NotificationPreference execute(UpdateNotificationPreferenceCommand command) {
        NotificationPreference pref = preferenceRepository.find(command.userId(), command.type())
                .orElseGet(() -> NotificationPreference.create(
                        command.userId(), command.type(), command.channels()));
        pref.setChannels(command.channels());
        return preferenceRepository.save(pref);
    }

    // ---- Dispatch ----

    @Override
    public List<Notification> execute(DispatchNotificationCommand command) {
        Set<NotificationChannel> channels = resolveChannels(command.userId(), command.type());
        if (channels.isEmpty()) {
            logger.debug("User {} opted out of {}, no notifications dispatched",
                    command.userId(), command.type());
            return List.of();
        }

        List<Notification> results = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            NotificationDispatcherPort dispatcher = dispatchers.get(channel);
            if (dispatcher == null) {
                logger.warn("No dispatcher registered for channel {} (user={}, type={}). Skipping.",
                        channel, command.userId(), command.type());
                continue;
            }
            Notification notification = Notification.create(
                    NotificationId.generate(),
                    command.userId(),
                    command.type(),
                    channel,
                    command.title(),
                    command.body(),
                    command.payload(),
                    command.relatedEntityId());
            try {
                Notification dispatched = dispatcher.dispatch(notification);
                results.add(dispatched);
            } catch (Exception e) {
                // Dispatchers are not supposed to throw, but defend against bugs.
                logger.error("Dispatcher for {} threw unexpectedly (notificationId={}, user={}, type={})",
                        channel, notification.getId(), command.userId(), command.type(), e);
                notification.markFailed("Dispatcher threw: " + e.getMessage());
                results.add(notification);
            }
        }
        return results;
    }

    private Set<NotificationChannel> resolveChannels(UserId userId, NotificationType type) {
        return preferenceRepository.find(userId, type)
                .map(NotificationPreference::getChannels)
                .orElseGet(() -> DEFAULT_CHANNELS.getOrDefault(type, Set.of()));
    }

    /** Visible for tests / config helpers. */
    public static Set<NotificationChannel> defaultChannelsFor(NotificationType type) {
        return DEFAULT_CHANNELS.getOrDefault(type, Set.of());
    }

    /** All known types, for listing in preferences. */
    public static List<NotificationType> allTypes() {
        return Arrays.asList(NotificationType.values());
    }
}
