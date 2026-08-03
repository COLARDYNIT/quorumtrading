package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.UpdateNotificationPreferenceRequest;
import com.quorumtrading.adapter.rest.dto.response.NotificationPreferenceResponse;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase;
import com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Path("/api/v1/notification-preferences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Notification Preferences", description = "Per-type channel selection")
public class NotificationPreferenceResource {

    @Inject
    GetNotificationPreferencesUseCase getNotificationPreferencesUseCase;

    @Inject
    UpdateNotificationPreferenceUseCase updateNotificationPreferenceUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    @Operation(summary = "List effective notification preferences",
            description = "Returns one row per NotificationType. Types without an explicit user "
                    + "preference fall back to system defaults — these are returned as if stored.")
    public List<NotificationPreferenceResponse> list() {
        UserId userId = currentUser.getCurrentUserId();
        return getNotificationPreferencesUseCase.listForUser(userId).stream()
                .map(NotificationPreferenceResponse::fromDomain)
                .toList();
    }

    @PUT
    @Path("/{type}")
    @Operation(summary = "Update preference for a notification type",
            description = "Set the enabled channels for this type. Empty array opts out entirely.")
    public NotificationPreferenceResponse update(
            @PathParam("type") String type,
            UpdateNotificationPreferenceRequest request) {

        UserId userId = currentUser.getCurrentUserId();
        NotificationType parsedType;
        try {
            parsedType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown notification type: " + type);
        }

        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (request != null && request.channels() != null) {
            for (String c : request.channels()) {
                if (c == null || c.isBlank()) continue;
                try {
                    channels.add(NotificationChannel.valueOf(c.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Unknown channel: " + c);
                }
            }
        }

        NotificationPreference updated = updateNotificationPreferenceUseCase.execute(
                new UpdateNotificationPreferenceCommand(userId, parsedType, channels));
        return NotificationPreferenceResponse.fromDomain(updated);
    }
}
