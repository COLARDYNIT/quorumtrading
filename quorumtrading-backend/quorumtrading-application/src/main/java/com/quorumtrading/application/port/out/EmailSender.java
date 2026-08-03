package com.quorumtrading.application.port.out;

/**
 * Output port for sending one email through whatever provider the deployment is wired to
 * (Quarkus mailer over SES SMTP, MailHog locally, etc.).
 *
 * <p>Implementations should NOT throw on send failure — instead, surface the result
 * via the returned {@link Result}. Higher-up notification dispatcher logic decides
 * whether to mark the {@code Notification} {@code DELIVERED} or {@code FAILED}.</p>
 */
public interface EmailSender {

    Result send(EmailMessage message);

    /**
     * Whether the underlying provider is configured and enabled. When false,
     * {@link #send} is a no-op that returns {@code Result.disabled()}. Lets the
     * notification dispatcher short-circuit cleanly when email delivery is gated
     * off (default in dev/test).
     */
    boolean isEnabled();

    record EmailMessage(
            String to,
            String subject,
            String plainTextBody,
            String htmlBody
    ) {
        public EmailMessage {
            if (to == null || to.isBlank()) {
                throw new IllegalArgumentException("recipient `to` is required");
            }
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("subject is required");
            }
            if (plainTextBody == null || plainTextBody.isBlank()) {
                throw new IllegalArgumentException("plainTextBody is required");
            }
        }
    }

    record Result(boolean delivered, String failureReason) {
        public static Result ok() { return new Result(true, null); }
        public static Result failed(String reason) { return new Result(false, reason); }
        public static Result disabled() { return new Result(false, "email-sender-disabled"); }
    }
}
