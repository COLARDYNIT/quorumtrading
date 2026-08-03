package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.NotificationResponse;
import com.quorumtrading.adapter.rest.dto.response.UnreadCountResponse;
import com.quorumtrading.adapter.rest.exception.NotFoundException;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.command.MarkNotificationReadCommand;
import com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.GetNotificationUseCase;
import com.quorumtrading.application.usecase.notification.ListNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.MarkAllNotificationsReadUseCase;
import com.quorumtrading.application.usecase.notification.MarkNotificationReadUseCase;
import com.quorumtrading.application.usecase.query.ListNotificationsQuery;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * In-app notification inbox. All endpoints are user-scoped — there is no admin
 * cross-user view here. Email/push channels do not surface through these routes.
 */
@Path("/api/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Notifications", description = "In-app notification inbox")
public class NotificationResource {

    @Inject
    ListNotificationsUseCase listNotificationsUseCase;

    @Inject
    GetNotificationUseCase getNotificationUseCase;

    @Inject
    MarkNotificationReadUseCase markNotificationReadUseCase;

    @Inject
    MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;

    @Inject
    CountUnreadNotificationsUseCase countUnreadNotificationsUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    @Operation(summary = "List notifications",
            description = "Returns notifications for the current user, newest first. "
                    + "Set status=unread to restrict to in-app DELIVERED rows (drives the bell badge).")
    public List<NotificationResponse> list(
            @QueryParam("status") String status,
            @QueryParam("limit") @DefaultValue("50") int limit) {

        UserId userId = currentUser.getCurrentUserId();
        boolean onlyUnread = "unread".equalsIgnoreCase(status);

        List<Notification> notifications = listNotificationsUseCase.execute(
                new ListNotificationsQuery(userId, onlyUnread, limit));

        return notifications.stream()
                .map(NotificationResponse::fromDomain)
                .toList();
    }

    @GET
    @Path("/unread-count")
    @Operation(summary = "Count unread notifications",
            description = "Cheap endpoint for the bell badge. Polled by the header component.")
    public UnreadCountResponse unreadCount() {
        UserId userId = currentUser.getCurrentUserId();
        return new UnreadCountResponse(countUnreadNotificationsUseCase.countUnread(userId));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get notification by id")
    public NotificationResponse getById(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        try {
            NotificationId notificationId = NotificationId.fromString(id);
            Notification n = getNotificationUseCase.execute(notificationId, userId);
            return NotificationResponse.fromDomain(n);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            throw new NotFoundException("Notification not found: " + id);
        } catch (SecurityException e) {
            throw new NotFoundException("Notification not found: " + id);
        }
    }

    @POST
    @Path("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public Response markRead(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        try {
            NotificationId notificationId = NotificationId.fromString(id);
            markNotificationReadUseCase.execute(
                    new MarkNotificationReadCommand(notificationId, userId));
            return Response.noContent().build();
        } catch (IllegalArgumentException | NoSuchElementException e) {
            throw new NotFoundException("Notification not found: " + id);
        } catch (SecurityException e) {
            throw new NotFoundException("Notification not found: " + id);
        }
    }

    @POST
    @Path("/read-all")
    @Operation(summary = "Mark all unread notifications as read")
    public UnreadCountResponse markAllRead() {
        UserId userId = currentUser.getCurrentUserId();
        int updated = markAllNotificationsReadUseCase.execute(userId);
        return new UnreadCountResponse(updated);
    }
}
