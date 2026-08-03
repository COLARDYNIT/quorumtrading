package com.quorumtrading.adapter.persistence.dispatcher;

import com.quorumtrading.application.port.out.EmailSender;
import com.quorumtrading.application.port.out.NotificationRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.service.EmailTemplateRegistry;
import com.quorumtrading.application.service.UnsubscribeTokenService;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.NotificationChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.quorumtrading.application.port.out.NotificationDispatcherPort;

/**
 * Renders + sends notification emails. Every outgoing email carries a per-type
 * unsubscribe link via {@link UnsubscribeTokenService}.
 *
 * <p><b>Persistence policy:</b> the {@code Notification} row is persisted in either
 * {@code DELIVERED} (sender accepted the message) or {@code FAILED} (sender rejected,
 * user has no email on file, or the sender is disabled) state. We never write
 * {@code PENDING} to the database from this dispatcher — that intermediate state
 * is invisible to the user and would complicate the inbox UI.</p>
 */
@ApplicationScoped
public class EmailNotificationDispatcher implements NotificationDispatcherPort {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationDispatcher.class);

    @Inject EmailSender emailSender;
    @Inject NotificationRepository notificationRepository;
    @Inject UserRepository userRepository;
    @Inject EmailTemplateRegistry templateRegistry;
    @Inject UnsubscribeTokenService unsubscribeTokenService;

    /**
     * Host for the unsubscribe link — must be the API host, because the unsubscribe endpoint
     * lives under {@code /api/v1}. Under host-based routing (prod: api.quorumtrading.com vs
     * quorumtrading.com) that is a different host than the CTA links, hence the split.
     */
    @Inject
    @ConfigProperty(name = "quorumtrading.notifications.email.api-base-url",
            defaultValue = "http://localhost:8085")
    String apiBaseUrl;

    /** Host for CTA links in email bodies — the web app (simulations, plans, strategies). */
    @Inject
    @ConfigProperty(name = "quorumtrading.frontend.base-url",
            defaultValue = "http://localhost:3000")
    String frontendBaseUrl;

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    @Transactional
    public Notification dispatch(Notification notification) {
        if (notification.getChannel() != NotificationChannel.EMAIL) {
            throw new IllegalArgumentException(
                    "EmailNotificationDispatcher cannot dispatch channel " + notification.getChannel());
        }

        if (!emailSender.isEnabled()) {
            logger.debug("Email sender disabled — skipping notification {} for user {}",
                    notification.getId(), notification.getUserId());
            notification.markFailed("email-sender-disabled");
            notificationRepository.save(notification);
            return notification;
        }

        Optional<User> recipientOpt = userRepository.findById(notification.getUserId());
        if (recipientOpt.isEmpty() || recipientOpt.get().getEmail() == null
                || recipientOpt.get().getEmail().isBlank()) {
            logger.warn("Cannot send email notification {} — user {} has no email on file",
                    notification.getId(), notification.getUserId());
            notification.markFailed("recipient-has-no-email");
            notificationRepository.save(notification);
            return notification;
        }

        String unsubscribeUrl = buildUnsubscribeUrl(notification);
        EmailTemplateRegistry.Rendered rendered = templateRegistry.render(
                notification, unsubscribeUrl, frontendBaseUrl);

        EmailSender.EmailMessage message = new EmailSender.EmailMessage(
                recipientOpt.get().getEmail(),
                rendered.subject(),
                rendered.plainText(),
                rendered.html());

        EmailSender.Result result = emailSender.send(message);
        if (result.delivered()) {
            notification.markDelivered();
            logger.debug("Sent email notification {} to user {} (type={})",
                    notification.getId(), notification.getUserId(), notification.getType());
        } else {
            notification.markFailed(result.failureReason() == null
                    ? "unknown-send-failure"
                    : result.failureReason());
            logger.warn("Failed to send email notification {} (user={}, reason={})",
                    notification.getId(), notification.getUserId(), result.failureReason());
        }
        notificationRepository.save(notification);
        return notification;
    }

    private String buildUnsubscribeUrl(Notification notification) {
        String token = unsubscribeTokenService.mint(notification.getUserId(), notification.getType());
        return apiBaseUrl + "/api/v1/notification-preferences/unsubscribe?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
