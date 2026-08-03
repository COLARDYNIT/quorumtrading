package com.quorumtrading.bootstrap.notification;

import com.quorumtrading.application.port.out.EmailSender;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Concrete {@link EmailSender} backed by the Quarkus Mailer extension.
 *
 * <p>Quarkus Mailer is a thin SMTP client; for production it points at the
 * SES SMTP endpoint with credentials generated through the AWS console. In
 * dev the {@code quarkus.mailer.mock=true} default short-circuits sends to a
 * log line, so no SMTP server is required.</p>
 *
 * <p><b>Disabled by default</b> via {@code quorumtrading.notifications.email.enabled=false}.
 * Even if SMTP is wired correctly, this flag must be flipped on per environment
 * before user-facing email notifications start flowing.</p>
 */
@ApplicationScoped
public class MailerEmailSenderAdapter implements EmailSender {

    private static final Logger LOG = Logger.getLogger(MailerEmailSenderAdapter.class);

    @Inject
    Mailer mailer;

    @Inject
    @ConfigProperty(name = "quorumtrading.notifications.email.enabled", defaultValue = "false")
    boolean enabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Result send(EmailMessage message) {
        if (!enabled) {
            return Result.disabled();
        }
        try {
            Mail mail = Mail.withText(message.to(), message.subject(), message.plainTextBody());
            if (message.htmlBody() != null && !message.htmlBody().isBlank()) {
                mail.setHtml(message.htmlBody());
            }
            // Blocking send is intentional — dispatcher transactions stay simple, and a single
            // outgoing email is fast compared to the orchestration around it.
            mailer.send(mail);
            return Result.ok();
        } catch (Exception e) {
            LOG.errorf(e, "Mailer.send failed for recipient=%s subject=%s",
                    message.to(), message.subject());
            return Result.failed("mailer-error: " + e.getMessage());
        }
    }
}
