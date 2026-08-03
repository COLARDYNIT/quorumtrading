package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpNotificationDto;
import com.quorumtrading.adapter.mcp.dto.McpNotificationPreferenceDto;
import com.quorumtrading.application.usecase.command.MarkNotificationReadCommand;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase;
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
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * MCP tools for the user-facing notification inbox.
 *
 * <p>All tools are user-scoped via {@link McpUserContext} — there is no
 * cross-user view. Email/push delivery state is visible only as the
 * per-channel {@code status} on each notification row.</p>
 */
@Singleton
@ActivateRequestContext
public class NotificationTools extends McpToolBase {

    @Inject ListNotificationsUseCase listNotificationsUseCase;
    @Inject GetNotificationUseCase getNotificationUseCase;
    @Inject MarkNotificationReadUseCase markNotificationReadUseCase;
    @Inject MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;
    @Inject CountUnreadNotificationsUseCase countUnreadNotificationsUseCase;
    @Inject GetNotificationPreferencesUseCase getNotificationPreferencesUseCase;
    @Inject UpdateNotificationPreferenceUseCase updateNotificationPreferenceUseCase;

    @Inject McpUserContext mcpUserContext;

    /**
     * Whether this instance can actually send email. Off by default in every environment, so a
     * preference row listing EMAIL is a statement of intent, not of delivery.
     */
    @ConfigProperty(name = "quorumtrading.notifications.email.enabled", defaultValue = "false")
    boolean emailDeliveryEnabled;

    @Tool(name = "list_notifications", description =
            "List in-app notifications for the current user, newest first. "
                    + "Use status='unread' to restrict to undelivered/unread in-app rows "
                    + "(useful for 'what happened since I last checked?'). "
                    + "Types: INVESTMENT_EXECUTED, INVESTMENT_SKIPPED, INVESTMENT_FAILED, PLAN_PAUSED, "
                    + "SIMULATION_DECISION, SIMULATION_PAUSED_BUDGET, SIMULATION_LOW_BUDGET, "
                    + "MARKET_RESEARCH_STRATEGY_CREATED, BROKER_REAUTH_REQUIRED, GENERIC.")
    public ToolResponse listNotifications(
            @ToolArg(description = "Optional filter: 'unread' or 'all' (default 'all')") Optional<String> status,
            @ToolArg(description = "Max rows to return (default 50, max 200)") Optional<Integer> limit
    ) {
        return execute(() -> {
            StatusFilter filter = status.filter(s -> !s.isBlank())
                    .map(s -> parseEnum(StatusFilter.class, s, "status"))
                    .orElse(StatusFilter.ALL);
            boolean onlyUnread = filter == StatusFilter.UNREAD;
            int safeLimit = parseLimit(limit, 50, 200);

            UserId userId = mcpUserContext.getUserId();
            List<Notification> notifications = listNotificationsUseCase.execute(
                    new ListNotificationsQuery(userId, onlyUnread, safeLimit));

            List<McpNotificationDto> dtos = notifications.stream().map(this::toDto).toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("notifications", dtos);
            body.put("count", dtos.size());
            return toJson(body);
        });
    }

    @Tool(name = "count_unread_notifications", description =
            "Return the number of unread in-app notifications for the current user. "
                    + "Cheap call — used to decide whether to fetch the full list.")
    public ToolResponse countUnreadNotifications() {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            long count = countUnreadNotificationsUseCase.countUnread(userId);
            return toJson(Map.of("unread", count));
        });
    }

    @Tool(name = "get_notification", description =
            "Fetch a single notification by its UUID. Returns 404-style error if the "
                    + "notification does not exist or belongs to another user.")
    public ToolResponse getNotification(
            @ToolArg(description = "Notification UUID") String notificationId
    ) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            NotificationId id = NotificationId.fromString(notificationId);
            Notification n = getNotificationUseCase.execute(id, userId);
            return toJson(toDto(n));
        });
    }

    @Tool(name = "mark_notification_read", description =
            "Mark a single in-app notification as read. Idempotent — already-read "
                    + "notifications return success. Only IN_APP rows can be marked read.")
    public ToolResponse markNotificationRead(
            @ToolArg(description = "Notification UUID") String notificationId
    ) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            NotificationId id = NotificationId.fromString(notificationId);
            markNotificationReadUseCase.execute(new MarkNotificationReadCommand(id, userId));
            return toJson(Map.of("status", "read", "id", notificationId));
        });
    }

    @Tool(name = "mark_all_notifications_read", description =
            "Mark every unread in-app notification for the current user as read. "
                    + "Returns the number of rows updated.")
    public ToolResponse markAllNotificationsRead() {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            int updated = markAllNotificationsReadUseCase.execute(userId);
            return toJson(Map.of("markedRead", updated));
        });
    }

    @Tool(name = "get_notification_preferences", description =
            "List the current user's notification preferences. One row per notification type. "
                    + "Types without an explicit preference fall back to system defaults "
                    + "(and are returned with those defaults filled in). "
                    + "Check emailDeliveryEnabled in the response before trusting an EMAIL channel: "
                    + "outgoing email is disabled by default on a self-hosted instance, and a "
                    + "channel listed there delivers nothing until an operator enables it.")
    public ToolResponse getNotificationPreferences() {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            List<NotificationPreference> prefs = getNotificationPreferencesUseCase.listForUser(userId);
            List<McpNotificationPreferenceDto> dtos = prefs.stream().map(this::toPrefDto).toList();
            // A preference row says which channels the user WANTS; it says nothing about whether
            // the instance can deliver on them. Listing EMAIL on an install where email is off —
            // for INVESTMENT_FAILED and BROKER_REAUTH_REQUIRED especially, and for GENERIC, which
            // is email-only and so reaches the user on no channel at all — is the same
            // "silently does nothing" failure the DCA scheduler flag already solves properly.
            var body = new LinkedHashMap<String, Object>();
            body.put("preferences", dtos);
            body.put("emailDeliveryEnabled", emailDeliveryEnabled);
            if (!emailDeliveryEnabled) {
                body.put("emailDeliveryNote",
                        "Outgoing email is disabled on this instance, so any EMAIL channel below "
                                + "delivers nothing — only IN_APP notifications arrive. Types whose "
                                + "only channel is EMAIL reach you nowhere. An operator enables it "
                                + "with quorumtrading.notifications.email.enabled=true (see "
                                + "docs/operations/EMAIL_READINESS.md).");
            }
            return toJson(body);
        });
    }

    @Tool(name = "update_notification_preference", description =
            "Set the enabled channels for one notification type. "
                    + "Pass an empty 'channels' to opt out of this type entirely. "
                    + "Valid types: INVESTMENT_EXECUTED, INVESTMENT_SKIPPED, INVESTMENT_FAILED, "
                    + "PLAN_PAUSED, SIMULATION_DECISION, SIMULATION_PAUSED_BUDGET, "
                    + "SIMULATION_LOW_BUDGET, MARKET_RESEARCH_STRATEGY_CREATED, "
                    + "BROKER_REAUTH_REQUIRED, GENERIC. "
                    + "Valid channels: IN_APP, EMAIL.")
    public ToolResponse updateNotificationPreference(
            @ToolArg(description = "Notification type") String type,
            @ToolArg(description = "Channels to enable (e.g. ['IN_APP','EMAIL']); empty to opt out") List<String> channels
    ) {
        return execute(() -> {
            UserId userId = mcpUserContext.getUserId();
            NotificationType parsedType = parseEnum(NotificationType.class, type, "type");

            Set<NotificationChannel> parsedChannels = EnumSet.noneOf(NotificationChannel.class);
            if (channels != null) {
                for (String c : channels) {
                    if (c == null || c.isBlank()) continue;
                    parsedChannels.add(parseEnum(NotificationChannel.class, c, "channel"));
                }
            }

            NotificationPreference updated = updateNotificationPreferenceUseCase.execute(
                    new UpdateNotificationPreferenceCommand(userId, parsedType, parsedChannels));
            return toJson(toPrefDto(updated));
        });
    }

    /**
     * The values {@code list_notifications} accepts for {@code status}.
     *
     * <p>An enum rather than a string compare so an unrecognised value is refused with the same
     * message {@code list_challenges} gives. Every value other than 'unread' used to mean "all":
     * {@code status=read} returned all 39 rows, from which the only available conclusion was that
     * all 39 were read. There is deliberately no READ member — the query underneath cannot express
     * it, and offering a filter that silently widens is what caused the defect.</p>
     */
    private enum StatusFilter { UNREAD, ALL }

    private McpNotificationDto toDto(Notification n) {
        return new McpNotificationDto(
                n.getId().value().toString(),
                n.getType().name(),
                n.getChannel().name(),
                n.getTitle(),
                n.getBody(),
                n.getPayload(),
                n.getRelatedEntityId() != null ? n.getRelatedEntityId().toString() : null,
                n.getStatus().name(),
                n.getCreatedAt() != null ? n.getCreatedAt().toString() : null,
                n.getDeliveredAt() != null ? n.getDeliveredAt().toString() : null,
                n.getReadAt() != null ? n.getReadAt().toString() : null,
                n.getFailureReason()
        );
    }

    private McpNotificationPreferenceDto toPrefDto(NotificationPreference p) {
        List<String> channels = p.getChannels().stream().map(Enum::name).sorted().toList();
        return new McpNotificationPreferenceDto(p.getType().name(), channels);
    }
}
