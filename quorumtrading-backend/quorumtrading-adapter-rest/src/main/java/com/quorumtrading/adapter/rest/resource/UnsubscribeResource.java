package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.service.UnsubscribeTokenService;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase;
import com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.EnumSet;
import java.util.Set;

/**
 * Public unsubscribe endpoint hit by the link embedded in every outgoing email.
 *
 * <p>Token-based — no session required. The token encodes (userId, type) and is
 * signed by {@link UnsubscribeTokenService}. We never reveal an email address or
 * any other user data in the response; the page just confirms which type was
 * disabled.</p>
 *
 * <p>Per-type unsubscribe: only the EMAIL channel for the encoded type is turned
 * off; in-app notifications continue to flow.</p>
 */
@Path("/api/v1/notification-preferences/unsubscribe")
@Produces(MediaType.TEXT_HTML)
@ApplicationScoped
@Tag(name = "Notification Preferences",
        description = "Per-type channel selection (unsubscribe entry point lives here too)")
@PermitAll
public class UnsubscribeResource {

    @Inject UnsubscribeTokenService tokenService;
    @Inject GetNotificationPreferencesUseCase getPreferencesUseCase;
    @Inject UpdateNotificationPreferenceUseCase updatePreferenceUseCase;

    @GET
    @Operation(summary = "Unsubscribe from a notification type via signed token",
            description = "Public endpoint hit by the link in outgoing emails. Toggles off the EMAIL "
                    + "channel for the encoded notification type. Idempotent — replays do not error.")
    public Response unsubscribe(@QueryParam("token") String token) {
        return tokenService.verify(token)
                .map(verified -> {
                    NotificationPreference current = getPreferencesUseCase.listForUser(verified.userId()).stream()
                            .filter(p -> p.getType() == verified.type())
                            .findFirst()
                            .orElseGet(() -> NotificationPreference.create(
                                    verified.userId(), verified.type(), Set.of()));
                    // noneOf, not copyOf, for the empty case: EnumSet.copyOf throws
                    // IllegalArgumentException("Collection is empty") when handed an empty
                    // collection that is not itself an EnumSet. That is the DEFAULT state for
                    // most users — a preference row exists only once someone changes something,
                    // so anyone on system defaults who clicked the unsubscribe link in an email
                    // got an error page instead of being unsubscribed.
                    Set<NotificationChannel> next = current.getChannels().isEmpty()
                            ? EnumSet.noneOf(NotificationChannel.class)
                            : EnumSet.copyOf(current.getChannels());
                    next.remove(NotificationChannel.EMAIL);
                    updatePreferenceUseCase.execute(new UpdateNotificationPreferenceCommand(
                            verified.userId(), verified.type(), next));
                    return Response.ok(successHtml(verified.type().name())).build();
                })
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST)
                        .entity(errorHtml())
                        .build());
    }

    private static String successHtml(String typeName) {
        return """
                <!doctype html>
                <html><body style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#222;max-width:560px;margin:60px auto;padding:24px;text-align:center;">
                  <h2 style="color:#0a7;">Unsubscribed</h2>
                  <p>You will no longer receive <strong>%s</strong> emails from QuorumTrading.</p>
                  <p style="color:#666;font-size:14px;">In-app notifications of this type continue.
                  You can re-enable email delivery any time from your notification preferences.</p>
                </body></html>
                """.formatted(typeName);
    }

    private static String errorHtml() {
        return """
                <!doctype html>
                <html><body style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#222;max-width:560px;margin:60px auto;padding:24px;text-align:center;">
                  <h2 style="color:#c00;">Invalid unsubscribe link</h2>
                  <p>The link in your email is missing or has been tampered with.</p>
                  <p style="color:#666;font-size:14px;">You can update your notification preferences from your account settings.</p>
                </body></html>
                """;
    }
}
